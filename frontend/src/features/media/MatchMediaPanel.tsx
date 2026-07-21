import { useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent, type RefObject } from 'react'
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
type RequestedSource = 'microphone' | 'camera'

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
  const [requestedSource, setRequestedSource] = useState<RequestedSource>()
  const floating = useFloatingCameraPanel(`gotg:media-position:${accountId}:${matchId}`)

  const leave = () => {
    sessionStorage.removeItem(consentKey)
    setToken(undefined)
    setStatus('OFF')
    setError('')
  }

  useEffect(() => {
    if (blocked) leave()
  }, [blocked])

  const enable = async (source?: RequestedSource) => {
    setRequestedSource(source)
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
      setRequestedSource(undefined)
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

  const togglePanel = () => setCollapsed((value) => {
    localStorage.setItem(panelKey, value ? 'open' : 'closed')
    return !value
  })

  const openSettings = () => {
    localStorage.setItem(panelKey, 'open')
    setCollapsed(false)
  }

  return (
    <section ref={floating.panelRef} style={floating.style}
      className={`media-panel floating-camera-panel${collapsed ? ' media-panel--collapsed' : ''}${floating.dragging ? ' floating-camera-panel--dragging' : ''}`}
      aria-label="Optional match audio and video">
      <div className="media-panel__heading" title="Drag camera panel" onPointerDown={floating.onPointerDown}>
        <div>
          <p className="eyebrow">Optional · peer media only</p>
          <h2>Camera</h2>
        </div>
        <span className="media-drag-handle" aria-hidden="true">⠿</span>
        <button type="button" className="button button--ghost" aria-expanded={!collapsed}
          onClick={togglePanel}>
          {collapsed ? token ? 'Settings' : 'Set up media' : token ? 'Close settings' : 'Collapse'}
        </button>
      </div>
      {!token && collapsed && (
        <div className="media-collapsed-preview">
          <div className="media-camera-off" aria-hidden="true">
            <span>▱</span>
            <small>Camera is off</small>
          </div>
          <div className={`media-compact-status media-status--${status.toLowerCase()}`}>
            <button type="button" aria-label="Turn microphone on" disabled={blocked || status === 'CONNECTING'}
              onClick={() => void enable('microphone')}>♩ Mic: off</button>
            <button type="button" aria-label="Turn camera on" disabled={blocked || status === 'CONNECTING'}
              onClick={() => void enable('camera')}>▣ Camera: off</button>
            <button type="button" className="media-settings-button" aria-label={`Open media settings · ${mediaStatusLabel(status)}`}
              onClick={openSettings}>⚙</button>
          </div>
        </div>
      )}
      {!token && !collapsed && (
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
          ) : null}
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
      {token && (
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
            setRequestedSource(undefined)
            setStatus('ERROR')
            setError('Media disconnected. Your match is still active.')
          }}
          onError={() => {
            setToken(undefined)
            setRequestedSource(undefined)
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
          <ConnectedMedia
            status={status}
            setStatus={setStatus}
            onLeave={leave}
            settingsOpen={!collapsed}
            onToggleSettings={togglePanel}
            requestedSource={requestedSource}
            onRequestedSourceHandled={() => setRequestedSource(undefined)}
          />
        </LiveKitRoom>
      )}
      {token && status === 'ERROR' && error && (
        <div className="media-error media-device-error" role="alert">
          <p>{error}</p>
        </div>
      )}
    </section>
  )
}

function useFloatingCameraPanel(storageKey: string): {
  panelRef: RefObject<HTMLElement | null>
  style: CSSProperties | undefined
  dragging: boolean
  onPointerDown: (event: ReactPointerEvent<HTMLDivElement>) => void
} {
  const panelRef = useRef<HTMLElement>(null)
  const drag = useRef<{ offsetX: number; offsetY: number } | null>(null)
  const [dragging, setDragging] = useState(false)
  const [position, setPosition] = useState<{ left: number; top: number } | null>(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(storageKey) ?? 'null') as { left?: unknown; top?: unknown } | null
      return saved && Number.isFinite(saved.left) && Number.isFinite(saved.top)
        ? { left: Number(saved.left), top: Number(saved.top) }
        : null
    } catch {
      return null
    }
  })

  useEffect(() => {
    const move = (event: PointerEvent) => {
      const panel = panelRef.current
      if (!drag.current || !panel) return
      const margin = 8
      const left = Math.min(
        Math.max(margin, event.clientX - drag.current.offsetX),
        Math.max(margin, window.innerWidth - panel.offsetWidth - margin),
      )
      const top = Math.min(
        Math.max(margin, event.clientY - drag.current.offsetY),
        Math.max(margin, window.innerHeight - panel.offsetHeight - margin),
      )
      setPosition({ left, top })
    }
    const stop = () => {
      if (!drag.current) return
      drag.current = null
      setDragging(false)
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop)
    window.addEventListener('pointercancel', stop)
    return () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', stop)
      window.removeEventListener('pointercancel', stop)
    }
  }, [])

  useEffect(() => {
    if (position) localStorage.setItem(storageKey, JSON.stringify(position))
  }, [position, storageKey])

  useEffect(() => {
    const constrainToViewport = () => {
      const panel = panelRef.current
      if (!panel) return
      setPosition((current) => {
        if (!current) return current
        const margin = 8
        const left = Math.max(margin, Math.min(current.left, Math.max(margin, window.innerWidth - panel.offsetWidth - margin)))
        const top = Math.max(margin, Math.min(current.top, Math.max(margin, window.innerHeight - panel.offsetHeight - margin)))
        return left === current.left && top === current.top ? current : { left, top }
      })
    }
    constrainToViewport()
    window.addEventListener('resize', constrainToViewport)
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(constrainToViewport)
    if (panelRef.current) observer?.observe(panelRef.current)
    return () => {
      window.removeEventListener('resize', constrainToViewport)
      observer?.disconnect()
    }
  }, [])

  return {
    panelRef,
    style: position ? { left: position.left, top: position.top, right: 'auto' } : undefined,
    dragging,
    onPointerDown: (event) => {
      if ((event.target as HTMLElement).closest('button, input, select, textarea, a')) return
      const rect = panelRef.current?.getBoundingClientRect()
      if (!rect) return
      drag.current = { offsetX: event.clientX - rect.left, offsetY: event.clientY - rect.top }
      setDragging(true)
      event.preventDefault()
    },
  }
}

function ConnectedMedia({ status, setStatus, onLeave, settingsOpen, onToggleSettings, requestedSource, onRequestedSourceHandled }: {
  status: MediaStatus
  setStatus: (status: MediaStatus) => void
  onLeave: () => void
  settingsOpen: boolean
  onToggleSettings: () => void
  requestedSource?: RequestedSource
  onRequestedSourceHandled: () => void
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
    if (status !== 'CONNECTED' || !requestedSource) return
    onRequestedSourceHandled()
    void (requestedSource === 'microphone' ? microphone.toggle(true) : camera.toggle(true))
  }, [camera, microphone, onRequestedSourceHandled, requestedSource, status])

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
      <div className={`media-compact-status media-status--${status.toLowerCase()}`}>
        <button type="button" {...microphone.buttonProps} aria-label={microphone.enabled ? 'Mute microphone' : 'Unmute microphone'}>
          ♩ Mic: {microphone.pending ? '…' : microphone.enabled ? 'on' : 'off'}
        </button>
        <button type="button" {...camera.buttonProps} aria-label={camera.enabled ? 'Turn camera off' : 'Turn camera on'}>
          ▣ Camera: {camera.pending ? '…' : camera.enabled ? 'on' : 'off'}
        </button>
        <button type="button" className="media-settings-button" aria-label={`${settingsOpen ? 'Close' : 'Open'} media settings · ${mediaStatusLabel(status)}`}
          aria-expanded={settingsOpen} onClick={onToggleSettings}>⚙</button>
      </div>
      {settingsOpen && (
        <div className="media-settings" aria-label="Media settings">
          <div className="media-settings__heading">
            <strong>Media settings</strong>
            <small>{mediaStatusLabel(status)}</small>
          </div>
          {remoteParticipants[0] ? (
            <ConnectedOpponentStatus
              participant={remoteParticipants[0]}
              microphoneOff={remoteMicrophoneOff}
              muted={remoteMuted}
              hidden={remoteHidden}
            />
          ) : (
            <p className="media-participant-status" aria-live="polite">Opponent not connected to media</p>
          )}
          <div className="media-opponent-controls">
            <button type="button" aria-pressed={remoteMuted} onClick={() => setRemoteMuted((value) => !value)}>{remoteMuted ? 'Unmute opponent' : 'Mute opponent'}</button>
            <button type="button" aria-pressed={remoteHidden} onClick={() => setRemoteHidden((value) => !value)}>{remoteHidden ? 'Show opponent video' : 'Hide opponent video'}</button>
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
          <button type="button" className="button button--danger media-leave" onClick={onLeave}>Leave media</button>
        </div>
      )}
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
