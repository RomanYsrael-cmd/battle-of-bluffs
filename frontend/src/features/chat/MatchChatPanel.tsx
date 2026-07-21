import { Fragment, useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  blockOpponent,
  getModerationStatus,
  reportOpponent,
  unblockOpponent,
} from '../../api/client'
import type { ChatError, ChatMessage, ReportCategory } from '../../api/types'

interface MatchChatPanelProps {
  accountId: string
  matchId: string
  connected: boolean
  messages: ChatMessage[]
  error: ChatError | null
  onSend: (body: string) => boolean
  onBlockedChange?: (blocked: boolean) => void
}

const reportCategories: { value: ReportCategory; label: string }[] = [
  { value: 'HARASSMENT', label: 'Harassment' },
  { value: 'ABUSE', label: 'Abusive conduct' },
  { value: 'CHEATING', label: 'Cheating' },
  { value: 'SPAM', label: 'Spam' },
  { value: 'OTHER', label: 'Other' },
]

export function MatchChatPanel({
  accountId,
  matchId,
  connected,
  messages,
  error,
  onSend,
  onBlockedChange,
}: MatchChatPanelProps) {
  const panelKey = `gotg:chat-panel:${accountId}:${matchId}`
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(panelKey) !== 'open')
  const [unread, setUnread] = useState(0)
  const [newMessagesBelow, setNewMessagesBelow] = useState(0)
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [showReport, setShowReport] = useState(false)
  const [reportCategory, setReportCategory] = useState<ReportCategory>('HARASSMENT')
  const [reportComment, setReportComment] = useState('')
  const muteKey = `gotg:chat-muted:${accountId}:${matchId}`
  const [muted, setMuted] = useState(() => window.localStorage.getItem(muteKey) === 'true')
  const latestSequence = useRef(0)
  const messagesInitialized = useRef(false)
  const historyEnd = useRef<HTMLDivElement | null>(null)
  const history = useRef<HTMLDivElement | null>(null)
  const nearBottom = useRef(true)
  const moderation = useQuery({
    queryKey: ['match-moderation', matchId],
    queryFn: () => getModerationStatus(matchId),
    retry: false,
    enabled: true,
  })
  const blockMutation = useMutation({
    mutationFn: () => moderation.data?.blockedByYou
      ? unblockOpponent(matchId)
      : blockOpponent(matchId),
    onSuccess: (status) => {
      onBlockedChange?.(status.blockedByYou)
      return moderation.refetch().then(() => status)
    },
  })
  const reportMutation = useMutation({
    mutationFn: () => reportOpponent(
      matchId,
      reportCategory,
      reportComment,
      messages.filter((message) => !message.ownMessage).slice(-20).map((message) => message.id),
    ),
    onSuccess: () => {
      setReportComment('')
      setShowReport(false)
    },
  })
  const visibleMessages = useMemo(
    () => muted ? messages.filter((message) => message.ownMessage) : messages,
    [messages, muted],
  )

  useEffect(() => {
    if (moderation.data) onBlockedChange?.(moderation.data.blockedByYou)
  }, [moderation.data, onBlockedChange])

  useEffect(() => {
    const newOpponentMessages = messages.filter((message) =>
      message.sequence > latestSequence.current && !message.ownMessage).length
    if (messagesInitialized.current) {
      if (collapsed) setUnread((current) => current + newOpponentMessages)
      else if (!nearBottom.current) setNewMessagesBelow((current) => current + newOpponentMessages)
    }
    latestSequence.current = Math.max(
      latestSequence.current,
      ...messages.map((message) => message.sequence),
    )
    if (!collapsed && nearBottom.current && typeof historyEnd.current?.scrollIntoView === 'function') {
      historyEnd.current.scrollIntoView({ block: 'nearest' })
      setNewMessagesBelow(0)
    }
    messagesInitialized.current = true
  }, [collapsed, messages])

  useEffect(() => {
    if (messages.at(-1)?.ownMessage) setSending(false)
  }, [messages])

  useEffect(() => {
    if (error) setSending(false)
  }, [error])

  const toggleMute = () => {
    const next = !muted
    setMuted(next)
    window.localStorage.setItem(muteKey, String(next))
  }

  return (
    <section className={`chat-panel${collapsed ? ' chat-panel--collapsed' : ''}`} aria-label="Match chat">
      <div className="chat-panel__heading">
        <div>
          <p className="eyebrow">Private to participants</p>
          <h2>{moderation.data?.opponentDisplayName ?? 'Match chat'}</h2>
        </div>
        <button
          type="button"
          className="button button--ghost"
          aria-expanded={!collapsed}
          onClick={() => {
            setCollapsed((current) => {
              localStorage.setItem(panelKey, current ? 'open' : 'closed')
              return !current
            })
            setUnread(0)
          }}
        >
          {collapsed ? `Open chat${unread ? ` (${unread} unread)` : ''}` : 'Collapse'}
        </button>
      </div>

      {collapsed && (
        <button type="button" className="chat-quick-open" onClick={() => {
          localStorage.setItem(panelKey, 'open')
          setCollapsed(false)
          setUnread(0)
        }}>
          <span>Type a message…</span><span aria-hidden="true">➤</span>
        </button>
      )}

      {!collapsed && (
        <>
          <div className="chat-tools">
            <span>{moderation.data?.opponentDisplayName ?? 'Opponent'}</span>
            <button type="button" onClick={toggleMute}>{muted ? 'Unmute' : 'Mute'}</button>
            <button
              type="button"
              disabled={blockMutation.isPending}
              onClick={() => {
                if (!moderation.data?.blockedByYou
                  || window.confirm('Unblock this opponent and allow future chat?')) {
                  blockMutation.mutate()
                }
              }}
            >
              {moderation.data?.blockedByYou ? 'Unblock' : 'Block'}
            </button>
            <button type="button" onClick={() => setShowReport((current) => !current)}>Report</button>
          </div>

          {showReport && (
            <form className="report-form" onSubmit={(event) => {
              event.preventDefault()
              reportMutation.mutate()
            }}>
              <label>
                Category
                <select
                  value={reportCategory}
                  onChange={(event) => setReportCategory(event.target.value as ReportCategory)}
                >
                  {reportCategories.map((category) => (
                    <option key={category.value} value={category.value}>{category.label}</option>
                  ))}
                </select>
              </label>
              <label>
                Optional details
                <textarea
                  maxLength={1000}
                  value={reportComment}
                  onChange={(event) => setReportComment(event.target.value)}
                />
              </label>
              <button type="submit" className="button button--secondary" disabled={reportMutation.isPending}>
                {reportMutation.isPending ? 'Submitting…' : 'Submit report'}
              </button>
            </form>
          )}
          {reportMutation.isSuccess && <p className="success-notice" role="status">Report received.</p>}
          {(reportMutation.error || blockMutation.error) && (
            <p className="error-notice" role="alert">The moderation action could not be completed.</p>
          )}

          <div ref={history} className="chat-history" role="log" aria-live="polite" aria-label="Chat messages"
            onScroll={() => {
              const node = history.current
              if (node) nearBottom.current = node.scrollHeight - node.scrollTop - node.clientHeight < 64
            }}>
            {visibleMessages.length === 0 && (
              <p className="chat-empty">{muted ? 'Opponent messages are muted.' : 'No messages yet.'}</p>
            )}
            {visibleMessages.map((message, index) => {
              const previous = visibleMessages[index - 1]
              const grouped = previous?.ownMessage === message.ownMessage
              const date = new Date(message.serverTimestamp).toLocaleDateString()
              const previousDate = previous
                ? new Date(previous.serverTimestamp).toLocaleDateString()
                : null
              return (
                <Fragment key={message.id}>
                  {date !== previousDate && <p className="chat-date-separator">{date}</p>}
                  <article className={`chat-message${message.ownMessage ? ' chat-message--own' : ''}${grouped ? ' chat-message--grouped' : ''}`}>
                    <div>
                      <strong>{message.ownMessage ? 'You' : message.senderDisplayName}</strong>
                      <time dateTime={message.serverTimestamp}>
                        {new Date(message.serverTimestamp).toLocaleTimeString([], {
                          hour: '2-digit', minute: '2-digit',
                        })}
                      </time>
                    </div>
                    <p>{message.body}</p>
                  </article>
                </Fragment>
              )
            })}
            <div ref={historyEnd} />
          </div>
          {newMessagesBelow > 0 && (
            <button type="button" className="chat-new-messages" onClick={() => {
              nearBottom.current = true
              setNewMessagesBelow(0)
              historyEnd.current?.scrollIntoView({ block: 'nearest' })
            }}>New messages ({newMessagesBelow})</button>
          )}

          <form className="chat-compose" onSubmit={(event) => {
            event.preventDefault()
            const body = draft.trim()
            if (!body) return
            if (onSend(body)) {
              setDraft('')
              setSending(true)
            }
          }}>
            <label htmlFor={`chat-${matchId}`}>Message</label>
            <textarea
              id={`chat-${matchId}`}
              maxLength={500}
              value={draft}
              disabled={!connected || moderation.data?.blockedByYou || sending}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  event.currentTarget.form?.requestSubmit()
                }
              }}
              placeholder={connected ? 'Write a plain-text message…' : 'Chat reconnecting…'}
            />
            <div>
              <small>{draft.length >= 400 ? `${draft.length}/500` : ''}</small>
              <button
                type="submit"
                className="button button--primary"
                disabled={!connected || !draft.trim() || sending || moderation.data?.blockedByYou}
              >
                {sending ? 'Sending…' : 'Send'}
              </button>
            </div>
          </form>
          {error && <p className="error-notice" role="alert">{error.message}</p>}
        </>
      )}
    </section>
  )
}
