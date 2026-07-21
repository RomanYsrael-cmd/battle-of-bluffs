import type { DragEventHandler } from 'react'
import { RANK_ABBREVIATIONS, RANK_LABELS, type LocalPiece } from '../../game/ranks'
import { PieceInsignia } from './PieceInsignia'

interface PieceProps {
  piece: LocalPiece
  selected?: boolean
  compact?: boolean
  disabled?: boolean
  draggable?: boolean
  dragging?: boolean
  onDragStart?: DragEventHandler<HTMLButtonElement>
  onDragEnd?: DragEventHandler<HTMLButtonElement>
  onClick?: () => void
}

export function Piece({
  piece,
  selected = false,
  compact = false,
  disabled = false,
  draggable = false,
  dragging = false,
  onDragStart,
  onDragEnd,
  onClick,
}: PieceProps) {
  return (
    <button
      type="button"
      className={`piece ${compact ? 'piece--compact' : ''} ${selected ? 'piece--selected' : ''}${dragging ? ' piece--dragging' : ''}`}
      aria-label={`${RANK_LABELS[piece.rank]}${selected ? ', selected' : ''}`}
      aria-pressed={selected}
      disabled={disabled}
      draggable={draggable && !disabled}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onClick={(event) => {
        event.stopPropagation()
        onClick?.()
      }}
    >
      <PieceInsignia rank={piece.rank} size={compact ? 'small' : 'medium'} decorative />
      <span className="piece__text">
        <span className="piece__rank">{RANK_ABBREVIATIONS[piece.rank]}</span>
        {!compact && <span className="piece__name">{RANK_LABELS[piece.rank]}</span>}
      </span>
    </button>
  )
}
