import { Client, type IMessage } from '@stomp/stompjs'
import { resolveWebSocketUrl } from '../config/runtime'
import type { MatchmakingFound } from './types'

export interface MatchmakingConnection {
  disconnect: () => void
}

export function connectMatchmaking(
  onFound: (event: MatchmakingFound) => void,
  onState?: (connected: boolean) => void,
): MatchmakingConnection {
  const client = new Client({
    brokerURL: resolveWebSocketUrl(),
    reconnectDelay: 3_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    connectionTimeout: 8_000,
    debug: () => undefined,
  })
  client.onConnect = () => {
    onState?.(true)
    client.subscribe('/user/queue/matchmaking', (message: IMessage) => {
      try {
        const event = JSON.parse(message.body) as MatchmakingFound
        if (event.type === 'MATCHMAKING_FOUND' && event.matchId && event.view) {
          onFound(event)
        }
      } catch {
        // Status polling remains the recovery path for malformed or missed delivery.
      }
    })
  }
  const disconnected = () => onState?.(false)
  client.onWebSocketClose = disconnected
  client.onWebSocketError = disconnected
  client.onStompError = disconnected
  client.activate()
  return {
    disconnect: () => {
      onState?.(false)
      void client.deactivate()
    },
  }
}
