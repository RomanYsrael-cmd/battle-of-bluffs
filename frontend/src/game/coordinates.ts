export const BOARD_ROWS = 8
export const BOARD_COLUMNS = 9
export const PLAYER_ONE_FORMATION_ROWS = [0, 1, 2] as const
export const PLAYER_TWO_FORMATION_ROWS = [5, 6, 7] as const

export type BoardSide = 'PLAYER_ONE' | 'PLAYER_TWO'

export interface Position {
  row: number
  column: number
}

export const coordinateKey = ({ row, column }: Position): string => `${row}:${column}`

export const isOnBoard = ({ row, column }: Position): boolean =>
  row >= 0 && row < BOARD_ROWS && column >= 0 && column < BOARD_COLUMNS

export const isPlayerOneFormationPosition = (position: Position): boolean =>
  isOnBoard(position) && PLAYER_ONE_FORMATION_ROWS.includes(position.row as 0 | 1 | 2)

export const isPlayerTwoFormationPosition = (position: Position): boolean =>
  isOnBoard(position) && PLAYER_TWO_FORMATION_ROWS.includes(position.row as 5 | 6 | 7)

export const isFormationPosition = (position: Position, side: BoardSide): boolean =>
  side === 'PLAYER_ONE'
    ? isPlayerOneFormationPosition(position)
    : isPlayerTwoFormationPosition(position)

export const canonicalPositions = (): Position[] =>
  Array.from({ length: BOARD_ROWS }, (_, row) =>
    Array.from({ length: BOARD_COLUMNS }, (_, column) => ({ row, column })),
  ).flat()

export const visualPositionsFor = (side: BoardSide): Position[] =>
  [...canonicalPositions()].sort((a, b) =>
    side === 'PLAYER_ONE'
      ? b.row - a.row || a.column - b.column
      : a.row - b.row || a.column - b.column,
  )

export const orthogonalDestinations = (source: Position): Position[] => [
  { row: source.row - 1, column: source.column },
  { row: source.row + 1, column: source.column },
  { row: source.row, column: source.column - 1 },
  { row: source.row, column: source.column + 1 },
].filter(isOnBoard)
