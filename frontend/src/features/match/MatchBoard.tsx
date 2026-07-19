import type { PlayerMatchView } from '../../api/types'
import { RANK_ABBREVIATIONS, RANK_LABELS } from '../../game/ranks'
import {
  coordinateKey,
  orthogonalDestinations,
  visualPositionsFor,
  type Position,
} from '../../game/coordinates'

interface MatchBoardProps {
  view: PlayerMatchView
  selectedPieceId: string | null
  disabled: boolean
  onSelectPiece: (pieceId: string) => void
  onDestination: (position: Position) => void
}

export function MatchBoard({
  view,
  selectedPieceId,
  disabled,
  onSelectPiece,
  onDestination,
}: MatchBoardProps) {
  const selected = view.ownPieces.find((piece) => piece.id === selectedPieceId && piece.position)
  const ownPositions = new Set(view.ownPieces
    .flatMap((piece) => piece.position ? [coordinateKey(piece.position)] : []))
  const candidates = new Set(selected?.position
    ? orthogonalDestinations(selected.position)
        .filter((position) => !ownPositions.has(coordinateKey(position)))
        .map(coordinateKey)
    : [])

  return (
    <div className="board-wrap">
      <div className="board-file-labels" aria-hidden="true">
        {Array.from({ length: 9 }, (_, column) => <span key={column}>{column}</span>)}
      </div>
      <div className="board match-board" role="grid" aria-label="Active match board">
        {visualPositionsFor(view.requestingSide).map((position) => {
          const key = coordinateKey(position)
          const ownPiece = view.ownPieces.find((piece) =>
            piece.position && coordinateKey(piece.position) === key)
          const opponentPiece = view.opponentPieces.find((piece) => coordinateKey(piece.position) === key)
          const candidate = !disabled && candidates.has(key)

          return (
            <button
              type="button"
              role="gridcell"
              key={key}
              data-position={key}
              aria-label={`Row ${position.row}, column ${position.column}${candidate ? ', candidate destination' : ''}`}
              className={`board-cell ${candidate ? 'board-cell--highlighted' : ''}`}
              disabled={disabled || (!ownPiece && !candidate)}
              onClick={() => {
                if (candidate) onDestination(position)
                else if (ownPiece) onSelectPiece(ownPiece.id)
              }}
            >
              <span className="board-cell__coordinate">{position.row},{position.column}</span>
              {ownPiece && (
                <span
                  className={`match-piece match-piece--own ${selectedPieceId === ownPiece.id ? 'match-piece--selected' : ''}`}
                  title={RANK_LABELS[ownPiece.rank]}
                >
                  <strong>{RANK_ABBREVIATIONS[ownPiece.rank]}</strong>
                  <small>{RANK_LABELS[ownPiece.rank]}</small>
                </span>
              )}
              {opponentPiece && (
                <span className="match-piece match-piece--opponent" data-piece-id={opponentPiece.id}>
                  <strong>?</strong>
                  <small>Opponent</small>
                </span>
              )}
            </button>
          )
        })}
      </div>
      <p className="board-caption">Coordinates remain canonical; only the visual row order rotates.</p>
    </div>
  )
}
