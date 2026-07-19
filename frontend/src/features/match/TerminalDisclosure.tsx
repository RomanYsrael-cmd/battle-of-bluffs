import type { PlayerMatchView } from '../../api/types'
import { RANK_ABBREVIATIONS, RANK_LABELS } from '../../game/ranks'
import { PieceInsignia } from '../../components/Piece/PieceInsignia'
import { RatingChangeDisplay } from '../../profile/ProfileScreens'
import type { RatingChange } from '../../profile/types'

export function TerminalDisclosure({
  view,
  onLeave,
  ratingChange,
}: {
  view: PlayerMatchView
  onLeave: () => void
  ratingChange?: RatingChange | null
}) {
  const result = view.terminalResult
  const winner = result?.winner
    ? result.winner === view.requestingSide ? 'You won' : `${result.winner === 'PLAYER_ONE' ? 'Player 1' : 'Player 2'} won`
    : 'Draw'

  return (
    <section className="terminal-panel" aria-labelledby="terminal-title">
      <p className="eyebrow">Terminal result</p>
      <h2 id="terminal-title">{winner}</h2>
      <p className="terminal-reason">{result?.reason.replaceAll('_', ' ')}</p>
      {ratingChange && <RatingChangeDisplay change={ratingChange} />}
      <h3>Complete post-match disclosure</h3>
      <div className="disclosure-grid">
        {view.postMatchPieces.map((piece) => (
          <div key={`${piece.owner}-${piece.id}`} className="disclosed-piece">
            <PieceInsignia rank={piece.rank} size="medium" decorative />
            <div className="disclosed-piece__details">
              <strong>{RANK_ABBREVIATIONS[piece.rank]}</strong>
              <span className="disclosed-piece__rank-name">{RANK_LABELS[piece.rank]}</span>
              <span>{piece.owner === 'PLAYER_ONE' ? 'Player 1' : 'Player 2'}</span>
              <small>{piece.position ? `${piece.position.row},${piece.position.column}` : 'removed'}</small>
            </div>
          </div>
        ))}
      </div>
      <button type="button" className="button button--primary" onClick={onLeave}>Return home</button>
    </section>
  )
}
