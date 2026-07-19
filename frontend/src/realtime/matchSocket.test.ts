import { describe, expect, it } from 'vitest'
import { matchView } from '../test-fixtures'
import { assessMatchUpdate, type MatchUpdateEnvelope } from './matchSocket'

function update(
  version: number,
  matchId = '00000000-0000-4000-8000-000000000001',
): MatchUpdateEnvelope {
  return {
    type: 'MATCH_VIEW_UPDATED',
    matchId,
    sequence: version,
    version,
    serverTimestamp: '2026-07-19T10:15:30Z',
    view: matchView({ matchId, version, liveSequence: version }),
  }
}

describe('match WebSocket sequencing', () => {
  it('applies the next authoritative version', () => {
    expect(assessMatchUpdate(update(3).matchId, 2, 2, update(3))).toBe('APPLY')
  })

  it('ignores duplicate or delayed delivery', () => {
    expect(assessMatchUpdate(update(3).matchId, 3, 3, update(3))).toBe('IGNORE')
    expect(assessMatchUpdate(update(3).matchId, 4, 4, update(3))).toBe('IGNORE')
  })

  it('requires a safe REST refetch after a sequence gap or malformed envelope', () => {
    expect(assessMatchUpdate(update(4).matchId, 2, 2, update(4))).toBe('REFETCH')
    expect(assessMatchUpdate(
      update(3).matchId,
      2,
      2,
      { ...update(3), sequence: 7 },
    )).toBe('REFETCH')
    expect(assessMatchUpdate(
      update(3).matchId,
      2,
      2,
      update(3, '00000000-0000-4000-8000-000000000099'),
    )).toBe('REFETCH')
  })

  it('applies timer synchronization without requiring a command-version change', () => {
    const timerSync = {
      ...update(6),
      version: 4,
      view: matchView({ matchId: update(6).matchId, version: 4, liveSequence: 6 }),
    }
    expect(assessMatchUpdate(timerSync.matchId, 5, 4, timerSync)).toBe('APPLY')
  })
})
