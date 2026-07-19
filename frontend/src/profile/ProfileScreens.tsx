import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ApiErrorNotice } from '../components/ApiErrorNotice'
import { EventHistory } from '../features/match/EventHistory'
import {
  getLeaderboard,
  getMatchHistory,
  getMyMatches,
  getMyProfile,
  getPublicProfile,
  updateMyProfile,
} from './client'
import type { CurrentRating, MatchListItem, ProfileStats } from './types'

function RatingCard({ rating }: { rating: CurrentRating }) {
  const next = nextTier(rating.rating)
  const progress = next
    ? Math.max(0, Math.min(100, ((rating.rating - next.minimum) / (next.maximum - next.minimum)) * 100))
    : 100
  return (
    <section className="profile-card rating-card">
      <p className="eyebrow">{rating.seasonName}</p>
      <h2>{rating.rating} · {rating.tierLabel}</h2>
      <p>{rating.provisional ? `Provisional · ${rating.ratedGames}/10 rated games` : 'Established rating'}</p>
      <div className="rating-progress" aria-label={`${Math.round(progress)}% progress toward next tier`}>
        <span style={{ width: `${progress}%` }} />
      </div>
      <small>{next ? `${next.maximum - rating.rating} points to ${next.label}` : 'Highest competitive tier'}</small>
    </section>
  )
}

function Statistics({ statistics }: { statistics: ProfileStats }) {
  const items = [
    ['Games', statistics.totalGames],
    ['Wins', statistics.wins],
    ['Losses', statistics.losses],
    ['Draws', statistics.draws],
    ['No contests', statistics.noContests],
    ['Win rate', `${Math.round(statistics.winRate * 100)}%`],
  ]
  return (
    <dl className="profile-statistics">
      {items.map(([label, value]) => (
        <div key={label}><dt>{label}</dt><dd>{value}</dd></div>
      ))}
    </dl>
  )
}

function RecentMatches({ matches }: { matches: MatchListItem[] }) {
  if (matches.length === 0) return <p className="empty-state">No completed matches yet.</p>
  return (
    <ul className="match-list">
      {matches.map((match) => (
        <li key={match.matchId}>
          <div>
            <strong className={`outcome outcome--${match.outcome.toLowerCase()}`}>{match.outcome.replace('_', ' ')}</strong>
            <span>{match.mode} against {match.opponentDisplayName}</span>
          </div>
          <div>
            {match.ratingDelta != null && <span>{match.ratingDelta >= 0 ? '+' : ''}{match.ratingDelta} rating</span>}
            <Link to={`/matches/${match.matchId}/history`}>Details</Link>
          </div>
        </li>
      ))}
    </ul>
  )
}

export function MyProfileScreen() {
  const queryClient = useQueryClient()
  const profile = useQuery({ queryKey: ['profile', 'me'], queryFn: getMyProfile })
  const [editing, setEditing] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const update = useMutation({
    mutationFn: () => updateMyProfile(displayName),
    onSuccess: (value) => {
      queryClient.setQueryData(['profile', 'me'], value)
      queryClient.setQueryData(['account'], (account: object | undefined) =>
        account ? { ...account, displayName: value.displayName } : account)
      setEditing(false)
    },
  })
  if (profile.isPending) return <PageStatus text="Loading profile…" />
  if (!profile.data) return <PageError error={profile.error} />
  return (
    <main className="app-shell profile-page">
      <header className="page-heading">
        <div><p className="eyebrow">Player profile</p><h1>{profile.data.displayName}</h1></div>
        <button type="button" className="button button--secondary" onClick={() => {
          setDisplayName(profile.data.displayName)
          setEditing(true)
        }}>Edit profile</button>
      </header>
      <p className="profile-identity">@{profile.data.username} · joined {formatDate(profile.data.joinedAt)}</p>
      <p className="profile-private">{profile.data.email} · {profile.data.emailVerified ? 'Email verified' : 'Email verification pending'}</p>
      {editing && (
        <form className="profile-edit" onSubmit={(event) => { event.preventDefault(); update.mutate() }}>
          <label>Display name<input minLength={2} maxLength={50} value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>
          <button className="button button--primary" disabled={update.isPending}>Save</button>
          <button type="button" className="button button--ghost" onClick={() => setEditing(false)}>Cancel</button>
        </form>
      )}
      <ApiErrorNotice error={update.error} />
      <div className="profile-grid"><RatingCard rating={profile.data.rating} /><Statistics statistics={profile.data.statistics} /></div>
      <section className="profile-card"><div className="section-heading"><h2>Recent matches</h2><Link to="/history">View all</Link></div><RecentMatches matches={profile.data.recentMatches} /></section>
    </main>
  )
}

export function PublicProfileScreen() {
  const { username = '' } = useParams()
  const profile = useQuery({ queryKey: ['profile', username], queryFn: () => getPublicProfile(username) })
  if (profile.isPending) return <PageStatus text="Loading player profile…" />
  if (!profile.data) return <PageError error={profile.error} />
  return (
    <main className="app-shell profile-page">
      <header className="page-heading"><div><p className="eyebrow">Public player profile</p><h1>{profile.data.displayName}</h1></div></header>
      <p className="profile-identity">@{profile.data.username} · joined {formatDate(profile.data.joinedAt)}</p>
      <div className="profile-grid"><RatingCard rating={profile.data.rating} /><Statistics statistics={profile.data.statistics} /></div>
      <section className="profile-card"><h2>Recent completed matches</h2><RecentMatches matches={profile.data.recentMatches} /></section>
    </main>
  )
}

export function MatchHistoryScreen() {
  const { matchId = '' } = useParams()
  const history = useQuery({ queryKey: ['match-history', matchId], queryFn: () => getMatchHistory(matchId) })
  if (history.isPending) return <PageStatus text="Loading match history…" />
  if (!history.data) return <PageError error={history.error} />
  return (
    <main className="app-shell history-page">
      <header className="page-heading"><div><p className="eyebrow">Match record</p><h1>{history.data.summary.mode} match</h1></div><span className="status-pill">{history.data.summary.phase}</span></header>
      {history.data.summary.terminalResult && <p className="result-banner">Result: {history.data.summary.terminalResult.reason.replaceAll('_', ' ')}</p>}
      {history.data.ratingChange && <RatingChangeDisplay change={history.data.ratingChange} />}
      {history.data.participantView ? (
        <>
          {history.data.participantView.phase === 'TERMINAL' && <p className="success-notice">Complete post-match formation disclosure is available to participants.</p>}
          <EventHistory events={history.data.participantView.events} />
        </>
      ) : <p className="profile-card">Public summary only. Private formations, ranks and chat are unavailable to nonparticipants.</p>}
    </main>
  )
}

export function MatchHistoryListScreen() {
  const [page, setPage] = useState(0)
  const matches = useQuery({ queryKey: ['matches', page], queryFn: () => getMyMatches(page) })
  if (matches.isPending) return <PageStatus text="Loading match history…" />
  if (!matches.data) return <PageError error={matches.error} />
  return (
    <main className="app-shell profile-page">
      <header className="page-heading"><div><p className="eyebrow">Personal archive</p><h1>Match history</h1></div></header>
      <RecentMatches matches={matches.data.items} />
      <Pagination page={page} totalPages={matches.data.totalPages} onPage={setPage} />
    </main>
  )
}

export function LeaderboardScreen() {
  const [scope, setScope] = useState<'seasonal' | 'all-time'>('seasonal')
  const [page, setPage] = useState(0)
  const board = useQuery({ queryKey: ['leaderboard', scope, page], queryFn: () => getLeaderboard(scope, page) })
  if (board.isPending) return <PageStatus text="Loading leaderboard…" />
  if (!board.data) return <PageError error={board.error} />
  return (
    <main className="app-shell leaderboard-page">
      <header className="page-heading"><div><p className="eyebrow">Competitive command</p><h1>Leaderboard</h1></div></header>
      <div className="scope-tabs"><button className={scope === 'seasonal' ? 'active' : ''} onClick={() => { setScope('seasonal'); setPage(0) }}>Seasonal</button><button className={scope === 'all-time' ? 'active' : ''} onClick={() => { setScope('all-time'); setPage(0) }}>All time</button></div>
      {board.data.ownEntry && <section className="own-ranking"><span>Your position</span><strong>{board.data.ownEntry.position ?? 'Provisional'}</strong><span>{board.data.ownEntry.rating} · {board.data.ownEntry.tierLabel}</span></section>}
      <p>Public ranking begins after {board.data.minimumGames} completed ranked games.</p>
      <ol className="leaderboard-list" start={page * board.data.size + 1}>
        {board.data.entries.map((entry) => <li key={entry.userId}><span className="leaderboard-position">#{entry.position}</span><div><Link to={`/players/${entry.username}`}><strong>{entry.displayName}</strong></Link><small>@{entry.username} · {entry.tierLabel}</small></div><div><strong>{entry.rating}</strong><small>{entry.wins} wins · {Math.round(entry.winRate * 100)}%</small></div></li>)}
      </ol>
      <Pagination page={page} totalPages={board.data.totalPages} onPage={setPage} />
    </main>
  )
}

export function RatingChangeDisplay({ change }: { change: { preMatchRating: number; postMatchRating: number; ratingDelta: number; kFactor: number } }) {
  return <section className="rating-change"><span>Rating</span><strong>{change.preMatchRating} → {change.postMatchRating}</strong><b>{change.ratingDelta >= 0 ? '+' : ''}{change.ratingDelta}</b><small>K {change.kFactor}</small></section>
}

function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  return <nav className="pagination" aria-label="Pagination"><button disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button><span>Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span><button disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button></nav>
}

function PageStatus({ text }: { text: string }) { return <main className="app-shell"><p role="status">{text}</p></main> }
function PageError({ error }: { error: unknown }) { return <main className="app-shell"><ApiErrorNotice error={error} /></main> }
function formatDate(value: string) { return new Date(value).toLocaleDateString() }
function nextTier(rating: number) {
  const tiers = [
    { minimum: 0, maximum: 900, label: 'Private' },
    { minimum: 900, maximum: 1100, label: 'Sergeant' },
    { minimum: 1100, maximum: 1300, label: 'Lieutenant' },
    { minimum: 1300, maximum: 1500, label: 'Captain' },
    { minimum: 1500, maximum: 1700, label: 'Colonel' },
    { minimum: 1700, maximum: 1900, label: 'General' },
    { minimum: 1900, maximum: 2100, label: 'Grand General' },
  ]
  return tiers.find((tier) => rating < tier.maximum)
}
