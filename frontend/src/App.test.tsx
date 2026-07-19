import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import {
  commandResponse,
  currentMatchSummary,
  jsonResponse,
  matchView,
} from './test-fixtures'
import { loadSession, saveSession } from './session/session'
import type { CurrentMatchSummary, PlayerMatchView } from './api/types'

vi.mock('./realtime/matchSocket', async (importOriginal) => {
  const original = await importOriginal<typeof import('./realtime/matchSocket')>()
  return {
    ...original,
    connectMatchUpdates: (_matchId: string, callbacks: {
      onState: (state: 'SYNCHRONIZED') => void
    }) => {
      callbacks.onState('SYNCHRONIZED')
      return () => undefined
    },
  }
})

vi.mock('./matchmaking/RankedMatchmaking', () => ({
  RankedMatchmaking: () => null,
}))

const account = {
  id: '10000000-0000-4000-8000-000000000001',
  username: 'host',
  displayName: 'Host',
  status: 'ACTIVE',
  emailVerified: true,
}

function installApi({
  activities = [],
  view = matchView(),
  joinError,
  lifecycleStaleOnce = false,
}: {
  activities?: CurrentMatchSummary[]
  view?: PlayerMatchView
  joinError?: { code: string; message: string; status: number }
  lifecycleStaleOnce?: boolean
} = {}) {
  let authoritative = activities
  let rejectLifecycleAsStale = lifecycleStaleOnce
  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    if (url === '/api/auth/me') return jsonResponse(account)
    if (url === '/api/profile/me') {
      return jsonResponse({
        username: account.username,
        displayName: account.displayName,
        email: 'host@example.test',
        joinedAt: '2026-01-01T00:00:00Z',
        emailVerified: true,
        statistics: {
          totalGames: 0,
          rankedGames: 0,
          casualGames: 0,
          wins: 0,
          losses: 0,
          draws: 0,
          noContests: 0,
          winRate: 0,
        },
        rating: {
          rating: 1200,
          tier: 'SERGEANT',
          tierLabel: 'Sergeant',
          ratedGames: 0,
          wins: 0,
          losses: 0,
          draws: 0,
          provisional: true,
          seasonId: 'season',
          seasonName: 'Season One',
        },
        recentMatches: [],
      })
    }
    if (url === '/api/matches/current') {
      return jsonResponse({
        activities: authoritative,
        multipleOpenMatches: authoritative.length > 1,
      })
    }
    if (url === '/api/matches' && method === 'POST') {
      authoritative = [currentMatchSummary({
        matchId: view.matchId,
        roomCode: view.roomCode,
        version: view.version,
        side: view.requestingSide,
        opponentPresent: view.playerTwoOccupied,
        resumeRoute: `/matches/${view.matchId}`,
      })]
      return jsonResponse(commandResponse(view), 201)
    }
    if (url === '/api/matches/join' && method === 'POST') {
      if (joinError) {
        return jsonResponse({
          code: joinError.code,
          message: joinError.message,
          timestamp: 'now',
        }, joinError.status)
      }
      authoritative = [currentMatchSummary({
        matchId: view.matchId,
        roomCode: view.roomCode,
        version: view.version,
        side: view.requestingSide,
        opponentPresent: view.playerTwoOccupied,
        resumeRoute: `/matches/${view.matchId}`,
      })]
      return jsonResponse(commandResponse(view))
    }
    if (url.endsWith('/cancel') && method === 'POST') {
      if (rejectLifecycleAsStale) {
        rejectLifecycleAsStale = false
        authoritative = authoritative.map((activity) => ({
          ...activity,
          version: activity.version + 2,
        }))
        return jsonResponse({ code: 'STALE_VERSION', message: 'stale', timestamp: 'now' }, 409)
      }
      authoritative = authoritative.filter((activity) => activity.matchId !== view.matchId)
      return jsonResponse({
        commandId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        version: view.version + 1,
        matchId: view.matchId,
        action: 'ROOM_CANCELLED',
      })
    }
    if (url.endsWith('/leave') && method === 'POST') {
      authoritative = authoritative.filter((activity) => activity.matchId !== view.matchId)
      return jsonResponse({
        commandId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        version: view.version + 1,
        matchId: view.matchId,
        action: 'LOBBY_LEFT',
      })
    }
    if (url === `/api/matches/${view.matchId}`) return jsonResponse(view)
    throw new Error(`Unexpected request: ${method} ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('authoritative open-match recovery', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    window.history.replaceState({}, '', '/')
  })

  afterEach(() => vi.unstubAllGlobals())

  it('creates a private match and routes to its durable match URL', async () => {
    const view = matchView({ requestingPlayerId: 'generated-host' })
    installApi({ view })
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Create private match' }))

    expect(await screen.findByDisplayValue('ABC234')).toBeInTheDocument()
    expect(screen.getByText(/waiting for a second player/i)).toBeInTheDocument()
    expect(window.location.pathname).toBe(`/matches/${view.matchId}`)
    expect(loadSession()).toEqual({ matchId: view.matchId, roomCode: 'ABC234' })
    expect(localStorage.length).toBe(0)
  })

  it('joins a room by code and enters as Player 2', async () => {
    const view = matchView({
      requestingPlayerId: 'generated-guest',
      requestingSide: 'PLAYER_TWO',
      playerTwoOccupied: true,
    })
    const fetchMock = installApi({ view })
    render(<App />)

    fireEvent.change(await screen.findByLabelText('Room code'), { target: { value: 'abc234' } })
    fireEvent.click(screen.getByRole('button', { name: 'Join match' }))

    expect(await screen.findByText('Side 2')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/matches/join', expect.objectContaining({ method: 'POST' }))
    expect(loadSession()?.matchId).toBe(view.matchId)
  })

  it('shows a persisted waiting room after a fresh login and repairs stale session storage', async () => {
    const activity = currentMatchSummary()
    const view = matchView()
    saveSession({ matchId: 'stale-local-match', roomCode: 'STALE1' })
    installApi({ activities: [activity], view })
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Current game' })).toBeInTheDocument()
    expect(screen.getByText('ABC234')).toBeInTheDocument()
    expect(screen.getAllByText('Waiting for opponent').length).toBeGreaterThan(0)
    await waitFor(() => expect(loadSession()?.matchId).toBe(activity.matchId))

    fireEvent.click(screen.getByRole('button', { name: 'Continue game' }))
    expect(await screen.findByDisplayValue('ABC234')).toBeInTheDocument()
    expect(window.location.pathname).toBe(activity.resumeRoute)
  })

  it('requires confirmation before the host cancels and then removes the current-game card', async () => {
    const activity = currentMatchSummary()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const fetchMock = installApi({ activities: [activity] })
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel room' }))
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/cancel'))).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'Cancel room' }))

    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Current game' })).not.toBeInTheDocument())
    expect(confirm).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('button', { name: 'Create private match' })).toBeEnabled()
  })

  it('offers a pre-lock guest a confirmed Leave lobby action', async () => {
    const activity = currentMatchSummary({
      side: 'PLAYER_TWO',
      opponentPresent: true,
      canCancel: false,
      canLeave: true,
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const fetchMock = installApi({ activities: [activity] })
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Leave lobby' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/leave'))).toBe(true))
    expect(screen.queryByText('Leave lobby')).not.toBeInTheDocument()
  })

  it('refetches and retries a permitted lifecycle command once after a stale version', async () => {
    const activity = currentMatchSummary()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const fetchMock = installApi({ activities: [activity], lifecycleStaleOnce: true })
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Cancel room' }))

    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Current game' })).not.toBeInTheDocument())
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/cancel'))).toHaveLength(2)
  })

  it('shows active recovery and a navigation indicator without offering cancellation', async () => {
    const activity = currentMatchSummary({
      phase: 'ACTIVE',
      version: 8,
      opponentPresent: true,
      canCancel: false,
      currentPlayer: 'PLAYER_ONE',
    })
    installApi({ activities: [activity], view: matchView({ phase: 'ACTIVE', version: 8 }) })
    render(<App />)

    expect(await screen.findByRole('button', { name: 'Resume game' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel room' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /in progress · your turn/i })).toHaveAttribute(
      'href',
      activity.resumeRoute,
    )
  })

  it('keeps the current-game indicator on settings without redirecting away', async () => {
    const activity = currentMatchSummary({
      phase: 'ACTIVE',
      opponentPresent: true,
      canCancel: false,
      currentPlayer: 'PLAYER_TWO',
    })
    window.history.replaceState({}, '', '/settings')
    installApi({ activities: [activity] })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Account settings' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /in progress · opponent.s turn/i })).toHaveAttribute(
      'href',
      activity.resumeRoute,
    )
    expect(window.location.pathname).toBe('/settings')
  })

  it('does not render backend details from a structured lifecycle or join error', async () => {
    installApi({
      joinError: {
        code: 'MATCH_NOT_FOUND',
        message: 'repository implementation detail',
        status: 404,
      },
    })
    render(<App />)

    fireEvent.change(await screen.findByLabelText('Room code'), { target: { value: 'none99' } })
    fireEvent.click(screen.getByRole('button', { name: 'Join match' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That room could not be found.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('repository implementation detail')
  })

  it('returns to the dashboard without treating navigation as resignation', async () => {
    const view = matchView()
    installApi({ view })
    render(<App />)
    fireEvent.click(await screen.findByRole('button', { name: 'Create private match' }))
    await screen.findByDisplayValue('ABC234')

    fireEvent.click(screen.getByRole('button', { name: 'Back to dashboard' }))

    await waitFor(() => expect(window.location.pathname).toBe('/'))
    expect(screen.getByRole('button', { name: 'Create private match' })).toBeInTheDocument()
  })
})
