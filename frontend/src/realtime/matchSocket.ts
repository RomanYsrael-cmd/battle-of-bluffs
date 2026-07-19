import { Client, type IMessage } from '@stomp/stompjs'
import type { PlayerMatchView } from '../api/types'

export type MatchConnectionState =
  | 'CONNECTING'
  | 'SYNCHRONIZED'
  | 'RECONNECTING'
  | 'RECOVERING'

export interface MatchUpdateEnvelope {
  type: string
  matchId: string
  sequence: number
  version: number
  serverTimestamp: string
  view: PlayerMatchView
}

export type MatchUpdateDecision = 'IGNORE' | 'APPLY' | 'REFETCH'

export function assessMatchUpdate(
  matchId: string,
  currentLiveSequence: number,
  currentVersion: number,
  update: MatchUpdateEnvelope,
): MatchUpdateDecision {
  if (update.matchId !== matchId
    || update.version !== update.view.version
    || update.sequence !== update.view.liveSequence) return 'REFETCH'
  if (update.sequence <= currentLiveSequence) return 'IGNORE'
  if (update.sequence !== currentLiveSequence + 1) return 'REFETCH'
  if (update.version < currentVersion || update.version > currentVersion + 1) return 'REFETCH'
  return 'APPLY'
}

interface MatchSocketCallbacks {
  onState: (state: MatchConnectionState) => void
  onConnected: () => void
  onUpdate: (update: MatchUpdateEnvelope) => void
  onInvalidMessage: () => void
}

export function connectMatchUpdates(
  matchId: string,
  callbacks: MatchSocketCallbacks,
): () => void {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const client = new Client({
    brokerURL: `${protocol}//${window.location.host}/ws`,
    reconnectDelay: 3_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    connectionTimeout: 8_000,
    debug: () => undefined,
  })

  let connectedBefore = false
  let stopped = false
  client.onConnect = () => {
    const receiptId = `match-subscription-${crypto.randomUUID()}`
    client.watchForReceipt(receiptId, () => {
      if (stopped) return
      callbacks.onState(connectedBefore ? 'RECOVERING' : 'SYNCHRONIZED')
      connectedBefore = true
      callbacks.onConnected()
    })
    client.subscribe(`/user/queue/matches/${matchId}`, (message: IMessage) => {
      try {
        callbacks.onUpdate(JSON.parse(message.body) as MatchUpdateEnvelope)
      } catch {
        callbacks.onInvalidMessage()
      }
    }, { receipt: receiptId })
  }
  const reconnecting = () => {
    if (!stopped) callbacks.onState('RECONNECTING')
  }
  client.onWebSocketClose = reconnecting
  client.onStompError = reconnecting
  client.onWebSocketError = reconnecting

  callbacks.onState('CONNECTING')
  client.activate()
  return () => {
    stopped = true
    void client.deactivate()
  }
}
