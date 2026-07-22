import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { matchView } from '../../test-fixtures'
import { CapturedPiecesRail } from './CapturedPiecesRail'
import { DesktopMatchShell } from './DesktopMatchShell'
import { MatchHistoryDrawer } from './MatchHistoryDrawer'

afterEach(() => vi.unstubAllGlobals())

describe('desktop match workspace', () => {
  it('keeps board, timers, captures and utility controls in the active shell', () => {
    const view = matchView({
      phase: 'ACTIVE',
      timerMode: 'STANDARD_15_PLUS_5',
      currentPlayer: 'PLAYER_ONE',
      playerTwoOccupied: true,
      timer: {
        ...matchView().timer,
        playerOneRemainingMillis: 894_000,
        playerTwoRemainingMillis: 900_000,
      },
    })
    render(
      <DesktopMatchShell view={view} connectionState="SYNCHRONIZED"
        connectionLabel="Live updates synchronized" syncMessage="" onLeave={vi.fn()}
        captures={<CapturedPiecesRail view={view} />}
        media={<button>Open media</button>} chat={<button>Open chat</button>}>
        <div role="grid" aria-label="Match board" />
      </DesktopMatchShell>,
    )

    expect(screen.getByRole('grid', { name: 'Match board' })).toBeInTheDocument()
    expect(screen.getByLabelText('Authoritative match timing')).toBeInTheDocument()
    expect(screen.getByLabelText('Captured pieces')).toBeInTheDocument()
    expect(screen.getByLabelText('Match communication and history')).toBeInTheDocument()
  })

  it('switches mobile board, eliminated pieces, camera, chat, and game-info views without unmounting media', () => {
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    const view = matchView({ phase: 'ACTIVE', playerTwoOccupied: true })
    render(
      <DesktopMatchShell view={view} connectionState="SYNCHRONIZED"
        connectionLabel="Live updates synchronized" syncMessage="" onLeave={vi.fn()}
        captures={<div aria-label="Eliminated pieces view">Lost pieces</div>}
        media={<div aria-label="Live camera view">Live video</div>}
        chat={<div aria-label="Expanded chat view">Messages</div>}
        history={<div aria-label="Game information view">History</div>}>
        <div role="grid" aria-label="Mobile match board" />
      </DesktopMatchShell>,
    )

    const board = screen.getByRole('grid', { name: 'Mobile match board' })
    const media = screen.getByLabelText('Live camera view')
    expect(board).toBeVisible()
    expect(media).toBeVisible()

    fireEvent.click(screen.getByRole('button', { name: /pieces/i }))
    expect(screen.getByLabelText('Eliminated pieces view')).toBeVisible()
    expect(board).not.toBeVisible()
    expect(media).not.toBeVisible()

    fireEvent.click(screen.getByRole('button', { name: /camera/i }))
    expect(media).toBeVisible()
    expect(screen.getByText('Live video')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /^chat$/i }))
    expect(screen.getByLabelText('Expanded chat view')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: /game info/i }))
    expect(screen.getByLabelText('Game information view')).toBeVisible()
    expect(media).toBeInTheDocument()
  })

  it('shows grouped own losses without the captured-opponent panel', () => {
    const view = matchView({
      phase: 'ACTIVE',
      ownPieces: [
        { id: 'p1', rank: 'PRIVATE', position: null, alive: false },
        { id: 'p2', rank: 'PRIVATE', position: null, alive: false },
      ],
      opponentPieces: Array.from({ length: 19 }, (_, index) => ({
        id: `opponent-${index}`,
        position: { row: 5 + Math.floor(index / 9), column: index % 9 },
      })),
    })
    render(<CapturedPiecesRail view={view} />)

    expect(screen.getByLabelText('Private, 2')).toHaveTextContent('×2')
    expect(screen.getByRole('heading', { name: 'Your lost pieces' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Captured by you' })).not.toBeInTheDocument()
    expect(screen.queryByText('SPY')).not.toBeInTheDocument()
  })

  it('shows a clear empty capture state', () => {
    const view = matchView({
      phase: 'ACTIVE',
      ownPieces: [],
      opponentPieces: Array.from({ length: 21 }, (_, index) => ({
        id: `opponent-${index}`,
        position: { row: 5 + Math.floor(index / 9), column: index % 9 },
      })),
    })
    render(<CapturedPiecesRail view={view} />)
    expect(screen.getByText('No captures yet')).toBeInTheDocument()
  })

  it('collapses history independently and persists the account-scoped preference', () => {
    localStorage.clear()
    const event = {
      sequence: 1, type: 'MATCH_CREATED' as const, actor: null,
      source: null, destination: null, removedPieceIds: [],
      ownBattleOutcome: null, terminalResult: null,
    }
    render(<MatchHistoryDrawer accountId="account-1" matchId="match-1" events={[event]} />)
    expect(screen.queryByText('Private room created.')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /open history/i }))
    expect(screen.getByText('Private room created.')).toBeInTheDocument()
    expect(localStorage.getItem('gotg:history-panel:account-1:match-1')).toBe('true')
  })
})
