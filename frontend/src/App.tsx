import { lazy, Suspense, useEffect, useState, type ReactNode } from 'react'
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { getChatHistory, getCurrentMatches, getPlayerView } from './api/client'
import {
  BrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useNavigate,
  useParams,
} from 'react-router-dom'
import {
  ForgotPasswordScreen,
  LoginScreen,
  RegistrationScreen,
  ResetPasswordScreen,
  VerificationStatusScreen,
  VerifyEmailScreen,
} from './auth/AuthScreens'
import { getCurrentAccount, logout, type CurrentAccount } from './auth/client'
import type {
  ChatError,
  ChatMessage,
  CommandResponse,
  CurrentMatchesResponse,
  CurrentMatchSummary,
} from './api/types'
import { ApiErrorNotice } from './components/ApiErrorNotice'
import { FormationScreen } from './features/formation/FormationScreen'
import { MatchChatPanel } from './features/chat/MatchChatPanel'
import {
  activityLabel,
  currentMatchesQueryKey,
  HomeScreen,
} from './features/home/HomeScreen'
import { LandingScreen } from './features/home/LandingScreen'
import { ActiveMatchScreen } from './features/match/ActiveMatchScreen'
import { CapturedPiecesRail } from './features/workspace/CapturedPiecesRail'
import { DesktopMatchShell } from './features/workspace/DesktopMatchShell'
import { MatchHistoryDrawer } from './features/workspace/MatchHistoryDrawer'
import {
  clearSession,
  loadSession,
  saveSession,
  type MatchSession,
} from './session/session'
import {
  assessMatchUpdate,
  connectMatchUpdates,
  mergeChatMessages,
  type MatchConnectionState,
} from './realtime/matchSocket'
import {
  LeaderboardScreen,
  AccountSettingsScreen,
  MatchHistoryListScreen,
  MatchHistoryScreen,
  MyProfileScreen,
  PublicProfileScreen,
} from './profile/ProfileScreens'
import { clearMediaSession } from './features/media/mediaSession'

const matchQueryKey = (session: MatchSession) => ['match', session.matchId]
const MatchMediaPanel = lazy(() => import('./features/media/MatchMediaPanel')
  .then((module) => ({ default: module.MatchMediaPanel })))

function MatchRoute({ session, onLeave }: { session: MatchSession; onLeave: () => void }) {
  const queryClient = useQueryClient()
  const [syncMessage, setSyncMessage] = useState('')
  const [connectionState, setConnectionState] = useState<MatchConnectionState>('CONNECTING')
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [chatError, setChatError] = useState<ChatError | null>(null)
  const [sendChat, setSendChat] = useState<(body: string) => boolean>(() => () => false)
  const [opponentBlocked, setOpponentBlocked] = useState(false)
  const query = useQuery({
    queryKey: matchQueryKey(session),
    queryFn: () => getPlayerView(session.matchId),
    retry: false,
    refetchInterval: (currentQuery) => {
      if (currentQuery.state.data?.phase === 'TERMINAL') return false
      return connectionState === 'SYNCHRONIZED' ? false : 15_000
    },
  })

  useEffect(() => {
    if (!query.data) return
    saveSession({ matchId: query.data.matchId, roomCode: query.data.roomCode })
  }, [query.data])

  useEffect(() => {
    let active = true
    const refetchSafeView = (recoveredMessage = '') => {
      void queryClient.fetchQuery({
        queryKey: matchQueryKey(session),
        queryFn: () => getPlayerView(session.matchId),
      }).then(() => {
        if (!active) return
        setConnectionState('SYNCHRONIZED')
        setSyncMessage(recoveredMessage)
      }).catch(() => {
        if (active) setConnectionState('RECOVERING')
      })
    }

    const loadChatHistory = () => {
      void getChatHistory(session.matchId).then((history) => {
        if (active) setChatMessages((current) => mergeChatMessages(current, history))
      }).catch(() => {
        if (active) setChatError({
          code: 'CHAT_HISTORY_UNAVAILABLE',
          message: 'Chat history could not be loaded.',
          serverTimestamp: new Date().toISOString(),
        })
      })
    }

    const connection = connectMatchUpdates(session.matchId, {
      onState: setConnectionState,
      onConnected: () => refetchSafeView(),
      onUpdate: (update) => {
        const current = queryClient.getQueryData<CommandResponse['view']>(matchQueryKey(session))
        if (!current) {
          setConnectionState('RECOVERING')
          refetchSafeView()
          return
        }
        const decision = assessMatchUpdate(
          session.matchId,
          current.liveSequence,
          current.version,
          update,
        )
        if (decision === 'APPLY') {
          queryClient.setQueryData(matchQueryKey(session), update.view)
          setConnectionState('SYNCHRONIZED')
          setSyncMessage('')
        } else if (decision === 'REFETCH') {
          setConnectionState('RECOVERING')
          setSyncMessage('Update gap detected. Recovering the latest safe state…')
          refetchSafeView('Sequence gap recovered.')
        }
      },
      onInvalidMessage: () => {
        setConnectionState('RECOVERING')
        refetchSafeView()
      },
      onChatConnected: loadChatHistory,
      onChatMessage: (message) => {
        if (!active || message.matchId !== session.matchId) return
        setChatError(null)
        setChatMessages((current) => mergeChatMessages(current, [message]))
      },
      onChatError: (error) => {
        if (active) setChatError(error)
      },
    })
    setSendChat(() => typeof connection === 'function' ? () => false : connection.sendChat)
    return () => {
      active = false
      if (typeof (connection as unknown) === 'function') {
        (connection as unknown as () => void)()
      }
      else connection.disconnect()
      setSendChat(() => () => false)
    }
  }, [queryClient, session])

  const acceptResponse = (response: CommandResponse) => {
    queryClient.setQueryData(matchQueryKey(session), response.view)
    setSyncMessage('')
  }
  const synchronize = async () => {
    setSyncMessage('The match changed. Synchronizing the latest state…')
    const result = await query.refetch().finally(() => {
      window.setTimeout(() => setSyncMessage(''), 1_500)
    })
    if (result.error) throw result.error
    if (!result.data) throw new Error('The refreshed match view was unavailable.')
    return result.data
  }

  if (query.isPending) {
    return <main className="app-shell"><p role="status">Loading private match…</p></main>
  }
  if (query.error || !query.data) {
    return (
      <main className="app-shell">
        <ApiErrorNotice error={query.error} />
        <button type="button" className="button button--ghost" onClick={onLeave}>
          Back to dashboard
        </button>
      </main>
    )
  }

  const utilityDock = query.data.playerTwoOccupied ? (
    <>
      <Suspense fallback={<p role="status">Loading optional media controls…</p>}>
        <MatchMediaPanel
          key={`media-${latestParticipantCycle(query.data.events)}`}
          accountId={query.data.requestingPlayerId}
          matchId={session.matchId}
          participantCycle={latestParticipantCycle(query.data.events)}
          blocked={opponentBlocked}
        />
      </Suspense>
      <MatchChatPanel
        accountId={query.data.requestingPlayerId}
        matchId={session.matchId}
        connected={connectionState === 'SYNCHRONIZED'}
        messages={chatMessages}
        error={chatError}
        onSend={sendChat}
        onBlockedChange={setOpponentBlocked}
      />
      <MatchHistoryDrawer
        accountId={query.data.requestingPlayerId}
        matchId={session.matchId}
        events={query.data.events}
      />
    </>
  ) : undefined

  return (
    <DesktopMatchShell
      view={query.data}
      connectionState={connectionState}
      connectionLabel={connectionLabel(connectionState)}
      syncMessage={syncMessage}
      onLeave={onLeave}
      captures={query.data.phase === 'FORMATION' ? undefined : <CapturedPiecesRail view={query.data} />}
      utilityDock={utilityDock}
    >
      {query.data.phase === 'FORMATION' ? (
        <FormationScreen
          view={query.data}
          session={session}
          onView={acceptResponse}
          onStale={synchronize}
        />
      ) : (
        <ActiveMatchScreen
          view={query.data}
          session={session}
          onView={acceptResponse}
          onStale={() => { void synchronize() }}
          onLeave={onLeave}
        />
      )}
    </DesktopMatchShell>
  )
}

function latestParticipantCycle(events: { sequence: number; type: string }[]): string {
  return String(events.reduce((latest, event) =>
    event.type === 'PLAYER_JOINED' || event.type === 'PLAYER_LEFT'
      ? Math.max(latest, event.sequence)
      : latest, 0))
}

function connectionLabel(state: MatchConnectionState): string {
  switch (state) {
    case 'SYNCHRONIZED': return 'Live updates synchronized'
    case 'RECONNECTING': return 'Live updates disconnected — reconnecting'
    case 'RECOVERING': return 'Recovering the authoritative match state'
    default: return 'Connecting live updates'
  }
}

function MatchApplication() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const enterMatch = (response: CommandResponse) => {
    const nextSession = {
      matchId: response.matchId,
      roomCode: response.roomCode,
    }
    saveSession(nextSession)
    queryClient.setQueryData(matchQueryKey(nextSession), response.view)
    queryClient.setQueryData<CurrentMatchesResponse>(currentMatchesQueryKey, (current) => {
      const existing = current?.activities.find((activity) => activity.matchId === response.matchId)
      const enteredMatch: CurrentMatchSummary = {
        matchId: response.matchId,
        mode: response.view.mode,
        phase: response.view.phase,
        version: response.version,
        roomCode: response.roomCode,
        side: response.view.requestingSide,
        opponentPresent: response.view.requestingSide === 'PLAYER_ONE'
          ? response.view.playerTwoOccupied
          : response.view.playerOneOccupied,
        ownFormationSubmitted: response.view.ownPieces.length > 0,
        ownLocked: response.view.requestingSide === 'PLAYER_ONE'
          ? response.view.playerOneLocked
          : response.view.playerTwoLocked,
        opponentLocked: response.view.requestingSide === 'PLAYER_ONE'
          ? response.view.playerTwoLocked
          : response.view.playerOneLocked,
        currentPlayer: response.view.currentPlayer,
        createdAt: existing?.createdAt ?? null,
        updatedAt: existing?.updatedAt ?? null,
        canResume: true,
        canCancel: existing?.canCancel ?? false,
        canLeave: existing?.canLeave ?? false,
        resumeRoute: `/matches/${response.matchId}`,
      }
      const otherActivities = current?.activities.filter(
        (activity) => activity.matchId !== response.matchId,
      ) ?? []
      const activities = [enteredMatch, ...otherActivities]
      return {
        activities,
        multipleOpenMatches: activities.length > 1,
      }
    })
    navigate(`/matches/${response.matchId}`)
    void queryClient.invalidateQueries({ queryKey: currentMatchesQueryKey })
  }

  const continueMatch = (activity: CurrentMatchSummary) => {
    saveSession({ matchId: activity.matchId, roomCode: activity.roomCode })
    navigate(activity.resumeRoute)
  }

  return <HomeScreen onEnteredMatch={enterMatch} onContinueMatch={continueMatch} />
}

function AuthoritativeMatchRoute() {
  const { matchId } = useParams()
  const navigate = useNavigate()
  if (!matchId) return <Navigate to="/" replace />
  const stored = loadSession()
  const session: MatchSession = {
    matchId,
    roomCode: stored?.matchId === matchId ? stored.roomCode : null,
  }
  return <MatchRoute session={session} onLeave={() => {
    clearSession()
    navigate('/')
  }} />
}

function AccountNavigation({ account }: { account: CurrentAccount }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const current = useQuery({
    queryKey: currentMatchesQueryKey,
    queryFn: getCurrentMatches,
  })
  const activity = current.data?.activities?.[0]
  return (
    <div className="account-bar">
      <nav aria-label="Primary navigation">
        <Link to="/">Play</Link>
        <Link to="/history">History</Link>
        <Link to="/leaderboard">Leaderboard</Link>
        <Link to="/profile">Profile</Link>
        <Link to="/settings">Settings</Link>
        {activity && (
          <Link className="current-game-indicator" to={activity.resumeRoute}>
            {activityLabel(activity)}
          </Link>
        )}
      </nav>
      <span>{account.displayName}</span>
      {!account.emailVerified && <Link to="/verification-status">Verify email</Link>}
      <button type="button" onClick={() => void logout().then(() => {
        clearMediaSession()
        clearSession()
        queryClient.clear()
        navigate('/login', { replace: true })
      })}>Sign out</button>
    </div>
  )
}

function AuthenticatedPage({ children }: { children: ReactNode }) {
  const account = useQuery({ queryKey: ['account'], queryFn: getCurrentAccount, retry: false })
  if (account.isPending) return <main className="app-shell"><p role="status">Loading account…</p></main>
  if (!account.data) return <Navigate to="/login" replace />
  return (
    <>
      <AccountNavigation account={account.data} />
      {children}
    </>
  )
}

function ApplicationRoutes() {
  const [sessionExpired, setSessionExpired] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  useEffect(() => {
    const expired = () => {
      clearMediaSession()
      clearSession()
      queryClient.clear()
      setSessionExpired(true)
      navigate('/login', { replace: true })
    }
    window.addEventListener('gotg:session-expired', expired)
    return () => window.removeEventListener('gotg:session-expired', expired)
  }, [navigate, queryClient])
  return (
    <>
      {sessionExpired && (
        <div className="session-expired" role="alert">
          Your session expired. Sign in again to continue.
          <button type="button" onClick={() => setSessionExpired(false)}>Dismiss</button>
        </div>
      )}
      <Routes>
        <Route path="/welcome" element={<LandingScreen />} />
        <Route path="/login" element={<LoginScreen />} />
        <Route path="/register" element={<RegistrationScreen />} />
        <Route path="/forgot-password" element={<ForgotPasswordScreen />} />
        <Route path="/reset-password" element={<ResetPasswordScreen />} />
        <Route path="/verify-email" element={<VerifyEmailScreen />} />
        <Route path="/verification-status" element={<VerificationStatusScreen />} />
        <Route path="/profile" element={<AuthenticatedPage><MyProfileScreen /></AuthenticatedPage>} />
        <Route path="/settings" element={<AuthenticatedPage><AccountSettingsScreen /></AuthenticatedPage>} />
        <Route path="/players/:username" element={<AuthenticatedPage><PublicProfileScreen /></AuthenticatedPage>} />
        <Route path="/history" element={<AuthenticatedPage><MatchHistoryListScreen /></AuthenticatedPage>} />
        <Route path="/matches/:matchId/history" element={<AuthenticatedPage><MatchHistoryScreen /></AuthenticatedPage>} />
        <Route path="/matches/:matchId" element={<AuthenticatedPage><AuthoritativeMatchRoute /></AuthenticatedPage>} />
        <Route path="/leaderboard" element={<AuthenticatedPage><LeaderboardScreen /></AuthenticatedPage>} />
        <Route path="/" element={<AuthenticatedPage><MatchApplication /></AuthenticatedPage>} />
        <Route path="*" element={<Navigate to="/welcome" replace />} />
      </Routes>
    </>
  )
}

export default function App() {
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  }))
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ApplicationRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}
