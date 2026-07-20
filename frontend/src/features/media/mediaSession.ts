const PREFIX = 'gotg:media-enabled:'

export const mediaSessionKey = (accountId: string, matchId: string, participantCycle: string) =>
  `${PREFIX}${accountId}:${matchId}:${participantCycle}`

export function clearMediaSession(): void {
  for (let index = sessionStorage.length - 1; index >= 0; index -= 1) {
    const key = sessionStorage.key(index)
    if (key?.startsWith(PREFIX)) sessionStorage.removeItem(key)
  }
}
