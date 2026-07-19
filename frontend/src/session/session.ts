export interface MatchSession {
  matchId: string
  roomCode: string | null
}

export const SESSION_STORAGE_KEY = 'battle-of-bluffs.match-session'

export const loadSession = (): MatchSession | null => {
  const stored = sessionStorage.getItem(SESSION_STORAGE_KEY)
  if (!stored) return null
  try {
    const parsed = JSON.parse(stored) as Partial<MatchSession>
    const hasRoomCode = Object.prototype.hasOwnProperty.call(parsed, 'roomCode')
    const validRoomCode = parsed.roomCode === null || typeof parsed.roomCode === 'string'
    return parsed.matchId && hasRoomCode && validRoomCode
      ? { matchId: parsed.matchId, roomCode: parsed.roomCode ?? null }
      : null
  } catch {
    return null
  }
}

export const saveSession = (session: MatchSession): void => {
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

export const clearSession = (): void => {
  sessionStorage.removeItem(SESSION_STORAGE_KEY)
}
