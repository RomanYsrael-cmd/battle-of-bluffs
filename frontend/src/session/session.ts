export interface MatchSession {
  playerId: string
  matchId: string
  roomCode: string
}

export const SESSION_STORAGE_KEY = 'battle-of-bluffs.match-session'

export const loadSession = (): MatchSession | null => {
  const stored = sessionStorage.getItem(SESSION_STORAGE_KEY)
  if (!stored) return null
  try {
    const parsed = JSON.parse(stored) as Partial<MatchSession>
    return parsed.playerId && parsed.matchId && parsed.roomCode
      ? { playerId: parsed.playerId, matchId: parsed.matchId, roomCode: parsed.roomCode }
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
