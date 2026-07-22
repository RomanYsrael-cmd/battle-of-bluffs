import { useState } from 'react'
import type { EventView } from '../../api/types'
import { EventHistory } from '../match/EventHistory'
import { useMobileMatchNavigation } from './DesktopMatchShell'

export function MatchHistoryDrawer({ accountId, matchId, events }: {
  accountId: string
  matchId: string
  events: EventView[]
}) {
  const mobileNavigation = useMobileMatchNavigation()
  const key = `gotg:history-panel:${accountId}:${matchId}`
  const [open, setOpen] = useState(() => localStorage.getItem(key) === 'true')
  const expanded = open || (mobileNavigation.isMobile
    && mobileNavigation.activeTab === 'chat'
    && mobileNavigation.communicationTab === 'info')
  const toggle = () => setOpen((current) => {
    localStorage.setItem(key, String(!current))
    return !current
  })
  return (
    <section className={`history-drawer${expanded ? ' history-drawer--open' : ''}`} aria-label="Match history panel">
      <div className="desktop-panel-header">
        <div><span className="desktop-panel-icon" aria-hidden="true">↺</span><strong>History</strong></div>
        <button type="button" aria-expanded={expanded} onClick={toggle}>
          {expanded ? 'Collapse history' : `Open history (${events.length})`}
        </button>
      </div>
      {expanded && <EventHistory events={events} />}
    </section>
  )
}
