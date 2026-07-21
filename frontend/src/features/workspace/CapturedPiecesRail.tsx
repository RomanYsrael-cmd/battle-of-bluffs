import type { PlayerMatchView } from '../../api/types'
import { PieceInsignia } from '../../components/Piece/PieceInsignia'
import { RANK_ABBREVIATIONS, RANK_LABELS, type Rank } from '../../game/ranks'

export function CapturedPiecesRail({ view }: { view: PlayerMatchView }) {
  const ownLosses = groupRanks(view.ownPieces.filter((piece) => !piece.alive).map((piece) => piece.rank))
  const opponentSide = view.requestingSide === 'PLAYER_ONE' ? 'PLAYER_TWO' : 'PLAYER_ONE'
  const disclosedCaptures = view.phase === 'TERMINAL'
    ? groupRanks(view.postMatchPieces
      .filter((piece) => piece.owner === opponentSide && !piece.alive)
      .map((piece) => piece.rank))
    : []
  const hiddenCapturedCount = view.phase === 'ACTIVE'
    ? Math.max(0, 21 - view.opponentPieces.length)
    : 0

  return (
    <aside className="captures-rail" aria-label="Captured pieces">
      <CaptureGroup title="Captured by you" entries={disclosedCaptures} hiddenCount={hiddenCapturedCount} />
      <CaptureGroup title="Your lost pieces" entries={ownLosses} />
    </aside>
  )
}

function CaptureGroup({ title, entries, hiddenCount = 0 }: {
  title: string
  entries: { rank: Rank; count: number }[]
  hiddenCount?: number
}) {
  return (
    <section className="capture-group" aria-label={title}>
      <h2>{title}</h2>
      <div className="capture-chips">
        {entries.map(({ rank, count }) => (
          <span className="capture-chip" key={rank} aria-label={`${RANK_LABELS[rank]}, ${count}`}>
            <PieceInsignia rank={rank} size="small" decorative />
            <span>{RANK_ABBREVIATIONS[rank]}</span>
            <strong>×{count}</strong>
          </span>
        ))}
        {hiddenCount > 0 && (
          <span className="capture-chip capture-chip--hidden" aria-label={`${hiddenCount} captured opponent pieces; ranks remain hidden`}>
            <span aria-hidden="true">◆</span><span>HIDDEN</span><strong>×{hiddenCount}</strong>
          </span>
        )}
        {entries.length === 0 && hiddenCount === 0 && <p>No captures yet</p>}
      </div>
    </section>
  )
}

function groupRanks(ranks: Rank[]): { rank: Rank; count: number }[] {
  const counts = new Map<Rank, number>()
  ranks.forEach((rank) => counts.set(rank, (counts.get(rank) ?? 0) + 1))
  return [...counts].map(([rank, count]) => ({ rank, count }))
}
