// ============================================================================
// DataSheet — fiche technique automotive signature Leadrive
// Pattern <dl> avec lignes label uppercase mono / valeur numeric tabular.
// Lecture "instrument de bord" : alignement vertical des valeurs, hairlines
// tan entre rows, pas de couleur autre que ink + ink-tertiary.
//
// 12+ inlines remplacés (Lead detail Pilotage/Véhicule/Specs/Commercial,
// Dashboard hero data-sheet vendeur/manager...).
// ============================================================================

import { ReactNode } from "react";

export interface DataRow {
  /** Libellé en mono uppercase wide-tracked (ex: "Km", "Année", "Énergie"). */
  label: ReactNode;
  /** Valeur affichée. mono=true active JetBrains Mono tabular-nums (défaut pour chiffres). */
  value: ReactNode;
  /** Force la mise en JetBrains Mono tabular-nums (par défaut true). */
  mono?: boolean;
}

type Variant = "nude" | "card";

interface DataSheetProps {
  rows: DataRow[];
  /** Wrapper visuel. nude = juste les rows (sidebar lead-detail), card = bordure + bg + padding (hero dashboards). */
  variant?: Variant;
  /** Eyebrow optionnel affiché au-dessus de la liste. */
  eyebrow?: string;
  /** Hairlines entre rows (défaut true). */
  bordered?: boolean;
  className?: string;
}

export default function DataSheet({
  rows,
  variant = "nude",
  eyebrow,
  bordered = true,
  className = "",
}: DataSheetProps) {
  const wrapper =
    variant === "card"
      ? "rounded-xl border border-[var(--border)] bg-[var(--card)] px-4 py-3"
      : "";

  return (
    <div className={`${wrapper} ${className}`}>
      {eyebrow && (
        <p className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
          {eyebrow}
        </p>
      )}
      <dl className="space-y-0">
        {rows.map((row, i) => {
          const isLast = i === rows.length - 1;
          const monoVal = row.mono ?? true;
          return (
            <div
              key={i}
              className={`group flex items-baseline justify-between gap-3 py-2 transition-colors duration-200 ease-cinema hover:bg-[var(--color-accent-subtle)] -mx-2 px-2 rounded-md ${
                bordered && !isLast ? "border-b border-[var(--border)]" : ""
              }`}
            >
              <dt className="font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)] transition-colors duration-200 ease-cinema group-hover:text-[var(--color-lr-text-secondary)]">
                {row.label}
              </dt>
              <dd
                className={`text-[12.5px] font-semibold text-[var(--foreground)] ${
                  monoVal ? "font-numeric" : ""
                }`}
              >
                {row.value}
              </dd>
            </div>
          );
        })}
      </dl>
    </div>
  );
}
