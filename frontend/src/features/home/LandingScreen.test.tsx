import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LandingScreen } from './LandingScreen'

describe('public landing screen', () => {
  it('introduces the platform and provides account entry paths', () => {
    render(<MemoryRouter><LandingScreen /></MemoryRouter>)

    expect(screen.getByRole('heading', { name: 'Outthink the army you cannot see.' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Create account' })).toHaveAttribute('href', '/register')
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login')
    expect(screen.getByText(/opponent ranks appear only when the rules reveal them/i)).toBeInTheDocument()
  })
})
