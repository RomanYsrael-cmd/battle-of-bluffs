import { useMemo, useState } from 'react'
import { isPlayerOneFormationPosition, type Position } from '../../game/coordinates'
import { isValidFormation, pieceAt, placeOrSwap, removePlacement, type Placements } from '../../game/formation'
import { createInventory } from '../../game/ranks'

export function useFormation() {
  const inventory = useMemo(createInventory, [])
  const [placements, setPlacements] = useState<Placements>({})
  const [selectedPieceId, setSelectedPieceId] = useState<string | null>(null)
  const [locked, setLocked] = useState(false)
  const valid = isValidFormation(inventory, placements)

  const selectPiece = (pieceId: string) => {
    if (!locked) setSelectedPieceId((selected) => selected === pieceId ? null : pieceId)
  }

  const selectCell = (position: Position) => {
    if (locked || !selectedPieceId || !isPlayerOneFormationPosition(position)) return
    const selectedIsPlaced = Boolean(placements[selectedPieceId])
    if (!selectedIsPlaced && pieceAt(placements, position)) return
    setPlacements((current) => placeOrSwap(current, selectedPieceId, position))
    setSelectedPieceId(null)
  }

  const returnSelectedToTray = () => {
    if (!locked && selectedPieceId && placements[selectedPieceId]) {
      setPlacements((current) => removePlacement(current, selectedPieceId))
      setSelectedPieceId(null)
    }
  }

  const reset = () => {
    setPlacements({})
    setSelectedPieceId(null)
    setLocked(false)
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
    returnSelectedToTray,
    lock: () => valid && setLocked(true),
    reset,
  }
}
