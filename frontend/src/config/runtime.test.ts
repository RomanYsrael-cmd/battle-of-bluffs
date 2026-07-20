import { describe, expect, it } from 'vitest'
import { resolveApiUrl, resolveWebSocketUrl } from './runtime'

describe('production runtime URLs', () => {
  it('maps existing API paths beneath the configured production prefix', () => {
    expect(resolveApiUrl(
      '/api/auth/csrf',
      'https://romanlms.com/bluffs/api',
    )).toBe('https://romanlms.com/bluffs/api/auth/csrf')
  })

  it('uses the explicit cross-origin production WebSocket URL', () => {
    expect(resolveWebSocketUrl(
      'wss://romanlms.com/bluffs/ws',
      { protocol: 'https:', host: 'bluffs.romanlms.com' } as Location,
    )).toBe('wss://romanlms.com/bluffs/ws')
  })

  it('preserves same-origin development defaults', () => {
    expect(resolveApiUrl('/api/matches', '')).toBe('/api/matches')
    expect(resolveWebSocketUrl('', {
      protocol: 'http:',
      host: 'localhost:5173',
    } as Location)).toBe('ws://localhost:5173/ws')
  })
})
