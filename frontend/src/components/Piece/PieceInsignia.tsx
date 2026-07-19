import { RANK_LABELS, type Rank } from '../../game/ranks'

type InsigniaSize = 'small' | 'medium' | 'large'

export interface PieceInsigniaProps {
  rank: Rank
  size?: InsigniaSize
  decorative?: boolean
  className?: string
}

interface InsigniaSpec {
  family: 'star' | 'sun' | 'triangle' | 'chevron' | 'eyes' | 'flag'
  count: number
}

export const INSIGNIA_SPECS: Record<Rank, InsigniaSpec> = {
  FIVE_STAR_GENERAL: { family: 'star', count: 5 },
  FOUR_STAR_GENERAL: { family: 'star', count: 4 },
  THREE_STAR_GENERAL: { family: 'star', count: 3 },
  TWO_STAR_GENERAL: { family: 'star', count: 2 },
  ONE_STAR_GENERAL: { family: 'star', count: 1 },
  COLONEL: { family: 'sun', count: 3 },
  LIEUTENANT_COLONEL: { family: 'sun', count: 2 },
  MAJOR: { family: 'sun', count: 1 },
  CAPTAIN: { family: 'triangle', count: 3 },
  FIRST_LIEUTENANT: { family: 'triangle', count: 2 },
  SECOND_LIEUTENANT: { family: 'triangle', count: 1 },
  SERGEANT: { family: 'chevron', count: 3 },
  PRIVATE: { family: 'chevron', count: 1 },
  SPY: { family: 'eyes', count: 2 },
  FLAG: { family: 'flag', count: 1 },
}

const positionsFor = (count: number, spacing: number): number[] =>
  Array.from({ length: count }, (_, index) => (index - (count - 1) / 2) * spacing)

function Stars({ count }: { count: number }) {
  const spacing = count >= 4 ? 10.5 : 15
  const scale = 0.78
  return positionsFor(count, spacing).map((x, index) => (
    <path
      key={index}
      data-symbol="star"
      d="M0-9 2.1-2.9 8.6-2.8 3.4 1.1 5.3 7.3 0 3.7-5.3 7.3-3.4 1.1-8.6-2.8-2.1-2.9Z"
      transform={`translate(${32 + x} 24) scale(${scale})`}
      fill="currentColor"
    />
  ))
}

function EightRaySun({ x }: { x: number }) {
  return (
    <g transform={`translate(${x} 24)`} data-symbol="sun">
      {Array.from({ length: 8 }, (_, index) => (
        <rect
          key={index}
          data-sun-ray="true"
          x="-1.55"
          y="-10.5"
          width="3.1"
          height="5.2"
          rx="0.8"
          transform={`rotate(${index * 45})`}
          fill="currentColor"
        />
      ))}
      <circle r="5.2" fill="currentColor" />
      <circle r="2" fill="var(--insignia-cutout, #2a2116)" />
    </g>
  )
}

function Suns({ count }: { count: number }) {
  const spacing = count === 3 ? 18 : 22
  const scale = count === 3 ? 0.76 : count === 1 ? 1.05 : 0.9
  return positionsFor(count, spacing).map((x, index) => (
    <g key={index} transform={`translate(${32 + x} 24) scale(${scale}) translate(${-32 - x} -24)`}>
      <EightRaySun x={32 + x} />
    </g>
  ))
}

function Triangles({ count }: { count: number }) {
  const spacing = count === 3 ? 17 : 21
  const scale = count === 1 ? 1.08 : 1
  return positionsFor(count, spacing).map((x, index) => (
    <g key={index} data-symbol="triangle" transform={`translate(${32 + x} 24) scale(${scale})`}>
      <path d="M0-10 8.5 8H-8.5Z" fill="currentColor" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M0-3.7 3.4 3.8H-3.4Z" fill="var(--insignia-cutout, #2a2116)" />
    </g>
  ))
}

function Chevrons({ count }: { count: number }) {
  const yPositions = count === 3 ? [13, 24, 35] : [24]
  return yPositions.map((y, index) => (
    <path
      key={index}
      data-symbol="chevron"
      d={`M16 ${y} 32 ${y - 4} 48 ${y} 48 ${y + 5} 32 ${y + 1} 16 ${y + 5}Z`}
      fill="currentColor"
    />
  ))
}

function WatchfulEyes() {
  return (
    <g>
      {[20, 44].map((x) => (
        <g key={x} transform={`translate(${x} 25)`} data-symbol="eye">
          <path d="M-10 0Q0-6.5 10 0Q0 6.5-10 0Z" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinejoin="round" />
          <path d="M-8-5Q0-9 8-5" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
          <circle r="2.8" fill="currentColor" />
        </g>
      ))}
    </g>
  )
}

function GenericFlag() {
  return (
    <g data-symbol="generic-flag" fill="currentColor">
      <rect x="15" y="8" width="4" height="34" rx="2" />
      <path d="M19 10C29 6 38 14 50 9V29C39 34 29 25 19 30Z" />
      <rect x="11" y="40" width="12" height="3" rx="1.5" />
    </g>
  )
}

function InsigniaArtwork({ spec }: { spec: InsigniaSpec }) {
  switch (spec.family) {
    case 'star': return <Stars count={spec.count} />
    case 'sun': return <Suns count={spec.count} />
    case 'triangle': return <Triangles count={spec.count} />
    case 'chevron': return <Chevrons count={spec.count} />
    case 'eyes': return <WatchfulEyes />
    case 'flag': return <GenericFlag />
  }
}

export function PieceInsignia({
  rank,
  size = 'medium',
  decorative = false,
  className = '',
}: PieceInsigniaProps) {
  const accessibility = decorative
    ? { 'aria-hidden': true as const }
    : { role: 'img', 'aria-label': rank === 'FLAG' ? 'Flag' : `${RANK_LABELS[rank]} insignia` }

  return (
    <svg
      {...accessibility}
      className={`piece-insignia piece-insignia--${size} ${className}`.trim()}
      viewBox="0 0 64 48"
      focusable="false"
      xmlns="http://www.w3.org/2000/svg"
    >
      <InsigniaArtwork spec={INSIGNIA_SPECS[rank]} />
    </svg>
  )
}

export function HiddenPieceInsignia({ className = '' }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      className={`piece-insignia piece-insignia--medium hidden-piece-insignia ${className}`.trim()}
      viewBox="0 0 64 48"
      focusable="false"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path d="M32 6 51 13V26C51 36 43 42 32 45 21 42 13 36 13 26V13Z" fill="none" stroke="currentColor" strokeWidth="3" />
      <path d="M22 18 42 34M42 18 22 34" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
      <circle cx="32" cy="26" r="3.5" fill="currentColor" />
    </svg>
  )
}
