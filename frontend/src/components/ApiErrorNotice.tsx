import { MatchApiError } from '../api/client'

export function ApiErrorNotice({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof MatchApiError
    ? error.message
    : 'Something went wrong while contacting the match server.'
  return <p className="error-notice" role="alert">{message}</p>
}
