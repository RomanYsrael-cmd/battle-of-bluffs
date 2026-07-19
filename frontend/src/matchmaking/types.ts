import type { PlayerMatchView } from '../api/types'

export interface QueueStatus {
  state: 'IDLE' | 'QUEUED' | 'MATCH_FOUND'
  queuedAt: string | null
  elapsedSeconds: number
  searchRange: number
  rating: number
  matchId: string | null
}

export interface MatchmakingFound {
  type: 'MATCHMAKING_FOUND'
  matchId: string
  opponentDisplayName: string
  opponentRating: number
  matchedAt: string
  view: PlayerMatchView
}
