import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { matchView } from '../test-fixtures'
import { RankedMatchmaking } from './RankedMatchmaking'
import type { MatchmakingFound, QueueStatus } from './types'

const mocks = vi.hoisted(() => ({
  getStatus: vi.fn(),
  join: vi.fn(),
  cancel: vi.fn(),
  connect: vi.fn(),
  getPlayerView: vi.fn(),
}))

vi.mock('./client', () => ({
  getMatchmakingStatus: mocks.getStatus,
  joinRankedQueue: mocks.join,
  cancelRankedQueue: mocks.cancel,
}))

vi.mock('./socket', () => ({
  connectMatchmaking: mocks.connect,
}))

vi.mock('../api/client', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/client')>(),
  getPlayerView: mocks.getPlayerView,
}))

function renderQueue(onEnteredMatch = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <RankedMatchmaking onEnteredMatch={onEnteredMatch} />
    </QueryClientProvider>,
  )
  return onEnteredMatch
}

describe('ranked matchmaking', () => {
  let found: (event: MatchmakingFound) => void

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.connect.mockImplementation((onFound: (event: MatchmakingFound) => void, onState: (value: boolean) => void) => {
      found = onFound
      onState(true)
      return { disconnect: vi.fn() }
    })
  })

  it('prevents duplicate queue submissions while the first request is pending', async () => {
    mocks.getStatus.mockResolvedValue(idle())
    let resolveJoin!: (status: QueueStatus) => void
    mocks.join.mockReturnValue(new Promise((resolve) => { resolveJoin = resolve }))
    renderQueue()

    const button = await screen.findByRole('button', { name: 'Find ranked match' })
    await waitFor(() => expect(button).toBeEnabled())
    fireEvent.click(button)
    fireEvent.click(button)

    await waitFor(() => expect(mocks.join).toHaveBeenCalledTimes(1))
    await act(async () => resolveJoin(queued()))
    expect(await screen.findByText('Searching for an opponent…')).toBeInTheDocument()
  })

  it('shows elapsed search range and supports cancellation', async () => {
    mocks.getStatus.mockResolvedValue(queued(new Date(Date.now() - 31_000).toISOString()))
    mocks.cancel.mockResolvedValue(idle())
    renderQueue()

    expect(await screen.findByText(/±250 search range/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel search' }))

    await waitFor(() => expect(mocks.cancel).toHaveBeenCalledTimes(1))
    expect(await screen.findByRole('button', { name: 'Find ranked match' })).toBeInTheDocument()
  })

  it('transitions once from a user-scoped MATCHMAKING_FOUND event without a room code', async () => {
    mocks.getStatus.mockResolvedValue(idle())
    const onEntered = renderQueue()
    await screen.findByText('Match alerts connected')
    const view = matchView({
      matchId: 'ranked-match',
      roomCode: null,
      mode: 'RANKED',
      timerMode: 'STANDARD_15_PLUS_5',
      playerTwoOccupied: true,
    })

    act(() => found({
      type: 'MATCHMAKING_FOUND',
      matchId: view.matchId,
      opponentDisplayName: 'Opponent',
      opponentRating: 1210,
      matchedAt: new Date().toISOString(),
      view,
    }))

    await waitFor(() => expect(onEntered).toHaveBeenCalledTimes(1))
    expect(onEntered.mock.calls[0][0]).toMatchObject({
      matchId: 'ranked-match',
      roomCode: null,
      view,
    })
  })
})

function idle(): QueueStatus {
  return { state: 'IDLE', queuedAt: null, elapsedSeconds: 0, searchRange: 0, rating: 0, matchId: null }
}

function queued(queuedAt = new Date().toISOString()): QueueStatus {
  return { state: 'QUEUED', queuedAt, elapsedSeconds: 0, searchRange: 200, rating: 1200, matchId: null }
}
