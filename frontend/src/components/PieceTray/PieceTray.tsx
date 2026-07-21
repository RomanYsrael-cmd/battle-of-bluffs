import type { DragEvent as ReactDragEvent, PointerEventHandler } from 'react'
import { Piece } from '../Piece/Piece'
import type { Placements } from '../../game/formation'
import type { LocalPiece } from '../../game/ranks'

interface PieceTrayProps {
  inventory: LocalPiece[]
  placements: Placements
  selectedPieceId: string | null
  locked: boolean
  compact?: boolean
  draggedPieceId?: string | null
  onDragStart?: (pieceId: string, event: ReactDragEvent<HTMLButtonElement>) => void
  onDragEnd?: () => void
  onHeaderPointerDown?: PointerEventHandler<HTMLDivElement>
  onSelect: (pieceId: string) => void
}

export function PieceTray({
  inventory,
  placements,
  selectedPieceId,
  locked,
  compact = false,
  draggedPieceId,
  onDragStart,
  onDragEnd,
  onHeaderPointerDown,
  onSelect,
}: PieceTrayProps) {
  const remaining = inventory.filter((piece) => !placements[piece.id])

  return (
    <section className="tray" aria-labelledby="tray-title">
      <div className="section-heading formation-tray__heading"
        title={onHeaderPointerDown ? 'Drag piece tray' : undefined}
        onPointerDown={onHeaderPointerDown}>
        <div>
          <p className="eyebrow">Your command</p>
          <h2 id="tray-title">Piece tray</h2>
        </div>
        {onHeaderPointerDown && <span className="formation-tray__drag-handle" aria-hidden="true">⠿</span>}
        <span className="count-badge" aria-label={`${remaining.length} pieces remaining`}>{remaining.length}</span>
      </div>
      {remaining.length > 0 ? (
        <div className="tray-grid">
          {remaining.map((piece) => (
            <Piece
              key={piece.id}
              piece={piece}
              selected={selectedPieceId === piece.id}
              compact={compact}
              disabled={locked}
              draggable={!locked}
              dragging={draggedPieceId === piece.id}
              onDragStart={(event) => onDragStart?.(piece.id, event)}
              onDragEnd={onDragEnd}
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
