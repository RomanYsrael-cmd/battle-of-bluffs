import type { PlayerMatchView } from '../../api/types'
import { DevelopmentWarning } from '../../components/DevelopmentWarning'
import { BRAND } from '../../config/brand'

interface MatchHeaderProps {
  view: PlayerMatchView
  onLeave: () => void
}

export function MatchHeader({ view, onLeave }: MatchHeaderProps) {
  return (
    <header className="match-header">
      <div>
        <p className="eyebrow">Private room</p>
        <h1>{BRAND.productName}</h1>
        <div className="match-meta">
          <label>
            Room code
            <input className="room-code-display" readOnly value={view.roomCode} aria-label="Room code" />
          </label>
          <span>Side {view.requestingSide === 'PLAYER_ONE' ? '1' : '2'}</span>
          <span>Phase {view.phase}</span>
          <span>Version {view.version}</span>
        </div>
      </div>
      <div className="match-header__actions">
        <DevelopmentWarning />
        <button type="button" className="button button--ghost" onClick={onLeave}>
          Leave local session
        </button>
      </div>
    </header>
  )
}
