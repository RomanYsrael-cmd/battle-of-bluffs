import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('formation interface', () => {
  it('keeps Lock Formation disabled until all pieces are placed', () => {
    render(<App />)
    expect(screen.getByRole('button', { name: /lock formation/i })).toBeDisabled()
    expect(screen.getByText(/not sent to a server/i)).toBeInTheDocument()
  })
})
