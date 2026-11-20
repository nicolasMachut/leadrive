"use client";

import { useEffect } from "react";
import Link from "next/link";
import * as Sentry from "@sentry/nextjs";
import ForceLightMode from "@/components/common/ForceLightMode";
import LeadriveLogo from "@/components/common/LeadriveLogo";

/**
 * In-app error boundary. Catche les erreurs rendues dans les routes app/.
 * Style Atelier (eyebrow ember + display + CTA retry + retour accueil) + Sentry
 * capture. Pas de dark mode (vu en mode déclenché, garder cohérent avec 404).
 */
export default function GlobalAppError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    Sentry.captureException(error);
  }, [error]);

  return (
    <div className="relative min-h-screen overflow-hidden bg-[var(--background)]">
      <ForceLightMode />

      <div className="bar-gradient fixed top-0 left-0 right-0 z-[200]" />

      <div className="absolute inset-0 bg-stripes-pattern-faint pointer-events-none" aria-hidden="true" />
      <div
        className="pointer-events-none absolute right-[-180px] top-[-120px] h-[460px] w-[460px] rounded-full opacity-30 blur-3xl"
        style={{ background: "radial-gradient(circle, rgba(229,72,77,0.30) 0%, transparent 70%)" }}
        aria-hidden="true"
      />

      <header className="relative z-10 mx-auto flex max-w-6xl items-center px-6 py-6">
        <Link href="/" className="transition hover:opacity-80">
          <LeadriveLogo variant="light" height={28} priority className="w-auto" />
        </Link>
      </header>

      <main className="relative z-10 mx-auto flex min-h-[calc(100vh-220px)] max-w-3xl flex-col items-start justify-center px-6 py-12">
        <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-danger">
          Erreur inattendue
        </p>
        <h1 className="mt-4 font-display text-[40px] font-bold leading-[1.02] tracking-display-tight text-[var(--foreground)] sm:text-[52px]">
          Quelque chose s&apos;est cassé.
        </h1>
        <p className="mt-5 max-w-xl text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
          L&apos;équipe a été notifiée automatiquement. En attendant, vous pouvez réessayer, ou retourner au tableau de bord.
          Vos prospects et rendez-vous restent intacts, l&apos;erreur n&apos;a affecté que l&apos;affichage.
        </p>

        {error.digest && (
          <p className="mt-4 font-mono text-[11px] text-[var(--color-lr-text-tertiary)]">
            Référence : <span className="text-[var(--foreground)]">{error.digest}</span>
          </p>
        )}

        <div className="divider-gradient mt-10 w-40" />

        <div className="mt-8 flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={reset}
            className="btn-primary btn-magnetic rounded-xl px-5 py-3 text-sm font-semibold"
          >
            Réessayer
          </button>
          <Link
            href="/dashboard"
            className="rounded-xl border border-[var(--border)] bg-[var(--card)] px-5 py-3 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--muted)]"
          >
            Tableau de bord
          </Link>
          <a
            href="mailto:contact@leadrive.fr?subject=Erreur%20app%20Leadrive"
            className="rounded-xl px-5 py-3 text-sm font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:text-[var(--foreground)]"
          >
            Contacter le support
          </a>
        </div>
      </main>

      <footer className="relative z-10 mx-auto max-w-6xl border-t border-[var(--border)] px-6 py-5">
        <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
          Leadrive · Performance commerciale automobile
        </p>
      </footer>
    </div>
  );
}
