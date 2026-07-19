import { apiRequest, clearCsrfToken, HttpApiError } from '../api/http'

export { HttpApiError as AuthApiError }

export interface CurrentAccount {
  id: string
  username: string
  displayName: string
  status: 'ACTIVE' | 'UNVERIFIED' | 'SUSPENDED' | 'DELETED'
  emailVerified: boolean
}

export const registerAccount = (input: {
  username: string
  email: string
  password: string
  displayName: string
}) => apiRequest<CurrentAccount>('/api/auth/register', { method: 'POST', body: JSON.stringify(input) })

export const login = (loginValue: string, password: string) =>
  apiRequest<CurrentAccount>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ login: loginValue, password }),
  })

export async function logout(): Promise<void> {
  await apiRequest<void>('/api/auth/logout', { method: 'POST' })
  clearCsrfToken()
}

export const getCurrentAccount = () => apiRequest<CurrentAccount>('/api/auth/me')
export const verifyEmail = (token: string) =>
  apiRequest<CurrentAccount>('/api/auth/verify-email', { method: 'POST', body: JSON.stringify({ token }) })
export const resendVerification = () => apiRequest<void>('/api/auth/resend-verification', { method: 'POST' })
export const forgotPassword = (email: string) =>
  apiRequest<{ message: string }>('/api/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) })
export const resetPassword = (token: string, password: string) =>
  apiRequest<void>('/api/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, password }) })

export const clearCsrfForTest = clearCsrfToken
