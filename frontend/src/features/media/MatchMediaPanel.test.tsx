import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PropsWithChildren } from 'react'

const mocks = vi.hoisted(() => ({
  requestMediaToken: vi.fn(),
  liveKitProps: undefined as Record<string, unknown> | undefined,
  setSubscribed: vi.fn(),
  microphoneToggle: vi.fn(),
  cameraToggle: vi.fn(),
  includeRemoteTracks: false,
  remoteMuted: false,
  remoteName: 'Opponent',
}))

vi.mock('../../config/runtime', () => ({ mediaServerUrl: 'wss://example.livekit.cloud' }))
vi.mock('../../api/client', () => ({ requestMediaToken: mocks.requestMediaToken }))
vi.mock('livekit-client', () => ({
  ConnectionState: { Reconnecting: 'reconnecting', Connected: 'connected' },
  Track: { Source: { Microphone: 'microphone', Camera: 'camera' } },
  VideoPresets: { h360: { resolution: { width: 640, height: 360, frameRate: 20 } } },
  RemoteTrackPublication: class {
    static [Symbol.hasInstance](instance: unknown) {
      return typeof (instance as { setSubscribed?: unknown })?.setSubscribed === 'function'
    }
  },
}))
vi.mock('@livekit/components-react', () => ({
  LiveKitRoom: ({ children, ...props }: PropsWithChildren<Record<string, unknown>>) => {
    mocks.liveKitProps = props
    return <div data-testid="livekit-room">{children}</div>
  },
  StartAudio: ({ label }: { label: string }) => <button>{label}</button>,
  VideoTrack: () => <video />,
  AudioTrack: () => <audio />,
  useRoomContext: () => ({ on: vi.fn(), off: vi.fn() }),
  useConnectionQualityIndicator: ({ participant }: { participant?: unknown }) => {
    if (!participant) throw new Error('No participant provided')
    return { quality: 'excellent' }
  },
  useRemoteParticipants: () => mocks.includeRemoteTracks ? [{ name: mocks.remoteName }] : [],
  useTrackToggle: ({ source }: { source: string }) => ({
    enabled: false,
    pending: false,
    toggle: source === 'microphone' ? mocks.microphoneToggle : mocks.cameraToggle,
    buttonProps: { onClick: source === 'microphone' ? mocks.microphoneToggle : mocks.cameraToggle },
  }),
  useMediaDeviceSelect: () => ({
    devices: [], activeDeviceId: '', setActiveMediaDevice: vi.fn(),
  }),
  useTracks: ([source]: [string]) => mocks.includeRemoteTracks ? [{
    source,
    participant: { isLocal: false },
    publication: { isMuted: mocks.remoteMuted, setSubscribed: mocks.setSubscribed },
  }] : [],
}))

import { MatchMediaPanel } from './MatchMediaPanel'

describe('optional match media', () => {
  const participant = { accountId: 'account-1', participantCycle: 'cycle-1' }
  beforeEach(() => {
    mocks.requestMediaToken.mockReset()
    mocks.liveKitProps = undefined
    mocks.setSubscribed.mockReset()
    mocks.microphoneToggle.mockReset()
    mocks.cameraToggle.mockReset()
    mocks.includeRemoteTracks = false
    mocks.remoteMuted = false
    mocks.remoteName = 'Opponent'
    sessionStorage.clear()
    localStorage.clear()
  })

  it('does not request a token or media permission until explicit consent and starts muted', async () => {
    mocks.requestMediaToken.mockResolvedValue({
      enabled: true, url: 'wss://example.livekit.cloud', token: 'secret-token',
      expiresAt: '2026-07-20T12:05:00Z', room: { matchId: 'match-1', participantCountLimit: 2 },
    })
    render(<MatchMediaPanel {...participant} matchId="match-1" blocked={false} />)

    expect(mocks.requestMediaToken).not.toHaveBeenCalled()
    expect(screen.getByText('Camera is off')).toBeInTheDocument()
    expect(screen.queryByText('Enable audio/video')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Set up media' }))
    expect(screen.getByText(/Microphone and camera start off/)).toBeInTheDocument()
    expect(mocks.requestMediaToken).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Enable audio/video' }))
    await waitFor(() => expect(mocks.requestMediaToken).toHaveBeenCalledWith('match-1'))
    expect(await screen.findByTestId('livekit-room')).toBeInTheDocument()
    expect(mocks.liveKitProps).toMatchObject({
      serverUrl: 'wss://example.livekit.cloud',
      token: 'secret-token',
      connect: true,
      audio: false,
      video: false,
      screen: false,
    })
    expect(document.body.textContent).not.toContain('secret-token')
  })

  it('keeps gameplay independent on errors and disables consent when blocked', async () => {
    mocks.requestMediaToken.mockRejectedValue(new Error('Cloud connection unavailable'))
    const { rerender } = render(<><button type="button">Make legal move</button><MatchMediaPanel {...participant} matchId="match-2" blocked={false} /></>)
    fireEvent.click(screen.getByRole('button', { name: 'Set up media' }))
    fireEvent.click(screen.getByRole('button', { name: 'Enable audio/video' }))
    expect(await screen.findByText('Media unavailable — gameplay is unaffected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry media' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Make legal move' })).toBeEnabled()

    rerender(<><button type="button">Make legal move</button><MatchMediaPanel {...participant} matchId="match-2" blocked /></>)
    expect(screen.getByRole('button', { name: 'Enable audio/video' })).toBeDisabled()
    expect(screen.getByText(/unavailable while this opponent is blocked/)).toBeInTheDocument()
  })

  it('uses SDK toggles and keeps opponent mute and hide local to this browser', async () => {
    mocks.includeRemoteTracks = true
    mocks.requestMediaToken.mockResolvedValue({
      enabled: true, url: 'wss://example.livekit.cloud', token: 'token',
      expiresAt: '2026-07-20T12:05:00Z', room: { matchId: 'match-3', participantCountLimit: 2 },
    })
    render(<MatchMediaPanel {...participant} matchId="match-3" blocked={false} />)
    fireEvent.click(screen.getByRole('button', { name: 'Set up media' }))
    fireEvent.click(screen.getByRole('button', { name: 'Enable audio/video' }))
    await screen.findByTestId('livekit-room')

    fireEvent.click(screen.getByRole('button', { name: 'Unmute microphone' }))
    fireEvent.click(screen.getByRole('button', { name: 'Turn camera on' }))
    expect(mocks.microphoneToggle).toHaveBeenCalledOnce()
    expect(mocks.cameraToggle).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Tap to enable opponent audio' })).toBeInTheDocument()
    expect(screen.getByLabelText('Microphone')).toBeInTheDocument()
    expect(screen.getByLabelText('Camera')).toBeInTheDocument()
    expect(screen.getByLabelText('Speaker')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Mute opponent' }))
    expect(screen.getByText(/muted for you/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Hide opponent video' }))
    await waitFor(() => expect(mocks.setSubscribed).toHaveBeenCalledWith(false))
    fireEvent.click(screen.getByRole('button', { name: 'Show opponent video' }))
    await waitFor(() => expect(mocks.setSubscribed).toHaveBeenCalledWith(true))

    act(() => {
      (mocks.liveKitProps?.onMediaDeviceFailure as (failure: unknown, kind: string) => void)(null, 'audioinput')
    })
    expect(screen.getByText(/Microphone access was blocked/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Leave media' }))
    expect(screen.queryByTestId('livekit-room')).not.toBeInTheDocument()
    expect(screen.getByText('Media off')).toBeInTheDocument()
    expect(mocks.requestMediaToken).toHaveBeenCalledTimes(1)
  })

  it('renders remote mute/camera state and hostile names only as text without game secrets', async () => {
    mocks.includeRemoteTracks = true
    mocks.remoteMuted = true
    mocks.remoteName = '<img src=x onerror=alert(1)> Five Star General'
    mocks.requestMediaToken.mockResolvedValue({
      enabled: true, url: 'wss://example.livekit.cloud', token: 'token',
      expiresAt: '2026-07-20T12:05:00Z', room: { matchId: 'match-5', participantCountLimit: 2 },
    })
    render(<MatchMediaPanel {...participant} matchId="match-5" blocked={false} />)
    fireEvent.click(screen.getByRole('button', { name: 'Set up media' }))
    fireEvent.click(screen.getByRole('button', { name: 'Enable audio/video' }))
    await screen.findByTestId('livekit-room')

    expect(screen.getByText(/microphone off/)).toBeInTheDocument()
    expect(screen.getByText('<img src=x onerror=alert(1)> Five Star General')).toBeInTheDocument()
    expect(document.querySelector('.media-panel img')).toBeNull()
    expect(document.body.textContent).not.toContain('FIVE_STAR_GENERAL')
  })

  it('reconnects only from browser-session consent and never restores capture', async () => {
    sessionStorage.setItem('gotg:media-enabled:account-1:match-4:cycle-1', 'true')
    mocks.requestMediaToken.mockResolvedValue({
      enabled: true, url: 'wss://example.livekit.cloud', token: 'fresh-token',
      expiresAt: '2026-07-20T12:05:00Z', room: { matchId: 'match-4', participantCountLimit: 2 },
    })
    render(<MatchMediaPanel {...participant} matchId="match-4" blocked={false} />)
    await waitFor(() => expect(mocks.requestMediaToken).toHaveBeenCalledWith('match-4'))
    await screen.findByTestId('livekit-room')
    expect(mocks.liveKitProps).toMatchObject({ audio: false, video: false })
  })

  it('makes compact mic and camera controls explicit consent actions and keeps the room mounted when settings close', async () => {
    mocks.requestMediaToken.mockResolvedValue({
      enabled: true, url: 'wss://example.livekit.cloud', token: 'token',
      expiresAt: '2026-07-20T12:05:00Z', room: { matchId: 'match-6', participantCountLimit: 2 },
    })
    render(<MatchMediaPanel {...participant} matchId="match-6" blocked={false} />)

    fireEvent.click(screen.getByRole('button', { name: 'Turn microphone on' }))
    await waitFor(() => expect(mocks.requestMediaToken).toHaveBeenCalledWith('match-6'))
    expect(await screen.findByTestId('livekit-room')).toBeInTheDocument()

    act(() => {
      (mocks.liveKitProps?.onConnected as () => void)()
    })
    await waitFor(() => expect(mocks.microphoneToggle).toHaveBeenCalledWith(true))

    fireEvent.click(screen.getByRole('button', { name: 'Settings' }))
    expect(screen.getByLabelText('Media settings')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Close settings' }))
    expect(screen.queryByLabelText('Media settings')).not.toBeInTheDocument()
    expect(screen.getByTestId('livekit-room')).toBeInTheDocument()
    expect(mocks.requestMediaToken).toHaveBeenCalledTimes(1)
  })
})
