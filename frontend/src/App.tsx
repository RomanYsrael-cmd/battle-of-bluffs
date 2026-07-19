import { useEffect, useState, type ReactNode } from 'react'
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { getChatHistory, getPlayerView } from './api/client'
import { BrowserRouter, Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import {
  ForgotPasswordScreen,
  LoginScreen,
  RegistrationScreen,
  ResetPasswordScreen,
  VerificationStatusScreen,
  VerifyEmailScreen,
} from './auth/AuthScreens'
import { getCurrentAccount, logout, type CurrentAccount } from './auth/client'
import type { ChatError, ChatMessage, CommandResponse } from './api/types'
import { ApiErrorNotice } from './components/ApiErrorNotice'
import { FormationScreen } from './features/formation/FormationScreen'
import { MatchChatPanel } from './features/chat/MatchChatPanel'
import { HomeScreen } from './features/home/HomeScreen'
import { LandingScreen } from './features/home/LandingScreen'
import { ActiveMatchScreen } from './features/match/ActiveMatchScreen'
import { MatchHeader } from './features/match/MatchHeader'
import { MatchTimers } from './features/match/MatchTimers'
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

const matchQueryKey = (session: MatchSession) => ['match', session.matchId]

function MatchRoute({ session, onLeave }: { session: MatchSession; onLeave: () => void }) {
  const queryClient = useQueryClient()
  const [syncMessage, setSyncMessage] = useState('')
  const [connectionState, setConnectionState] = useState<MatchConnectionState>('CONNECTING')
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [chatError, setChatError] = useState<ChatError | null>(null)
  const [sendChat, setSendChat] = useState<(body: string) => boolean>(() => () => false)
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
  const synchronize = () => {
    setSyncMessage('The match changed. Synchronizing the latest state…')
    void query.refetch().finally(() => {
      window.setTimeout(() => setSyncMessage(''), 1_500)
    })
  }

  if (query.isPending) {
    return <main className="app-shell"><p role="status">Loading private match…</p></main>
  }
  if (query.error || !query.data) {
    return (
      <main className="app-shell">
        <ApiErrorNotice error={query.error} />
        <button type="button" className="button button--ghost" onClick={onLeave}>
          Leave local session
        </button>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <MatchHeader view={query.data} onLeave={onLeave} />
      <MatchTimers view={query.data} />
      <p className={`connection-state connection-state--${connectionState.toLowerCase()}`} role="status">
        {connectionLabel(connectionState)}
      </p>
      {syncMessage && <p className="sync-message" role="status">{syncMessage}</p>}
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
          onStale={synchronize}
          onLeave={onLeave}
        />
      )}
      {query.data.playerTwoOccupied && (
        <MatchChatPanel
          matchId={session.matchId}
          connected={connectionState === 'SYNCHRONIZED'}
          messages={chatMessages}
          error={chatError}
          onSend={sendChat}
        />
      )}
    </main>
  )
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
  const [session, setSession] = useState<MatchSession | null>(loadSession)

  const enterMatch = (response: CommandResponse) => {
    const nextSession = {
      matchId: response.matchId,
      roomCode: response.roomCode,
    }
    saveSession(nextSession)
    queryClient.setQueryData(matchQueryKey(nextSession), response.view)
    setSession(nextSession)
  }

  const leave = () => {
    clearSession()
    queryClient.clear()
    setSession(null)
  }

  return session
    ? <MatchRoute session={session} onLeave={leave} />
    : <HomeScreen onEnteredMatch={enterMatch} />
}

function AccountNavigation({ account }: { account: CurrentAccount }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  return (
    <div className="account-bar">
      <nav aria-label="Primary navigation">
        <Link to="/">Play</Link>
        <Link to="/history">History</Link>
        <Link to="/leaderboard">Leaderboard</Link>
        <Link to="/profile">Profile</Link>
        <Link to="/settings">Settings</Link>
      </nav>
      <span>{account.displayName}</span>
      {!account.emailVerified && <Link to="/verification-status">Verify email</Link>}
      <button type="button" onClick={() => void logout().then(() => {
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
  useEffect(() => {
    const expired = () => setSessionExpired(true)
    window.addEventListener('gotg:session-expired', expired)
    return () => window.removeEventListener('gotg:session-expired', expired)
  }, [])
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
