"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface RepStatsPrecision {
  totalFeedbacks: number;
  precisionVisible: boolean;
  precisionIa: number;
  palierActuel: string;
  palierDescription: string;
  palierSuivant: string | null;
  feedbacksRestantsPalier: number;
}

export default function PrecisionIaCard() {
  const apiFetch = useApiClient();
  const [stats, setStats] = useState<RepStatsPrecision | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    apiFetch(`${API_BASE}/api/feedback/precision-ia`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => setStats(data))
      .catch(() => {})
      .finally(() => setLoaded(true));
  }, [apiFetch]);

  if (!loaded || !stats) return null;
  if (stats.totalFeedbacks === 0) return null;

  if (!stats.precisionVisible) {
    const cap = Math.min(100, (stats.totalFeedbacks / 20) * 100);
    return (
      <div className="card-leadrive p-5">
        <div className="flex items-center gap-2 mb-3">
          <span className="text-lg">{stats.palierActuel.split(" ")[0]}</span>
          <h3 className="text-sm font-semibold text-[var(--foreground)]">Apprentissage IA</h3>
        </div>
        <p className="text-xs text-[var(--color-lr-text-secondary)] mb-2">
          {stats.palierDescription}
        </p>
        <div className="h-2 w-full overflow-hidden rounded-full bg-[var(--muted)] mb-2">
          <div className="h-full bg-accent transition-all" style={{ width: `${cap}%` }} />
        </div>
        <p className="text-[11px] text-[var(--color-lr-text-tertiary)]">
          {stats.totalFeedbacks} retour{stats.totalFeedbacks > 1 ? "s" : ""} vendeurs ·
          {" "}
          encore {Math.max(0, 20 - stats.totalFeedbacks)} pour mesurer la précision
        </p>
      </div>
    );
  }

  const couleur = stats.precisionIa >= 70 ? "text-success"
    : stats.precisionIa >= 50 ? "text-warning"
    : "text-danger";

  const barColor = stats.precisionIa >= 70 ? "bg-success"
    : stats.precisionIa >= 50 ? "bg-warning"
    : "bg-danger";

  return (
    <div className="card-leadrive p-5">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-[var(--foreground)]">Précision de votre IA</h3>
        <span className="rounded-full border border-[var(--border)] bg-[var(--muted)] px-2 py-0.5 text-[11px] font-medium text-[var(--foreground)]">
          {stats.palierActuel}
        </span>
      </div>
      <div className="flex items-baseline gap-2 mb-2">
        <span className={`text-3xl font-bold tabular-nums ${couleur}`}>{stats.precisionIa}%</span>
        <span className="text-xs text-[var(--color-lr-text-secondary)]">de qualifications correctes</span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-[var(--muted)] mb-2">
        <div className={`h-full transition-all ${barColor}`} style={{ width: `${stats.precisionIa}%` }} />
      </div>
      <p className="text-[11px] text-[var(--color-lr-text-tertiary)]">
        Basé sur {stats.totalFeedbacks} retour{stats.totalFeedbacks > 1 ? "s" : ""} vendeurs
        {stats.palierSuivant && stats.feedbacksRestantsPalier > 0 && (
          <> · encore {stats.feedbacksRestantsPalier} pour {stats.palierSuivant}</>
        )}
      </p>
    </div>
  );
}
