import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { createMatch, joinMatch } from '../../api/client'
import type { CommandResponse } from '../../api/types'
import { ApiErrorNotice } from '../../components/ApiErrorNotice'
import { DevelopmentWarning } from '../../components/DevelopmentWarning'

interface HomeScreenProps {
  onEnteredMatch: (response: CommandResponse) => void
}

export function HomeScreen({ onEnteredMatch }: HomeScreenProps) {
  const [roomCode, setRoomCode] = useState('')
  const createMutation = useMutation({ mutationFn: createMatch, retry: false, onSuccess: onEnteredMatch })
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
          <p className="eyebrow">Private match · REST development milestone</p>
          <h1>Enter the room. Keep your rank quiet.</h1>
          <p className="hero__copy">
            Create a private room or join another player with their six-character room code.
          </p>
        </div>
        <DevelopmentWarning />
      </header>

      <section className="home-actions" aria-label="Private match actions">
        <article className="entry-card">
          <p className="eyebrow">Host</p>
          <h2>Create private match</h2>
          <p>A temporary identity and room code will be generated for this browser tab.</p>
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
