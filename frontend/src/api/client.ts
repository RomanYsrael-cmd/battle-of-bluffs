import type {
  ApiErrorBody,
  CommandResponse,
  FormationItem,
  PlayerMatchView,
} from './types'
import type { Position } from '../game/coordinates'

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '')
const API_BASE_URL = `${configuredBaseUrl ?? ''}/api/dev/matches`

export const ERROR_MESSAGES: Record<string, string> = {
  MATCH_NOT_FOUND: 'That room could not be found.',
  MATCH_FULL: 'That room already has two players.',
  INVALID_FORMATION: 'The server rejected this formation. Review every piece and position.',
  ALREADY_LOCKED: 'Your formation is already locked.',
  PLAYER_NOT_IN_MATCH: 'This temporary player session does not belong to the match.',
  STALE_VERSION: 'The match changed. Synchronizing the latest state…',
  COMMAND_CONFLICT: 'That command identifier was already used for another action.',
  ILLEGAL_MOVE: 'The server rejected that move as illegal.',
  TERMINAL_MATCH: 'This match has already ended.',
  INVALID_ROOM_STATE: 'That action is not available in the current match phase.',
  INVALID_REQUEST: 'The request was incomplete or invalid.',
  SERVER_UNAVAILABLE: 'The development server is unavailable. Check that the backend is running.',
}

export class MatchApiError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
  ) {
    super(ERROR_MESSAGES[code] ?? 'The match server could not complete that action.')
    this.name = 'MatchApiError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: init?.body
        ? { 'Content-Type': 'application/json', ...init.headers }
        : init?.headers,
    })
  } catch {
    throw new MatchApiError('SERVER_UNAVAILABLE', 0)
  }

  if (!response.ok) {
    let body: ApiErrorBody | null = null
    try {
      body = await response.json() as ApiErrorBody
    } catch {
      // A proxy or unavailable server can return a non-JSON error page.
    }
    throw new MatchApiError(body?.code ?? 'SERVER_UNAVAILABLE', response.status)
  }
  return response.json() as Promise<T>
}

export const createMatch = (): Promise<CommandResponse> => request('', {
  method: 'POST',
  body: JSON.stringify({}),
})

export const joinMatch = (roomCode: string): Promise<CommandResponse> =>
  request('/join', {
    method: 'POST',
    body: JSON.stringify({
      commandId: crypto.randomUUID(),
      roomCode,
      expectedVersion: 1,
    }),
  })

export const getPlayerView = (matchId: string, playerId: string): Promise<PlayerMatchView> =>
  request(`/${matchId}?playerId=${encodeURIComponent(playerId)}`)

export const submitFormation = (
  matchId: string,
  playerId: string,
  expectedVersion: number,
  pieces: FormationItem[],
): Promise<CommandResponse> => request(`/${matchId}/formation`, {
  method: 'PUT',
  body: JSON.stringify({
    commandId: crypto.randomUUID(),
    playerId,
    expectedVersion,
    pieces,
  }),
})

export const lockFormation = (
  matchId: string,
  playerId: string,
  expectedVersion: number,
): Promise<CommandResponse> => request(`/${matchId}/lock`, {
  method: 'POST',
  body: JSON.stringify({ commandId: crypto.randomUUID(), playerId, expectedVersion }),
})

export const makeMove = (
  matchId: string,
  playerId: string,
  expectedVersion: number,
  source: Position,
  destination: Position,
): Promise<CommandResponse> => request(`/${matchId}/moves`, {
  method: 'POST',
  body: JSON.stringify({
    commandId: crypto.randomUUID(),
    playerId,
    expectedVersion,
    source,
    destination,
  }),
})

export const resign = (
  matchId: string,
  playerId: string,
  expectedVersion: number,
): Promise<CommandResponse> => request(`/${matchId}/resign`, {
  method: 'POST',
  body: JSON.stringify({ commandId: crypto.randomUUID(), playerId, expectedVersion }),
})
