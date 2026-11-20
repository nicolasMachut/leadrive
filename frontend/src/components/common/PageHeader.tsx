// ============================================================================
// PageHeader, En-tete de page signature Leadrive (palette Atelier).
// Pattern editorial : eyebrow mono uppercase wide-tracked + titre display
// Bricolage + sous-titre. Aligne avec les hero dashboards et la landing pour
// une coherence visuelle totale a travers l'app.
//
// Variants :
// - default : H1 28→34px, border-b tan, mb-7. Pour les pages principales (stats,
//   equipe, planning, /aide, /import).
// - compact : H1 24px, pas de border-b, mb-4. Pour les pages qui ont une barre
//   de filtres juste sous le titre (/leads, /inbox header).
// ============================================================================

import { ReactNode } from "react";

type Variant = "default" | "compact";

interface PageHeaderProps {
  /** Eyebrow mono uppercase (ex: "Tableau de bord", "Prospects", "Inbox"). */
  eyebrow?: string;
  /** Titre principal H1, affiche en font-display Bricolage Grotesque. */
  title: string;
  /** Sous-titre / description optionnelle. */
  subtitle?: ReactNode;
  /** Compteur en JetBrains Mono affiche en exposant a cote du titre (ex: nombre total prospects). */
  count?: ReactNode;
  /** Actions a droite (boutons, filtres). */
  actions?: ReactNode;
  /** default = page principale avec border-b, compact = inline barre de filtres. */
  variant?: Variant;
  /** @deprecated conserve pour compat, mais ignore. */
  showStripes?: boolean;
  className?: string;
}

export default function PageHeader({
  eyebrow,
  title,
  subtitle,
  count,
  actions,
  variant = "default",
  className = "",
}: PageHeaderProps) {
  const isCompact = variant === "compact";
  const wrapper = isCompact
    ? `mb-4 ${className}`
    : `mb-7 border-b border-[var(--border)] pb-6 ${className}`;
  const titleSize = isCompact
    ? "text-[24px]"
    : "text-[28px] sm:text-[34px]";

  return (
    <div className={wrapper}>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0 flex-1">
          {eyebrow && (
            <p className="inline-flex items-center gap-2 font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
              {/* Signature dot ember : repris du `.section-label` pour donner du poids
                  visuel à l'eyebrow sur toutes les pages produit. */}
              <span
                className="inline-block h-1.5 w-1.5 shrink-0 rounded-full"
                style={{ background: "linear-gradient(135deg, #A8401F, #D9603A)" }}
                aria-hidden="true"
              />
              {eyebrow}
            </p>
          )}
          <div className={`${eyebrow ? "mt-2" : ""} flex items-baseline gap-2`}>
            <h1
              className={`font-display ${titleSize} font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)]`}
            >
              {title}
            </h1>
            {count != null && (
              <span className="font-mono text-[13px] font-semibold text-[var(--color-lr-text-tertiary)] tabular-nums">
                {count}
              </span>
            )}
          </div>
          {subtitle && (
            <p className="mt-2 max-w-2xl text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              {subtitle}
            </p>
          )}
        </div>
        {actions && (
          <div className="flex flex-wrap items-center gap-2">{actions}</div>
        )}
      </div>
    </div>
  );
}
