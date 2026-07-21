import { expect, test, type BrowserContext, type Page, type Response } from '@playwright/test'

const runId = process.env.E2E_RUN_ID
  ?? `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`
const password = 'Strategist!2026'
const mailpitUrl = process.env.MAILPIT_API_URL ?? 'http://127.0.0.1:8025'
const accounts = {
  first: {
    username: `alpha_${runId}`,
    email: `alpha-${runId}@example.test`,
    displayName: `Alpha ${runId}`,
  },
  second: {
    username: `bravo_${runId}`,
    email: `bravo-${runId}@example.test`,
    displayName: `Bravo ${runId}`,
  },
  outsider: {
    username: `charlie_${runId}`,
    email: `charlie-${runId}@example.test`,
    displayName: `Charlie ${runId}`,
  },
}
const firstState = 'test-results/first-auth.json'
const secondState = 'test-results/second-auth.json'
const outsiderState = 'test-results/outsider-auth.json'

test.describe.serial('complete local platform', () => {
  test('E2E 1 — accounts register, verify, log out and log back in independently', async ({ browser }) => {
    const first = await browser.newContext()
    const second = await browser.newContext()
    const outsider = await browser.newContext()
    try {
      await registerVerifyAndLogin(first, accounts.first)
      await registerVerifyAndLogin(second, accounts.second)
      await registerVerifyAndLogin(outsider, accounts.outsider)

      await logoutAndLogin(first, accounts.first.username)
      await logoutAndLogin(second, accounts.second.username)
      await first.storageState({ path: firstState })
      await second.storageState({ path: secondState })
      await outsider.storageState({ path: outsiderState })
    } finally {
      await first.close()
      await second.close()
      await outsider.close()
    }
  })

  test('E2E 2 — abandoned lobby recovery, cancellation, and guest replacement are durable', async ({ browser }) => {
    const first = await browser.newContext({ storageState: firstState })
    const second = await browser.newContext({ storageState: secondState })
    const outsider = await browser.newContext({ storageState: outsiderState })
    try {
      const firstPage = await first.newPage()
      const secondPage = await second.newPage()
      const outsiderPage = await outsider.newPage()

      await firstPage.goto('/')
      await firstPage.getByRole('button', { name: 'Create private match' }).click()
      const abandonedRoomCodeInput = firstPage.getByLabel('Room code')
      await expect(abandonedRoomCodeInput).toHaveValue(/^[A-Z2-9]{6}$/)
      const abandonedRoomCode = await abandonedRoomCodeInput.inputValue()
      await firstPage.goto('/history')
      await firstPage.goto('/')
      const currentGame = firstPage.locator('.current-games')
      await expect(currentGame).toContainText(abandonedRoomCode)
      await currentGame.getByRole('button', { name: 'Continue game' }).click()
      await expect(firstPage.getByLabel('Room code')).toHaveValue(abandonedRoomCode)
      await firstPage.getByRole('button', { name: 'Back to dashboard' }).click()
      firstPage.once('dialog', (dialog) => dialog.accept())
      await currentGame.getByRole('button', { name: 'Cancel room' }).click()
      await expect(currentGame).not.toBeVisible()

      await firstPage.getByRole('button', { name: 'Find ranked match' }).click()
      await expect(firstPage.getByText('Searching for an opponent…')).toBeVisible()
      await firstPage.getByRole('button', { name: 'Cancel search' }).click()

      await firstPage.getByRole('button', { name: 'Create private match' }).click()
      const reusableRoomCodeInput = firstPage.getByLabel('Room code')
      await expect(reusableRoomCodeInput).toHaveValue(/^[A-Z2-9]{6}$/)
      const reusableRoomCode = await reusableRoomCodeInput.inputValue()
      await secondPage.goto('/')
      await secondPage.getByLabel('Room code').fill(reusableRoomCode)
      await secondPage.getByRole('button', { name: 'Join match' }).click()
      await secondPage.getByRole('button', { name: 'Back to dashboard' }).click()
      secondPage.once('dialog', (dialog) => dialog.accept())
      await secondPage.getByRole('button', { name: 'Leave lobby' }).click()
      await expect(secondPage.locator('.current-games')).not.toBeVisible()
      await expect(firstPage.getByText(/waiting for a second player/i)).toBeVisible()

      await outsiderPage.goto('/')
      await outsiderPage.getByLabel('Room code').fill(reusableRoomCode)
      await outsiderPage.getByRole('button', { name: 'Join match' }).click()
      await expect(outsiderPage.getByText('Side 2')).toBeVisible()
      await firstPage.getByRole('button', { name: 'Back to dashboard' }).click()
      firstPage.once('dialog', (dialog) => dialog.accept())
      await firstPage.getByRole('button', { name: 'Cancel room' }).click()
      await expect(firstPage.locator('.current-games')).not.toBeVisible()
    } finally {
      await first.close()
      await second.close()
      await outsider.close()
    }
  })

  test('E2E 3 — casual room supports recovery, formation, chat, live move, resignation, disclosure and history', async ({ browser }) => {
    const first = await browser.newContext({ storageState: firstState })
    const second = await browser.newContext({ storageState: secondState })
    try {
      const firstPage = await first.newPage()
      const secondPage = await second.newPage()
      const roomCode = await createAndJoinPrivateRoom(firstPage, secondPage)
      expect(roomCode).toMatch(/^[A-Z2-9]{6}$/)

      await prepareBothArmies(firstPage, secondPage)
      await firstPage.goto('/')
      const currentGame = firstPage.locator('.current-games')
      await expect(currentGame.getByRole('button', { name: 'Resume game' })).toBeVisible()
      await expect(currentGame.getByRole('button', { name: 'Cancel room' })).toHaveCount(0)
      await expect(firstPage.getByText('You already have a game in progress.')).toBeVisible()
      await currentGame.getByRole('button', { name: 'Resume game' }).click()
      await expect(firstPage.getByRole('heading', { name: /Your turn|Opponent’s turn/ })).toBeVisible()
      await exchangeChat(firstPage, secondPage, `Ready ${runId}`)
      await makeOneLegalMove(firstPage, secondPage)
      await resignAndExpectDisclosure(secondPage, firstPage)

      await firstPage.goto('/history')
      await expect(firstPage.getByRole('heading', { name: 'Match history' })).toBeVisible()
      await expect(firstPage.getByText(
        `CASUAL against ${accounts.second.displayName}`,
      )).toBeVisible()
    } finally {
      await first.close()
      await second.close()
    }
  })

  test('E2E 4 — ranked pairing updates rating once and appears on profile and leaderboard', async ({ browser }) => {
    const first = await browser.newContext({ storageState: firstState })
    const second = await browser.newContext({ storageState: secondState })
    try {
      const firstPage = await first.newPage()
      const secondPage = await second.newPage()
      await Promise.all([firstPage.goto('/'), secondPage.goto('/')])
      await Promise.all([
        enterRankedQueue(firstPage),
        enterRankedQueue(secondPage),
      ])
      await expect(firstPage.getByText('Ranked match', { exact: true }))
        .toBeVisible({ timeout: 45_000 })
      await expect(secondPage.getByText('Ranked match', { exact: true }))
        .toBeVisible({ timeout: 45_000 })

      await prepareBothArmies(firstPage, secondPage)
      await makeOneLegalMove(firstPage, secondPage)
      await resignAndExpectDisclosure(secondPage, firstPage)
      await expect(firstPage.locator('.rating-change')).toBeVisible({ timeout: 20_000 })

      await firstPage.goto('/profile')
      await expect(firstPage.locator('.rating-card')).toContainText(/rating|provisional/i)
      await firstPage.goto('/leaderboard')
      await expect(firstPage.getByText('Your position')).toBeVisible()
      await expect(firstPage.locator('.own-ranking')).toContainText(/\d+/)
    } finally {
      await first.close()
      await second.close()
    }
  })

  test('E2E 5 — outsider access fails, active ranks stay secret and hostile chat stays text', async ({ browser }) => {
    const first = await browser.newContext({ storageState: firstState })
    const second = await browser.newContext({ storageState: secondState })
    const outsider = await browser.newContext({ storageState: outsiderState })
    try {
      const firstPage = await first.newPage()
      const secondPage = await second.newPage()
      const outsiderPage = await outsider.newPage()
      await createAndJoinPrivateRoom(firstPage, secondPage)
      await prepareBothArmies(firstPage, secondPage)
      const matchId = await activeMatchId(firstPage)

      const forbidden = await outsider.request.get(`/api/matches/${matchId}`)
      expect(forbidden.status()).toBe(403)
      expect((await forbidden.json()).code).toBe('PLAYER_NOT_IN_MATCH')
      expect(await forbiddenSubscriptionIsClosed(outsiderPage, matchId)).toBe(true)

      const safeView = await first.request.get(`/api/matches/${matchId}`)
      expect(safeView.ok()).toBe(true)
      const body = await safeView.json()
      expect(body.phase).toBe('ACTIVE')
      expect(body.opponentPieces).toHaveLength(21)
      expect(body.opponentPieces.every((piece: Record<string, unknown>) => !('rank' in piece))).toBe(true)
      await expect(firstPage.locator('.match-piece--opponent')).toHaveCount(21)
      expect(await firstPage.locator('.match-piece--opponent').allTextContents())
        .toEqual(Array(21).fill('HIDDEN'))

      const hostileText = `<img src=x onerror="window.e2eInjected=true"> ${runId}`
      await exchangeChat(firstPage, secondPage, hostileText)
      await expect(secondPage.locator('.chat-message').filter({ hasText: hostileText })).toBeVisible()
      await expect(secondPage.locator('.chat-message img')).toHaveCount(0)
      expect(await secondPage.evaluate(() => (window as Window & { e2eInjected?: boolean }).e2eInjected))
        .not.toBe(true)

      await resignAndExpectDisclosure(secondPage, firstPage)
    } finally {
      await first.close()
      await second.close()
      await outsider.close()
    }
  })

  test('E2E 6 — optional LiveKit media remains isolated from authoritative gameplay', async ({ browser }) => {
    test.skip(process.env.MEDIA_E2E !== 'true', 'Requires an operator-provided disposable LiveKit Cloud project')
    const origin = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:5173'
    const first = await browser.newContext({
      storageState: firstState,
      permissions: ['camera', 'microphone'],
      baseURL: origin,
    })
    const second = await browser.newContext({
      storageState: secondState,
      permissions: ['camera', 'microphone'],
      baseURL: origin,
    })
    try {
      const firstPage = await first.newPage()
      const secondPage = await second.newPage()
      await createAndJoinPrivateRoom(firstPage, secondPage)

      for (const page of [firstPage, secondPage]) {
        await page.getByRole('button', { name: 'Open media' }).click()
        await expect(page.getByText('Media off')).toBeVisible()
        await page.getByRole('button', { name: 'Enable audio/video' }).click()
        await expect(page.getByText('Media connected')).toBeVisible({ timeout: 30_000 })
        await expect(page.getByRole('button', { name: 'Unmute mic' })).toBeVisible()
        await expect(page.getByRole('button', { name: 'Turn camera on' })).toBeVisible()
        await page.getByRole('button', { name: 'Unmute mic' }).click()
        await page.getByRole('button', { name: 'Turn camera on' }).click()
      }

      await expect(firstPage.locator('.media-video-frame video')).toHaveCount(2, { timeout: 30_000 })
      await expect(secondPage.locator('.media-video-frame video')).toHaveCount(2, { timeout: 30_000 })
      await firstPage.getByRole('button', { name: 'Mute opponent' }).click()
      await firstPage.getByRole('button', { name: 'Hide opponent video' }).click()
      await expect(firstPage.getByText(/Video hidden locally/)).toBeVisible()
      await expect(secondPage.getByText('Media connected')).toBeVisible()

      await secondPage.getByRole('button', { name: 'Leave media' }).click()
      await expect(secondPage.getByText('Media off')).toBeVisible()
      await expect(firstPage.getByText('Live updates synchronized')).toBeVisible()
      await firstPage.reload()
      await expect(firstPage.getByText('Live updates synchronized')).toBeVisible()
      await expect(firstPage.getByText('Media connected')).toBeVisible({ timeout: 30_000 })
      await expect(firstPage.getByRole('button', { name: 'Unmute mic' })).toBeVisible()
      await expect(firstPage.getByRole('button', { name: 'Turn camera on' })).toBeVisible()

      await prepareBothArmies(firstPage, secondPage)
      await expect(firstPage.getByRole('heading', { name: /Your turn|Opponent’s turn/ })).toBeVisible()
      await firstPage.getByRole('button', { name: 'Open chat' }).click()
      await firstPage.getByRole('button', { name: 'Block' }).click()
      await expect(firstPage.getByText('Media off')).toBeVisible()
      const matchId = await activeMatchId(firstPage)
      const denied = await firstPage.evaluate(async (id) => {
        const csrf = await fetch('/api/auth/csrf', { credentials: 'include' })
          .then((response) => response.json()) as { headerName: string; token: string }
        const response = await fetch(`/api/matches/${id}/media-token`, {
          method: 'POST', credentials: 'include', headers: { [csrf.headerName]: csrf.token },
        })
        return { status: response.status, body: await response.json() as { code: string } }
      }, matchId)
      expect(denied.status).toBe(403)
      expect(denied.body.code).toBe('MEDIA_BLOCKED')

      await expect(firstPage.getByText('Live updates synchronized')).toBeVisible()
      await firstPage.setViewportSize({ width: 390, height: 844 })
      expect(await firstPage.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      firstPage.once('dialog', (dialog) => dialog.accept())
      await firstPage.getByRole('button', { name: 'Resign match' }).click()
      await expect(firstPage.getByText('Match complete')).toBeVisible()
    } finally {
      await first.close()
      await second.close()
    }
  })
})

async function enterRankedQueue(page: Page) {
  try {
    await page.getByRole('button', { name: 'Find ranked match' }).click()
  } catch (error) {
    if (!await page.getByText('Ranked match', { exact: true }).isVisible()) throw error
  }
}

async function registerVerifyAndLogin(
  context: BrowserContext,
  account: { username: string; email: string; displayName: string },
) {
  const page = context.pages()[0] ?? await context.newPage()
  await page.goto('/register')
  await page.getByLabel('Username').fill(account.username)
  await page.getByLabel('Display name').fill(account.displayName)
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page).toHaveURL(/\/login/)

  const verificationUrl = await verificationLink(account.email)
  await page.goto(verificationUrl)
  await expect(page.getByText('Email verified. Your account is active.')).toBeVisible()
  await login(page, account.username)
}

async function verificationLink(email: string): Promise<string> {
  const deadline = Date.now() + 20_000
  while (Date.now() < deadline) {
    const search = await fetch(
      `${mailpitUrl}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`,
    )
    if (search.ok) {
      const result = await search.json() as { messages?: { ID?: string; id?: string }[] }
      const id = result.messages?.[0]?.ID ?? result.messages?.[0]?.id
      if (id) {
        const response = await fetch(`${mailpitUrl}/api/v1/message/${id}`)
        const message = await response.json() as { Text?: string; HTML?: string }
        const match = `${message.Text ?? ''}\n${message.HTML ?? ''}`
          .match(/http:\/\/[^\s<]+\/verify-email\?token=[A-Za-z0-9_-]+/)
        if (match) return match[0]
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`Verification mail did not arrive for ${email}`)
}

async function login(page: Page, username: string) {
  await page.goto('/login')
  await page.getByLabel('Username or email').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

async function logoutAndLogin(context: BrowserContext, username: string) {
  const page = context.pages()[0] ?? await context.newPage()
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login/)
  await login(page, username)
}

async function createAndJoinPrivateRoom(firstPage: Page, secondPage: Page): Promise<string> {
  await firstPage.goto('/')
  await firstPage.getByLabel('Clock').selectOption('STANDARD_15_PLUS_5')
  await firstPage.getByRole('button', { name: 'Create private match' }).click()
  await expect(firstPage.getByLabel('Room code')).toHaveValue(/^[A-Z2-9]{6}$/)
  const roomCode = await firstPage.getByLabel('Room code').inputValue()

  await secondPage.goto('/')
  await secondPage.getByLabel('Room code').fill(roomCode)
  await secondPage.getByRole('button', { name: 'Join match' }).click()
  await expect(secondPage.getByText('Side 2')).toBeVisible()
  await expect(firstPage.getByText(/Opponent: joined/)).toBeVisible()
  return roomCode
}

async function prepareBothArmies(firstPage: Page, secondPage: Page) {
  await Promise.all([deployFormation(firstPage), deployFormation(secondPage)])
  await submitAndLockFormation(firstPage)
  await submitAndLockFormation(secondPage)
  await expect(firstPage.getByRole('heading', { name: /Your turn|Opponent’s turn/ })).toBeVisible()
  await expect(secondPage.getByRole('heading', { name: /Your turn|Opponent’s turn/ })).toBeVisible()
}

interface FormationSafeState {
  status: number
  code?: string
  version?: number
  phase?: string
  ownPieceCount?: number
  ownLocked?: boolean
  opponentRankExposed?: boolean
}

async function submitAndLockFormation(page: Page) {
  await expect(page.getByText('Live updates synchronized')).toBeVisible()
  const submittedVersion = await displayedMatchVersion(page)
  const matchId = await activeMatchId(page)
  const formationResponses: Response[] = []
  const observeFormationResponses = (response: Response) => {
    const pathname = new URL(response.url()).pathname
    if (pathname.endsWith('/formation') || pathname.endsWith('/lock')) {
      formationResponses.push(response)
    }
  }
  page.on('response', observeFormationResponses)

  try {
    await page.getByRole('button', { name: 'Submit formation' }).click()
    try {
      await expect.poll(
        () => readFormationSafeState(page, matchId),
        { message: 'formation submission must be confirmed by the authenticated safe view' },
      ).toMatchObject({
        status: 200,
        phase: 'FORMATION',
        ownPieceCount: 21,
        ownLocked: false,
        opponentRankExposed: false,
      })
      await expect.poll(() => readSafeVersion(page, matchId), {
        message: `formation version must advance beyond submitted version ${submittedVersion}`,
      }).toBeGreaterThan(submittedVersion)
    } catch (error) {
      throw await formationDiagnostic(
        page,
        matchId,
        submittedVersion,
        formationResponses,
        'submission',
        error,
      )
    }

    await expect(page.getByRole('button', { name: 'Formation submitted' })).toBeEnabled()
    await expect(page.getByRole('button', { name: 'Reset formation' })).toBeEnabled()
    await expect(page.getByRole('button', { name: 'Lock formation' })).toBeEnabled()

    const versionBeforeLock = await readSafeVersion(page, matchId)
    await page.getByRole('button', { name: 'Lock formation' }).click()
    try {
      await expect.poll(
        () => readFormationSafeState(page, matchId),
        { message: 'formation lock must be confirmed by the authenticated safe view' },
      ).toMatchObject({
        status: 200,
        ownPieceCount: 21,
        ownLocked: true,
        opponentRankExposed: false,
      })
      await expect.poll(() => readSafeVersion(page, matchId), {
        message: `lock version must advance beyond ${versionBeforeLock}`,
      }).toBeGreaterThan(versionBeforeLock)
    } catch (error) {
      throw await formationDiagnostic(
        page,
        matchId,
        versionBeforeLock,
        formationResponses,
        'lock',
        error,
      )
    }
  } finally {
    page.off('response', observeFormationResponses)
  }
}

async function readFormationSafeState(page: Page, matchId: string): Promise<FormationSafeState> {
  const response = await page.request.get(`/api/matches/${matchId}`)
  const body = await response.json().catch(() => ({})) as {
    code?: string
    version?: number
    phase?: string
    requestingSide?: 'PLAYER_ONE' | 'PLAYER_TWO'
    playerOneLocked?: boolean
    playerTwoLocked?: boolean
    ownPieces?: unknown[]
    opponentPieces?: Record<string, unknown>[]
  }
  const ownLocked = body.requestingSide === 'PLAYER_ONE'
    ? body.playerOneLocked
    : body.playerTwoLocked
  return {
    status: response.status(),
    code: body.code,
    version: body.version,
    phase: body.phase,
    ownPieceCount: body.ownPieces?.length,
    ownLocked,
    opponentRankExposed: body.opponentPieces?.some((piece) => 'rank' in piece) ?? false,
  }
}

async function readSafeVersion(page: Page, matchId: string): Promise<number> {
  const state = await readFormationSafeState(page, matchId)
  return state.version ?? -1
}

async function formationDiagnostic(
  page: Page,
  matchId: string,
  submittedVersion: number,
  responses: Response[],
  action: 'submission' | 'lock',
  cause: unknown,
): Promise<Error> {
  const relevantResponse = responses.at(-1)
  const responseBody = relevantResponse
    ? await relevantResponse.json().catch(() => ({})) as { code?: string; message?: string }
    : {}
  const refreshed = await readFormationSafeState(page, matchId)
    .catch((): FormationSafeState => ({ status: 0 }))
  const visibleErrors = await page.getByRole('alert').allTextContents()
  const syncMessages = await page.locator('.sync-message').allTextContents()
  return new Error([
    `Formation ${action} did not recover`,
    `response status ${relevantResponse?.status() ?? '<none>'}`,
    `response code ${responseBody.code ?? '<none>'}`,
    `submitted version ${submittedVersion}`,
    `refreshed version ${refreshed.version ?? '<unavailable>'}`,
    visibleErrors.length > 0 ? `visible error: ${visibleErrors.join(' | ')}` : 'visible error: <none>',
    syncMessages.length > 0 ? `sync state: ${syncMessages.join(' | ')}` : 'sync state: <none>',
    `cause: ${cause instanceof Error ? cause.message : String(cause)}`,
  ].join(' — '))
}

async function deployFormation(page: Page) {
  const sideLabel = page.locator('.match-status-bar').getByText(/^Side [12]$/)
  await expect(sideLabel).toBeVisible()
  const firstRow = await sideLabel.textContent() === 'Side 1' ? 0 : 5
  const ranks = [
    'Five-Star General', 'Four-Star General', 'Three-Star General', 'Two-Star General',
    'One-Star General', 'Colonel', 'Lieutenant Colonel', 'Major', 'Captain',
    'First Lieutenant', 'Second Lieutenant', 'Sergeant',
    'Private', 'Private', 'Private', 'Private', 'Private', 'Private', 'Spy', 'Spy', 'Flag',
  ]
  const tray = page.locator('.tray')
  for (let index = 0; index < ranks.length; index += 1) {
    await tray.getByRole('button', { name: ranks[index], exact: true }).first().click()
    const row = firstRow + Math.floor(index / 9)
    const column = index % 9
    await page.getByRole('gridcell', {
      name: `Row ${row}, column ${column}, formation cell`,
      exact: true,
    }).click()
  }
  await expect(page.getByText('21/21 placed · 6 empty')).toBeVisible()
}

async function exchangeChat(sender: Page, recipient: Page, text: string) {
  await sender.getByRole('button', { name: /Open chat/ }).click()
  await recipient.getByRole('button', { name: /Open chat/ }).click()
  await sender.getByRole('textbox', { name: 'Message', exact: true }).fill(text)
  await sender.getByRole('button', { name: 'Send' }).click()
  await expect(recipient.locator('.chat-message').filter({ hasText: text })).toBeVisible()
}

async function makeOneLegalMove(firstPage: Page, secondPage: Page) {
  await Promise.all([
    expect(firstPage.locator('.status-pill')).toContainText('ACTIVE'),
    expect(secondPage.locator('.status-pill')).toContainText('ACTIVE'),
  ])
  await expect.poll(async () => {
    const [firstMoves, secondMoves, firstVersion, secondVersion] = await Promise.all([
      firstPage.getByRole('heading', { name: 'Your turn' }).isVisible(),
      secondPage.getByRole('heading', { name: 'Your turn' }).isVisible(),
      displayedMatchVersion(firstPage),
      displayedMatchVersion(secondPage),
    ])
    return {
      visibleTurnHeadings: Number(firstMoves) + Number(secondMoves),
      versionsConverged: firstVersion === secondVersion,
    }
  }, { message: 'both active views must agree on one current player and one version' }).toEqual({
    visibleTurnHeadings: 1,
    versionsConverged: true,
  })

  const firstMoves = await firstPage.getByRole('heading', { name: 'Your turn' }).isVisible()
  const mover = firstMoves ? firstPage : secondPage
  const observer = firstMoves ? secondPage : firstPage
  const sideLabel = mover.locator('.match-status-bar').getByText(/^Side [12]$/)
  await expect(sideLabel).toBeVisible()
  const sideOneMoves = await sideLabel.textContent() === 'Side 1'
  const sourceRow = sideOneMoves ? 2 : 5
  const destinationRow = sideOneMoves ? 3 : 4
  const sourceRank = sideOneMoves ? 'Spy' : 'Five-Star General'
  const source = mover.getByRole('gridcell', {
    name: new RegExp(`Row ${sourceRow}, column 0, ${sourceRank}`),
  })
  await expect(source).toBeEnabled()
  const previousVersion = await displayedMatchVersion(mover)
  await source.click()
  const destination = mover.getByRole('gridcell', {
    name: `Row ${destinationRow}, column 0, candidate destination`,
    exact: true,
  })
  await expect(destination).toBeEnabled()

  const moveResponsePromise = mover.waitForResponse((response) =>
    response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/moves'))
  await destination.click()
  const moveResponse = await moveResponsePromise
  const responseBody = await moveResponse.json() as {
    code?: string
    message?: string
    version?: number
    view?: { events?: { type?: string }[] }
  }
  if (!moveResponse.ok()) {
    const visibleErrors = await mover.getByRole('alert').allTextContents()
    const currentVersion = await displayedMatchVersion(mover)
    throw new Error([
      `Move rejected with HTTP ${moveResponse.status()}`,
      responseBody.code && `${responseBody.code}: ${responseBody.message ?? 'no message'}`,
      `submitted version ${previousVersion}; displayed version ${currentVersion}`,
      visibleErrors.length > 0 && `visible error: ${visibleErrors.join(' | ')}`,
    ].filter(Boolean).join(' — '))
  }
  expect(responseBody.version).toBeGreaterThan(previousVersion)
  expect(responseBody.view?.events?.some((event) => event.type === 'MOVE_APPLIED')).toBe(true)

  const acceptedVersion = responseBody.version as number
  await expect.poll(async () => Promise.all([
    displayedMatchVersion(mover),
    displayedMatchVersion(observer),
  ]), { message: `both players must converge on accepted version ${acceptedVersion}` })
    .toEqual([acceptedVersion, acceptedVersion])
  await expect(mover.getByRole('heading', { name: 'Opponent’s turn' })).toBeVisible()
  await expect(observer.getByRole('heading', { name: 'Your turn' })).toBeVisible()
}

async function displayedMatchVersion(page: Page): Promise<number> {
  const status = await page.locator('.match-header').getByText(/^Version \d+$/).textContent()
  const version = status?.match(/version\s+(\d+)/i)?.[1]
  if (!version) throw new Error(`Could not read match version from status: ${status ?? '<missing>'}`)
  return Number(version)
}

async function resignAndExpectDisclosure(resigner: Page, observer: Page) {
  resigner.on('dialog', (dialog) => dialog.accept())
  await resigner.getByRole('button', { name: 'Resign match' }).click()
  await expect(resigner.getByRole('heading', { name: /You won|Player [12] won|Draw/ })).toBeVisible()
  await expect(observer.getByText('Complete post-match disclosure')).toBeVisible()
  await expect(observer.locator('.disclosed-piece')).toHaveCount(42)
}

async function activeMatchId(page: Page): Promise<string> {
  return page.evaluate(() => {
    const raw = sessionStorage.getItem('battle-of-bluffs.match-session')
    if (!raw) throw new Error('Missing match session')
    return (JSON.parse(raw) as { matchId: string }).matchId
  })
}

async function forbiddenSubscriptionIsClosed(page: Page, matchId: string): Promise<boolean> {
  return page.evaluate((id) => new Promise<boolean>((resolve) => {
    const socket = new WebSocket(`ws://${window.location.host}/ws`, ['v12.stomp'])
    const timeout = window.setTimeout(() => { socket.close(); resolve(false) }, 5_000)
    socket.onopen = () => socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0')
    socket.onmessage = (event) => {
      const frame = String(event.data)
      if (frame.startsWith('CONNECTED')) {
        socket.send(`SUBSCRIBE\nid:forbidden\ndestination:/user/queue/matches/${id}\n\n\0`)
      } else if (frame.startsWith('ERROR')) {
        window.clearTimeout(timeout)
        socket.close()
        resolve(true)
      }
    }
    socket.onclose = () => { window.clearTimeout(timeout); resolve(true) }
  }), matchId)
}
