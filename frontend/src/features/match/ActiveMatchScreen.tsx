import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { makeMove, MatchApiError, resign } from '../../api/client'
import type { CommandResponse, PlayerMatchView } from '../../api/types'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import type { Position } from '../../game/coordinates'
import type { MatchSession } from '../../session/session'
import { EventHistory } from './EventHistory'
import { MatchBoard } from './MatchBoard'
import { TerminalDisclosure } from './TerminalDisclosure'
import { getMatchHistory } from '../../profile/client'

interface ActiveMatchScreenProps {
  view: PlayerMatchView
  session: MatchSession
  onView: (response: CommandResponse) => void
  onStale: () => void
  onLeave: () => void
}

export function ActiveMatchScreen({
  view,
  session,
  onView,
  onStale,
  onLeave,
}: ActiveMatchScreenProps) {
  const [selectedPieceId, setSelectedPieceId] = useState<string | null>(null)
  const handleError = (error: unknown) => {
    if (error instanceof MatchApiError && error.code === 'STALE_VERSION') {
      setSelectedPieceId(null)
      onStale()
    }
  }
  const moveMutation = useMutation({
    mutationFn: ({ source, destination }: { source: Position; destination: Position }) =>
      makeMove(session.matchId, view.version, source, destination),
    retry: false,
    onSuccess: (response) => {
      setSelectedPieceId(null)
      onView(response)
    },
    onError: handleError,
  })
  const resignMutation = useMutation({
    mutationFn: () => resign(session.matchId, view.version),
    retry: false,
    onSuccess: (response) => onView(response),
    onError: handleError,
  })
  const ratingHistory = useQuery({
    queryKey: ['match-history', view.matchId],
    queryFn: () => getMatchHistory(view.matchId),
    enabled: view.phase === 'TERMINAL'
      && view.mode === 'RANKED'
      && view.terminalResult?.reason !== 'NO_CONTEST',
    refetchInterval: (query) => query.state.data?.ratingChange ? false : 1_000,
  })
  const selectedPiece = view.ownPieces.find((piece) => piece.id === selectedPieceId)
  const isOwnTurn = view.currentPlayer === view.requestingSide
  const terminal = view.phase === 'TERMINAL'
  const mutationError = moveMutation.error ?? resignMutation.error
  const error = mutationError instanceof MatchApiError && mutationError.code === 'STALE_VERSION'
    ? null
    : mutationError

  return (
    <div className="match-workspace">
      <section className="board-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Current player</p>
            <h2>
              {terminal
                ? 'Match complete'
                : isOwnTurn
                  ? 'Your turn'
                  : 'Opponent’s turn'}
            </h2>
          </div>
          <div className="status-pill">{view.phase} · version {view.version}</div>
        </div>

        {view.pendingFlagChallenge && (
          <p className="challenge-notice" role="status">
            Flag challenge at {view.pendingFlagChallenge.flagPosition.row},
            {view.pendingFlagChallenge.flagPosition.column}. Responding side:{' '}
            {view.pendingFlagChallenge.respondingPlayer === 'PLAYER_ONE' ? 'Player 1' : 'Player 2'}.
          </p>
        )}

        <MatchBoard
          view={view}
          selectedPieceId={selectedPieceId}
          disabled={terminal || !isOwnTurn || moveMutation.isPending}
          onSelectPiece={(pieceId) => setSelectedPieceId((current) => current === pieceId ? null : pieceId)}
          onDestination={(destination) => {
            if (selectedPiece?.position) {
              moveMutation.mutate({ source: selectedPiece.position, destination })
            }
          }}
        />

        <div className="actions">
          <button
            type="button"
            className="button button--secondary"
            disabled={terminal || resignMutation.isPending}
            onClick={() => {
              if (window.confirm('Resign this match? This cannot be undone.')) {
                resignMutation.mutate()
              }
            }}
          >
            {resignMutation.isPending ? 'Resigning…' : 'Resign match'}
          </button>
        </div>
        {moveMutation.isPending && <p role="status">Waiting for the server to apply the move…</p>}
        <ApiErrorNotice error={error} />
        {terminal && (
          <TerminalDisclosure
            view={view}
            onLeave={onLeave}
            ratingChange={ratingHistory.data?.ratingChange}
          />
        )}
      </section>
      <EventHistory events={view.events} />
    </div>
  )
}
