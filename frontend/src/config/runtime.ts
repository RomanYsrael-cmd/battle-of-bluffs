const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''
const configuredWebSocketUrl = import.meta.env.VITE_WS_URL?.replace(/\/$/, '') ?? ''

export function resolveApiUrl(path: string, apiBaseUrl = configuredApiBaseUrl): string {
  if (!apiBaseUrl) return path
  const relativePath = path === '/api' ? '' : path.replace(/^\/api(?=\/)/, '')
  return `${apiBaseUrl.replace(/\/$/, '')}${relativePath}`
}

export function resolveWebSocketUrl(
  webSocketUrl = configuredWebSocketUrl,
  location = window.location,
): string {
  if (webSocketUrl) return webSocketUrl
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws`
}
