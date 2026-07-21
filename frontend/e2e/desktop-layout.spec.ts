import { expect, test, type Page } from '@playwright/test'

const matchId = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
const ranks = [
  'FIVE_STAR_GENERAL', 'FOUR_STAR_GENERAL', 'THREE_STAR_GENERAL', 'TWO_STAR_GENERAL',
  'ONE_STAR_GENERAL', 'COLONEL', 'LIEUTENANT_COLONEL', 'MAJOR', 'CAPTAIN',
  'FIRST_LIEUTENANT', 'SECOND_LIEUTENANT', 'SERGEANT',
  'PRIVATE', 'PRIVATE', 'PRIVATE', 'PRIVATE', 'PRIVATE', 'PRIVATE', 'SPY', 'SPY', 'FLAG',
]

const baseView = (phase: 'FORMATION' | 'ACTIVE' | 'TERMINAL') => ({
  matchId, roomCode: 'DESK42', version: 9, liveSequence: 9, phase,
  mode: 'CASUAL', timerMode: 'STANDARD_15_PLUS_5', requestingPlayerId: 'desktop-account',
  requestingSide: 'PLAYER_ONE', playerOneOccupied: true, playerTwoOccupied: true,
  playerOneLocked: phase !== 'FORMATION', playerTwoLocked: phase !== 'FORMATION',
  currentPlayer: phase === 'ACTIVE' ? 'PLAYER_ONE' : null,
  ownPieces: phase === 'FORMATION' ? [] : ranks.map((rank, index) => ({
    id: `own-${index}`, rank, position: { row: Math.floor(index / 9), column: index % 9 }, alive: true,
  })),
  opponentPieces: phase === 'FORMATION' ? [] : ranks.slice(0, 20).map((_rank, index) => ({
    id: `opponent-${index}`, position: { row: 5 + Math.floor(index / 9), column: index % 9 },
  })),
  events: phase === 'ACTIVE' ? Array.from({ length: 5 }, (_, index) => ({
    sequence: index + 1, type: 'MOVE_APPLIED', actor: index % 2 ? 'PLAYER_TWO' : 'PLAYER_ONE',
    source: { row: 2, column: index }, destination: { row: 3, column: index },
    removedPieceIds: [], ownBattleOutcome: null, terminalResult: null,
  })) : [], pendingFlagChallenge: null,
  terminalResult: phase === 'TERMINAL' ? { winner: 'PLAYER_ONE', reason: 'FLAG_CAPTURE' } : null,
  postMatchPieces: [],
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

async function openMockMatch(page: Page, phase: 'FORMATION' | 'ACTIVE' | 'TERMINAL') {
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
        opponentPresent: true, ownFormationSubmitted: false, ownLocked: phase !== 'FORMATION',
        opponentLocked: phase !== 'FORMATION', currentPlayer: view.currentPlayer,
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
    await expect(page.getByRole('button', { name: 'Set up media', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Turn microphone on' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Turn camera on' })).toBeVisible()
    await expect(page.getByRole('button', { name: /Open media settings/ })).toBeVisible()
    await expect(page.locator('.board-cell__coordinate')).toHaveCount(0)
    await expect(page.locator('.board-rank-labels span')).toHaveCount(8)
    await expect(page.locator('.board-rank-labels span').first()).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Your lost pieces' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Captured by you' })).toHaveCount(0)
    expect(await page.evaluate(() => ({
      html: document.documentElement.scrollHeight - document.documentElement.clientHeight,
      body: document.body.scrollHeight - document.body.clientHeight,
    }))).toEqual({ html: 0, body: 0 })
    for (const selector of ['.match-board', '.play-clock', '.captures-rail', '.floating-camera-panel', '.chat-panel', '.actions']) {
      await expectInsideViewport(page, selector)
    }
    const board = await page.locator('.match-board').boundingBox()
    expect(board!.width / board!.height).toBeCloseTo(9 / 8, 1)
    expect(board!.width).toBeGreaterThanOrEqual(viewport.height * .8)
    const camera = await page.locator('.floating-camera-panel').boundingBox()
    const chat = await page.locator('.chat-panel').boundingBox()
    const actions = await page.locator('.actions').boundingBox()
    const dashboardButton = await page.getByRole('button', { name: 'Back to dashboard' }).boundingBox()
    const resignButton = await page.getByRole('button', { name: 'Resign match' }).boundingBox()
    expect(board!.x + board!.width).toBeLessThanOrEqual(Math.min(camera!.x, chat!.x) + 1)
    expect(actions!.x + actions!.width).toBeLessThanOrEqual(board!.x + 1)
    expect(dashboardButton!.y + dashboardButton!.height).toBeLessThanOrEqual(resignButton!.y)
    await expect(page.locator('.account-bar')).toBeHidden()
    await expect(page.locator('.match-header')).toBeHidden()
  })
}

test('terminal status and the complete board fit 1366x768', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openMockMatch(page, 'TERMINAL')

  await expect(page.getByText('Low time')).toHaveCount(0)
  await expectInsideViewport(page, '.match-status-bar')
  await expectInsideViewport(page, '.match-board')
  const status = await page.locator('.match-status-bar').boundingBox()
  expect(status!.height).toBeLessThanOrEqual(90)
  await expect(page.locator('.board-rank-labels span').first()).toBeVisible()
  await expect(page.getByRole('button', { name: 'Back to dashboard' })).toBeInViewport()
  await expect(page.getByRole('button', { name: 'Resign match' })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollHeight - document.documentElement.clientHeight)).toBe(0)
})

test('formation board, 21-piece tray, actions and dock fit 1366x768', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openMockMatch(page, 'FORMATION')
  await expect(page.locator('.tray-grid .piece')).toHaveCount(21)
  const boardBefore = await page.locator('.board').boundingBox()
  const trayPanel = page.locator('.floating-formation-tray')
  expect(await trayPanel.evaluate((node) => ({
    position: getComputedStyle(node).position,
    resize: getComputedStyle(node).resize,
  }))).toEqual({ position: 'fixed', resize: 'both' })

  const fiveStar = page.getByRole('button', { name: 'Five-Star General' })
  const firstCell = page.getByRole('gridcell', { name: /row 0, column 0, formation cell/i })
  await fiveStar.dragTo(firstCell)
  await expect(page.locator('.tray-grid .piece')).toHaveCount(20)
  await expect(firstCell.getByRole('button', { name: 'Five-Star General' })).toBeVisible()
  await firstCell.getByRole('button', { name: 'Five-Star General' }).dragTo(trayPanel)
  await expect(page.locator('.tray-grid .piece')).toHaveCount(21)

  const trayBeforeDrag = await trayPanel.boundingBox()
  const trayHeading = await trayPanel.locator('.formation-tray__heading').boundingBox()
  await page.mouse.move(trayHeading!.x + 70, trayHeading!.y + 20)
  await page.mouse.down()
  await page.mouse.move(trayHeading!.x + 150, trayHeading!.y + 65, { steps: 5 })
  await page.mouse.up()
  const trayAfterDrag = await trayPanel.boundingBox()
  expect(trayAfterDrag!.x).toBeGreaterThan(trayBeforeDrag!.x + 50)
  expect(trayAfterDrag!.y).toBeGreaterThan(trayBeforeDrag!.y + 25)

  await page.mouse.move(trayAfterDrag!.x + trayAfterDrag!.width - 2, trayAfterDrag!.y + trayAfterDrag!.height - 2)
  await page.mouse.down()
  await page.mouse.move(trayAfterDrag!.x + trayAfterDrag!.width + 40, trayAfterDrag!.y + trayAfterDrag!.height + 25, { steps: 5 })
  await page.mouse.up()
  const trayAfterResize = await trayPanel.boundingBox()
  expect(trayAfterResize!.width).toBeGreaterThan(trayAfterDrag!.width + 20)
  expect(trayAfterResize!.height).toBeGreaterThan(trayAfterDrag!.height + 10)
  for (const name of ['Submit formation', 'Lock formation', 'Reset formation', 'Open chat', 'Set up media']) {
    await expect(page.getByRole('button', { name })).toBeInViewport()
  }
  await expectInsideViewport(page, '.board')
  const board = await page.locator('.board').boundingBox()
  const actions = await page.locator('.formation-workspace .actions').boundingBox()
  const deadline = await page.locator('.setup-clock').boundingBox()
  const connection = await page.locator('.connection-state').boundingBox()
  expect(actions!.x).toBeGreaterThanOrEqual(deadline!.x + deadline!.width)
  expect(actions!.x + actions!.width).toBeLessThanOrEqual(connection!.x + 1)
  expect(Math.abs((actions!.y + actions!.height / 2) - (deadline!.y + deadline!.height / 2)))
    .toBeLessThanOrEqual(8)
  expect(board).toEqual(boardBefore)
  expect(await page.evaluate(() => document.documentElement.scrollHeight - document.documentElement.clientHeight)).toBe(0)
})

test('formation board keeps its desktop size when browser chrome reduces viewport height', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openMockMatch(page, 'FORMATION')
  const fullHeightBoard = await page.locator('.board').boundingBox()

  await page.setViewportSize({ width: 1366, height: 640 })
  const shortHeightBoard = await page.locator('.board').boundingBox()

  expect(shortHeightBoard!.width).toBeCloseTo(fullHeightBoard!.width, 0)
  expect(shortHeightBoard!.height).toBeCloseTo(fullHeightBoard!.height, 0)
  expect(await page.evaluate(() => document.documentElement.scrollHeight - document.documentElement.clientHeight))
    .toBeGreaterThan(0)
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

test('floating resizable camera and messenger chat never resize the board', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 768 })
  await openMockMatch(page, 'ACTIVE')
  const boardBefore = await page.locator('.match-board').boundingBox()
  const camera = page.locator('.floating-camera-panel')
  expect(await camera.evaluate((node) => ({
    position: getComputedStyle(node).position,
    resize: getComputedStyle(node).resize,
  }))).toEqual({ position: 'fixed', resize: 'both' })

  const cameraBeforeDrag = await camera.boundingBox()
  const cameraHeading = await camera.locator('.media-panel__heading').boundingBox()
  await page.mouse.move(cameraHeading!.x + 80, cameraHeading!.y + 20)
  await page.mouse.down()
  await page.mouse.move(cameraHeading!.x - 120, cameraHeading!.y + 90, { steps: 5 })
  await page.mouse.up()
  const cameraAfterDrag = await camera.boundingBox()
  expect(cameraAfterDrag!.x).toBeLessThan(cameraBeforeDrag!.x - 100)
  expect(cameraAfterDrag!.y).toBeGreaterThan(cameraBeforeDrag!.y + 40)

  await page.mouse.move(
    cameraAfterDrag!.x + cameraAfterDrag!.width - 2,
    cameraAfterDrag!.y + cameraAfterDrag!.height - 2,
  )
  await page.mouse.down()
  await page.mouse.move(
    cameraAfterDrag!.x + cameraAfterDrag!.width + 58,
    cameraAfterDrag!.y + cameraAfterDrag!.height + 38,
    { steps: 5 },
  )
  await page.mouse.up()
  const cameraAfterResize = await camera.boundingBox()
  expect(cameraAfterResize!.width).toBeGreaterThan(cameraAfterDrag!.width + 30)
  expect(cameraAfterResize!.height).toBeGreaterThan(cameraAfterDrag!.height + 15)

  await page.getByRole('button', { name: 'Set up media', exact: true }).click()
  await page.getByRole('button', { name: 'Open chat' }).click()
  await expect(page.getByRole('button', { name: 'Enable audio/video' })).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Message' })).toBeInViewport()
  await expect(page.getByRole('button', { name: 'Send' })).toBeInViewport()
  await expectInsideViewport(page, '.match-board')
  const boardAfter = await page.locator('.match-board').boundingBox()
  expect(boardAfter).toEqual(boardBefore)
  await page.getByRole('button', { name: 'Collapse' }).last().click()
  await expect(page.getByRole('button', { name: 'Type a message…' })).toBeVisible()
  await expect(page.locator('.match-status-turn')).toContainText('You to move')
})
