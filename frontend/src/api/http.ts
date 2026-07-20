import { resolveApiUrl } from '../config/runtime'

export class HttpApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
    public readonly context: unknown = null,
  ) {
    super(message)
    this.name = 'HttpApiError'
  }
}

interface CsrfResponse {
  headerName: string
  token: string
}

let csrfRequest: Promise<CsrfResponse> | null = null

async function csrf(): Promise<CsrfResponse> {
  csrfRequest ??= fetch(resolveApiUrl('/api/auth/csrf'), { credentials: 'include' })
    .then((response) => {
      if (!response.ok) throw new HttpApiError('CSRF_UNAVAILABLE', 'Security token unavailable.', response.status)
      return response.json() as Promise<CsrfResponse>
    })
    .catch((error) => {
      csrfRequest = null
      throw error
    })
  return csrfRequest
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method?.toUpperCase() ?? 'GET'
  const headers = new Headers(init?.headers)
  if (init?.body) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfToken = await csrf()
    headers.set(csrfToken.headerName, csrfToken.token)
  }

  const response = await fetch(resolveApiUrl(path), {
    ...init,
    headers,
    credentials: 'include',
  })
  if (response.status === 401) {
    clearCsrfToken()
    window.dispatchEvent(new Event('gotg:session-expired'))
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null) as {
      code?: string
      message?: string
      context?: unknown
    } | null
    throw new HttpApiError(
      body?.code ?? 'REQUEST_FAILED',
      body?.message ?? 'The request could not be completed.',
      response.status,
      body?.context,
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function clearCsrfToken(): void {
  csrfRequest = null
}

export function primeCsrfTokenForTest(headerName: string, token: string): void {
  csrfRequest = Promise.resolve({ headerName, token })
}
