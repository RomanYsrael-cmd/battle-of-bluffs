import type { DragEvent as ReactDragEvent } from 'react'
import { Piece } from '../Piece/Piece'
import {
  coordinateKey,
  isFormationPosition,
  visualPositionsFor,
  visualRowsFor,
  type BoardSide,
  type Position,
} from '../../game/coordinates'
import { pieceAt, type Placements } from '../../game/formation'
import type { LocalPiece } from '../../game/ranks'

interface BoardProps {
  inventory: LocalPiece[]
  placements: Placements
  selectedPieceId: string | null
  side: BoardSide
  locked: boolean
  draggedPieceId?: string | null
  onPieceDragStart?: (pieceId: string, event: ReactDragEvent<HTMLButtonElement>) => void
  onPieceDragEnd?: () => void
  onPieceDrop?: (pieceId: string, position: Position) => void
  onCellClick: (position: Position) => void
  onPieceSelect: (pieceId: string) => void
}

export function Board({
  inventory,
  placements,
  selectedPieceId,
  side,
  locked,
  draggedPieceId,
  onPieceDragStart,
  onPieceDragEnd,
  onPieceDrop,
  onCellClick,
  onPieceSelect,
}: BoardProps) {
  const actionablePieceId = draggedPieceId ?? selectedPieceId
  const selectedIsPlaced = actionablePieceId ? Boolean(placements[actionablePieceId]) : false
  const visualPositions = visualPositionsFor(side)
  const visualRows = visualRowsFor(side)

  return (
    <div className="board-wrap">
      <div className="board-coordinate-frame">
        <div className="board-file-labels" aria-hidden="true">
          {Array.from({ length: 9 }, (_, column) => <span key={column}>{column}</span>)}
        </div>
        <div className="board-rank-labels" aria-hidden="true">
          {visualRows.map((row) => <span key={row}>{row}</span>)}
        </div>
        <div
          className="board"
          role="grid"
          aria-label="Canonical 8 by 9 formation board"
          data-board-side={side}
        >
          {visualPositions.map((position) => {
          const occupantId = pieceAt(placements, position)
          const occupant = inventory.find((piece) => piece.id === occupantId)
          const deploymentCell = isFormationPosition(position, side)
          const highlighted = !locked && Boolean(actionablePieceId) && deploymentCell
            && (selectedIsPlaced || !occupantId)

          const activateCell = () => {
            if (highlighted) onCellClick(position)
          }

          return (
            <div
              role="gridcell"
              key={coordinateKey(position)}
              className={`board-cell ${deploymentCell ? 'board-cell--deployment' : ''} ${highlighted ? 'board-cell--highlighted' : ''}`}
              aria-label={`Row ${position.row}, column ${position.column}${deploymentCell ? ', formation cell' : ''}`}
              data-position={coordinateKey(position)}
              aria-disabled={locked || !highlighted}
              tabIndex={highlighted && !occupant ? 0 : -1}
              onClick={activateCell}
              onDragOver={(event) => {
                if (!draggedPieceId || !highlighted) return
                event.preventDefault()
                event.dataTransfer.dropEffect = 'move'
              }}
              onDrop={(event) => {
                if (!draggedPieceId || !highlighted) return
                event.preventDefault()
                onPieceDrop?.(draggedPieceId, position)
              }}
              onKeyDown={(event) => {
                if ((event.key === 'Enter' || event.key === ' ') && highlighted) {
                  event.preventDefault()
                  activateCell()
                }
              }}
            >
              {occupant && (
                <Piece
                  piece={occupant}
                  compact
                  selected={selectedPieceId === occupant.id}
                  disabled={locked}
                  draggable={!locked}
                  dragging={draggedPieceId === occupant.id}
                  onDragStart={(event) => onPieceDragStart?.(occupant.id, event)}
                  onDragEnd={onPieceDragEnd}
                  onClick={() => selectedPieceId && selectedPieceId !== occupant.id
                    ? onCellClick(position)
                    : onPieceSelect(occupant.id)}
                />
              )}
            </div>
          )
          })}
        </div>
      </div>
      <p className="board-caption">
        Your deployment area · canonical rows {side === 'PLAYER_ONE' ? '0–2' : '5–7'}
      </p>
    </div>
  )
}
