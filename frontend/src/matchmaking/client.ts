import { apiRequest } from '../api/http'
import type { QueueStatus } from './types'

const URL = '/api/matchmaking'

export const getMatchmakingStatus = (): Promise<QueueStatus> =>
  apiRequest<QueueStatus>(`${URL}/status`)

export const joinRankedQueue = (): Promise<QueueStatus> =>
  apiRequest<QueueStatus>(`${URL}/ranked`, { method: 'POST' })

export const cancelRankedQueue = (): Promise<QueueStatus> =>
  apiRequest<QueueStatus>(`${URL}/ranked`, { method: 'DELETE' })
