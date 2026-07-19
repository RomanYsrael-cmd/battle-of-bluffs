export interface CurrentAccount {
  id: string
  username: string
  displayName: string
  status: 'ACTIVE' | 'UNVERIFIED' | 'SUSPENDED' | 'DELETED'
  emailVerified: boolean
}

export class AuthApiError extends Error {
  constructor(public readonly code: string, message: string, public readonly status: number) {
    super(message)
    this.name = 'AuthApiError'
  }
}

interface CsrfResponse {
  headerName: string
  token: string
}

let csrfRequest: Promise<CsrfResponse> | null = null

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

async function csrf(): Promise<CsrfResponse> {
  csrfRequest ??= fetch(`${configuredBaseUrl}/api/auth/csrf`, { credentials: 'include' })
    .then((response) => {
      if (!response.ok) throw new AuthApiError('CSRF_UNAVAILABLE', 'Security token unavailable.', response.status)
      return response.json() as Promise<CsrfResponse>
    })
    .catch((error) => {
      csrfRequest = null
      throw error
    })
  return csrfRequest
}

async function authRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method?.toUpperCase() ?? 'GET'
  const headers = new Headers(init?.headers)
  if (init?.body) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfToken = await csrf()
    headers.set(csrfToken.headerName, csrfToken.token)
  }

  const response = await fetch(`${configuredBaseUrl}/api/auth${path}`, {
    ...init,
    headers,
    credentials: 'include',
  })
  if (response.status === 401) {
    window.dispatchEvent(new Event('gotg:session-expired'))
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { code?: string; message?: string } | null
    throw new AuthApiError(
      body?.code ?? 'AUTH_REQUEST_FAILED',
      body?.message ?? 'The account request could not be completed.',
      response.status,
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const registerAccount = (input: {
  username: string
  email: string
  password: string
  displayName: string
}) => authRequest<CurrentAccount>('/register', { method: 'POST', body: JSON.stringify(input) })

export const login = (loginValue: string, password: string) =>
  authRequest<CurrentAccount>('/login', {
    method: 'POST',
    body: JSON.stringify({ login: loginValue, password }),
  })

export async function logout(): Promise<void> {
  await authRequest<void>('/logout', { method: 'POST' })
  csrfRequest = null
}

export const getCurrentAccount = () => authRequest<CurrentAccount>('/me')
export const verifyEmail = (token: string) =>
  authRequest<CurrentAccount>('/verify-email', { method: 'POST', body: JSON.stringify({ token }) })
export const resendVerification = () => authRequest<void>('/resend-verification', { method: 'POST' })
export const forgotPassword = (email: string) =>
  authRequest<{ message: string }>('/forgot-password', { method: 'POST', body: JSON.stringify({ email }) })
export const resetPassword = (token: string, password: string) =>
  authRequest<void>('/reset-password', { method: 'POST', body: JSON.stringify({ token, password }) })

export function clearCsrfForTest(): void {
  csrfRequest = null
}
