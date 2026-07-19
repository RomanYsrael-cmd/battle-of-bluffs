import { Board } from './components/Board/Board'
import { PieceTray } from './components/PieceTray/PieceTray'
import { FORMATION_CELL_COUNT, FORMATION_PIECE_COUNT } from './game/formation'
import { useFormation } from './features/formation/useFormation'

export default function App() {
  const formation = useFormation()
  const emptyCells = FORMATION_CELL_COUNT - formation.placedCount

  return (
    <main className="app-shell">
      <header className="hero">
        <div>
          <p className="eyebrow">Formation lab · Player 1</p>
          <h1>Arrange the quiet before the bluff.</h1>
          <p className="hero__copy">
            Deploy all 21 pieces across canonical rows 0–2. Your opponent sees occupied cells,
            never your ranks.
          </p>
        </div>
        <div className="local-notice" role="note">
          <span className="local-notice__dot" />
          Local prototype only
          <small>This formation is not sent to a server.</small>
        </div>
      </header>

      <div className="workspace">
        <section className="board-panel" aria-labelledby="board-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Canonical board</p>
              <h2 id="board-title">Formation</h2>
            </div>
            <div className={`status-pill ${formation.valid ? 'status-pill--valid' : ''}`}>
              {formation.placedCount}/{FORMATION_PIECE_COUNT} placed · {emptyCells} empty
            </div>
          </div>

          <Board
            inventory={formation.inventory}
            placements={formation.placements}
            selectedPieceId={formation.selectedPieceId}
            locked={formation.locked}
            onCellClick={formation.selectCell}
            onPieceSelect={formation.selectPiece}
          />

          <div className="actions">
            <button
              type="button"
              className="button button--secondary"
              disabled={!formation.selectedPieceId || !formation.placements[formation.selectedPieceId] || formation.locked}
              onClick={formation.returnSelectedToTray}
            >
              Return selected to tray
            </button>
            <button type="button" className="button button--ghost" onClick={formation.reset}>
              Reset formation
            </button>
            <button
              type="button"
              className="button button--primary"
              disabled={!formation.valid || formation.locked}
              onClick={formation.lock}
            >
              {formation.locked ? 'Formation locked' : 'Lock formation'}
            </button>
          </div>
          {formation.locked && (
            <p className="locked-message" role="status">
              Local formation locked. Reset to edit again.
            </p>
          )}
        </section>

        <PieceTray
          inventory={formation.inventory}
          placements={formation.placements}
          selectedPieceId={formation.selectedPieceId}
          locked={formation.locked}
          onSelect={formation.selectPiece}
        />
      </div>
    </main>
  )
}
