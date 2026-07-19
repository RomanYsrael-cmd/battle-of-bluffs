import type { EventView, PlayerSide } from '../../api/types'

const sideName = (side: PlayerSide | null): string => side === 'PLAYER_ONE'
  ? 'Player 1'
  : side === 'PLAYER_TWO' ? 'Player 2' : 'Server'

const position = (value: { row: number; column: number } | null): string =>
  value ? `(${value.row}, ${value.column})` : ''

export const describeEvent = (event: EventView): string => {
  switch (event.type) {
    case 'MATCH_CREATED': return 'Private room created.'
    case 'PLAYER_JOINED': return `${sideName(event.actor)} joined.`
    case 'FORMATION_LOCKED': return `${sideName(event.actor)} locked a formation.`
    case 'MATCH_STARTED': return `Match started. ${sideName(event.actor)} moves first.`
    case 'MOVE_APPLIED': return `${sideName(event.actor)} moved ${position(event.source)} → ${position(event.destination)}.`
    case 'BATTLE_RESOLVED': return `Battle at ${position(event.destination)}: ${(event.ownBattleOutcome ?? 'resolved').replaceAll('_', ' ').toLowerCase()}.`
    case 'FLAG_CHALLENGE_STARTED': return `Flag challenge opened at ${position(event.destination)}.`
    case 'PLAYER_RESIGNED': return `${sideName(event.actor)} resigned.`
    case 'PLAYER_LEFT': return `${sideName(event.actor)} left the lobby.`
    case 'ROOM_CANCELLED': return 'The private room was cancelled.'
    case 'MATCH_ENDED': return `Match ended: ${event.terminalResult?.reason.replaceAll('_', ' ').toLowerCase() ?? 'complete'}.`
  }
}

export function EventHistory({ events }: { events: EventView[] }) {
  return (
    <section className="history-panel" aria-labelledby="history-title">
      <p className="eyebrow">Accepted public events</p>
      <h2 id="history-title">Match history</h2>
      <ol className="event-list">
        {[...events].reverse().map((event) => (
          <li key={event.sequence}>
            <span>#{event.sequence}</span>
            {describeEvent(event)}
          </li>
        ))}
      </ol>
    </section>
  )
}
