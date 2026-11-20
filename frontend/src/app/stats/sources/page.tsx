"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useApiClient, formatErreur } from "@/lib/auth";
import AppLayout from "@/components/AppLayout";
import PageHeader from "@/components/common/PageHeader";
import Skeleton from "@/components/common/Skeleton";
import InfoIcon from "@/components/common/InfoIcon";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";
import Alert from "@/components/common/Alert";
import Button from "@/components/common/Button";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface SourceStat {
  source: string;
  nbLeads: number;
  nbRdv: number;
  tauxConversion: number;
}

interface SourcesData {
  periodeJours: number;
  totalLeads: number;
  sources: SourceStat[];
}

const PERIODES = [
  { value: 7, label: "7 jours" },
  { value: 30, label: "30 jours" },
  { value: 90, label: "90 jours" },
  { value: 365, label: "1 an" },
];

const PERIODE_VALUES = PERIODES.map((p) => p.value);

export default function StatsSourcesPage() {
  // Suspense boundary requis par useSearchParams() pour le prerender Next.js.
  return (
    <Suspense>
      <StatsSourcesContent />
    </Suspense>
  );
}

function StatsSourcesContent() {
  const apiFetch = useApiClient();
  const router = useRouter();
  const searchParams = useSearchParams();
  // Période persistée dans l'URL (CLAUDE.md règle #10 : F5 restitue l'état exact).
  const initialPeriode = PERIODE_VALUES.includes(Number(searchParams.get("periode")))
    ? Number(searchParams.get("periode"))
    : 30;
  const [periode, setPeriode] = useState(initialPeriode);
  const [data, setData] = useState<SourcesData | null>(null);
  const [loading, setLoading] = useState(true);
  const [erreur, setErreur] = useState("");

  // Sync période → URL sans scroll.
  useEffect(() => {
    const params = new URLSearchParams(searchParams.toString());
    if (periode !== 30) params.set("periode", String(periode));
    else params.delete("periode");
    router.replace(`/stats/sources${params.toString() ? "?" + params.toString() : ""}`, { scroll: false });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [periode]);

  const charger = useCallback(async () => {
    setLoading(true);
    setErreur("");
    try {
      const res = await apiFetch(`${API_BASE}/api/stats/sources?periode=${periode}`);
      if (res.ok) setData(await res.json());
      else setErreur(formatErreur(new Error(`HTTP ${res.status}`)));
    } catch (err) {
      setErreur(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [apiFetch, periode]);

  useEffect(() => { charger(); }, [charger]);

  // Max explicite (pas sources[0]) : indépendant de l'ordre de tri SQL.
  const maxLeads = Math.max(0, ...(data?.sources?.map((s) => s.nbLeads) ?? [0]));

  return (
    <AppLayout>
      <div className="mx-auto max-w-5xl px-6 py-6 space-y-6">
        <PageHeader
          eyebrow="Acquisition"
          title="Sources de leads"
          actions={
            <div className="flex rounded-lg border border-[var(--border)] bg-[var(--card)] p-0.5">
              {PERIODES.map(p => (
                <button
                  key={p.value}
                  onClick={() => setPeriode(p.value)}
                  className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                    p.value === periode
                      ? "bg-accent text-white"
                      : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"
                  }`}
                >
                  {p.label}
                </button>
              ))}
            </div>
          }
        />

        {loading && !data ? (
          <div className="space-y-6">
            <div className="card-leadrive p-6">
              <Skeleton className="h-3 w-32 mb-2" />
              <Skeleton className="h-9 w-20" />
            </div>
            <div className="card-leadrive p-6">
              <Skeleton className="h-5 w-44 mb-4" />
              <div className="space-y-3">
                {[1, 2, 3, 4, 5].map((i) => (
                  <div key={i} className="space-y-1">
                    <div className="flex items-center justify-between">
                      <Skeleton className="h-4 w-28" />
                      <Skeleton className="h-4 w-40" />
                    </div>
                    <Skeleton className="h-2 w-full" />
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : erreur && !data ? (
          <ErrorState message={erreur} onRetry={charger} />
        ) : data && data.sources.length > 0 ? (
          <>
            {erreur && (
              <div role="alert">
                <Alert tone="danger" className="flex flex-wrap items-center justify-between gap-3">
                  <span>{erreur}</span>
                  <Button variant="secondary" size="sm" onClick={charger}>Réessayer</Button>
                </Alert>
              </div>
            )}
            <div className="card-leadrive p-6">
              <div className="flex items-center gap-1.5 mb-1">
                <p className="text-xs text-[var(--color-lr-text-secondary)]">Leads sur la période</p>
                <InfoIcon>
                  Tous les leads créés sur la période, toutes origines confondues (CSV, texte libre, email automatique).
                </InfoIcon>
              </div>
              <p className="text-3xl font-bold tabular-nums text-[var(--foreground)]">
                {data.totalLeads}
              </p>
            </div>

            <div className="card-leadrive p-6">
              <div className="flex items-center gap-2 mb-4">
                <h2 className="display-h2 text-[var(--foreground)]">Répartition par source</h2>
                <InfoIcon>
                  Comparez la performance des différentes plateformes : Le Bon Coin, La Centrale… Le taux de conversion indique le pourcentage de leads qui aboutissent à un RDV.
                </InfoIcon>
              </div>
              <div className="space-y-3">
                {data.sources.map((src) => (
                  <div key={src.source} className="space-y-1">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium text-[var(--foreground)]">{src.source || "Non spécifié"}</span>
                      <div className="flex items-center gap-4 text-xs">
                        <span className="text-[var(--color-lr-text-secondary)]">
                          <strong className="text-[var(--foreground)]">{src.nbLeads}</strong> leads
                        </span>
                        <span className="text-[var(--color-lr-text-secondary)]">
                          <strong className="text-success">{src.nbRdv}</strong> RDV
                        </span>
                        <span
                          className={`font-semibold ${
                            src.tauxConversion >= 20
                              ? "text-success"
                              : src.tauxConversion >= 10
                                ? "text-warning"
                                : "text-[var(--color-lr-text-tertiary)]"
                          }`}
                        >
                          {src.tauxConversion}%
                        </span>
                      </div>
                    </div>
                    <div className="h-2 w-full rounded-full bg-[var(--muted)] overflow-hidden">
                      <div
                        className="h-full rounded-full bg-accent transition-all"
                        style={{ width: `${maxLeads > 0 ? (src.nbLeads / maxLeads) * 100 : 0}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </>
        ) : (
          <div className="card-leadrive">
            <EmptyState
              eyebrow="Sans source"
              eyebrowTone="muted"
              title="Aucun lead avec une source identifiée sur cette période."
              description="Les sources sont détectées automatiquement par l'IA lors de l'import par email ou collage de texte (Le Bon Coin, La Centrale…)."
              size="md"
              align="center"
              divider
            />
          </div>
        )}
      </div>
    </AppLayout>
  );
}
