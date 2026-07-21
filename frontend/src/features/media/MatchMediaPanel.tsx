import { useEffect, useMemo, useState } from 'react'
import {
  AudioTrack,
  LiveKitRoom,
  StartAudio,
  VideoTrack,
  useConnectionQualityIndicator,
  useMediaDeviceSelect,
  useRemoteParticipants,
  useRoomContext,
  useTrackToggle,
  useTracks,
} from '@livekit/components-react'
import { ConnectionState, RemoteTrackPublication, Track, VideoPresets } from 'livekit-client'
import { requestMediaToken } from '../../api/client'
import { mediaServerUrl } from '../../config/runtime'
import { mediaSessionKey } from './mediaSession'

interface MatchMediaPanelProps {
  accountId: string
  matchId: string
  participantCycle: string
  blocked: boolean
}

type MediaStatus = 'OFF' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'ERROR'

export function MatchMediaPanel({ accountId, matchId, participantCycle, blocked }: MatchMediaPanelProps) {
  const consentKey = mediaSessionKey(accountId, matchId, participantCycle)
  const panelKey = `gotg:media-panel:${accountId}:${matchId}`
  const [resumeRequested] = useState(() => sessionStorage.getItem(consentKey) === 'true')
  const [collapsed, setCollapsed] = useState(() => {
    const saved = localStorage.getItem(panelKey)
    return saved ? saved !== 'open' : !resumeRequested
  })
  const [token, setToken] = useState<string>()
  const [status, setStatus] = useState<MediaStatus>('OFF')
  const [error, setError] = useState('')

  const leave = () => {
    sessionStorage.removeItem(consentKey)
    setToken(undefined)
    setStatus('OFF')
    setError('')
  }

  useEffect(() => {
    if (blocked) leave()
  }, [blocked])

  const enable = async () => {
    setStatus('CONNECTING')
    setError('')
    try {
      if (!mediaServerUrl) throw new Error('Media is not configured for this site.')
      const response = await requestMediaToken(matchId)
      if (!response.enabled || response.url !== mediaServerUrl
        || response.room.matchId !== matchId || response.room.participantCountLimit !== 2) {
        throw new Error('The media authorization response was invalid.')
      }
      setToken(response.token)
      sessionStorage.setItem(consentKey, 'true')
    } catch (cause) {
      setStatus('ERROR')
      setError(cause instanceof Error && cause.message === 'Media is not configured for this site.'
        ? cause.message
        : 'Media could not be enabled. Your match is still active.')
    }
  }

  useEffect(() => {
    if (resumeRequested && !blocked) void enable()
    // Resume is intentionally evaluated once for the browser-session consent scoped to this match.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <section className={`media-panel${collapsed ? ' media-panel--collapsed' : ''}`} aria-label="Optional match audio and video">
      <div className="media-panel__heading">
        <div>
          <p className="eyebrow">Optional · peer media only</p>
          <h2>Audio &amp; video</h2>
        </div>
        <button type="button" className="button button--ghost" aria-expanded={!collapsed}
          onClick={() => setCollapsed((value) => {
            localStorage.setItem(panelKey, value ? 'open' : 'closed')
            return !value
          })}>
          {collapsed ? 'Open media' : 'Collapse'}
        </button>
      </div>
      {collapsed && (
        <div className="media-collapsed-preview">
          <div className="media-camera-off" aria-hidden="true">
            <span>▱</span>
            <small>Camera is off</small>
          </div>
          <p className={`media-compact-status media-status--${status.toLowerCase()}`}>
            <span>♩ Mic: off</span><span>▣ Camera: off</span><span>{mediaStatusLabel(status)}</span>
          </p>
        </div>
      )}
      {!collapsed && (
        <>
          <p className="media-consent">
            Microphone and camera start off. Enabling media connects only the two match participants;
            nothing is recorded, and gameplay continues if media fails or you leave.
          </p>
          {!token ? (
            <button type="button" className="button button--secondary"
              disabled={blocked || status === 'CONNECTING'} onClick={() => void enable()}>
              {status === 'CONNECTING' ? 'Connecting…' : 'Enable audio/video'}
            </button>
          ) : (
            <LiveKitRoom
              serverUrl={mediaServerUrl}
              token={token}
              connect
              audio={false}
              video={false}
              screen={false}
              options={{ adaptiveStream: true, dynacast: true, videoCaptureDefaults: { resolution: VideoPresets.h360.resolution } }}
              connectOptions={{ autoSubscribe: true }}
              onConnected={() => setStatus('CONNECTED')}
              onDisconnected={() => {
                setToken(undefined)
                setStatus('ERROR')
                setError('Media disconnected. Your match is still active.')
              }}
              onError={() => {
                setToken(undefined)
                setStatus('ERROR')
                setError('Media could not connect. Your match is still active.')
              }}
              onMediaDeviceFailure={(_failure, kind) => {
                setStatus('ERROR')
                setError(kind === 'audioinput'
                  ? 'Microphone access was blocked or the device is unavailable. Check this site’s browser permission.'
                  : kind === 'videoinput'
                    ? 'Camera access was blocked or the device is unavailable. Check this site’s browser permission.'
                    : 'The selected media device is unavailable. Choose another device or check browser permission.')
              }}
            >
              <ConnectedMedia status={status} setStatus={setStatus} onLeave={leave} />
            </LiveKitRoom>
          )}
          <p className={`media-status media-status--${status.toLowerCase()}`} role="status">
            {mediaStatusLabel(status)}
          </p>
          {status === 'ERROR' && (
            <div className="media-error" role="alert">
              <p>{error || 'Media disconnected. Your match is still active.'}</p>
              <button type="button" onClick={() => void enable()}>Retry media</button>
            </div>
          )}
          {blocked && <p className="media-error" role="status">Media is unavailable while this opponent is blocked.</p>}
        </>
      )}
    </section>
  )
}

function ConnectedMedia({ status, setStatus, onLeave }: {
  status: MediaStatus
  setStatus: (status: MediaStatus) => void
  onLeave: () => void
}) {
  const room = useRoomContext()
  const remoteParticipants = useRemoteParticipants()
  const [remoteMuted, setRemoteMuted] = useState(false)
  const [remoteHidden, setRemoteHidden] = useState(false)
  const [volume, setVolume] = useState(1)
  const microphone = useTrackToggle({ source: Track.Source.Microphone })
  const camera = useTrackToggle({ source: Track.Source.Camera })
  const cameras = useMediaDeviceSelect({ kind: 'videoinput' })
  const microphones = useMediaDeviceSelect({ kind: 'audioinput' })
  const speakers = useMediaDeviceSelect({ kind: 'audiooutput' })
  const videoTracks = useTracks([Track.Source.Camera], { onlySubscribed: false })
  const audioTracks = useTracks([Track.Source.Microphone], { onlySubscribed: false })
  const localVideo = videoTracks.find((ref) => ref.participant.isLocal)
  const remoteVideo = videoTracks.find((ref) => !ref.participant.isLocal)
  const remoteAudio = audioTracks.find((ref) => !ref.participant.isLocal)
  const remoteCameraOff = !remoteVideo || remoteVideo.publication?.isMuted
  const remoteMicrophoneOff = !remoteAudio || remoteAudio.publication?.isMuted

  useEffect(() => {
    const handleState = (state: ConnectionState) => {
      if (state === ConnectionState.Reconnecting) setStatus('RECONNECTING')
      if (state === ConnectionState.Connected) setStatus('CONNECTED')
    }
    room.on('connectionStateChanged', handleState)
    return () => { room.off('connectionStateChanged', handleState) }
  }, [room, setStatus])

  useEffect(() => {
    if (remoteVideo?.publication instanceof RemoteTrackPublication) {
      remoteVideo.publication.setSubscribed(!remoteHidden)
    }
  }, [remoteHidden, remoteVideo?.publication])

  const opponentName = remoteParticipants[0]?.name || 'Opponent'
  const deviceControls = useMemo(() => [
    { label: 'Microphone', selector: microphones },
    { label: 'Camera', selector: cameras },
    { label: 'Speaker', selector: speakers },
  ], [microphones, cameras, speakers])

  return (
    <div className="media-room">
      <StartAudio className="button button--secondary" label="Tap to enable opponent audio" />
      {remoteParticipants[0] ? (
        <ConnectedOpponentStatus
          participant={remoteParticipants[0]}
          microphoneOff={remoteMicrophoneOff}
          muted={remoteMuted}
          hidden={remoteHidden}
        />
      ) : (
        <p className="media-participant-status" aria-live="polite">
          Opponent not connected to media
          {remoteMuted ? ' · muted for you' : ''}
          {remoteHidden ? ' · hidden for you' : ''}
        </p>
      )}
      <div className="media-videos">
        <figure>
          <div className="media-video-frame">
            {localVideo && camera.enabled ? <VideoTrack className="media-local-preview" trackRef={localVideo} /> : <span>Camera off</span>}
          </div>
          <figcaption>You</figcaption>
        </figure>
        <figure>
          <div className="media-video-frame">
            {!remoteCameraOff && !remoteHidden ? <VideoTrack trackRef={remoteVideo} /> : <span>{remoteHidden ? 'Video hidden locally · hidden for you' : 'Opponent camera off'}</span>}
          </div>
          <figcaption>{opponentName}</figcaption>
        </figure>
      </div>
      {remoteAudio && <AudioTrack trackRef={remoteAudio} volume={remoteMuted ? 0 : volume} />}
      <div className="media-controls">
        <button type="button" {...microphone.buttonProps}><span aria-hidden="true">🎙</span> {microphone.pending ? 'Updating microphone…' : microphone.enabled ? 'Mute mic' : 'Unmute mic'}</button>
        <button type="button" {...camera.buttonProps}><span aria-hidden="true">📷</span> {camera.pending ? 'Updating camera…' : camera.enabled ? 'Turn camera off' : 'Turn camera on'}</button>
        <button type="button" aria-pressed={remoteMuted} onClick={() => setRemoteMuted((value) => !value)}><span aria-hidden="true">🔇</span> {remoteMuted ? 'Unmute opponent' : 'Mute opponent'}</button>
        <button type="button" aria-pressed={remoteHidden} onClick={() => setRemoteHidden((value) => !value)}><span aria-hidden="true">◉</span> {remoteHidden ? 'Show opponent video' : 'Hide opponent video'}</button>
        <button type="button" className="button button--danger" onClick={onLeave}><span aria-hidden="true">×</span> Leave media</button>
      </div>
      <label className="media-volume">Opponent volume
        <input type="range" min="0" max="1" step="0.05" value={volume}
          onChange={(event) => setVolume(Number(event.target.value))} />
      </label>
      <div className="media-devices">
        {deviceControls.map(({ label, selector }) => (
          <label key={label}>{label}
            <select value={selector.activeDeviceId}
              onChange={(event) => void selector.setActiveMediaDevice(event.target.value)}>
              {selector.devices.map((device) => <option key={device.deviceId} value={device.deviceId}>{device.label || `${label} device`}</option>)}
            </select>
          </label>
        ))}
      </div>
      {status === 'RECONNECTING' && <p>Media reconnecting. Gameplay remains connected separately.</p>}
    </div>
  )
}

function ConnectedOpponentStatus({ participant, microphoneOff, muted, hidden }: {
  participant: ReturnType<typeof useRemoteParticipants>[number]
  microphoneOff: boolean
  muted: boolean
  hidden: boolean
}) {
  const connectionQuality = useConnectionQualityIndicator({ participant })
  const opponentName = participant.name || 'Opponent'
  return (
    <p className="media-participant-status" aria-live="polite">
      {`${opponentName} connected · quality ${connectionQuality.quality}`}
      {microphoneOff ? ' · microphone off' : ''}
      {muted ? ' · muted for you' : ''}
      {hidden ? ' · hidden for you' : ''}
    </p>
  )
}

function mediaStatusLabel(status: MediaStatus): string {
  switch (status) {
    case 'CONNECTED': return 'Media connected'
    case 'CONNECTING': return 'Media connecting'
    case 'RECONNECTING': return 'Media reconnecting — gameplay is unaffected'
    case 'ERROR': return 'Media unavailable — gameplay is unaffected'
    default: return 'Media off'
  }
}
