import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cancelMatch,
  createMatch,
  getCurrentMatches,
  joinMatch,
  leaveMatch,
  MatchApiError,
} from '../../api/client'
import type { CommandResponse, CurrentMatchSummary } from '../../api/types'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { BRAND } from '../../config/brand'
import { RankedMatchmaking } from '../../matchmaking/RankedMatchmaking'
import { clearSession, loadSession, saveSession } from '../../session/session'

export const currentMatchesQueryKey = ['current-matches'] as const

interface HomeScreenProps {
  onEnteredMatch: (response: CommandResponse) => void
  onContinueMatch: (activity: CurrentMatchSummary) => void
}

export function HomeScreen({ onEnteredMatch, onContinueMatch }: HomeScreenProps) {
  const queryClient = useQueryClient()
  const [roomCode, setRoomCode] = useState('')
  const [timerMode, setTimerMode] = useState<'CASUAL_UNTIMED' | 'STANDARD_15_PLUS_5'>('CASUAL_UNTIMED')
  const current = useQuery({
    queryKey: currentMatchesQueryKey,
    queryFn: getCurrentMatches,
  })
  const createMutation = useMutation({
    mutationFn: () => createMatch(timerMode),
    retry: false,
    onSuccess: onEnteredMatch,
  })
  const joinMutation = useMutation({
    mutationFn: () => joinMatch(roomCode.trim().toUpperCase()),
    retry: false,
    onSuccess: onEnteredMatch,
  })
  const lifecycleMutation = useMutation({
    mutationFn: async ({ activity, action }: {
      activity: CurrentMatchSummary
      action: 'cancel' | 'leave'
    }) => {
      const execute = (summary: CurrentMatchSummary) => action === 'cancel'
        ? cancelMatch(summary.matchId, summary.version)
        : leaveMatch(summary.matchId, summary.version)
      try {
        return await execute(activity)
      } catch (error) {
        if (!(error instanceof MatchApiError) || error.code !== 'STALE_VERSION') throw error
        const authoritative = await getCurrentMatches()
        queryClient.setQueryData(currentMatchesQueryKey, authoritative)
        const refreshed = authoritative.activities.find(
          (candidate) => candidate.matchId === activity.matchId,
        )
        const stillPermitted = action === 'cancel' ? refreshed?.canCancel : refreshed?.canLeave
        if (!refreshed || !stillPermitted) throw error
        return execute(refreshed)
      }
    },
    onSuccess: async (_response, variables) => {
      if (loadSession()?.matchId === variables.activity.matchId) clearSession()
      await queryClient.invalidateQueries({ queryKey: currentMatchesQueryKey })
      await queryClient.invalidateQueries({ queryKey: ['matchmaking'] })
    },
  })
  const activities = current.data?.activities ?? []

  useEffect(() => {
    if (!current.data) return
    const stored = loadSession()
    if (!stored) return
    const currentActivities = current.data.activities ?? []
    const authoritative = currentActivities.find((activity) => activity.matchId === stored.matchId)
      ?? (currentActivities.length === 1 ? currentActivities[0] : undefined)
    if (!authoritative) {
      clearSession()
      return
    }
    saveSession({ matchId: authoritative.matchId, roomCode: authoritative.roomCode })
  }, [current.data])

  const confirmLifecycle = (activity: CurrentMatchSummary, action: 'cancel' | 'leave') => {
    const prompt = action === 'cancel'
      ? 'Cancel this private room? This cannot be undone.'
      : 'Leave this private lobby? Your seat and submitted formation will be removed.'
    if (window.confirm(prompt)) lifecycleMutation.mutate({ activity, action })
  }

  const pending = createMutation.isPending || joinMutation.isPending
  const error = createMutation.error ?? joinMutation.error
    ?? lifecycleMutation.error ?? current.error

  return (
    <main className="app-shell home-screen">
      {activities.length > 0 && (
        <section className="current-games" aria-labelledby="current-game-heading">
          <p className="eyebrow">Resume</p>
          <h1 id="current-game-heading">Current game</h1>
          {current.data?.multipleOpenMatches && (
            <p className="integrity-warning" role="alert">
              Multiple open games were found. Each is shown so none is hidden.
            </p>
          )}
          <div className="current-games__list">
            {activities.map((activity) => (
              <CurrentGameCard
                key={activity.matchId}
                activity={activity}
                pending={lifecycleMutation.isPending}
                onContinue={() => onContinueMatch(activity)}
                onLifecycle={confirmLifecycle}
              />
            ))}
          </div>
        </section>
      )}

      <header className="hero hero--home">
        <div>
          <p className="eyebrow">{BRAND.productName} · Private match</p>
          <h1>{BRAND.tagline}</h1>
          <p className="hero__copy">
            Create a private room or join another player with their six-character room code.
          </p>
        </div>
      </header>

      <section className="home-actions" aria-label="Private match actions">
        <article className="entry-card">
          <p className="eyebrow">Host</p>
          <h2>Create private match</h2>
          <p>Your account takes Player 1 and receives a private room code to share.</p>
          <label htmlFor="timer-mode">Clock</label>
          <select
            id="timer-mode"
            className="text-input"
            value={timerMode}
            disabled={activities.length > 0}
            onChange={(event) => setTimerMode(event.target.value as typeof timerMode)}
          >
            <option value="CASUAL_UNTIMED">Untimed</option>
            <option value="STANDARD_15_PLUS_5">15 minutes + 5 seconds</option>
          </select>
          <button
            type="button"
            className="button button--primary button--wide"
            disabled={pending || activities.length > 0}
            onClick={() => createMutation.mutate()}
          >
            {createMutation.isPending ? 'Creating room…' : 'Create private match'}
          </button>
        </article>

        <form
          className="entry-card"
          onSubmit={(event) => {
            event.preventDefault()
            if (roomCode.trim()) joinMutation.mutate()
          }}
        >
          <p className="eyebrow">Guest</p>
          <h2>Join match</h2>
          <label htmlFor="room-code">Room code</label>
          <input
            id="room-code"
            className="text-input room-code-input"
            value={roomCode}
            maxLength={6}
            autoComplete="off"
            disabled={activities.length > 0}
            onChange={(event) => setRoomCode(event.target.value.toUpperCase())}
          />
          <button
            type="submit"
            className="button button--secondary button--wide"
            disabled={pending || !roomCode.trim() || activities.length > 0}
          >
            {joinMutation.isPending ? 'Joining room…' : 'Join match'}
          </button>
        </form>

        <RankedMatchmaking
          onEnteredMatch={onEnteredMatch}
          blockingMatches={activities}
          onContinueMatch={onContinueMatch}
        />
      </section>
      <ApiErrorNotice error={error} />
    </main>
  )
}

function CurrentGameCard({
  activity,
  pending,
  onContinue,
  onLifecycle,
}: {
  activity: CurrentMatchSummary
  pending: boolean
  onContinue: () => void
  onLifecycle: (activity: CurrentMatchSummary, action: 'cancel' | 'leave') => void
}) {
  return (
    <article className="current-game-card">
      <div>
        <p className="eyebrow">{activity.mode === 'RANKED' ? 'Ranked' : 'Casual'}</p>
        <h2>{activityLabel(activity)}</h2>
        {activity.side === 'PLAYER_ONE' && activity.roomCode && (
          <p>Room code <strong>{activity.roomCode}</strong></p>
        )}
        <p>{activity.opponentPresent ? 'Opponent joined' : 'Waiting for opponent'}</p>
        <p className="current-game-card__time">Updated {formatTimestamp(activity.updatedAt)}</p>
      </div>
      <div className="current-game-card__actions">
        <button type="button" className="button button--primary" onClick={onContinue}>
          {activity.phase === 'ACTIVE' ? 'Resume game' : 'Continue game'}
        </button>
        {activity.canCancel && (
          <button
            type="button"
            className="button button--secondary"
            disabled={pending}
            onClick={() => onLifecycle(activity, 'cancel')}
          >
            Cancel room
          </button>
        )}
        {activity.canLeave && (
          <button
            type="button"
            className="button button--secondary"
            disabled={pending}
            onClick={() => onLifecycle(activity, 'leave')}
          >
            Leave lobby
          </button>
        )}
      </div>
    </article>
  )
}

export function activityLabel(activity: CurrentMatchSummary): string {
  if (activity.phase === 'ACTIVE') {
    return activity.currentPlayer === activity.side ? 'In progress · Your turn' : 'In progress · Opponent’s turn'
  }
  if (!activity.opponentPresent) return 'Waiting for opponent'
  return activity.ownLocked ? 'Formation · Waiting for opponent' : 'Formation pending'
}

function formatTimestamp(value: string | null): string {
  if (!value) return 'recently'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}
