import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMatch, joinMatch, makeMove, MatchApiError } from './client'
import { commandResponse, jsonResponse, matchView } from '../test-fixtures'

describe('development match API client', () => {
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

    expect(fetchMock.mock.calls[0][0]).toBe('/api/dev/matches')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/dev/matches/join')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toMatchObject({
      roomCode: 'ABC234',
      expectedVersion: 1,
    })
  })

  it('sends a fresh commandId, expectedVersion, and canonical move coordinates', async () => {
    const view = matchView({ phase: 'ACTIVE', version: 12 })
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(commandResponse(view)))
    vi.stubGlobal('fetch', fetchMock)

    await makeMove(view.matchId, view.requestingPlayerId, 11,
      { row: 5, column: 2 }, { row: 4, column: 2 })
    const payload = JSON.parse(fetchMock.mock.calls[0][1].body)

    expect(payload.commandId).toMatch(/^[0-9a-f-]{36}$/)
    expect(payload.expectedVersion).toBe(11)
    expect(payload.source).toEqual({ row: 5, column: 2 })
    expect(payload.destination).toEqual({ row: 4, column: 2 })
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
})
