import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { commandResponse, fullOwnFormation, jsonResponse, matchView } from '../../test-fixtures'
import type { PlayerMatchView } from '../../api/types'
import { FormationScreen } from './FormationScreen'

const session = {
  playerId: 'dev-player-a',
  matchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  roomCode: 'ABC234',
}

function renderFormation(view: PlayerMatchView) {
  const onView = vi.fn()
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <FormationScreen view={view} session={session} onView={onView} onStale={vi.fn()} />
    </QueryClientProvider>,
  )
  return { onView }
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
})
