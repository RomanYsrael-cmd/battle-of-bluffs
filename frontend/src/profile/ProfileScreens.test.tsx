import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { jsonResponse } from '../test-fixtures'
import { primeCsrfTokenForTest } from '../api/http'
import { AccountSettingsScreen, LeaderboardScreen, MyProfileScreen } from './ProfileScreens'

function renderScreen(screenElement: ReactNode) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{screenElement}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('profile and competition screens', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('renders private profile statistics, verification, tier and provisional progress', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      username: 'marshal',
      displayName: 'Marshal',
      email: 'marshal@example.test',
      joinedAt: '2026-01-01T00:00:00Z',
      emailVerified: true,
      statistics: { totalGames: 4, rankedGames: 3, casualGames: 1, wins: 2, losses: 1, draws: 1, noContests: 0, winRate: 0.5 },
      rating: { rating: 1280, tier: 'SERGEANT', tierLabel: 'Sergeant', ratedGames: 3, wins: 2, losses: 1, draws: 0, provisional: true, seasonId: 'season', seasonName: 'Season One' },
      recentMatches: [],
    })))

    renderScreen(<MyProfileScreen />)

    expect(await screen.findByRole('heading', { name: 'Marshal' })).toBeInTheDocument()
    expect(screen.getByText('marshal@example.test · Email verified')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '1280 · Sergeant' })).toBeInTheDocument()
    expect(screen.getByText('Provisional · 3/10 rated games')).toBeInTheDocument()
    expect(screen.getByText('50%')).toBeInTheDocument()
  })

  it('shows the authenticated provisional row even outside the public leaderboard', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      scope: 'SEASONAL',
      seasonName: 'Season One',
      entries: [],
      ownEntry: { userId: 'me', username: 'marshal', displayName: 'Marshal', rating: 1200, tier: 'SERGEANT', tierLabel: 'Sergeant', ratedGames: 2, wins: 1, losses: 1, draws: 0, winRate: 0.5, provisional: true, eligible: false, position: null },
      page: 0,
      size: 25,
      totalItems: 0,
      totalPages: 0,
      minimumGames: 5,
    })))

    renderScreen(<LeaderboardScreen />)

    expect(await screen.findByText('Provisional')).toBeInTheDocument()
    expect(screen.getByText('1200 · Sergeant')).toBeInTheDocument()
    expect(screen.getByText(/ranking begins after 5/i)).toBeInTheDocument()
  })

  it('shows private sign-in details and updates the public display name', async () => {
    const profile = {
      username: 'marshal',
      displayName: 'Marshal',
      email: 'marshal@example.test',
      joinedAt: '2026-01-01T00:00:00Z',
      emailVerified: false,
      statistics: { totalGames: 0, rankedGames: 0, casualGames: 0, wins: 0, losses: 0, draws: 0, noContests: 0, winRate: 0 },
      rating: { rating: 1200, tier: 'SERGEANT', tierLabel: 'Sergeant', ratedGames: 0, wins: 0, losses: 0, draws: 0, provisional: true, seasonId: 'season', seasonName: 'Season One' },
      recentMatches: [],
    }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(profile))
      .mockResolvedValueOnce(jsonResponse({ ...profile, displayName: 'Field Marshal' }))
    vi.stubGlobal('fetch', fetchMock)
    primeCsrfTokenForTest('X-CSRF-TOKEN', 'csrf')

    renderScreen(<AccountSettingsScreen />)

    const displayName = await screen.findByLabelText('Display name')
    expect(screen.getByText('marshal@example.test')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Complete email verification' })).toHaveAttribute('href', '/verification-status')
    fireEvent.change(displayName, { target: { value: 'Field Marshal' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save display name' }))

    expect(await screen.findByText('Display name updated.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith('/api/profile/me', expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ displayName: 'Field Marshal' }),
    }))
  })
})
