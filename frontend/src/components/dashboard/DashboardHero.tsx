// ============================================================================
// DashboardHero — cockpit éditorial signature Leadrive (vendeur + manager).
// Dédoublonne ~80 lignes JSX entre DashboardVendeur.tsx et DashboardManager.tsx,
// qui reconstruisaient le même hero (eyebrow date + display greeting +
// KPI géant 64px + DataSheet 2 lignes) avec des variations involontaires.
//
// Source unique de vérité pour le rythme cockpit. Quand on touche au hero,
// on touche ICI, jamais inline dans les pages.
// ============================================================================

import { ReactNode } from "react";
import DataSheet, { type DataRow } from "@/components/common/DataSheet";

interface HeroKpi {
  /** Label JetBrains Mono uppercase (ex: "RDV cette semaine"). */
  label: string;
  /** Valeur affichée. Si isEmpty=true, on bascule sur empty copy. */
  value: ReactNode;
  /** Copy display Bricolage si isEmpty=true (ex: "Aucun RDV pour l'instant."). */
  emptyTitle?: ReactNode;
  /** Hint sous emptyTitle (ex: "Une relance bien placée, et la semaine démarre."). */
  emptyHint?: ReactNode;
  /** Active le mode empty (KPI=0). */
  isEmpty?: boolean;
}

interface HeroProgress {
  /** Pourcentage 0-100. */
  value: number;
  /** Si true (objectif atteint), pulse success. */
  success?: boolean;
}

interface DashboardHeroProps {
  /** Mot ember à gauche de la date (ex: "Cockpit", "Bureau de pilotage"). */
  eyebrowLeft: string;
  /** Date du jour formatée (ex: "lundi 16 juin"). */
  eyebrowDate: string;
  /** H1 display Bricolage (ex: "Bonjour Nicolas."). */
  greeting: ReactNode;
  /** Sous-titre Geist body sous le greeting. */
  subtitle?: ReactNode;
  /** Barre de progression fine (objectif mensuel). */
  progress?: HeroProgress;
  /** KPI géant 64px JetBrains Mono (count-up géré par le caller). */
  hero?: HeroKpi;
  /** DataSheet 2-3 lignes à droite (appels jour / RDV mois / taux conversion). */
  sheet?: DataRow[];
}

export default function DashboardHero({
  eyebrowLeft,
  eyebrowDate,
  greeting,
  subtitle,
  progress,
  hero,
  sheet,
}: DashboardHeroProps) {
  const hasHero = !!hero;
  const hasSheet = !!sheet && sheet.length > 0;

  return (
    <header className="border-b border-[var(--border)] pb-7">
      <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-[var(--color-lr-text-tertiary)]">
        <span className="text-[var(--color-accent)]">{eyebrowLeft}</span>
        <span className="mx-2">·</span>
        {eyebrowDate}
      </p>
      <div className="mt-4 grid grid-cols-1 gap-8 lg:grid-cols-12 lg:items-end">
        {/* Greeting + subtitle + progress */}
        <div className={hasHero || hasSheet ? "lg:col-span-6" : "lg:col-span-12"}>
          <h1 className="font-display text-[36px] font-bold leading-[1.02] tracking-display-tight text-[var(--foreground)] break-words sm:text-[44px]">
            {greeting}
          </h1>
          {subtitle && (
            <p className="mt-2 text-[15px] text-[var(--color-lr-text-secondary)]">{subtitle}</p>
          )}
          {progress && (
            <div className="mt-3 h-1.5 w-full max-w-xs overflow-hidden rounded-full bg-[var(--muted)]">
              <div
                className="h-full rounded-full bg-[var(--color-accent)] animate-progress-fill"
                style={{
                  ["--progress-target" as string]: `${progress.value}%`,
                  width: `${progress.value}%`,
                }}
              />
            </div>
          )}
        </div>

        {/* KPI géant 64px ou empty editorial */}
        {hero && (
          <div className="lg:col-span-3">
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
              {hero.label}
            </p>
            {hero.isEmpty ? (
              <>
                <p className="mt-2 font-display text-[18px] font-bold leading-tight text-[var(--foreground)]">
                  {hero.emptyTitle}
                </p>
                {hero.emptyHint && (
                  <p className="mt-1 text-[12px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                    {hero.emptyHint}
                  </p>
                )}
              </>
            ) : (
              <p className="mt-1 font-numeric text-[64px] font-semibold leading-none tracking-[-0.04em] text-[var(--foreground)] animate-kpi-ignite">
                {hero.value}
              </p>
            )}
          </div>
        )}

        {/* DataSheet 2-3 lignes en card */}
        {hasSheet && (
          <div className="lg:col-span-3">
            <DataSheet rows={sheet} variant="card" />
          </div>
        )}
      </div>
    </header>
  );
}
