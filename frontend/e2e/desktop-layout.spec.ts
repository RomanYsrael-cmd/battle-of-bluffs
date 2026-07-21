import { expect, test, type Page } from '@playwright/test'

const matchId = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
const ranks = [
  'FIVE_STAR_GENERAL', 'FOUR_STAR_GENERAL', 'THREE_STAR_GENERAL', 'TWO_STAR_GENERAL',
  'ONE_STAR_GENERAL', 'COLONEL', 'LIEUTENANT_COLONEL', 'MAJOR', 'CAPTAIN',
  'FIRST_LIEUTENANT', 'SECOND_LIEUTENANT', 'SERGEANT',
  'PRIVATE', 'PRIVATE', 'PRIVATE', 'PRIVATE', 'PRIVATE', 'PRIVATE', 'SPY', 'SPY', 'FLAG',
]

const baseView = (phase: 'FORMATION' | 'ACTIVE') => ({
  matchId, roomCode: 'DESK42', version: 9, liveSequence: 9, phase,
  mode: 'CASUAL', timerMode: 'STANDARD_15_PLUS_5', requestingPlayerId: 'desktop-account',
  requestingSide: 'PLAYER_ONE', playerOneOccupied: true, playerTwoOccupied: true,
  playerOneLocked: phase === 'ACTIVE', playerTwoLocked: phase === 'ACTIVE',
  currentPlayer: phase === 'ACTIVE' ? 'PLAYER_ONE' : null,
  ownPieces: phase === 'FORMATION' ? [] : ranks.map((rank, index) => ({
    id: `own-${index}`, rank, position: { row: Math.floor(index / 9), column: index % 9 }, alive: true,
  })),
  opponentPieces: phase === 'FORMATION' ? [] : ranks.slice(0, 20).map((_rank, index) => ({
    id: `opponent-${index}`, position: { row: 5 + Math.floor(index / 9), column: index % 9 },
  })),
  events: [], pendingFlagChallenge: null, terminalResult: null, postMatchPieces: [],
  timer: {
    playerOneRemainingMillis: 894_000, playerTwoRemainingMillis: 900_000,
    formationDeadline: phase === 'FORMATION' ? '2099-07-21T10:10:00Z' : null,
    activeTurnDeadline: null, incrementMillis: 5_000, serverTimestamp: '2099-07-21T10:00:00Z',
  },
  presence: {
    playerOneConnected: true, playerTwoConnected: true,
    playerOneDisconnectedSince: null, playerTwoDisconnectedSince: null,
    playerOneCumulativeDisconnectedMillis: 0, playerTwoCumulativeDisconnectedMillis: 0,
    disconnectGraceMillis: 60_000, rankedCumulativeAllowanceMillis: 120_000,
    serverTimestamp: '2099-07-21T10:00:00Z',
  },
})

async function openMockMatch(page: Page, phase: 'FORMATION' | 'ACTIVE') {
  const view = baseView(phase)
  const browserErrors: string[] = []
  page.on('pageerror', (error) => browserErrors.push(error.message))
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    if (pathname === '/api/auth/me') return route.fulfill({ json: {
      id: 'desktop-account', username: 'desktop', displayName: 'Desktop General',
      status: 'ACTIVE', emailVerified: true,
    } })
    if (pathname === '/api/matches/current') return route.fulfill({ json: {
      activities: [{
        matchId, mode: 'CASUAL', phase, version: 9, roomCode: 'DESK42', side: 'PLAYER_ONE',
        opponentPresent: true, ownFormationSubmitted: false, ownLocked: phase === 'ACTIVE',
        opponentLocked: phase === 'ACTIVE', currentPlayer: view.currentPlayer,
        createdAt: null, updatedAt: null, canResume: true, canCancel: false, canLeave: false,
        resumeRoute: `/matches/${matchId}`,
      }], multipleOpenMatches: false,
    } })
    if (pathname === `/api/matches/${matchId}`) return route.fulfill({ json: view })
    if (pathname.endsWith('/moderation')) return route.fulfill({ json: {
      opponentDisplayName: 'Opponent', blockedByYou: false,
    } })
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: 'Mock route unavailable' } })
  })
  await page.addInitScript(({ matchId: id }) => {
    sessionStorage.setItem('battle-of-bluffs.match-session', JSON.stringify({ matchId: id, roomCode: 'DESK42' }))
  }, { matchId })
  await page.goto(`/matches/${matchId}`)
  await expect(page.locator('.match-app-shell'), browserErrors.join('\n')).toBeVisible()
}

async function expectInsideViewport(page: Page, selector: string) {
  const box = await page.locator(selector).first().boundingBox()
  expect(box, `${selector} should have a bounding box`).not.toBeNull()
  const viewport = page.viewportSize()!
  expect(box!.x).toBeGreaterThanOrEqual(-1)
  expect(box!.y).toBeGreaterThanOrEqual(-1)
  expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width + 1)
  expect(box!.y + box!.height, `${selector} should end inside the viewport`).toBeLessThanOrEqual(viewport.height + 1)
}

for (const viewport of [
  { width: 1280, height: 720 },
  { width: 1366, height: 768 },
  { width: 1440, height: 900 },
  { width: 1920, height: 1080 },
]) {
  test(`active workspace fits ${viewport.width}x${viewport.height} without document scrolling`, async ({ page }) => {
    await page.setViewportSize(viewport)
    await openMockMatch(page, 'ACTIVE')
    await expect(page.getByRole('button', { name: 'Open chat' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Open media' })).toBeVisible()
    expect(await page.evaluate(() => ({
      html: document.documentElement.scrollHeight - document.documentElement.clientHeight,
      body: document.body.scrollHeight - document.body.clientHeight,
    }))).toEqual({ html: 0, body: 0 })
    for (const selector of ['.match-board', '.play-clock', '.captures-rail', '.desktop-utility-dock', '.actions']) {
      await expectInsideViewport(page, selector)
    }
    const board = await page.locator('.match-board').boundingBox()
    expect(board!.width / board!.height).toBeCloseTo(9 / 8, 1)
  })
}

test('formation board, 21-piece tray, actions and dock fit 1366x768', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openMockMatch(page, 'FORMATION')
  await expect(page.locator('.tray-grid .piece')).toHaveCount(21)
  for (const name of ['Submit formation', 'Lock formation', 'Reset formation', 'Open chat', 'Open media']) {
    await expect(page.getByRole('button', { name })).toBeInViewport()
  }
  await expectInsideViewport(page, '.board')
  expect(await page.evaluate(() => document.documentElement.scrollHeight - document.documentElement.clientHeight)).toBe(0)
})

test('stacked fallback activates immediately below the 1024px desktop breakpoint', async ({ page }) => {
  await page.setViewportSize({ width: 1023, height: 900 })
  await openMockMatch(page, 'ACTIVE')
  expect(await page.evaluate(() => getComputedStyle(document.body).overflow)).not.toBe('hidden')
  expect(await page.locator('.desktop-match-workspace').evaluate((node) => getComputedStyle(node).display)).toBe('grid')
})

test('session expiration uses a fixed overlay instead of shifting workspace layout', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openMockMatch(page, 'ACTIVE')
  await page.evaluate(() => window.dispatchEvent(new Event('gotg:session-expired')))
  const notice = page.locator('.session-expired')
  await expect(notice).toBeVisible()
  expect(await notice.evaluate((node) => getComputedStyle(node).position)).toBe('fixed')
})

test('expanded media and messenger chat share the dock without moving the board', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openMockMatch(page, 'ACTIVE')
  const boardBefore = await page.locator('.match-board').boundingBox()
  await page.getByRole('button', { name: 'Open media' }).click()
  await page.getByRole('button', { name: 'Open chat' }).click()
  await expect(page.getByRole('button', { name: 'Enable audio/video' })).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Message' })).toBeInViewport()
  await expectInsideViewport(page, '.match-board')
  const boardAfter = await page.locator('.match-board').boundingBox()
  expect(boardAfter).toEqual(boardBefore)
  await page.getByRole('button', { name: 'Collapse' }).last().click()
  await expect(page.getByText('Your turn', { exact: true })).toBeVisible()
})
