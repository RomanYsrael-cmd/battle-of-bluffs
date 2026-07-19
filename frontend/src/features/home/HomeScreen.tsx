import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { createMatch, joinMatch } from '../../api/client'
import type { CommandResponse } from '../../api/types'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { BRAND } from '../../config/brand'

interface HomeScreenProps {
  onEnteredMatch: (response: CommandResponse) => void
}

export function HomeScreen({ onEnteredMatch }: HomeScreenProps) {
  const [roomCode, setRoomCode] = useState('')
  const [timerMode, setTimerMode] = useState<'CASUAL_UNTIMED' | 'STANDARD_15_PLUS_5'>('CASUAL_UNTIMED')
  const createMutation = useMutation({
    mutationFn: () => createMatch(timerMode),
    retry: false,
    onSuccess: onEnteredMatch,
  })
  const joinMutation = useMutation({
    mutationFn: () => joinMatch(roomCode.trim().toUpperCase()),
    retry: false,
    onSuccess: onEnteredMatch,
  })
  const pending = createMutation.isPending || joinMutation.isPending
  const error = createMutation.error ?? joinMutation.error

  return (
    <main className="app-shell home-screen">
      <header className="hero hero--home">
        <div>
          <p className="eyebrow">{BRAND.productName} · Private match</p>
          <h1>{BRAND.tagline}</h1>
          <p className="hero__copy">
            Create a private room or join another player with their six-character room code.
          </p>
        </div>
      </header>

      <section className="home-actions" aria-label="Private match actions">
        <article className="entry-card">
          <p className="eyebrow">Host</p>
          <h2>Create private match</h2>
          <p>Your account takes Player 1 and receives a private room code to share.</p>
          <label htmlFor="timer-mode">Clock</label>
          <select
            id="timer-mode"
            className="text-input"
            value={timerMode}
            onChange={(event) => setTimerMode(event.target.value as typeof timerMode)}
          >
            <option value="CASUAL_UNTIMED">Untimed</option>
            <option value="STANDARD_15_PLUS_5">15 minutes + 5 seconds</option>
          </select>
          <button
            type="button"
            className="button button--primary button--wide"
            disabled={pending}
            onClick={() => createMutation.mutate()}
          >
            {createMutation.isPending ? 'Creating room…' : 'Create private match'}
          </button>
        </article>

        <form
          className="entry-card"
          onSubmit={(event) => {
            event.preventDefault()
            if (roomCode.trim()) joinMutation.mutate()
          }}
        >
          <p className="eyebrow">Guest</p>
          <h2>Join match</h2>
          <label htmlFor="room-code">Room code</label>
          <input
            id="room-code"
            className="text-input room-code-input"
            value={roomCode}
            maxLength={6}
            autoComplete="off"
            onChange={(event) => setRoomCode(event.target.value.toUpperCase())}
          />
          <button
            type="submit"
            className="button button--secondary button--wide"
            disabled={pending || !roomCode.trim()}
          >
            {joinMutation.isPending ? 'Joining room…' : 'Join match'}
          </button>
        </form>
      </section>
      <ApiErrorNotice error={error} />
    </main>
  )
}
