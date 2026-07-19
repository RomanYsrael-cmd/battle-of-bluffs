import { describe, expect, it } from 'vitest'
import { createInventory } from './ranks'
import {
  isFormationPosition,
  isPlayerOneFormationPosition,
  isPlayerTwoFormationPosition,
  visualPositionsFor,
} from './coordinates'
import { isValidFormation, placeOrSwap, type Placements } from './formation'

describe('formation rules', () => {
  it('creates the complete 21-piece inventory', () => {
    const inventory = createInventory()
    expect(inventory).toHaveLength(21)
    expect(inventory.filter((piece) => piece.rank === 'PRIVATE')).toHaveLength(6)
    expect(inventory.filter((piece) => piece.rank === 'SPY')).toHaveLength(2)
    expect(inventory.filter((piece) => piece.rank === 'FLAG')).toHaveLength(1)
    expect(new Set(inventory.map((piece) => piece.id)).size).toBe(21)
  })

  it('accepts only canonical Player 1 formation rows', () => {
    expect(isPlayerOneFormationPosition({ row: 0, column: 0 })).toBe(true)
    expect(isPlayerOneFormationPosition({ row: 2, column: 8 })).toBe(true)
    expect(isPlayerOneFormationPosition({ row: 3, column: 0 })).toBe(false)
    expect(isPlayerOneFormationPosition({ row: 7, column: 8 })).toBe(false)
  })

  it('accepts only canonical Player 2 formation rows', () => {
    expect(isPlayerTwoFormationPosition({ row: 5, column: 0 })).toBe(true)
    expect(isPlayerTwoFormationPosition({ row: 7, column: 8 })).toBe(true)
    expect(isPlayerTwoFormationPosition({ row: 4, column: 0 })).toBe(false)
    expect(isFormationPosition({ row: 6, column: 4 }, 'PLAYER_TWO')).toBe(true)
  })

  it('rotates visual rows for Player 2 without changing canonical coordinates', () => {
    const playerOne = visualPositionsFor('PLAYER_ONE')
    const playerTwo = visualPositionsFor('PLAYER_TWO')
    expect(playerOne[0]).toEqual({ row: 7, column: 0 })
    expect(playerOne.at(-1)).toEqual({ row: 0, column: 8 })
    expect(playerTwo[0]).toEqual({ row: 0, column: 0 })
    expect(playerTwo.at(-1)).toEqual({ row: 7, column: 8 })
    expect(new Set(playerTwo.map(({ row, column }) => `${row}:${column}`)).size).toBe(72)
  })

  it('recognizes a complete formation with exactly six empty deployment cells', () => {
    const inventory = createInventory()
    const placements = Object.fromEntries(inventory.map((piece, index) => [
      piece.id,
      { row: Math.floor(index / 9), column: index % 9 },
    ]))
    expect(isValidFormation(inventory, placements)).toBe(true)
    expect(27 - Object.keys(placements).length).toBe(6)
  })

  it('prevents placement outside the deployment area', () => {
    const placements: Placements = {}
    expect(placeOrSwap(placements, 'piece-01', { row: 3, column: 0 })).toBe(placements)
  })

  it('swaps two already placed pieces', () => {
    const placements: Placements = {
      'piece-01': { row: 0, column: 0 },
      'piece-02': { row: 0, column: 1 },
    }
    expect(placeOrSwap(placements, 'piece-01', { row: 0, column: 1 })).toEqual({
      'piece-01': { row: 0, column: 1 },
      'piece-02': { row: 0, column: 0 },
    })
  })
})
