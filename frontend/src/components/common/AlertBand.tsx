// ============================================================================
// AlertBand — bandeau éditorial contextuel (≠ Alert minimal).
// Structure : icon-container coloré + eyebrow mono uppercase + display title.
// Utilisé pour les alertes "fortes" qui demandent une action (RDV en retard,
// RDV aujourd'hui, lead pas contacté, rappel urgent prospect...).
//
// Alert (common existant) reste pour les box d'erreur formulaire minimales.
// AlertBand pour les bandeaux contextuels en haut/dans le flow d'une page.
// ============================================================================

import { ReactNode } from "react";

type Tone = "danger" | "accent" | "warning" | "success" | "info";

const TONE: Record<
  Tone,
  { border: string; bg: string; eyebrow: string; iconBg: string; iconText: string }
> = {
  danger: {
    border: "border-danger/30",
    bg: "bg-danger/5",
    eyebrow: "text-danger",
    iconBg: "bg-danger/15",
    iconText: "text-danger",
  },
  accent: {
    border: "border-[var(--color-accent)]/30",
    bg: "bg-[var(--color-accent-subtle)]",
    eyebrow: "text-[var(--color-accent)]",
    iconBg: "bg-[var(--color-accent)]/15",
    iconText: "text-[var(--color-accent)]",
  },
  warning: {
    border: "border-warning/30",
    bg: "bg-warning/5",
    eyebrow: "text-warning",
    iconBg: "bg-warning/15",
    iconText: "text-warning",
  },
  success: {
    border: "border-success/30",
    bg: "bg-success/5",
    eyebrow: "text-success",
    iconBg: "bg-success/15",
    iconText: "text-success",
  },
  info: {
    border: "border-[var(--border)]",
    bg: "bg-[var(--muted)]",
    eyebrow: "text-[var(--color-lr-text-secondary)]",
    iconBg: "bg-[var(--card)]",
    iconText: "text-[var(--color-lr-text-secondary)]",
  },
};

interface AlertBandProps {
  tone?: Tone;
  /** Icône (<svg>). Si absent et pulse=true, on affiche un pulse dot du ton. */
  icon?: ReactNode;
  /** Anime un ping autour du dot (réservé aux danger urgents). */
  pulse?: boolean;
  /** Eyebrow mono uppercase (ex: "RDV en retard"). */
  eyebrow: string;
  /** Titre principal display Bricolage. */
  title: ReactNode;
  /** Action(s) à droite (bouton, lien). */
  action?: ReactNode;
  className?: string;
}

export default function AlertBand({
  tone = "accent",
  icon,
  pulse = false,
  eyebrow,
  title,
  action,
  className = "",
}: AlertBandProps) {
  const t = TONE[tone];
  const dotBg =
    tone === "danger"
      ? "bg-danger"
      : tone === "warning"
      ? "bg-warning"
      : tone === "success"
      ? "bg-success"
      : "bg-[var(--color-accent)]";

  return (
    <div
      role={tone === "danger" && pulse ? "alert" : "status"}
      aria-live={tone === "danger" && pulse ? "assertive" : "polite"}
      className={`flex items-center gap-3 rounded-xl border animate-fade-in-up ${t.border} ${t.bg} px-5 py-4 ${className}`}
    >
      {icon ? (
        <span
          className={`inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${t.iconBg} ${t.iconText}`}
          aria-hidden="true"
        >
          {icon}
        </span>
      ) : pulse ? (
        <span className="relative flex h-2.5 w-2.5 shrink-0" aria-hidden="true">
          <span className={`absolute inset-0 rounded-full ${dotBg} animate-ping opacity-60`} />
          <span className={`relative h-2.5 w-2.5 rounded-full ${dotBg}`} />
        </span>
      ) : (
        <span
          className={`inline-block h-2.5 w-2.5 shrink-0 rounded-full ${dotBg}`}
          aria-hidden="true"
        />
      )}
      <div className="min-w-0 flex-1">
        <p
          className={`font-mono text-[10px] font-semibold uppercase tracking-[0.18em] ${t.eyebrow}`}
        >
          {eyebrow}
        </p>
        <p className="mt-0.5 font-display text-[15px] font-semibold tracking-display-tight text-[var(--foreground)]">
          {title}
        </p>
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}
