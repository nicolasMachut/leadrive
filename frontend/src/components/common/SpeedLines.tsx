type Size = "sm" | "md" | "lg";

interface SpeedLinesProps {
  /** sm 80px, md 128px, lg 192px de large */
  size?: Size;
  /** Texte optionnel sous le loader */
  label?: string;
  /** Variant accent (bleu) ou phare (jaune-orange) */
  variant?: "accent" | "phare";
  className?: string;
}

const WIDTH: Record<Size, number> = { sm: 80, md: 128, lg: 192 };
const STREAK_WIDTH: Record<Size, number> = { sm: 28, md: 44, lg: 64 };

/**
 * SpeedLines, loader signature Leadrive. 3 streaks horizontaux qui defilent
 * de gauche a droite en stagger. Lecture instantanee : "ca avance, ca
 * accelere". Remplace le spinner rond generique partout ou ca a du sens.
 *
 * Usage : <SpeedLines label="Chargement des prospects" />
 */
export default function SpeedLines({
  size = "md",
  label,
  variant = "accent",
  className = "",
}: SpeedLinesProps) {
  const width = WIDTH[size];
  const streakWidth = STREAK_WIDTH[size];
  const couleur = variant === "phare" ? "var(--color-phare)" : "var(--color-accent)";

  return (
    <div
      className={`flex flex-col items-center gap-3 ${className}`}
      role="status"
      aria-live="polite"
      aria-label={label ?? "Chargement"}
    >
      <div
        className="relative flex flex-col gap-1"
        style={{ width }}
      >
        {/* 3 pistes superposees avec stagger pour effet trace de vitesse */}
        <div className="relative h-[3px] overflow-hidden rounded-full">
          <div
            className="absolute inset-y-0 rounded-full animate-speed-streak-1"
            style={{
              width: streakWidth,
              background: `linear-gradient(90deg, transparent, ${couleur}, transparent)`,
            }}
          />
        </div>
        <div className="relative h-[3px] overflow-hidden rounded-full">
          <div
            className="absolute inset-y-0 rounded-full animate-speed-streak-2"
            style={{
              width: streakWidth,
              background: `linear-gradient(90deg, transparent, ${couleur}, transparent)`,
              opacity: 0.7,
            }}
          />
        </div>
        <div className="relative h-[3px] overflow-hidden rounded-full">
          <div
            className="absolute inset-y-0 rounded-full animate-speed-streak-3"
            style={{
              width: streakWidth,
              background: `linear-gradient(90deg, transparent, ${couleur}, transparent)`,
              opacity: 0.4,
            }}
          />
        </div>
      </div>
      {label && (
        <span className="text-xs font-medium text-[var(--color-lr-text-secondary)]">
          {label}
        </span>
      )}
    </div>
  );
}
