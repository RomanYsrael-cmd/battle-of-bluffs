import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RANKS, RANK_LABELS, type Rank } from '../../game/ranks'
import { INSIGNIA_SPECS, PieceInsignia } from './PieceInsignia'

const renderInsignia = (rank: Rank) => render(<PieceInsignia rank={rank} />).container

describe('classic piece insignia', () => {
  it.each(RANKS)('maps %s to an accessible insignia', (rank) => {
    render(<PieceInsignia rank={rank} />)
    expect(INSIGNIA_SPECS[rank]).toBeDefined()
    const accessibleName = rank === 'FLAG' ? 'Flag' : `${RANK_LABELS[rank]} insignia`
    expect(screen.getByRole('img', { name: accessibleName })).toBeInTheDocument()
  })

  it.each([
    ['FIVE_STAR_GENERAL', 5],
    ['FOUR_STAR_GENERAL', 4],
    ['THREE_STAR_GENERAL', 3],
    ['TWO_STAR_GENERAL', 2],
    ['ONE_STAR_GENERAL', 1],
  ] as const)('renders the correct star count for %s', (rank, count) => {
    expect(renderInsignia(rank).querySelectorAll('[data-symbol="star"]')).toHaveLength(count)
  })

  it.each([
    ['COLONEL', 3],
    ['LIEUTENANT_COLONEL', 2],
    ['MAJOR', 1],
  ] as const)('renders the correct eight-ray sun count for %s', (rank, count) => {
    const suns = renderInsignia(rank).querySelectorAll('[data-symbol="sun"]')
    expect(suns).toHaveLength(count)
    suns.forEach((sun) => expect(sun.querySelectorAll('[data-sun-ray]')).toHaveLength(8))
  })

  it.each([
    ['CAPTAIN', 3],
    ['FIRST_LIEUTENANT', 2],
    ['SECOND_LIEUTENANT', 1],
  ] as const)('renders the correct triangle count for %s', (rank, count) => {
    expect(renderInsignia(rank).querySelectorAll('[data-symbol="triangle"]')).toHaveLength(count)
  })

  it.each([
    ['SERGEANT', 3],
    ['PRIVATE', 1],
  ] as const)('renders distinct military chevrons for %s', (rank, count) => {
    expect(renderInsignia(rank).querySelectorAll('[data-symbol="chevron"]')).toHaveLength(count)
  })

  it('renders two watchful eyes for the Spy', () => {
    expect(renderInsignia('SPY').querySelectorAll('[data-symbol="eye"]')).toHaveLength(2)
  })

  it('renders a neutral monochrome generic flag without national symbols', () => {
    const container = renderInsignia('FLAG')
    expect(container.querySelector('[data-symbol="generic-flag"]')).toBeInTheDocument()
    expect(container.querySelector('[data-symbol="sun"], [data-symbol="star"]')).not.toBeInTheDocument()
    expect(container.querySelector('[fill*="#"]')).not.toBeInTheDocument()
    expect(container).not.toHaveTextContent(/philippine|country|faction/i)
  })
})
