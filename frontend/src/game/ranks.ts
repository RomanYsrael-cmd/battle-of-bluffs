export const RANKS = [
  'FIVE_STAR_GENERAL',
  'FOUR_STAR_GENERAL',
  'THREE_STAR_GENERAL',
  'TWO_STAR_GENERAL',
  'ONE_STAR_GENERAL',
  'COLONEL',
  'LIEUTENANT_COLONEL',
  'MAJOR',
  'CAPTAIN',
  'FIRST_LIEUTENANT',
  'SECOND_LIEUTENANT',
  'SERGEANT',
  'PRIVATE',
  'SPY',
  'FLAG',
] as const

export type Rank = (typeof RANKS)[number]

export const RANK_LABELS: Record<Rank, string> = {
  FIVE_STAR_GENERAL: 'Five-Star General',
  FOUR_STAR_GENERAL: 'Four-Star General',
  THREE_STAR_GENERAL: 'Three-Star General',
  TWO_STAR_GENERAL: 'Two-Star General',
  ONE_STAR_GENERAL: 'One-Star General',
  COLONEL: 'Colonel',
  LIEUTENANT_COLONEL: 'Lieutenant Colonel',
  MAJOR: 'Major',
  CAPTAIN: 'Captain',
  FIRST_LIEUTENANT: 'First Lieutenant',
  SECOND_LIEUTENANT: 'Second Lieutenant',
  SERGEANT: 'Sergeant',
  PRIVATE: 'Private',
  SPY: 'Spy',
  FLAG: 'Flag',
}

export const RANK_ABBREVIATIONS: Record<Rank, string> = {
  FIVE_STAR_GENERAL: '5★',
  FOUR_STAR_GENERAL: '4★',
  THREE_STAR_GENERAL: '3★',
  TWO_STAR_GENERAL: '2★',
  ONE_STAR_GENERAL: '1★',
  COLONEL: 'COL',
  LIEUTENANT_COLONEL: 'LTC',
  MAJOR: 'MAJ',
  CAPTAIN: 'CPT',
  FIRST_LIEUTENANT: '1LT',
  SECOND_LIEUTENANT: '2LT',
  SERGEANT: 'SGT',
  PRIVATE: 'PVT',
  SPY: 'SPY',
  FLAG: '⚑',
}

const INVENTORY_QUANTITIES: Record<Rank, number> = {
  FIVE_STAR_GENERAL: 1,
  FOUR_STAR_GENERAL: 1,
  THREE_STAR_GENERAL: 1,
  TWO_STAR_GENERAL: 1,
  ONE_STAR_GENERAL: 1,
  COLONEL: 1,
  LIEUTENANT_COLONEL: 1,
  MAJOR: 1,
  CAPTAIN: 1,
  FIRST_LIEUTENANT: 1,
  SECOND_LIEUTENANT: 1,
  SERGEANT: 1,
  PRIVATE: 6,
  SPY: 2,
  FLAG: 1,
}

export interface LocalPiece {
  id: string
  rank: Rank
}

export const createInventory = (): LocalPiece[] => {
  let sequence = 0
  return RANKS.flatMap((rank) =>
    Array.from({ length: INVENTORY_QUANTITIES[rank] }, () => ({
      id: `piece-${String(++sequence).padStart(2, '0')}`,
      rank,
    })),
  )
}
