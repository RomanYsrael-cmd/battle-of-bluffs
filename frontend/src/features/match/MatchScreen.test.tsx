import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { saveSession } from '../../session/session'
import { commandResponse, currentMatchSummary, jsonResponse, matchView } from '../../test-fixtures'
import type { PlayerMatchView } from '../../api/types'
import { ActiveMatchScreen } from './ActiveMatchScreen'
import { MatchBoard } from './MatchBoard'
import { TerminalDisclosure } from './TerminalDisclosure'

const session = {
  matchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  roomCode: 'ABC234',
}

const activeView = (overrides: Partial<PlayerMatchView> = {}) => matchView({
  phase: 'ACTIVE',
  version: 6,
  playerTwoOccupied: true,
  playerOneLocked: true,
  playerTwoLocked: true,
  currentPlayer: 'PLAYER_ONE',
  ownPieces: [{
    id: '11111111-1111-4111-8111-111111111111',
    rank: 'FLAG',
    position: { row: 2, column: 0 },
    alive: true,
  }],
  opponentPieces: [{
    id: '99999999-9999-4999-8999-999999999999',
    position: { row: 5, column: 0 },
  }],
  ...overrides,
})

function renderActive(view: PlayerMatchView) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const onView = vi.fn()
  render(
    <QueryClientProvider client={client}>
      <ActiveMatchScreen
        view={view}
        session={session}
        onView={onView}
        onStale={vi.fn()}
        onLeave={vi.fn()}
      />
    </QueryClientProvider>,
  )
  return { onView }
}

describe('active and terminal match screens', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    sessionStorage.clear()
  })

  it('shows the own rank insignia while every opponent uses the neutral hidden back', () => {
    const view = activeView()
    render(
      <MatchBoard
        view={view}
        selectedPieceId={null}
        disabled={false}
        onSelectPiece={vi.fn()}
        onDestination={vi.fn()}
      />,
    )

    const ownPiece = document.querySelector('.match-piece--own')
    const opponentPiece = screen.getByLabelText('Hidden opponent piece')
    expect(ownPiece?.querySelector('[data-symbol="generic-flag"]')).toBeInTheDocument()
    expect(screen.getByRole('gridcell', { name: /row 2, column 0, flag/i })).toBeInTheDocument()
    expect(opponentPiece).toHaveClass('match-piece--opponent')
    expect(opponentPiece.querySelector('.hidden-piece-insignia')).toBeInTheDocument()
    expect(opponentPiece.querySelector('[data-symbol]')).not.toBeInTheDocument()
    expect(opponentPiece).not.toHaveAccessibleName(/flag|spy|general|colonel|major|captain|lieutenant|sergeant|private/i)
    expect(screen.queryByText('Spy')).not.toBeInTheDocument()
    expect(document.querySelectorAll('.board-cell__coordinate')).toHaveLength(0)
    expect(Array.from(document.querySelectorAll('.board-rank-labels span'), (label) => label.textContent))
      .toEqual(['7', '6', '5', '4', '3', '2', '1', '0'])
    expect(Array.from(document.querySelectorAll('.board-file-labels span'), (label) => label.textContent))
      .toEqual(['0', '1', '2', '3', '4', '5', '6', '7', '8'])
  })

  it('submits a selected move without resolving the board optimistically', async () => {
    const view = activeView()
    const moved = activeView({
      version: 7,
      currentPlayer: 'PLAYER_TWO',
      ownPieces: [{ ...view.ownPieces[0], position: { row: 3, column: 0 } }],
    })
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(commandResponse(moved)))
    vi.stubGlobal('fetch', fetchMock)
    const { onView } = renderActive(view)

    fireEvent.click(screen.getByRole('gridcell', { name: /row 2, column 0/i }))
    fireEvent.click(screen.getByRole('gridcell', { name: /row 3, column 0, candidate destination/i }))

    expect(document.querySelector('.match-piece--own [data-symbol="generic-flag"]'))
      .toBeInTheDocument()
    await waitFor(() => expect(onView).toHaveBeenCalledWith(commandResponse(moved)))
    const payload = JSON.parse(fetchMock.mock.calls[0][1].body)
    expect(payload.expectedVersion).toBe(6)
    expect(payload.commandId).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('disables piece selection and candidate destinations when it is not the player turn', () => {
    renderActive(activeView({ currentPlayer: 'PLAYER_TWO' }))

    const ownPieceCell = screen.getByRole('gridcell', { name: /row 2, column 0, flag/i })
    expect(ownPieceCell).toBeDisabled()
    fireEvent.click(ownPieceCell)
    expect(screen.queryByRole('gridcell', { name: /candidate destination/i })).not.toBeInTheDocument()
    expect(ownPieceCell).not.toHaveAccessibleName(/selected/i)
  })

  it('refetches after a stale version and does not retry the move', async () => {
    const initial = activeView()
    const synchronized = activeView({ version: 7, currentPlayer: 'PLAYER_TWO' })
    saveSession(session)
    window.history.replaceState({}, '', `/matches/${session.matchId}`)
    let playerViewRequests = 0
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/auth/me') return jsonResponse({
        id: 'account-id', username: 'marshal', displayName: 'Marshal', status: 'ACTIVE', emailVerified: true,
      })
      if (url === '/api/matches/current') return jsonResponse({
        activities: [currentMatchSummary({ phase: 'ACTIVE', version: 6 })],
        multipleOpenMatches: false,
      })
      if (url.endsWith('/moves') && init?.method === 'POST') return jsonResponse({
        code: 'STALE_VERSION',
        message: 'Expected version 6 but current is 7',
        timestamp: 'now',
      }, 409)
      if (url === `/api/matches/${session.matchId}`) {
        playerViewRequests++
        return jsonResponse(playerViewRequests === 1 ? initial : synchronized)
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    await screen.findByText('Your turn')
    fireEvent.click(screen.getByRole('gridcell', { name: /row 2, column 0/i }))
    fireEvent.click(screen.getByRole('gridcell', { name: /row 3, column 0, candidate destination/i }))

    await waitFor(() => expect(screen.getAllByText(/synchronizing the latest state/i).length)
      .toBeGreaterThan(0))
    await waitFor(() => expect(playerViewRequests).toBe(2))
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1)
    expect(await screen.findByText('Opponent’s turn')).toBeInTheDocument()
    expect(screen.queryByRole('gridcell', { name: /candidate destination/i })).not.toBeInTheDocument()
    expect(screen.getByRole('gridcell', { name: /row 2, column 0, flag/i })).toBeDisabled()
  })

  it('requires confirmation before resignation', async () => {
    const terminal = activeView({
      phase: 'TERMINAL',
      version: 7,
      currentPlayer: null,
      terminalResult: { winner: 'PLAYER_TWO', reason: 'RESIGNATION' },
    })
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(commandResponse(terminal)))
    vi.stubGlobal('fetch', fetchMock)
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const { onView } = renderActive(activeView())

    fireEvent.click(screen.getByRole('button', { name: 'Resign match' }))
    expect(fetchMock).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Resign match' }))

    expect(confirm).toHaveBeenCalledTimes(2)
    await waitFor(() => expect(onView).toHaveBeenCalledWith(commandResponse(terminal)))
  })

  it('renders terminal result and complete server-supplied disclosure', () => {
    const terminal = activeView({
      phase: 'TERMINAL',
      currentPlayer: null,
      terminalResult: { winner: 'PLAYER_ONE', reason: 'FLAG_CAPTURE' },
      postMatchPieces: [
        { id: 'own', owner: 'PLAYER_ONE', rank: 'FLAG', position: { row: 7, column: 0 }, alive: true },
        { id: 'public-opponent', owner: 'PLAYER_TWO', rank: 'SPY', position: null, alive: false },
      ],
    })
    render(<TerminalDisclosure view={terminal} onLeave={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'You won' })).toBeInTheDocument()
    expect(screen.getByText('FLAG CAPTURE')).toBeInTheDocument()
    expect(screen.getAllByText('Flag')).toHaveLength(1)
    expect(screen.getByText('Spy')).toBeInTheDocument()
    expect(document.querySelector('.disclosed-piece [data-symbol="generic-flag"]')).toBeInTheDocument()
    expect(document.querySelectorAll('.disclosed-piece [data-symbol="eye"]')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Return home' })).toBeInTheDocument()
  })
})
