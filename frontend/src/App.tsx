import { useEffect, useState } from 'react'
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { getPlayerView } from './api/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import {
  ForgotPasswordScreen,
  LoginScreen,
  RegistrationScreen,
  ResetPasswordScreen,
  VerificationStatusScreen,
  VerifyEmailScreen,
} from './auth/AuthScreens'
import type { CommandResponse } from './api/types'
import { ApiErrorNotice } from './components/ApiErrorNotice'
import { FormationScreen } from './features/formation/FormationScreen'
import { HomeScreen } from './features/home/HomeScreen'
import { ActiveMatchScreen } from './features/match/ActiveMatchScreen'
import { MatchHeader } from './features/match/MatchHeader'
import {
  clearSession,
  loadSession,
  saveSession,
  type MatchSession,
} from './session/session'

const matchQueryKey = (session: MatchSession) => ['match', session.matchId, session.playerId]

function MatchRoute({ session, onLeave }: { session: MatchSession; onLeave: () => void }) {
  const queryClient = useQueryClient()
  const [syncMessage, setSyncMessage] = useState('')
  const query = useQuery({
    queryKey: matchQueryKey(session),
    queryFn: () => getPlayerView(session.matchId, session.playerId),
    retry: false,
    refetchInterval: (currentQuery) =>
      currentQuery.state.data?.phase === 'TERMINAL' ? false : 1_500,
  })

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
    </main>
  )
}

function MatchApplication() {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<MatchSession | null>(loadSession)

  const enterMatch = (response: CommandResponse) => {
    const nextSession = {
      playerId: response.playerId,
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
        <Route path="/login" element={<LoginScreen />} />
        <Route path="/register" element={<RegistrationScreen />} />
        <Route path="/forgot-password" element={<ForgotPasswordScreen />} />
        <Route path="/reset-password" element={<ResetPasswordScreen />} />
        <Route path="/verify-email" element={<VerifyEmailScreen />} />
        <Route path="/verification-status" element={<VerificationStatusScreen />} />
        <Route path="*" element={<MatchApplication />} />
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
