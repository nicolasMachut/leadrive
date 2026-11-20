import Link from "next/link";
import type { Metadata } from "next";
import ForceLightMode from "@/components/common/ForceLightMode";
import LeadriveLogo from "@/components/common/LeadriveLogo";

export const metadata: Metadata = {
  title: "Page introuvable",
  description: "Cette page n'existe pas ou a été déplacée.",
  robots: { index: false, follow: false },
};

/**
 * 404 éditoriale Atelier. Sand bg, eyebrow ember, display Bricolage, CTA retour.
 * Pas de dark mode (page publique, cf. règle CLAUDE.md / memory).
 */
export default function NotFound() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[var(--background)]">
      <ForceLightMode />

      {/* Signature gradient bar identique au layout app */}
      <div className="bar-gradient fixed top-0 left-0 right-0 z-[200]" />

      {/* Pattern road-stripes en très faible opacité, derrière le contenu */}
      <div className="absolute inset-0 bg-stripes-pattern-faint pointer-events-none" aria-hidden="true" />

      {/* Glow ember discret en haut à droite */}
      <div
        className="pointer-events-none absolute right-[-180px] top-[-120px] h-[460px] w-[460px] rounded-full opacity-30 blur-3xl"
        style={{ background: "radial-gradient(circle, rgba(217,96,58,0.35) 0%, transparent 70%)" }}
        aria-hidden="true"
      />

      <header className="relative z-10 mx-auto flex max-w-6xl items-center px-6 py-6">
        <Link href="/" className="transition hover:opacity-80">
          <LeadriveLogo variant="light" height={28} priority className="w-auto" />
        </Link>
      </header>

      <main className="relative z-10 mx-auto flex min-h-[calc(100vh-220px)] max-w-3xl flex-col items-start justify-center px-6 py-12">
        <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
          Erreur 404
        </p>
        <h1 className="mt-4 font-display text-[44px] font-bold leading-[1.02] tracking-display-tight text-[var(--foreground)] sm:text-[56px]">
          Cette page n&apos;existe pas, <span className="text-[var(--color-accent-deep)]">ou plus.</span>
        </h1>
        <p className="mt-5 max-w-xl text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
          Le lien que vous avez suivi est cassé, la page a été déplacée, ou son adresse contient une faute.
          Aucun de vos prospects ou rendez-vous n&apos;est concerné, c&apos;est juste un mauvais chemin.
        </p>

        {/* Hairline gradient signature */}
        <div className="divider-gradient mt-10 w-40" />

        <div className="mt-8 flex flex-wrap items-center gap-3">
          <Link
            href="/"
            className="btn-primary btn-magnetic rounded-xl px-5 py-3 text-sm font-semibold"
          >
            Retour à l&apos;accueil
          </Link>
          <Link
            href="/dashboard"
            className="rounded-xl border border-[var(--border)] bg-[var(--card)] px-5 py-3 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--muted)]"
          >
            Mon tableau de bord
          </Link>
          <Link
            href="/aide"
            className="rounded-xl px-5 py-3 text-sm font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:text-[var(--foreground)]"
          >
            Centre d&apos;aide
          </Link>
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
