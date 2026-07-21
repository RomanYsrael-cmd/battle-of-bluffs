import type { ReactNode } from 'react'
import type { PlayerMatchView } from '../../api/types'
import type { MatchConnectionState } from '../../realtime/matchSocket'
import { MatchHeader } from '../match/MatchHeader'
import { MatchTimers } from '../match/MatchTimers'

interface DesktopMatchShellProps {
  view: PlayerMatchView
  connectionState: MatchConnectionState
  connectionLabel: string
  syncMessage: string
  onLeave: () => void
  captures?: ReactNode
  children: ReactNode
  utilityDock?: ReactNode
}

export function DesktopMatchShell({
  view,
  connectionState,
  connectionLabel,
  syncMessage,
  onLeave,
  captures,
  children,
  utilityDock,
}: DesktopMatchShellProps) {
  const turn = view.phase === 'FORMATION'
    ? 'Deploy your formation'
    : view.phase === 'TERMINAL'
      ? 'Match complete'
      : view.currentPlayer === view.requestingSide ? 'You to move' : 'Opponent to move'

  return (
    <main className={`app-shell match-app-shell match-app-shell--${view.phase.toLowerCase()}`}>
      <MatchHeader view={view} onLeave={onLeave} />
      <section className="match-status-bar" aria-label="Match status and timers">
        <div className="match-status-summary">
          <span className="match-status-turn">{turn}</span>
          <span>{view.mode === 'RANKED' ? 'Ranked' : 'Casual'}</span>
          <span>Side {view.requestingSide === 'PLAYER_ONE' ? '1' : '2'}</span>
          <span>{view.phase}</span>
        </div>
        <MatchTimers view={view} />
        <div className={`connection-state connection-state--${connectionState.toLowerCase()}`} role="status">
          <span className="connection-state__dot" aria-hidden="true" />
          {connectionLabel}
        </div>
      </section>
      {syncMessage && <p className="sync-message match-sync-toast" role="status">{syncMessage}</p>}
      <div className={`desktop-match-workspace${captures ? ' desktop-match-workspace--captures' : ''}`}>
        {captures}
        <div className="match-primary-stage">{children}</div>
        {utilityDock && (
          <aside className="desktop-utility-dock" aria-label="Match communication and history">
            {utilityDock}
          </aside>
        )}
      </div>
    </main>
  )
}
