import { Piece } from '../Piece/Piece'
import type { Placements } from '../../game/formation'
import type { LocalPiece } from '../../game/ranks'

interface PieceTrayProps {
  inventory: LocalPiece[]
  placements: Placements
  selectedPieceId: string | null
  locked: boolean
  onSelect: (pieceId: string) => void
}

export function PieceTray({ inventory, placements, selectedPieceId, locked, onSelect }: PieceTrayProps) {
  const remaining = inventory.filter((piece) => !placements[piece.id])

  return (
    <section className="tray" aria-labelledby="tray-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Your command</p>
          <h2 id="tray-title">Piece tray</h2>
        </div>
        <span className="count-badge" aria-label={`${remaining.length} pieces remaining`}>{remaining.length}</span>
      </div>
      {remaining.length > 0 ? (
        <div className="tray-grid">
          {remaining.map((piece) => (
            <Piece
              key={piece.id}
              piece={piece}
              selected={selectedPieceId === piece.id}
              disabled={locked}
              onClick={() => onSelect(piece.id)}
            />
          ))}
        </div>
      ) : (
        <p className="tray-empty">All pieces deployed. Review your formation before locking.</p>
      )}
    </section>
  )
}
