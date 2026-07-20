import { describe, expect, it } from 'vitest'
import vercel from '../../vercel.json'

describe('production media security headers', () => {
  it('permits only the exact LiveKit Cloud project origins', () => {
    const headers = vercel.headers[0]?.headers ?? []
    const csp = headers.find((header) => header.key === 'Content-Security-Policy')?.value ?? ''
    const permissions = headers.find((header) => header.key === 'Permissions-Policy')?.value ?? ''

    expect(csp).toContain('https://games-of-the-generals-productio-k5zs51ec.livekit.cloud')
    expect(csp).toContain('wss://games-of-the-generals-productio-k5zs51ec.livekit.cloud')
    expect(csp).not.toContain('*.livekit.cloud')
    expect(csp).toContain("script-src 'self'")
    expect(csp).not.toContain("script-src 'self' 'unsafe-eval'")
    expect(csp).toContain("frame-ancestors 'none'")
    expect(csp).toContain("object-src 'none'")
    expect(permissions).toContain('camera=(self)')
    expect(permissions).toContain('microphone=(self)')
  })
})
