import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ChatError, ChatMessage } from '../../api/types'
import { MatchChatPanel } from './MatchChatPanel'

const matchId = '00000000-0000-4000-8000-000000000001'
const opponentMessage: ChatMessage = {
  id: '00000000-0000-4000-8000-000000000010',
  matchId,
  sequence: 1,
  senderDisplayName: 'Opponent',
  ownMessage: false,
  body: '<img src=x onerror=alert(1)> https://unsafe.example',
  serverTimestamp: '2026-07-19T10:15:30Z',
}

function renderPanel(
  messages: ChatMessage[] = [],
  error: ChatError | null = null,
  onSend: (body: string) => boolean = vi.fn(() => true),
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return {
    onSend,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MatchChatPanel
          matchId={matchId}
          connected
          messages={messages}
          error={error}
          onSend={onSend}
        />
      </QueryClientProvider>,
    ),
  }
}

describe('participant match chat', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      opponentDisplayName: 'Opponent',
      blockedByYou: false,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
  })

  it('renders hostile markup and links only as inert text', async () => {
    const rendered = renderPanel([opponentMessage])
    fireEvent.click(screen.getByRole('button', { name: /open chat/i }))

    expect(await screen.findByText(opponentMessage.body)).toBeInTheDocument()
    expect(rendered.container.querySelector('img')).toBeNull()
    expect(rendered.container.querySelector('a')).toBeNull()
  })

  it('shows unread opponent delivery while collapsed and trims outgoing text', async () => {
    const onSend = vi.fn(() => true)
    const rendered = renderPanel([], null, onSend)
    rendered.rerender(
      <QueryClientProvider client={new QueryClient()}>
        <MatchChatPanel
          matchId={matchId}
          connected
          messages={[opponentMessage]}
          error={null}
          onSend={onSend}
        />
      </QueryClientProvider>,
    )
    expect(await screen.findByRole('button', { name: /1 unread/i })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /open chat/i }))
    const messageBox = await screen.findByLabelText('Message')
    fireEvent.change(messageBox, { target: { value: '  hello general  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send' }))

    await waitFor(() => expect(onSend).toHaveBeenCalledWith('hello general'))
    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled()
  })

  it('surfaces server rate-limit errors in the chat panel', () => {
    renderPanel([], {
      code: 'CHAT_RATE_LIMITED',
      message: 'You can send up to five messages every ten seconds.',
      serverTimestamp: '2026-07-19T10:15:30Z',
    })
    fireEvent.click(screen.getByRole('button', { name: /open chat/i }))

    expect(screen.getByRole('alert')).toHaveTextContent('five messages every ten seconds')
  })
})
