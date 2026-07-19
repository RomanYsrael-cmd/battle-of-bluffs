import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cancelMatch,
  createMatch,
  getCurrentMatches,
  joinMatch,
  leaveMatch,
  makeMove,
  MatchApiError,
} from './client'
import { commandResponse, jsonResponse, matchView } from '../test-fixtures'

describe('authenticated match API client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates a match and joins using only a room code', async () => {
    const created = commandResponse(matchView())
    const joined = commandResponse(matchView({ requestingSide: 'PLAYER_TWO' }))
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(created, 201))
      .mockResolvedValueOnce(jsonResponse(joined))
    vi.stubGlobal('fetch', fetchMock)

    await expect(createMatch()).resolves.toEqual(created)
    await expect(joinMatch('ABC234')).resolves.toEqual(joined)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/matches')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/matches/join')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toMatchObject({
      roomCode: 'ABC234',
    })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).not.toHaveProperty('expectedVersion')
  })

  it('sends a fresh commandId, expectedVersion, and canonical move coordinates', async () => {
    const view = matchView({ phase: 'ACTIVE', version: 12 })
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(commandResponse(view)))
    vi.stubGlobal('fetch', fetchMock)

    await makeMove(view.matchId, 11,
      { row: 5, column: 2 }, { row: 4, column: 2 })
    const payload = JSON.parse(fetchMock.mock.calls[0][1].body)

    expect(payload.commandId).toMatch(/^[0-9a-f-]{36}$/)
    expect(payload.expectedVersion).toBe(11)
    expect(payload.source).toEqual({ row: 5, column: 2 })
    expect(payload.destination).toEqual({ row: 4, column: 2 })
    expect(payload).not.toHaveProperty('playerId')
  })

  it('maps structured errors without exposing raw backend messages', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      code: 'MATCH_FULL',
      message: 'internal detail that should not render',
      timestamp: 'now',
    }, 409)))

    const error = await joinMatch('FULL99').catch((caught) => caught)
    expect(error).toBeInstanceOf(MatchApiError)
    expect(error.message).toBe('That room already has two players.')
    expect(error.message).not.toContain('internal detail')
  })

  it('discovers current matches and sends versioned idempotent lifecycle commands', async () => {
    const current = { activities: [], multipleOpenMatches: false }
    const cancelled = {
      commandId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      version: 5,
      matchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      action: 'ROOM_CANCELLED',
    }
    const left = { ...cancelled, action: 'LOBBY_LEFT' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(current))
      .mockResolvedValueOnce(jsonResponse(cancelled))
      .mockResolvedValueOnce(jsonResponse(left))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getCurrentMatches()).resolves.toEqual(current)
    await cancelMatch(cancelled.matchId, 4)
    await leaveMatch(cancelled.matchId, 5)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/matches/current')
    for (const call of fetchMock.mock.calls.slice(1)) {
      const payload = JSON.parse(call[1].body)
      expect(payload.commandId).toMatch(/^[0-9a-f-]{36}$/)
      expect(payload.expectedVersion).toBeGreaterThanOrEqual(4)
    }
  })

  it('preserves safe recovery metadata on OPEN_MATCH_EXISTS errors', async () => {
    const context = { blockingMatches: [{
      matchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      version: 3,
      resumeRoute: '/matches/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    }] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      code: 'OPEN_MATCH_EXISTS',
      message: 'internal wording',
      timestamp: 'now',
      context,
    }, 409)))

    const caught = await createMatch().then(() => null, (error) => error)
    expect(caught).toBeInstanceOf(MatchApiError)
    const error = caught as MatchApiError

    expect(error.message).toBe('You already have a game in progress.')
    expect(error.context).toEqual(context)
  })
})
