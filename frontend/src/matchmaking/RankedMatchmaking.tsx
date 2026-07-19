import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getPlayerView } from '../api/client'
import type { CommandResponse, PlayerMatchView } from '../api/types'
import { ApiErrorNotice } from '../components/ApiErrorNotice'
import { cancelRankedQueue, getMatchmakingStatus, joinRankedQueue } from './client'
import { connectMatchmaking } from './socket'
import type { MatchmakingFound, QueueStatus } from './types'

export function RankedMatchmaking({
  onEnteredMatch,
}: {
  onEnteredMatch: (response: CommandResponse) => void
}) {
  const queryClient = useQueryClient()
  const enteredMatch = useRef<string | null>(null)
  const joining = useRef(false)
  const [connected, setConnected] = useState(false)
  const [transitionError, setTransitionError] = useState<unknown>(null)
  const [, setClockTick] = useState(0)
  const status = useQuery({
    queryKey: ['matchmaking'],
    queryFn: getMatchmakingStatus,
    refetchInterval: 5_000,
  })

  const enter = async (matchId: string, suppliedView?: PlayerMatchView) => {
    if (enteredMatch.current === matchId) return
    enteredMatch.current = matchId
    try {
      const view = suppliedView ?? await getPlayerView(matchId)
      setTransitionError(null)
      onEnteredMatch({ commandId: null, version: view.version, matchId, roomCode: null, view })
    } catch (error) {
      enteredMatch.current = null
      setTransitionError(error)
    }
  }

  useEffect(() => {
    const connection = connectMatchmaking(
      (event: MatchmakingFound) => {
        queryClient.setQueryData<QueueStatus>(['matchmaking'], {
          state: 'MATCH_FOUND', queuedAt: null, elapsedSeconds: 0,
          searchRange: 0, rating: 0, matchId: event.matchId,
        })
        void enter(event.matchId, event.view)
      },
      setConnected,
    )
    return connection.disconnect
  }, [queryClient])

  useEffect(() => {
    if (status.data?.state === 'MATCH_FOUND' && status.data.matchId) {
      void enter(status.data.matchId)
    }
  }, [status.data?.state, status.data?.matchId, status.dataUpdatedAt])

  useEffect(() => {
    if (status.data?.state !== 'QUEUED') return undefined
    const interval = window.setInterval(() => setClockTick((value) => value + 1), 1_000)
    return () => window.clearInterval(interval)
  }, [status.data?.state])

  const join = useMutation({
    mutationFn: joinRankedQueue,
    onSuccess: (value) => queryClient.setQueryData(['matchmaking'], value),
    onSettled: () => { joining.current = false },
  })
  const cancel = useMutation({
    mutationFn: cancelRankedQueue,
    onSuccess: (value) => queryClient.setQueryData(['matchmaking'], value),
  })
  const queued = status.data?.state === 'QUEUED'
  const elapsed = status.data?.queuedAt
    ? Math.max(0, Math.floor((Date.now() - new Date(status.data.queuedAt).getTime()) / 1_000))
    : status.data?.elapsedSeconds ?? 0
  const range = queued
    ? Math.min(600, 200 + Math.floor(elapsed / 30) * 50)
    : status.data?.searchRange ?? 200
  const error = join.error ?? cancel.error ?? status.error ?? transitionError
  const startSearch = () => {
    if (joining.current || queued) return
    joining.current = true
    join.mutate()
  }

  return (
    <article className="entry-card ranked-entry" aria-labelledby="ranked-heading">
      <p className="eyebrow">Competitive</p>
      <h2 id="ranked-heading">Ranked matchmaking</h2>
      <p>Verified players are paired by rating for a fixed 15 minute + 5 second game.</p>
      <p className="queue-connection" aria-live="polite">
        Match alerts {connected ? 'connected' : 'reconnecting'}
      </p>
      {queued ? (
        <div className="queue-status" role="status">
          <strong>Searching for an opponent…</strong>
          <span>{formatElapsed(elapsed)} elapsed</span>
          <span>Rating {status.data?.rating} · ±{range} search range</span>
          <button
            type="button"
            className="button button--secondary button--wide"
            disabled={cancel.isPending}
            onClick={() => cancel.mutate()}
          >
            {cancel.isPending ? 'Cancelling…' : 'Cancel search'}
          </button>
        </div>
      ) : (
        <button
          type="button"
          className="button button--primary button--wide"
          disabled={join.isPending || status.isPending || status.data?.state === 'MATCH_FOUND'}
          onClick={startSearch}
        >
          {join.isPending ? 'Joining queue…' : 'Find ranked match'}
        </button>
      )}
      <ApiErrorNotice error={error} />
    </article>
  )
}

function formatElapsed(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`
}
