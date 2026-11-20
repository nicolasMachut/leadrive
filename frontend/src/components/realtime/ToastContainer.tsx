"use client";

import Link from "next/link";
import { useRealtime, type ToastNotification, type ToastTone } from "@/lib/realtime";

/**
 * Pile de toasts en bas a droite (bas pleine largeur sur mobile).
 * Supporte les tonalites info/success/warning/danger et les notifications
 * realtime (avec lien "Voir").
 *
 * Style editorial Atelier : eyebrow mono + titre + sous-titre + dot pulsant.
 * Plus de side-stripe (banni impeccable).
 */
export default function ToastContainer() {
  const { toasts, dismissToast } = useRealtime();

  if (toasts.length === 0) return null;

  return (
    <div className="pointer-events-none fixed inset-x-4 bottom-4 z-[120] flex flex-col-reverse items-end gap-2 sm:right-4 sm:left-auto sm:max-w-sm">
      {toasts.map((t) => (
        <ToastCard key={t.id} toast={t} onDismiss={() => dismissToast(t.id)} />
      ))}
    </div>
  );
}

const TONE_STYLES: Record<ToastTone, { ring: string; eyebrow: string; dot: string; iconBg: string; iconColor: string }> = {
  info: {
    ring: "ring-[var(--color-accent)]/25",
    eyebrow: "text-[var(--color-accent)]",
    dot: "bg-[var(--color-accent)]",
    iconBg: "bg-[var(--color-accent-subtle)]",
    iconColor: "text-[var(--color-accent)]",
  },
  success: {
    ring: "ring-success/25",
    eyebrow: "text-success",
    dot: "bg-success",
    iconBg: "bg-success/10",
    iconColor: "text-success",
  },
  warning: {
    ring: "ring-warning/25",
    eyebrow: "text-warning",
    dot: "bg-warning",
    iconBg: "bg-warning/10",
    iconColor: "text-warning",
  },
  danger: {
    ring: "ring-danger/25",
    eyebrow: "text-danger",
    dot: "bg-danger",
    iconBg: "bg-danger/10",
    iconColor: "text-danger",
  },
};

const TONE_LABEL: Record<ToastTone, string> = {
  info: "Info",
  success: "OK",
  warning: "Attention",
  danger: "Erreur",
};

const TONE_ICONS: Record<ToastTone, string> = {
  info: "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
  success: "M5 13l4 4L19 7",
  warning: "M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z",
  danger: "M6 18L18 6M6 6l12 12",
};

function ToastCard({ toast, onDismiss }: { toast: ToastNotification; onDismiss: () => void }) {
  const tone = toast.tone ?? "info";
  const styles = TONE_STYLES[tone];
  const label = TONE_LABEL[tone];

  return (
    <div
      className={`pointer-events-auto w-full animate-slide-up rounded-xl border border-[var(--border)] bg-[var(--card)] shadow-card ring-1 ${styles.ring}`}
      role="status"
      aria-live="polite"
    >
      <div className="flex items-start gap-3 p-4">
        {/* Icon en pastille */}
        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${styles.iconBg}`}>
          <svg className={`h-4 w-4 ${styles.iconColor}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d={TONE_ICONS[tone]} />
          </svg>
        </div>
        <div className="min-w-0 flex-1">
          {/* Eyebrow tonalite */}
          <p className={`font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] ${styles.eyebrow}`}>
            {label}
          </p>
          {/* Title */}
          <p className="mt-0.5 font-display text-[13.5px] font-bold tracking-display-tight text-[var(--foreground)]">
            {toast.titre}
          </p>
          {/* Message */}
          {toast.message && (
            <p className="mt-0.5 text-[12.5px] leading-snug text-[var(--color-lr-text-secondary)]">
              {toast.message}
            </p>
          )}
          {/* Cible link */}
          {toast.cible && (
            <Link
              href={toast.cible}
              onClick={onDismiss}
              className="mt-2 inline-block font-mono text-[10.5px] font-semibold uppercase tracking-[0.16em] text-[var(--color-accent)] transition-colors hover:text-[var(--color-accent-deep)]"
            >
              Voir →
            </Link>
          )}
          {/* Bouton d'action (ex. "Annuler" pour un undo) : execute puis ferme. */}
          {toast.action && (
            <button
              type="button"
              onClick={() => { toast.action!.onClick(); onDismiss(); }}
              className="mt-2 rounded-md border border-[var(--border)] px-2.5 py-1 font-mono text-[10.5px] font-semibold uppercase tracking-[0.16em] text-[var(--foreground)] transition-colors hover:bg-[var(--muted)]"
            >
              {toast.action.label}
            </button>
          )}
        </div>
        {/* Close */}
        <button
          type="button"
          onClick={onDismiss}
          className="shrink-0 rounded-md p-1 text-[var(--color-lr-text-tertiary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
          aria-label="Fermer la notification"
        >
          <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>
  );
}
