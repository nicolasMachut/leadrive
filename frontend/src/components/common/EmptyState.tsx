// ============================================================================
// EmptyState — pattern éditorial signature Leadrive
// Triptyque : eyebrow mono uppercase wide-tracked + titre display Bricolage
// + paragraphe Geist + action optionnelle + divider-gradient.
// Remplace les 9+ inlines dispersés (inbox vide, pipeline vide, filtres vides,
// pas de fiche IA, pas d'entonnoir...) par une source unique de vérité.
// ============================================================================

import { ReactNode } from "react";

type EyebrowTone = "accent" | "muted" | "success" | "warning" | "danger";
type Align = "start" | "center";
type Size = "sm" | "md" | "lg";

interface EmptyStateProps {
  /** Eyebrow mono uppercase au-dessus du titre (optionnel). */
  eyebrow?: string;
  /** Couleur de l'eyebrow. accent = ember (call-to-action), muted = tertiary (état neutre). */
  eyebrowTone?: EyebrowTone;
  /** Titre principal en font-display Bricolage. */
  title: ReactNode;
  /** Description Geist body sous le titre. */
  description?: ReactNode;
  /** Action(s) sous la description : typiquement un <Link className="btn-primary">. */
  action?: ReactNode;
  /** Visuel décoratif au-dessus de l'eyebrow (icône, pulse bubbles, illustration). */
  visual?: ReactNode;
  /** Alignement du bloc. center pour les empty states pleine surface,
   *  start pour ceux dans une colonne / sidebar. */
  align?: Align;
  /** Taille du titre + des paddings. sm = 16px (sub-cards), md = 20px (sidebar/list),
   *  lg = 28px (pleine page, hero empty). */
  size?: Size;
  /** Niveau de heading sémantique. Par défaut h3 (sous un h2 typique de section).
   *  Adapter à la hiérarchie réelle du contexte d'usage (a11y). */
  headingLevel?: 2 | 3 | 4;
  /** Affiche un divider-gradient ember en bas du bloc. */
  divider?: boolean;
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
  sm: "text-[16px]",
  md: "text-[20px]",
  lg: "text-[28px]",
};

const CONTAINER_PADDING: Record<Size, string> = {
  sm: "py-6",
  md: "py-10",
  lg: "py-12",
};

const DESCRIPTION_MAXW: Record<Size, string> = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-md",
};

export default function EmptyState({
  eyebrow,
  eyebrowTone = "muted",
  title,
  description,
  action,
  visual,
  align = "center",
  size = "md",
  headingLevel = 3,
  divider = false,
  className = "",
}: EmptyStateProps) {
  const isCenter = align === "center";
  const HeadingTag: "h2" | "h3" | "h4" = headingLevel === 2 ? "h2" : headingLevel === 4 ? "h4" : "h3";
  return (
    <div
      className={`flex flex-col gap-2 px-5 animate-fade-in-up ${CONTAINER_PADDING[size]} ${
        isCenter ? "items-center text-center" : "items-start text-left"
      } ${className}`}
    >
      {visual && <div className={`mb-4 ${isCenter ? "" : "self-start"}`}>{visual}</div>}
      {eyebrow && (
        <p
          className={`font-mono text-[10px] font-semibold uppercase tracking-[0.18em] ${EYEBROW_TONE[eyebrowTone]}`}
        >
          {eyebrow}
        </p>
      )}
      <HeadingTag
        className={`font-display ${TITLE_SIZE[size]} font-bold tracking-display-tight leading-[1.1] text-[var(--foreground)]`}
      >
        {title}
      </HeadingTag>
      {description && (
        <p
          className={`text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)] ${DESCRIPTION_MAXW[size]}`}
        >
          {description}
        </p>
      )}
      {action && <div className="mt-3">{action}</div>}
      {divider && <div className={`divider-gradient mt-6 w-24 ${isCenter ? "" : "self-start"}`} aria-hidden="true" />}
    </div>
  );
}
