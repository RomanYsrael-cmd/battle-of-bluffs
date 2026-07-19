import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { commandResponse, jsonResponse, matchView } from './test-fixtures'
import { loadSession } from './session/session'

describe('private room entry flows', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })

  afterEach(() => vi.unstubAllGlobals())

  it('creates a private match, stores the generated identity, and displays its room code', async () => {
    const view = matchView({ requestingPlayerId: 'generated-host' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(commandResponse(view), 201)))
    render(<App />)

    fireEvent.click(screen.getByRole('button', { name: 'Create private match' }))

    expect(await screen.findByDisplayValue('ABC234')).toBeInTheDocument()
    expect(screen.getByText(/waiting for a second player/i)).toBeInTheDocument()
    expect(loadSession()).toEqual({
      playerId: 'generated-host',
      matchId: view.matchId,
      roomCode: 'ABC234',
    })
    expect(localStorage.length).toBe(0)
  })

  it('joins a room by code and enters as Player 2', async () => {
    const view = matchView({
      requestingPlayerId: 'generated-guest',
      requestingSide: 'PLAYER_TWO',
      playerTwoOccupied: true,
    })
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(commandResponse(view)))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(screen.getByLabelText('Room code'), { target: { value: 'abc234' } })
    fireEvent.click(screen.getByRole('button', { name: 'Join match' }))

    expect(await screen.findByText('Side 2')).toBeInTheDocument()
    expect(fetchMock.mock.calls[0][0]).toBe('/api/dev/matches/join')
    expect(loadSession()?.playerId).toBe('generated-guest')
  })

  it('shows a readable structured API error instead of the backend detail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      code: 'MATCH_NOT_FOUND',
      message: 'repository implementation detail',
      timestamp: 'now',
    }, 404)))
    render(<App />)

    fireEvent.change(screen.getByLabelText('Room code'), { target: { value: 'none99' } })
    fireEvent.click(screen.getByRole('button', { name: 'Join match' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That room could not be found.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('repository implementation detail')
  })

  it('clears the current tab session and returns home', async () => {
    const view = matchView({ requestingPlayerId: 'generated-host' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(commandResponse(view), 201)))
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Create private match' }))
    await screen.findByDisplayValue('ABC234')

    fireEvent.click(screen.getByRole('button', { name: 'Leave local session' }))

    await waitFor(() => expect(loadSession()).toBeNull())
    expect(screen.getByRole('button', { name: 'Create private match' })).toBeInTheDocument()
  })
})
