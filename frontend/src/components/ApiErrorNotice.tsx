import { MatchApiError } from '../api/client'
import { HttpApiError } from '../api/http'

export function ApiErrorNotice({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof MatchApiError || error instanceof HttpApiError
    ? error.message
    : 'Something went wrong while contacting the match server.'
  return <p className="error-notice" role="alert">{message}</p>
}
