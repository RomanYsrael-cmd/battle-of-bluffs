import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { PlayerMatchView } from '../../api/types'
import type { MatchConnectionState } from '../../realtime/matchSocket'
import { MatchHeader } from '../match/MatchHeader'
import { MatchTimers } from '../match/MatchTimers'

interface DesktopMatchShellProps {
  view: PlayerMatchView
  connectionState: MatchConnectionState
  connectionLabel: string
  syncMessage: string
  onLeave: () => void
  captures?: ReactNode
  children: ReactNode
  media?: ReactNode
  chat?: ReactNode
  history?: ReactNode
}

type MobileMatchTab = 'board' | 'pieces' | 'camera' | 'chat'
type MobileCommunicationTab = 'chat' | 'info'

interface MobileMatchNavigation {
  isMobile: boolean
  activeTab: MobileMatchTab
  communicationTab: MobileCommunicationTab
  selectTab: (tab: MobileMatchTab) => void
}

const MobileMatchNavigationContext = createContext<MobileMatchNavigation>({
  isMobile: false,
  activeTab: 'board',
  communicationTab: 'chat',
  selectTab: () => undefined,
})

export const useMobileMatchNavigation = () => useContext(MobileMatchNavigationContext)

export function DesktopMatchShell({
  view,
  connectionState,
  connectionLabel,
  syncMessage,
  onLeave,
  captures,
  children,
  media,
  chat,
  history,
}: DesktopMatchShellProps) {
  const [activeTab, setActiveTab] = useState<MobileMatchTab>('board')
  const [communicationTab, setCommunicationTab] = useState<MobileCommunicationTab>('chat')
  const [isMobile, setIsMobile] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia?.('(max-width: 1023px)').matches === true)
  useEffect(() => {
    const query = window.matchMedia?.('(max-width: 1023px)')
    if (!query) return
    const update = () => setIsMobile(query.matches)
    update()
    query.addEventListener?.('change', update)
    return () => query.removeEventListener?.('change', update)
  }, [])
  const tabbed = view.phase !== 'FORMATION'
  const navigation = useMemo(() => ({
    isMobile,
    activeTab,
    communicationTab,
    selectTab: setActiveTab,
  }), [activeTab, communicationTab, isMobile])
  const turn = view.phase === 'FORMATION'
    ? 'Deploy your formation'
    : view.phase === 'TERMINAL'
      ? 'Match complete'
      : view.currentPlayer === view.requestingSide ? 'You to move' : 'Opponent to move'

  return (
    <MobileMatchNavigationContext.Provider value={navigation}>
      <main className={`app-shell match-app-shell match-app-shell--${view.phase.toLowerCase()}${tabbed ? ` match-app-shell--mobile-tabbed mobile-match-view--${activeTab}` : ''}`}>
      <MatchHeader view={view} onLeave={onLeave} />
      <section className="match-status-bar" aria-label="Match status and timers">
        <div className="match-status-summary">
          <span className="match-status-turn"><span aria-hidden="true">●</span>{turn}</span>
          <span className="match-status-meta">
            <span>{view.mode === 'RANKED' ? 'Ranked' : 'Casual'}</span>
            <span aria-hidden="true">•</span>
            <span>Side {view.requestingSide === 'PLAYER_ONE' ? '1' : '2'}</span>
            <span aria-hidden="true">•</span>
            <span>{view.phase}</span>
          </span>
        </div>
        <MatchTimers view={view} />
        <div className={`connection-state connection-state--${connectionState.toLowerCase()}`} role="status">
          <span className="connection-state__dot" aria-hidden="true" />
          <span className="connection-state__full-label">{connectionLabel}</span>
          <span className="connection-state__short-label">
            {connectionState === 'SYNCHRONIZED' ? 'Live' : connectionState === 'CONNECTING' ? 'Connecting' : 'Reconnecting'}
          </span>
        </div>
      </section>
      {syncMessage && <p className="sync-message match-sync-toast" role="status">{syncMessage}</p>}
      <div className={`desktop-match-workspace${captures ? ' desktop-match-workspace--captures' : ''}`}>
        {captures && (
          <div className="mobile-tab-panel mobile-tab-panel--pieces" hidden={isMobile && tabbed && activeTab !== 'pieces'}>
            {captures}
          </div>
        )}
        <div className="match-primary-stage mobile-tab-panel mobile-tab-panel--board"
          hidden={isMobile && tabbed && activeTab !== 'board'}>{children}</div>
        {(media || chat || history) && (
          <aside className="desktop-utility-dock" aria-label="Match communication and history">
            {media && (
              <div className="mobile-tab-panel mobile-tab-panel--camera" hidden={isMobile && tabbed && activeTab !== 'board' && activeTab !== 'camera'}>
                {media}
              </div>
            )}
            {(chat || history) && (
              <div className="mobile-tab-panel mobile-tab-panel--communication" hidden={isMobile && tabbed && activeTab !== 'chat'}>
                <nav className="mobile-communication-tabs" aria-label="Communication sections">
                  <button type="button" className={communicationTab === 'chat' ? 'active' : ''}
                    aria-pressed={communicationTab === 'chat'} onClick={() => setCommunicationTab('chat')}>Chat</button>
                  <button type="button" className={communicationTab === 'info' ? 'active' : ''}
                    aria-pressed={communicationTab === 'info'} onClick={() => setCommunicationTab('info')}>Game info</button>
                </nav>
                <div className="mobile-communication-content" hidden={isMobile && communicationTab !== 'chat'}>{chat}</div>
                <div className="mobile-game-info-content" hidden={isMobile && communicationTab !== 'info'}>{history}</div>
              </div>
            )}
          </aside>
        )}
      </div>
      {tabbed && (
        <nav className="mobile-match-tabs" aria-label="Match views">
          <button type="button" className={activeTab === 'board' ? 'active' : ''} aria-pressed={activeTab === 'board'}
            onClick={() => setActiveTab('board')}><span aria-hidden="true">▦</span>Board</button>
          <button type="button" className={activeTab === 'pieces' ? 'active' : ''} aria-pressed={activeTab === 'pieces'}
            onClick={() => setActiveTab('pieces')} disabled={!captures}><span aria-hidden="true">⚑</span>Pieces</button>
          <button type="button" className={activeTab === 'camera' ? 'active' : ''} aria-pressed={activeTab === 'camera'}
            onClick={() => setActiveTab('camera')} disabled={!media}><span aria-hidden="true">▣</span>Camera</button>
          <button type="button" className={activeTab === 'chat' ? 'active' : ''} aria-pressed={activeTab === 'chat'}
            onClick={() => setActiveTab('chat')} disabled={!chat}><span aria-hidden="true">◯</span>Chat</button>
        </nav>
      )}
      </main>
    </MobileMatchNavigationContext.Provider>
  )
}
