import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { commandResponse, fullOwnFormation, jsonResponse, matchView } from '../../test-fixtures'
import type { PlayerMatchView } from '../../api/types'
import { FormationScreen } from './FormationScreen'

const session = {
  matchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  roomCode: 'ABC234',
}

function renderFormation(
  view: PlayerMatchView,
  onStale: () => Promise<PlayerMatchView> = vi.fn().mockResolvedValue(view),
) {
  const onView = vi.fn()
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <FormationScreen view={view} session={session} onView={onView} onStale={onStale} />
    </QueryClientProvider>,
  )
  return { onView, onStale }
}

const staleResponse = () => jsonResponse({
  code: 'STALE_VERSION',
  message: 'Expected version did not match',
}, 409)

function rearrangeCompleteFormation() {
  fireEvent.click(screen.getByRole('button', { name: 'Five-Star General' }))
  fireEvent.click(screen.getByRole('gridcell', { name: /row 2, column 8/i }))
}

describe('server-aware formation setup', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('keeps lock disabled before a complete submitted formation', () => {
    renderFormation(matchView({ playerTwoOccupied: true }))
    expect(screen.getByRole('button', { name: 'Lock formation' })).toBeDisabled()
    expect(screen.getByText('0/21 placed · 27 empty')).toBeInTheDocument()
  })

  it('submits Player 2 canonical coordinates after visually rotating the board', async () => {
    const view = matchView({
      version: 3,
      requestingSide: 'PLAYER_TWO',
      requestingPlayerId: 'dev-player-b',
      playerTwoOccupied: true,
      ownPieces: fullOwnFormation('PLAYER_TWO'),
    })
    const updated = { ...view, version: 4 }
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(commandResponse(updated)))
    vi.stubGlobal('fetch', fetchMock)
    renderFormation(view)

    fireEvent.click(screen.getByRole('button', { name: 'Five-Star General' }))
    fireEvent.click(screen.getByRole('gridcell', { name: /row 7, column 8/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Submit formation' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    const payload = JSON.parse(fetchMock.mock.calls[0][1].body)
    expect(payload.expectedVersion).toBe(3)
    expect(payload.pieces).toHaveLength(21)
    expect(payload.pieces.find((piece: { rank: string }) => piece.rank === 'FIVE_STAR_GENERAL'))
      .toMatchObject({ row: 7, column: 8 })
  })

  it('enables locking only for the currently submitted formation', () => {
    const view = matchView({
      playerTwoOccupied: true,
      ownPieces: fullOwnFormation('PLAYER_ONE'),
    })
    renderFormation(view)
    expect(screen.getByRole('button', { name: 'Lock formation' })).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: 'Five-Star General' }))
    fireEvent.click(screen.getByRole('gridcell', { name: /row 2, column 8/i }))
    expect(screen.getByRole('button', { name: 'Lock formation' })).toBeDisabled()
  })

  it('disables all editing after the server confirms the lock', () => {
    renderFormation(matchView({
      playerTwoOccupied: true,
      playerOneLocked: true,
      ownPieces: fullOwnFormation('PLAYER_ONE'),
    }))

    expect(screen.getByRole('button', { name: 'Five-Star General' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Reset formation' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Formation locked' })).toBeDisabled()
    expect(screen.getByText(/server-confirmed lock/i)).toBeInTheDocument()
  })

  it('recovers a formation submission that races with a stale presence-era view', async () => {
    const initial = matchView({
      version: 2,
      liveSequence: 2,
      playerTwoOccupied: true,
      ownPieces: fullOwnFormation('PLAYER_ONE'),
    })
    const refreshed = { ...initial, version: 3, liveSequence: 4 }
    const accepted = { ...refreshed, version: 4, liveSequence: 5 }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(staleResponse())
      .mockResolvedValueOnce(jsonResponse(commandResponse(accepted)))
    const onStale = vi.fn().mockResolvedValue(refreshed)
    vi.stubGlobal('fetch', fetchMock)
    renderFormation(initial, onStale)
    rearrangeCompleteFormation()

    fireEvent.click(screen.getByRole('button', { name: 'Submit formation' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const firstPayload = JSON.parse(fetchMock.mock.calls[0][1].body)
    const retryPayload = JSON.parse(fetchMock.mock.calls[1][1].body)
    expect(firstPayload.expectedVersion).toBe(2)
    expect(retryPayload.expectedVersion).toBe(3)
    expect(retryPayload.commandId).toBe(firstPayload.commandId)
    expect(retryPayload.pieces).toEqual(firstPayload.pieces)
    expect(onStale).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole('button', { name: 'Formation submitted' })).toBeEnabled()
  })

  it('keeps all local placements intact while stale recovery refetches', async () => {
    const initial = matchView({
      version: 2,
      playerTwoOccupied: true,
      ownPieces: fullOwnFormation('PLAYER_ONE'),
    })
    let finishRefetch!: (view: PlayerMatchView) => void
    const refetchPending = new Promise<PlayerMatchView>((resolve) => {
      finishRefetch = resolve
    })
    const onStale = vi.fn(() => refetchPending)
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(staleResponse())
      .mockResolvedValueOnce(jsonResponse(commandResponse({ ...initial, version: 3 })))
    vi.stubGlobal('fetch', fetchMock)
    renderFormation(initial, onStale)
    rearrangeCompleteFormation()

    fireEvent.click(screen.getByRole('button', { name: 'Submit formation' }))
    await waitFor(() => expect(onStale).toHaveBeenCalledTimes(1))

    expect(screen.getByText('21/21 placed · 6 empty')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled()
    finishRefetch({ ...initial, version: 3 })
    expect(await screen.findByRole('button', { name: 'Formation submitted' })).toBeEnabled()
  })

  it('shows a clear conflict after a second stale response without looping', async () => {
    const initial = matchView({
      version: 2,
      playerTwoOccupied: true,
      ownPieces: fullOwnFormation('PLAYER_ONE'),
    })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(staleResponse())
      .mockResolvedValueOnce(staleResponse())
    vi.stubGlobal('fetch', fetchMock)
    renderFormation(initial, vi.fn().mockResolvedValue({ ...initial, version: 3 }))
    rearrangeCompleteFormation()

    fireEvent.click(screen.getByRole('button', { name: 'Submit formation' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/match changed again/i)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('button', { name: 'Submit formation' })).toBeEnabled()
  })

  it('retries locking only after the authoritative view confirms the same submission', async () => {
    const initial = matchView({
      version: 4,
      playerTwoOccupied: true,
      ownPieces: fullOwnFormation('PLAYER_ONE'),
    })
    const refreshed = { ...initial, version: 5, liveSequence: 6 }
    const locked = { ...refreshed, version: 6, playerOneLocked: true }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(staleResponse())
      .mockResolvedValueOnce(jsonResponse(commandResponse(locked)))
    vi.stubGlobal('fetch', fetchMock)
    const { onView } = renderFormation(initial, vi.fn().mockResolvedValue(refreshed))

    fireEvent.click(screen.getByRole('button', { name: 'Lock formation' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    const firstPayload = JSON.parse(fetchMock.mock.calls[0][1].body)
    const retryPayload = JSON.parse(fetchMock.mock.calls[1][1].body)
    expect(retryPayload.expectedVersion).toBe(5)
    expect(retryPayload.commandId).toBe(firstPayload.commandId)
    expect(onView).toHaveBeenCalledWith(commandResponse(locked))
  })

  it('does not lock automatically when the refreshed view has no confirmed submission', async () => {
    const initial = matchView({
      version: 4,
      playerTwoOccupied: true,
      ownPieces: fullOwnFormation('PLAYER_ONE'),
    })
    const fetchMock = vi.fn().mockResolvedValueOnce(staleResponse())
    vi.stubGlobal('fetch', fetchMock)
    const { onView } = renderFormation(initial, vi.fn().mockResolvedValue({
      ...initial,
      version: 5,
      ownPieces: [],
    }))

    fireEvent.click(screen.getByRole('button', { name: 'Lock formation' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/was not retried/i)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(onView).not.toHaveBeenCalled()
  })

  it('rejects stale recovery if a supposedly safe view exposes an opponent rank', async () => {
    const initial = matchView({
      version: 2,
      playerTwoOccupied: true,
      ownPieces: fullOwnFormation('PLAYER_ONE'),
    })
    const exposedOpponent = {
      id: 'opaque-id',
      position: { row: 7, column: 0 },
      rank: 'FLAG',
    }
    const fetchMock = vi.fn().mockResolvedValueOnce(staleResponse())
    vi.stubGlobal('fetch', fetchMock)
    renderFormation(initial, vi.fn().mockResolvedValue({
      ...initial,
      version: 3,
      opponentPieces: [exposedOpponent],
    } as PlayerMatchView))
    rearrangeCompleteFormation()

    fireEvent.click(screen.getByRole('button', { name: 'Submit formation' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/was not retried/i)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
