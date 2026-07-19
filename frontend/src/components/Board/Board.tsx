import { Piece } from '../Piece/Piece'
import { coordinateKey, isPlayerOneFormationPosition, playerOneVisualPositions, type Position } from '../../game/coordinates'
import { pieceAt, type Placements } from '../../game/formation'
import type { LocalPiece } from '../../game/ranks'

interface BoardProps {
  inventory: LocalPiece[]
  placements: Placements
  selectedPieceId: string | null
  locked: boolean
  onCellClick: (position: Position) => void
  onPieceSelect: (pieceId: string) => void
}

export function Board({ inventory, placements, selectedPieceId, locked, onCellClick, onPieceSelect }: BoardProps) {
  const selectedIsPlaced = selectedPieceId ? Boolean(placements[selectedPieceId]) : false

  return (
    <div className="board-wrap">
      <div className="board-file-labels" aria-hidden="true">
        {Array.from({ length: 9 }, (_, column) => <span key={column}>{column}</span>)}
      </div>
      <div className="board" role="grid" aria-label="Canonical 8 by 9 formation board">
        {playerOneVisualPositions().map((position) => {
          const occupantId = pieceAt(placements, position)
          const occupant = inventory.find((piece) => piece.id === occupantId)
          const deploymentCell = isPlayerOneFormationPosition(position)
          const highlighted = !locked && Boolean(selectedPieceId) && deploymentCell
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
              onKeyDown={(event) => {
                if ((event.key === 'Enter' || event.key === ' ') && highlighted) {
                  event.preventDefault()
                  activateCell()
                }
              }}
            >
              <span className="board-cell__coordinate">{position.row},{position.column}</span>
              {occupant && (
                <Piece
                  piece={occupant}
                  compact
                  selected={selectedPieceId === occupant.id}
                  disabled={locked}
                  onClick={() => selectedPieceId && selectedPieceId !== occupant.id
                    ? onCellClick(position)
                    : onPieceSelect(occupant.id)}
                />
              )}
            </div>
          )
        })}
      </div>
      <p className="board-caption">Your deployment area · canonical rows 0–2</p>
    </div>
  )
}
