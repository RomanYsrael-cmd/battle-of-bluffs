import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearSession,
  loadSession,
  saveSession,
  SESSION_STORAGE_KEY,
} from './session'

describe('temporary tab identity', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })

  it('stores identity only in sessionStorage and clears the local session', () => {
    const identity = { playerId: 'tab-a', matchId: 'match-a', roomCode: 'ROOMA1' }
    saveSession(identity)

    expect(loadSession()).toEqual(identity)
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).not.toBeNull()
    expect(localStorage.length).toBe(0)

    clearSession()
    expect(loadSession()).toBeNull()
  })

  it('does not accept incomplete or corrupt stored identities', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, '{bad json')
    expect(loadSession()).toBeNull()
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ playerId: 'only-one-field' }))
    expect(loadSession()).toBeNull()
  })
})
