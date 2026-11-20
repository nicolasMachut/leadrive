interface RoadDividerProps {
  /** Label optionnel centre (style eyebrow uppercase wide-tracked) */
  label?: string;
  /** accent (bleu brand) ou phare (jaune signature) */
  variant?: "accent" | "phare";
  className?: string;
}

/**
 * RoadDivider, separateur signature Leadrive. 3 stripes paralleles
 * (echo du logo road-stripes) qui s'estompent sur les bords, avec un label
 * eyebrow optionnel centre. Remplace les <hr> generiques entre sections.
 *
 * Usage :
 *   <RoadDivider />                                        // sans label
 *   <RoadDivider label="Section 02 · La solution" />       // avec label
 *   <RoadDivider label="Win" variant="phare" />            // accent jaune
 */
export default function RoadDivider({
  label,
  variant = "accent",
  className = "",
}: RoadDividerProps) {
  const couleur = variant === "phare" ? "var(--color-phare)" : "var(--color-accent)";

  // 3 lignes : faible / forte / faible, donne l'impression d'une trace de route
  // (centerline + bordures laterales) sans tomber dans le dessin litteral.
  const stripes = (
    <div className="flex flex-1 flex-col gap-[3px]" aria-hidden="true">
      <div
        className="h-px"
        style={{ background: `linear-gradient(90deg, transparent, ${couleur}33, transparent)` }}
      />
      <div
        className="h-px"
        style={{ background: `linear-gradient(90deg, transparent, ${couleur}, transparent)` }}
      />
      <div
        className="h-px"
        style={{ background: `linear-gradient(90deg, transparent, ${couleur}33, transparent)` }}
      />
    </div>
  );

  return (
    <div
      className={`flex items-center gap-4 ${className}`}
      role="separator"
      aria-label={label}
    >
      {stripes}
      {label && (
        <span
          className="font-mono shrink-0 text-[11px] font-semibold uppercase tracking-[0.18em]"
          style={{ color: couleur }}
        >
          {label}
        </span>
      )}
      {label && stripes}
    </div>
  );
}
