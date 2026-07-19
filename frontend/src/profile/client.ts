import { apiRequest } from '../api/http'
import type {
  LeaderboardPage,
  MatchHistory,
  MatchListItem,
  PageView,
  PrivateProfile,
  PublicProfile,
} from './types'

export const getMyProfile = (): Promise<PrivateProfile> => apiRequest('/api/profile/me')

export const updateMyProfile = (displayName: string): Promise<PrivateProfile> =>
  apiRequest('/api/profile/me', {
    method: 'PATCH',
    body: JSON.stringify({ displayName }),
  })

export const getPublicProfile = (username: string): Promise<PublicProfile> =>
  apiRequest(`/api/profiles/${encodeURIComponent(username)}`)

export const getMyMatches = (page = 0, size = 20): Promise<PageView<MatchListItem>> =>
  apiRequest(`/api/profile/me/matches?page=${page}&size=${size}`)

export const getMatchHistory = (matchId: string): Promise<MatchHistory> =>
  apiRequest(`/api/matches/${matchId}/history`)

export const getLeaderboard = (
  scope: 'seasonal' | 'all-time',
  page = 0,
  size = 25,
): Promise<LeaderboardPage> =>
  apiRequest(`/api/leaderboards/${scope}?page=${page}&size=${size}`)
