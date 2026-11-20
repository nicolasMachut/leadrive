// ============================================================================
// RoadStripes, Motif signature Leadrive
// Reprend les 2 bandes parallelogrammes du logo (perspective road-stripes).
// Utilisable comme :
//   - eyebrow decoration avant un titre
//   - separateur de section
//   - empty state visual
//   - loading indicator (avec animation)
// Couleur via currentColor : herite du parent (text-accent / text-white / etc.)
// ============================================================================

interface RoadStripesProps {
  size?: number;
  className?: string;
  /** Si true, opacite reduite sur la 2eme bande comme dans le logo. */
  duotone?: boolean;
  /** Si true, anime les bandes (pulse subtil). */
  animated?: boolean;
}

export default function RoadStripes({ size = 16, className = "", duotone = true, animated = false }: RoadStripesProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`shrink-0 ${className}`}
      aria-hidden="true"
    >
      <path
        d="M4 42 Q4 46 8 46 L15 46 Q19 46 19 42 L26 8 L18 8 Z"
        fill="currentColor"
        className={animated ? "origin-bottom-left animate-pulse-stripe-1" : ""}
      />
      <path
        d="M30 8 L38 8 L45 42 Q45 46 41 46 L34 46 Q30 46 30 42 Z"
        fill="currentColor"
        opacity={duotone ? 0.5 : 1}
        className={animated ? "origin-bottom-right animate-pulse-stripe-2" : ""}
      />
    </svg>
  );
}
