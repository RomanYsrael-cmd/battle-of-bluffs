import { useMemo, useState } from 'react'
import { isFormationPosition, type BoardSide, type Position } from '../../game/coordinates'
import { isValidFormation, pieceAt, placeOrSwap, removePlacement, type Placements } from '../../game/formation'
import { createInventory, type LocalPiece } from '../../game/ranks'

interface InitialPiece extends LocalPiece {
  position: Position | null
}

export function useFormation(side: BoardSide, locked: boolean, initialPieces: InitialPiece[] = []) {
  const inventory = useMemo(
    () => initialPieces.length === 21
      ? initialPieces.map(({ id, rank }) => ({ id, rank }))
      : createInventory(),
    [initialPieces],
  )
  const [placements, setPlacements] = useState<Placements>(() => Object.fromEntries(
    initialPieces.flatMap((piece) => piece.position ? [[piece.id, piece.position]] : []),
  ))
  const [selectedPieceId, setSelectedPieceId] = useState<string | null>(null)
  const valid = isValidFormation(inventory, placements, side)

  const selectPiece = (pieceId: string) => {
    if (!locked) setSelectedPieceId((selected) => selected === pieceId ? null : pieceId)
  }

  const selectCell = (position: Position) => {
    if (locked || !selectedPieceId || !isFormationPosition(position, side)) return
    const selectedIsPlaced = Boolean(placements[selectedPieceId])
    if (!selectedIsPlaced && pieceAt(placements, position)) return
    setPlacements((current) => placeOrSwap(current, selectedPieceId, position, side))
    setSelectedPieceId(null)
  }

  const placePiece = (pieceId: string, position: Position) => {
    if (locked || !inventory.some((piece) => piece.id === pieceId)
      || !isFormationPosition(position, side)) return
    const selectedIsPlaced = Boolean(placements[pieceId])
    if (!selectedIsPlaced && pieceAt(placements, position)) return
    setPlacements((current) => placeOrSwap(current, pieceId, position, side))
    setSelectedPieceId(null)
  }

  const returnPieceToTray = (pieceId: string) => {
    if (locked || !placements[pieceId]) return
    setPlacements((current) => removePlacement(current, pieceId))
    setSelectedPieceId(null)
  }

  const returnSelectedToTray = () => {
    if (selectedPieceId) returnPieceToTray(selectedPieceId)
  }

  const reset = () => {
    setPlacements({})
    setSelectedPieceId(null)
  }

  return {
    inventory,
    placements,
    selectedPieceId,
    locked,
    valid,
    placedCount: Object.keys(placements).length,
    selectPiece,
    selectCell,
    placePiece,
    returnPieceToTray,
    returnSelectedToTray,
    reset,
  }
}
