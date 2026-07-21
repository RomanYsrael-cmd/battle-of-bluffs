import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { matchView } from '../../test-fixtures'
import { MatchTimers } from './MatchTimers'

describe('authoritative timer and presence rendering', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-19T10:00:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  it('renders server time and replaces it after an authoritative correction', () => {
    const initial = matchView({
      phase: 'ACTIVE',
      timerMode: 'STANDARD_15_PLUS_5',
      currentPlayer: 'PLAYER_ONE',
      timer: {
        playerOneRemainingMillis: 90_000,
        playerTwoRemainingMillis: 900_000,
        formationDeadline: null,
        activeTurnDeadline: '2026-07-19T10:01:30Z',
        incrementMillis: 5_000,
        serverTimestamp: '2026-07-19T10:00:00Z',
      },
    })
    const { rerender } = render(<MatchTimers view={initial} />)
    expect(screen.getByText('1:30')).toBeInTheDocument()

    rerender(<MatchTimers view={{
      ...initial,
      timer: {
        ...initial.timer,
        playerOneRemainingMillis: 30_000,
        activeTurnDeadline: '2026-07-19T10:00:30Z',
        serverTimestamp: '2026-07-19T10:00:00.001Z',
      },
    }} />)
    expect(screen.getByText('0:30')).toBeInTheDocument()
  })

  it('shows formation and opponent disconnect grace countdowns', () => {
    render(<MatchTimers view={matchView({
      playerTwoOccupied: true,
      timer: {
        playerOneRemainingMillis: 0,
        playerTwoRemainingMillis: 0,
        formationDeadline: '2026-07-19T10:05:00Z',
        activeTurnDeadline: null,
        incrementMillis: 0,
        serverTimestamp: '2026-07-19T10:00:00Z',
      },
      presence: {
        playerOneConnected: true,
        playerTwoConnected: false,
        playerOneDisconnectedSince: null,
        playerTwoDisconnectedSince: '2026-07-19T10:00:00Z',
        playerOneCumulativeDisconnectedMillis: 0,
        playerTwoCumulativeDisconnectedMillis: 0,
        disconnectGraceMillis: 60_000,
        rankedCumulativeAllowanceMillis: 0,
        serverTimestamp: '2026-07-19T10:00:00Z',
      },
    })} />)

    expect(screen.getByText('5:00')).toBeInTheDocument()
    expect(screen.getByText(/opponent disconnected · 1:00 grace remaining/i)).toBeInTheDocument()
  })

  it('does not inflate completed clocks with an inactive low-time warning', () => {
    render(<MatchTimers view={matchView({
      phase: 'TERMINAL',
      timerMode: 'STANDARD_15_PLUS_5',
      currentPlayer: null,
      timer: {
        ...matchView().timer,
        playerOneRemainingMillis: 0,
        playerTwoRemainingMillis: 0,
      },
    })} />)

    expect(screen.queryByText('Low time')).not.toBeInTheDocument()
    expect(document.querySelector('.play-clock--low')).not.toBeInTheDocument()
  })
})
