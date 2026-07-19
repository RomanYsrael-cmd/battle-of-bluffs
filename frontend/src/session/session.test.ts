import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearSession,
  loadSession,
  saveSession,
  SESSION_STORAGE_KEY,
} from './session'

describe('authenticated match navigation', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })

  it('stores only match navigation data and clears the local session', () => {
    const navigation = { matchId: 'match-a', roomCode: 'ROOMA1' }
    saveSession(navigation)

    expect(loadSession()).toEqual(navigation)
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).not.toBeNull()
    expect(localStorage.length).toBe(0)

    clearSession()
    expect(loadSession()).toBeNull()
  })

  it('does not accept incomplete or corrupt stored identities', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, '{bad json')
    expect(loadSession()).toBeNull()
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ matchId: 'only-one-field' }))
    expect(loadSession()).toBeNull()
  })
})
