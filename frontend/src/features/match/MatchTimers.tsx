import { useEffect, useState } from 'react'
import type { PlayerMatchView, PlayerSide } from '../../api/types'

export function MatchTimers({ view }: { view: PlayerMatchView }) {
  const [receivedAt, setReceivedAt] = useState(Date.now())
  const [sampledAt, setSampledAt] = useState(Date.now())
  useEffect(() => {
    const now = Date.now()
    setReceivedAt(now)
    setSampledAt(now)
  }, [view.timer.serverTimestamp])
  useEffect(() => {
    if (view.phase === 'TERMINAL') return undefined
    const interval = window.setInterval(() => setSampledAt(Date.now()), 250)
    return () => window.clearInterval(interval)
  }, [view.phase])

  const estimatedServerNow = Date.parse(view.timer.serverTimestamp) + sampledAt - receivedAt
  const playerOneRemaining = remainingFor(
    view,
    'PLAYER_ONE',
    estimatedServerNow,
  )
  const playerTwoRemaining = remainingFor(
    view,
    'PLAYER_TWO',
    estimatedServerNow,
  )
  const opponentIsPlayerOne = view.requestingSide === 'PLAYER_TWO'
  const opponentConnected = opponentIsPlayerOne
    ? view.presence.playerOneConnected
    : view.presence.playerTwoConnected
  const opponentDisconnectedSince = opponentIsPlayerOne
    ? view.presence.playerOneDisconnectedSince
    : view.presence.playerTwoDisconnectedSince
  const graceRemaining = opponentDisconnectedSince
    ? Math.max(
      0,
      Date.parse(opponentDisconnectedSince)
        + view.presence.disconnectGraceMillis
        - estimatedServerNow,
    )
    : 0

  return (
    <section className="match-timing" aria-label="Authoritative match timing">
      {view.phase === 'FORMATION' && view.timer.formationDeadline && (
        <div className="setup-clock">
          <span>Formation deadline</span>
          <strong>{formatDuration(Date.parse(view.timer.formationDeadline) - estimatedServerNow)}</strong>
        </div>
      )}
      {view.phase !== 'FORMATION' && view.timerMode === 'STANDARD_15_PLUS_5' && (
        <div className="play-clocks">
          <Clock
            label={view.requestingSide === 'PLAYER_ONE' ? 'Your clock' : 'Opponent clock'}
            remaining={playerOneRemaining}
            active={view.currentPlayer === 'PLAYER_ONE' && view.phase === 'ACTIVE'}
          />
          <Clock
            label={view.requestingSide === 'PLAYER_TWO' ? 'Your clock' : 'Opponent clock'}
            remaining={playerTwoRemaining}
            active={view.currentPlayer === 'PLAYER_TWO' && view.phase === 'ACTIVE'}
          />
        </div>
      )}
      {!opponentConnected && opponentDisconnectedSince && view.phase !== 'TERMINAL' && (
        <p className="opponent-disconnected" role="status">
          Opponent disconnected · {formatDuration(graceRemaining)} grace remaining
        </p>
      )}
    </section>
  )
}

function Clock({ label, remaining, active }: {
  label: string
  remaining: number
  active: boolean
}) {
  const low = remaining <= 60_000
  return (
    <div className={`play-clock${active ? ' play-clock--active' : ''}${low ? ' play-clock--low' : ''}`}
      aria-label={`${label}: ${formatDuration(remaining)}${low ? ', low time' : ''}`}>
      <span>{label}</span>
      <strong>{formatDuration(remaining)}</strong>
      {low && <em>Low time</em>}
    </div>
  )
}

function remainingFor(
  view: PlayerMatchView,
  side: PlayerSide,
  estimatedServerNow: number,
): number {
  const synchronizedRemaining = side === 'PLAYER_ONE'
    ? view.timer.playerOneRemainingMillis
    : view.timer.playerTwoRemainingMillis
  if (view.phase !== 'ACTIVE'
    || view.currentPlayer !== side
    || !view.timer.activeTurnDeadline) return synchronizedRemaining
  return Math.max(0, Date.parse(view.timer.activeTurnDeadline) - estimatedServerNow)
}

export function formatDuration(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.ceil(milliseconds / 1_000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
