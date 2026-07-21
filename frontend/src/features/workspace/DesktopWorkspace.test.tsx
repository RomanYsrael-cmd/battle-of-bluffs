import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { matchView } from '../../test-fixtures'
import { CapturedPiecesRail } from './CapturedPiecesRail'
import { DesktopMatchShell } from './DesktopMatchShell'
import { MatchHistoryDrawer } from './MatchHistoryDrawer'

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
        utilityDock={<><button>Open media</button><button>Open chat</button></>}>
        <div role="grid" aria-label="Match board" />
      </DesktopMatchShell>,
    )

    expect(screen.getByRole('grid', { name: 'Match board' })).toBeInTheDocument()
    expect(screen.getByLabelText('Authoritative match timing')).toBeInTheDocument()
    expect(screen.getByLabelText('Captured pieces')).toBeInTheDocument()
    expect(screen.getByLabelText('Match communication and history')).toBeInTheDocument()
  })

  it('groups known losses while opponent capture ranks remain absent during active play', () => {
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
    expect(screen.getByLabelText(/2 captured opponent pieces; ranks remain hidden/i)).toBeInTheDocument()
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
    expect(screen.getAllByText('No captures yet')).toHaveLength(2)
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
