import { coordinateKey, isFormationPosition, type BoardSide, type Position } from './coordinates'
import type { LocalPiece } from './ranks'

export type Placements = Readonly<Record<string, Position>>

export const FORMATION_PIECE_COUNT = 21
export const FORMATION_CELL_COUNT = 27
export const VALID_EMPTY_CELL_COUNT = 6

export const pieceAt = (placements: Placements, position: Position): string | undefined =>
  Object.entries(placements).find(([, placed]) => coordinateKey(placed) === coordinateKey(position))?.[0]

export const isValidFormation = (
  inventory: LocalPiece[],
  placements: Placements,
  side: BoardSide = 'PLAYER_ONE',
): boolean => {
  const positions = Object.values(placements)
  return inventory.length === FORMATION_PIECE_COUNT
    && Object.keys(placements).length === FORMATION_PIECE_COUNT
    && positions.every((position) => isFormationPosition(position, side))
    && new Set(positions.map(coordinateKey)).size === FORMATION_PIECE_COUNT
}

export const placeOrSwap = (
  placements: Placements,
  selectedPieceId: string,
  destination: Position,
  side: BoardSide = 'PLAYER_ONE',
): Placements => {
  if (!isFormationPosition(destination, side)) return placements

  const current = placements[selectedPieceId]
  const occupyingId = pieceAt(placements, destination)
  const next = { ...placements, [selectedPieceId]: destination }

  if (occupyingId && occupyingId !== selectedPieceId) {
    if (!current) return placements
    next[occupyingId] = current
  }
  return next
}

export const removePlacement = (placements: Placements, pieceId: string): Placements => {
  const next = { ...placements }
  delete next[pieceId]
  return next
}
