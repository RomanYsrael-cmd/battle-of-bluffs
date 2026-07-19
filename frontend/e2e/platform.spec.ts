import { expect, test, type BrowserContext, type Page } from '@playwright/test'

const runId = process.env.E2E_RUN_ID
  ?? `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`
const password = 'Strategist!2026'
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

test.describe.serial('complete local platform', () => {
  test('E2E 1 — two accounts register, verify, log out and log back in independently', async ({ browser }) => {
    const first = await browser.newContext()
    const second = await browser.newContext()
    try {
      await registerVerifyAndLogin(first, accounts.first)
      await registerVerifyAndLogin(second, accounts.second)

      await logoutAndLogin(first, accounts.first.username)
      await logoutAndLogin(second, accounts.second.username)
      await first.storageState({ path: firstState })
      await second.storageState({ path: secondState })
    } finally {
      await first.close()
      await second.close()
    }
  })

  test('E2E 2 — casual room supports formation, chat, live move, resignation, disclosure and history', async ({ browser }) => {
    const first = await browser.newContext({ storageState: firstState })
    const second = await browser.newContext({ storageState: secondState })
    try {
      const firstPage = await first.newPage()
      const secondPage = await second.newPage()
      const roomCode = await createAndJoinPrivateRoom(firstPage, secondPage)
      expect(roomCode).toMatch(/^[A-Z2-9]{6}$/)

      await prepareBothArmies(firstPage, secondPage)
      await exchangeChat(firstPage, secondPage, `Ready ${runId}`)
      await makeOneLegalMove(firstPage, secondPage)
      await resignAndExpectDisclosure(secondPage, firstPage)

      await firstPage.goto('/history')
      await expect(firstPage.getByRole('heading', { name: 'Match history' })).toBeVisible()
      await expect(firstPage.getByText(/CASUAL against/i)).toBeVisible()
    } finally {
      await first.close()
      await second.close()
    }
  })

  test('E2E 3 — ranked pairing updates rating once and appears on profile and leaderboard', async ({ browser }) => {
    const first = await browser.newContext({ storageState: firstState })
    const second = await browser.newContext({ storageState: secondState })
    try {
      const firstPage = await first.newPage()
      const secondPage = await second.newPage()
      await Promise.all([firstPage.goto('/'), secondPage.goto('/')])
      await Promise.all([
        firstPage.getByRole('button', { name: 'Find ranked match' }).click(),
        secondPage.getByRole('button', { name: 'Find ranked match' }).click(),
      ])
      await expect(firstPage.getByText('Ranked match')).toBeVisible()
      await expect(secondPage.getByText('Ranked match')).toBeVisible()

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

  test('E2E 4 — outsider access fails, active ranks stay secret and hostile chat stays text', async ({ browser }) => {
    const first = await browser.newContext({ storageState: firstState })
    const second = await browser.newContext({ storageState: secondState })
    const outsider = await browser.newContext()
    try {
      const firstPage = await first.newPage()
      const secondPage = await second.newPage()
      const outsiderPage = await outsider.newPage()
      await createAndJoinPrivateRoom(firstPage, secondPage)
      await prepareBothArmies(firstPage, secondPage)
      const matchId = await activeMatchId(firstPage)

      await registerVerifyAndLogin(outsider, accounts.outsider)
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
})

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
      `http://127.0.0.1:8025/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`,
    )
    if (search.ok) {
      const result = await search.json() as { messages?: { ID?: string; id?: string }[] }
      const id = result.messages?.[0]?.ID ?? result.messages?.[0]?.id
      if (id) {
        const response = await fetch(`http://127.0.0.1:8025/api/v1/message/${id}`)
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
  await firstPage.getByRole('button', { name: 'Submit formation' }).click()
  await expect(firstPage.getByRole('button', { name: 'Formation submitted' })).toBeEnabled()
  await secondPage.getByRole('button', { name: 'Submit formation' }).click()
  await expect(secondPage.getByRole('button', { name: 'Formation submitted' })).toBeEnabled()
  await firstPage.getByRole('button', { name: 'Lock formation' }).click()
  await expect(firstPage.getByText(/Server-confirmed lock/)).toBeVisible()
  await secondPage.getByRole('button', { name: 'Lock formation' }).click()
  await expect(firstPage.getByRole('heading', { name: /Your turn|Opponent’s turn/ })).toBeVisible()
  await expect(secondPage.getByRole('heading', { name: /Your turn|Opponent’s turn/ })).toBeVisible()
}

async function deployFormation(page: Page) {
  const firstRow = await page.getByText('Side 1', { exact: true }).isVisible() ? 0 : 5
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
  const firstMoves = await firstPage.getByRole('heading', { name: 'Your turn' }).isVisible()
  const mover = firstMoves ? firstPage : secondPage
  const sideOneMoves = await mover.getByText('Side 1', { exact: true }).isVisible()
  const sourceRow = sideOneMoves ? 2 : 7
  const destinationRow = sideOneMoves ? 3 : 6
  await mover.getByRole('gridcell', { name: new RegExp(`Row ${sourceRow}, column 0, Spy`) }).click()
  await mover.getByRole('gridcell', {
    name: `Row ${destinationRow}, column 0, candidate destination`,
    exact: true,
  }).click()
  await expect(mover.getByText(/Version 7|Version 8|Version 9/)).toBeVisible()
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
