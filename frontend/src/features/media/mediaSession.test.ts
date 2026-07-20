import { beforeEach, describe, expect, it } from 'vitest'
import { clearMediaSession, mediaSessionKey } from './mediaSession'

describe('media browser-session consent', () => {
  beforeEach(() => sessionStorage.clear())

  it('scopes consent by match and clears every media opt-in on logout', () => {
    sessionStorage.setItem(mediaSessionKey('account-a', 'match-a', 'cycle-1'), 'true')
    sessionStorage.setItem(mediaSessionKey('account-a', 'match-b', 'cycle-1'), 'true')
    sessionStorage.setItem('gotg:unrelated', 'preserved')

    expect(sessionStorage.getItem(mediaSessionKey('account-b', 'match-a', 'cycle-1'))).toBeNull()
    expect(sessionStorage.getItem(mediaSessionKey('account-a', 'match-a', 'cycle-2'))).toBeNull()
    clearMediaSession()

    expect(sessionStorage.getItem(mediaSessionKey('account-a', 'match-a', 'cycle-1'))).toBeNull()
    expect(sessionStorage.getItem(mediaSessionKey('account-a', 'match-b', 'cycle-1'))).toBeNull()
    expect(sessionStorage.getItem('gotg:unrelated')).toBe('preserved')
  })
})
