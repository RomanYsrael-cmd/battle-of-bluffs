import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfForTest, login } from './client'

describe('account API client security', () => {
  beforeEach(() => clearCsrfForTest())
  afterEach(() => vi.unstubAllGlobals())

  it('uses the server session and attaches the session-bound CSRF token', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf-value' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: 'account-id', username: 'marshal', displayName: 'Marshal', status: 'ACTIVE', emailVerified: true,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await login('marshal', 'Strategist!2026')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/csrf', { credentials: 'include' })
    const loginRequest = fetchMock.mock.calls[1][1] as RequestInit
    expect(loginRequest.credentials).toBe('include')
    expect(new Headers(loginRequest.headers).get('X-CSRF-TOKEN')).toBe('csrf-value')
    expect(loginRequest.body).toBe(JSON.stringify({ login: 'marshal', password: 'Strategist!2026' }))
  })
})
