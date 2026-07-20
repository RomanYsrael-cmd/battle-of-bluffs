import { Client, type IMessage } from '@stomp/stompjs'
import type { ChatError, ChatMessage, PlayerMatchView } from '../api/types'
import { resolveWebSocketUrl } from '../config/runtime'

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
  onChatConnected?: () => void
  onChatMessage?: (message: ChatMessage) => void
  onChatError?: (error: ChatError) => void
}

export interface MatchRealtimeConnection {
  disconnect: () => void
  sendChat: (body: string) => boolean
}

export function connectMatchUpdates(
  matchId: string,
  callbacks: MatchSocketCallbacks,
): MatchRealtimeConnection {
  const client = new Client({
    brokerURL: resolveWebSocketUrl(),
    reconnectDelay: 3_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    connectionTimeout: 8_000,
    debug: () => undefined,
  })

  let connectedBefore = false
  let stopped = false
  client.onConnect = () => {
    client.subscribe(`/user/queue/matches/${matchId}`, (message: IMessage) => {
      try {
        callbacks.onUpdate(JSON.parse(message.body) as MatchUpdateEnvelope)
      } catch {
        callbacks.onInvalidMessage()
      }
    })
    client.subscribe(`/user/queue/matches/${matchId}/chat`, (message: IMessage) => {
      try {
        callbacks.onChatMessage?.(JSON.parse(message.body) as ChatMessage)
      } catch {
        callbacks.onInvalidMessage()
      }
    })
    client.subscribe(`/user/queue/matches/${matchId}/chat/errors`, (message: IMessage) => {
      try {
        callbacks.onChatError?.(JSON.parse(message.body) as ChatError)
      } catch {
        callbacks.onInvalidMessage()
      }
    })
    if (stopped) return
    callbacks.onState(connectedBefore ? 'RECOVERING' : 'SYNCHRONIZED')
    connectedBefore = true
    callbacks.onConnected()
    callbacks.onChatConnected?.()
  }
  const reconnecting = () => {
    if (!stopped) callbacks.onState('RECONNECTING')
  }
  client.onWebSocketClose = reconnecting
  client.onStompError = reconnecting
  client.onWebSocketError = reconnecting

  callbacks.onState('CONNECTING')
  client.activate()
  return {
    disconnect: () => {
      stopped = true
      void client.deactivate()
    },
    sendChat: (body: string) => {
      if (!client.connected) return false
      client.publish({
        destination: `/app/matches/${matchId}/chat`,
        body: JSON.stringify({ body }),
      })
      return true
    },
  }
}

export function mergeChatMessages(
  current: ChatMessage[],
  incoming: ChatMessage[],
): ChatMessage[] {
  const byId = new Map(current.map((message) => [message.id, message]))
  incoming.forEach((message) => byId.set(message.id, message))
  return [...byId.values()].sort((left, right) => left.sequence - right.sequence)
}
