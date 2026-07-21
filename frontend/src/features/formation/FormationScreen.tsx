import { useMemo, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { lockFormation, submitFormation } from '../../api/client'
import { executeWithOneStaleRetry } from '../../api/staleCommand'
import type { CommandResponse, FormationItem, PlayerMatchView } from '../../api/types'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { Board } from '../../components/Board/Board'
import { PieceTray } from '../../components/PieceTray/PieceTray'
import { FORMATION_CELL_COUNT, FORMATION_PIECE_COUNT, type Placements } from '../../game/formation'
import type { MatchSession } from '../../session/session'
import { useFormation } from './useFormation'

const placementSignature = (placements: Placements): string => Object.entries(placements)
  .sort(([left], [right]) => left.localeCompare(right))
  .map(([id, position]) => `${id}:${position.row}:${position.column}`)
  .join('|')

export const formationItems = (
  inventory: ReturnType<typeof useFormation>['inventory'],
  placements: Placements,
): FormationItem[] => inventory.map((piece) => ({
  pieceId: piece.id,
  rank: piece.rank,
  row: placements[piece.id].row,
  column: placements[piece.id].column,
}))

interface FormationScreenProps {
  view: PlayerMatchView
  session: MatchSession
  onView: (response: CommandResponse) => void
  onStale: () => Promise<PlayerMatchView>
}

export function FormationScreen({ view, session, onView, onStale }: FormationScreenProps) {
  const ownLocked = view.requestingSide === 'PLAYER_ONE'
    ? view.playerOneLocked
    : view.playerTwoLocked
  const opponentJoined = view.requestingSide === 'PLAYER_ONE'
    ? view.playerTwoOccupied
    : view.playerOneOccupied
  const opponentLocked = view.requestingSide === 'PLAYER_ONE'
    ? view.playerTwoLocked
    : view.playerOneLocked
  const initialPieces = useMemo(() => view.ownPieces, [])
  const formation = useFormation(view.requestingSide, ownLocked, initialPieces)
  const currentSignature = placementSignature(formation.placements)
  const currentSignatureRef = useRef(currentSignature)
  currentSignatureRef.current = currentSignature
  const [submittedSignature, setSubmittedSignature] = useState(
    view.ownPieces.length === FORMATION_PIECE_COUNT ? currentSignature : '',
  )
  const submittedAttemptSignature = useRef('')

  const submitMutation = useMutation({
    mutationFn: () => {
      const submittedVersion = view.version
      const submittedPieces = formationItems(formation.inventory, formation.placements)
      const submittedPlacementSignature = currentSignature
      const commandId = crypto.randomUUID()
      submittedAttemptSignature.current = submittedPlacementSignature
      return executeWithOneStaleRetry({
        expectedVersion: submittedVersion,
        commandId,
        execute: (expectedVersion, retryCommandId) => submitFormation(
          session.matchId,
          expectedVersion,
          submittedPieces,
          retryCommandId,
        ),
        refetch: onStale,
        canRetry: (refreshedView) => isSubmitRetrySafe(
          refreshedView,
          submittedPlacementSignature,
          currentSignatureRef.current,
          submittedPieces,
          formation.valid,
        ),
      })
    },
    retry: false,
    onSuccess: (response) => {
      setSubmittedSignature(submittedAttemptSignature.current)
      onView(response)
    },
  })
  const lockMutation = useMutation({
    mutationFn: () => {
      const submittedVersion = view.version
      const lockedPlacementSignature = currentSignature
      const commandId = crypto.randomUUID()
      return executeWithOneStaleRetry({
        expectedVersion: submittedVersion,
        commandId,
        execute: (expectedVersion, retryCommandId) => lockFormation(
          session.matchId,
          expectedVersion,
          retryCommandId,
        ),
        refetch: onStale,
        canRetry: (refreshedView) => isLockRetrySafe(
          refreshedView,
          lockedPlacementSignature,
          currentSignatureRef.current,
        ),
      })
    },
    retry: false,
    onSuccess: (response) => onView(response),
  })
  const error = submitMutation.error ?? lockMutation.error
  const submittedCurrent = Boolean(submittedSignature) && submittedSignature === currentSignature
  const emptyCells = FORMATION_CELL_COUNT - formation.placedCount

  return (
    <div className="formation-workspace">
      <section className="board-panel" aria-labelledby="formation-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Canonical deployment</p>
            <h2 id="formation-title">Formation</h2>
          </div>
          <div className={`status-pill ${formation.valid ? 'status-pill--valid' : ''}`}>
            {formation.placedCount}/{FORMATION_PIECE_COUNT} placed · {emptyCells} empty
          </div>
        </div>

        {!opponentJoined && (
          <p className="waiting-message" role="status">
            {view.roomCode
              ? `Waiting for a second player. Share room code ${view.roomCode}.`
              : 'Waiting for the matched opponent.'}
          </p>
        )}
        <p className="opponent-status">
          Opponent: {opponentJoined ? 'joined' : 'not joined'} · {opponentLocked ? 'locked' : 'not locked'}
        </p>

        <Board
          inventory={formation.inventory}
          placements={formation.placements}
          selectedPieceId={formation.selectedPieceId}
          side={view.requestingSide}
          locked={ownLocked}
          onCellClick={formation.selectCell}
          onPieceSelect={formation.selectPiece}
        />

        <div className="actions">
          <button
            type="button"
            className="button button--secondary"
            disabled={!formation.selectedPieceId
              || !formation.placements[formation.selectedPieceId]
              || ownLocked}
            onClick={formation.returnSelectedToTray}
          >
            Return selected to tray
          </button>
          <button
            type="button"
            className="button button--ghost"
            disabled={ownLocked}
            onClick={formation.reset}
          >
            Reset formation
          </button>
          <button
            type="button"
            className="button button--secondary"
            disabled={!opponentJoined || !formation.valid || ownLocked || submitMutation.isPending}
            onClick={() => submitMutation.mutate()}
          >
            {submitMutation.isPending ? 'Submitting…' : submittedCurrent ? 'Formation submitted' : 'Submit formation'}
          </button>
          <button
            type="button"
            className="button button--primary"
            disabled={!formation.valid || !submittedCurrent || ownLocked || lockMutation.isPending}
            onClick={() => lockMutation.mutate()}
          >
            {ownLocked ? 'Formation locked' : lockMutation.isPending ? 'Locking…' : 'Lock formation'}
          </button>
        </div>
        {ownLocked && (
          <p className="locked-message" role="status">
            Server-confirmed lock. Your formation can no longer be edited.
          </p>
        )}
        <ApiErrorNotice error={error} />
      </section>

      <PieceTray
        inventory={formation.inventory}
        placements={formation.placements}
        selectedPieceId={formation.selectedPieceId}
        locked={ownLocked}
        compact
        onSelect={formation.selectPiece}
      />
    </div>
  )
}

function isSubmitRetrySafe(
  refreshedView: PlayerMatchView,
  submittedSignature: string,
  latestSignature: string,
  submittedPieces: FormationItem[],
  submittedFormationValid: boolean,
): boolean {
  const ownLocked = refreshedView.requestingSide === 'PLAYER_ONE'
    ? refreshedView.playerOneLocked
    : refreshedView.playerTwoLocked
  const opponentPresent = refreshedView.requestingSide === 'PLAYER_ONE'
    ? refreshedView.playerTwoOccupied
    : refreshedView.playerOneOccupied
  const pieceIds = new Set(submittedPieces.map((piece) => piece.pieceId))
  const positions = new Set(submittedPieces.map((piece) => `${piece.row}:${piece.column}`))
  return refreshedView.phase === 'FORMATION'
    && !ownLocked
    && opponentPresent
    && submittedSignature === latestSignature
    && submittedFormationValid
    && submittedPieces.length === FORMATION_PIECE_COUNT
    && pieceIds.size === FORMATION_PIECE_COUNT
    && positions.size === FORMATION_PIECE_COUNT
    && refreshedView.opponentPieces.every((piece) => !('rank' in piece))
}

function isLockRetrySafe(
  refreshedView: PlayerMatchView,
  submittedSignature: string,
  latestSignature: string,
): boolean {
  const ownLocked = refreshedView.requestingSide === 'PLAYER_ONE'
    ? refreshedView.playerOneLocked
    : refreshedView.playerTwoLocked
  const opponentPresent = refreshedView.requestingSide === 'PLAYER_ONE'
    ? refreshedView.playerTwoOccupied
    : refreshedView.playerOneOccupied
  const authoritativePlacements = Object.fromEntries(refreshedView.ownPieces.flatMap((piece) =>
    piece.position ? [[piece.id, piece.position]] : []))
  return refreshedView.phase === 'FORMATION'
    && !ownLocked
    && opponentPresent
    && submittedSignature === latestSignature
    && refreshedView.ownPieces.length === FORMATION_PIECE_COUNT
    && placementSignature(authoritativePlacements) === submittedSignature
    && refreshedView.opponentPieces.every((piece) => !('rank' in piece))
}
