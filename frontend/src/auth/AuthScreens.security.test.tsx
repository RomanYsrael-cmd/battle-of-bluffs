import { render, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ResetPasswordScreen, VerifyEmailScreen } from './AuthScreens'

vi.mock('./client', async (importOriginal) => {
  const original = await importOriginal<typeof import('./client')>()
  return {
    ...original,
    verifyEmail: vi.fn(() => new Promise(() => undefined)),
  }
})

describe('account link token cleanup', () => {
  beforeEach(() => window.history.replaceState({}, '', '/'))

  it('removes a reset token from the browser URL while retaining it only in component memory', async () => {
    window.history.replaceState({}, '', '/reset-password?token=reset-secret')
    render(<BrowserRouter><ResetPasswordScreen /></BrowserRouter>)

    await waitFor(() => expect(window.location.href).not.toContain('reset-secret'))
    expect(window.location.pathname).toBe('/reset-password')
  })

  it('removes a verification token from the browser URL before processing completes', async () => {
    window.history.replaceState({}, '', '/verify-email?token=verification-secret')
    render(<BrowserRouter><VerifyEmailScreen /></BrowserRouter>)

    await waitFor(() => expect(window.location.href).not.toContain('verification-secret'))
    expect(window.location.pathname).toBe('/verify-email')
  })
})
