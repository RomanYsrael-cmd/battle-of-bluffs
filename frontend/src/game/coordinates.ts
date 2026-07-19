export const BOARD_ROWS = 8
export const BOARD_COLUMNS = 9
export const PLAYER_ONE_FORMATION_ROWS = [0, 1, 2] as const

export interface Position {
  row: number
  column: number
}

export const coordinateKey = ({ row, column }: Position): string => `${row}:${column}`

export const isOnBoard = ({ row, column }: Position): boolean =>
  row >= 0 && row < BOARD_ROWS && column >= 0 && column < BOARD_COLUMNS

export const isPlayerOneFormationPosition = (position: Position): boolean =>
  isOnBoard(position) && PLAYER_ONE_FORMATION_ROWS.includes(position.row as 0 | 1 | 2)

export const canonicalPositions = (): Position[] =>
  Array.from({ length: BOARD_ROWS }, (_, row) =>
    Array.from({ length: BOARD_COLUMNS }, (_, column) => ({ row, column })),
  ).flat()

export const playerOneVisualPositions = (): Position[] =>
  [...canonicalPositions()].sort((a, b) => b.row - a.row || a.column - b.column)
