import type { EventView, PlayerMatchView, TerminalResult } from '../api/types'

export type CompetitiveTier =
  | 'CADET' | 'PRIVATE' | 'SERGEANT' | 'LIEUTENANT'
  | 'CAPTAIN' | 'COLONEL' | 'GENERAL' | 'GRAND_GENERAL'

export interface CurrentRating {
  rating: number
  tier: CompetitiveTier
  tierLabel: string
  ratedGames: number
  wins: number
  losses: number
  draws: number
  provisional: boolean
  seasonId: string
  seasonName: string
}

export interface ProfileStats {
  totalGames: number
  rankedGames: number
  casualGames: number
  wins: number
  losses: number
  draws: number
  noContests: number
  winRate: number
}

export interface MatchListItem {
  matchId: string
  mode: 'CASUAL' | 'RANKED'
  timerMode: string
  phase: 'FORMATION' | 'ACTIVE' | 'TERMINAL'
  outcome: 'IN_PROGRESS' | 'WIN' | 'LOSS' | 'DRAW' | 'NO_CONTEST'
  opponentDisplayName: string
  opponentUsername: string | null
  createdAt: string | null
  terminalAt: string | null
  terminalResult: TerminalResult | null
  acceptedMoveCount: number
  ratingDelta: number | null
}

export interface PrivateProfile {
  username: string
  displayName: string
  email: string
  joinedAt: string
  emailVerified: boolean
  statistics: ProfileStats
  rating: CurrentRating
  recentMatches: MatchListItem[]
}

export interface PublicProfile {
  username: string
  displayName: string
  joinedAt: string
  statistics: ProfileStats
  rating: CurrentRating
  recentMatches: MatchListItem[]
}

export interface PageView<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface RatingChange {
  matchId: string
  preMatchRating: number
  expectedScore: number
  actualScore: number
  ratingDelta: number
  postMatchRating: number
  kFactor: number
  seasonId: string
  calculatedAt: string
}

export interface MatchHistory {
  summary: {
    matchId: string
    mode: 'CASUAL' | 'RANKED'
    timerMode: string
    phase: 'FORMATION' | 'ACTIVE' | 'TERMINAL'
    terminalResult: TerminalResult | null
    acceptedMoveCount: number | null
    createdAt: string | null
    terminalAt: string | null
  }
  participant: boolean
  participantView: PlayerMatchView | null
  ratingChange: RatingChange | null
}

export interface LeaderboardEntry {
  userId: string
  username: string
  displayName: string
  rating: number
  tier: CompetitiveTier
  tierLabel: string
  ratedGames: number
  wins: number
  losses: number
  draws: number
  winRate: number
  provisional: boolean
  eligible: boolean
  position: number | null
}

export interface LeaderboardPage {
  scope: 'SEASONAL' | 'ALL_TIME'
  seasonName: string | null
  entries: LeaderboardEntry[]
  ownEntry: LeaderboardEntry | null
  page: number
  size: number
  totalItems: number
  totalPages: number
  minimumGames: number
}

export type HistoryEvent = EventView
