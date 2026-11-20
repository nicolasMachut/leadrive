// ============================================================================
// SectionHeader — en-tête de section IN-page (à ne pas confondre avec PageHeader).
// PageHeader = H1 de page (border-b, title XXL, mb-7). SectionHeader = sous-titre
// d'une section/card dans une page (size sm pour inline list/inbox, md pour card
// headers comme Brief IA / Conversation / Pilotage).
//
// Compose : eyebrow mono uppercase + titre display Bricolage + count optionnel
// + actions à droite. Bordered=true pour les card headers (border-b tan).
// ============================================================================

import { ReactNode } from "react";

type EyebrowTone = "accent" | "muted" | "success" | "warning" | "danger";
type Size = "sm" | "md";

interface SectionHeaderProps {
  /** Eyebrow mono uppercase au-dessus du titre. */
  eyebrow?: string;
  /** Couleur de l'eyebrow. accent par défaut pour signaler la section. */
  eyebrowTone?: EyebrowTone;
  /** Titre H2 en font-display Bricolage. */
  title: ReactNode;
  /** Compteur en JetBrains Mono à côté du titre (ex: nombre total prospects). */
  count?: ReactNode;
  /** Actions à droite (boutons, liens, filtres). */
  actions?: ReactNode;
  /** sm = titre 14-15px (inline), md = titre 18px (card header). */
  size?: Size;
  /** border-b tan + padding standard de card header (px-6 py-4). */
  bordered?: boolean;
  className?: string;
}

const EYEBROW_TONE: Record<EyebrowTone, string> = {
  accent: "text-[var(--color-accent)]",
  muted: "text-[var(--color-lr-text-tertiary)]",
  success: "text-success",
  warning: "text-warning",
  danger: "text-danger",
};

const TITLE_SIZE: Record<Size, string> = {
  sm: "text-[15px]",
  md: "text-[18px]",
};

export default function SectionHeader({
  eyebrow,
  eyebrowTone = "accent",
  title,
  count,
  actions,
  size = "md",
  bordered = false,
  className = "",
}: SectionHeaderProps) {
  const wrapper = bordered
    ? "flex flex-wrap items-center justify-between gap-3 border-b border-[var(--border)] px-6 py-4"
    : "flex flex-wrap items-center justify-between gap-3";

  return (
    <div className={`${wrapper} ${className}`}>
      <div className="min-w-0">
        {eyebrow && (
          <p
            className={`font-mono text-[10px] font-semibold uppercase tracking-[0.18em] ${EYEBROW_TONE[eyebrowTone]}`}
          >
            {eyebrow}
          </p>
        )}
        <div className="mt-0.5 flex items-baseline gap-2">
          <h2
            className={`font-display ${TITLE_SIZE[size]} font-bold tracking-display-tight text-[var(--foreground)]`}
          >
            {title}
          </h2>
          {count != null && (
            <span className="font-mono text-[13px] font-semibold text-[var(--color-lr-text-tertiary)] tabular-nums">
              {count}
            </span>
          )}
        </div>
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}
