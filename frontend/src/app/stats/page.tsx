"use client";

import Link from "next/link";
import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useApiClient, useCurrentRole, formatErreur } from "@/lib/auth";
import { useRealtime } from "@/lib/realtime";
import AppLayout from "@/components/AppLayout";
import { AreaChart, Area, BarChart, Bar, CartesianGrid, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend } from "recharts";
import InfoIcon from "@/components/common/InfoIcon";
import PageHeader from "@/components/common/PageHeader";
import { KpiCard } from "@/components/common/Card";
import Skeleton, { SkeletonGrid } from "@/components/common/Skeleton";
import Alert from "@/components/common/Alert";
import Button from "@/components/common/Button";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";
import DataSheet from "@/components/common/DataSheet";
import { STATUT_LABELS, STATUT_COLORS, STATUTS_PIPELINE } from "@/lib/leads/statuts";
import { CHART_COLORS } from "@/lib/charts/colors";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

const PERIODES = ["7j", "30j", "90j", "12m"];

interface ResumeStats {
  totalLeads: number;
  leadsTermines: number;
  tauxTermine: number;
  tauxTermineEvolution: number | null;
  tempsMoyenTermine: number;
  tempsMoyenTermineEvolution: number | null;
  velocite: number;
  velociteEvolution: number | null;
  /** Nombre de leads ayant atteint RDV_PRIS sur la période (flux, transitions). */
  rdvPris?: number | null;
  /** Taux RDV (rdvPris × 100 / totalLeads) sur la période. */
  tauxRdv?: number | null;
  /** Évolution du taux RDV en points de % vs période précédente (peut être négatif). */
  tauxRdvEvolution?: number | null;
}

interface EntonnoirEtape {
  statut: string;
  label: string;
  couleur: string;
  count: number;
  pourcentage: number;
  tauxPassage: number;
}

interface EvolutionPoint {
  date: string;
  nouveaux: number;
  termines: number;
  actifs: number;
}

interface PerformanceVendeur {
  vendeurId: number;
  nom: string;
  leadsAssignes: number;
  leadsTermines: number;
  tauxTermine: number;
  scoreActivite: number;
}

interface StatsData {
  periode: string;
  resume: ResumeStats;
  entonnoir: EntonnoirEtape[];
  evolution: EvolutionPoint[];
  performanceVendeurs: PerformanceVendeur[] | null;
}

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

// Snapshot de distribution (issu de l'ancien endpoint /api/leads/stats fusionné
// dans cette page : Top territoires + Top marques). All-time, pas affecté par
// la période sélectionnée — c'est de la structure de base, pas du flux.
interface DistributionStats {
  parStatut: Record<string, number>;
  parTerritoire: Record<string, number>;
  parMarque: Record<string, number>;
  total: number;
}

const PERIODE_EN_JOURS: Record<string, number> = {
  "7j": 7,
  "30j": 30,
  "90j": 90,
  "12m": 365,
};

// Formate une date ISO (LocalDate "2026-06-19" sérialisée par le backend) en
// libellé FR court pour l'axe X de la courbe ("19 juin"). Évite l'ISO brut non localisé.
function formatAxisDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString("fr-FR", { day: "2-digit", month: "short" });
}

export default function StatsPage() {
  // Wrap StatsContent dans Suspense : useSearchParams() requiert un Suspense
  // boundary pour le prerender Next.js (sinon CSR bailout error au build).
  return (
    <Suspense>
      <StatsContent />
    </Suspense>
  );
}

function StatsContent() {
  const apiFetch = useApiClient();
  const router = useRouter();
  const searchParams = useSearchParams();
  const { role } = useCurrentRole();
  // Period persisté URL (CLAUDE.md règle #10 : F5 restitue l'état exact).
  const initialPeriode = searchParams.get("periode") && PERIODES.includes(searchParams.get("periode") as string)
    ? (searchParams.get("periode") as string)
    : "30j";
  const [periode, setPeriode] = useState(initialPeriode);

  // Sort Performance équipe (par défaut : leads traités desc, le ranking le plus utile)
  const [sortPerfField, setSortPerfField] = useState<PerfSortField>("traites");
  const [sortPerfDir, setSortPerfDir] = useState<"asc" | "desc">("desc");

  // Sync period → URL sans scroll
  useEffect(() => {
    const params = new URLSearchParams(searchParams.toString());
    if (periode !== "30j") params.set("periode", periode);
    else params.delete("periode");
    router.replace(`/stats${params.toString() ? "?" + params.toString() : ""}`, { scroll: false });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [periode]);
  const [data, setData] = useState<StatsData | null>(null);
  const [sources, setSources] = useState<SourcesData | null>(null);
  const [distribution, setDistribution] = useState<DistributionStats | null>(null);
  const [loading, setLoading] = useState(true);
  // Erreur persistante : le toast est ephemere, mais si le 1er chargement echoue
  // la page resterait vide sans explication. On garde un etat dedie + Reessayer.
  const [erreur, setErreur] = useState("");

  // Garde de séquence : sur changement rapide de période ou rafale d'events
  // realtime, plusieurs fetchStats partent en parallèle. Seule la réponse de la
  // requête la PLUS RÉCENTE doit s'appliquer (sinon "last response wins" affiche
  // des données qui ne correspondent plus à la période sélectionnée).
  const fetchSeqRef = useRef(0);

  const fetchStats = useCallback(async () => {
    const seq = ++fetchSeqRef.current;
    setLoading(true);
    setErreur("");
    try {
      const jours = PERIODE_EN_JOURS[periode] ?? 30;
      // Sources + distribution sont réservés au MANAGER/SUPER_ADMIN côté backend
      // (403 sinon). On ne les fetch PAS pour un VENDEUR : sinon 403 silencieux +
      // sections absentes sans explication (la page paraît cassée). Le pipeline,
      // lui, est vendeur-scopé et toujours chargé.
      const canSeeManagerStats = role === "MANAGER" || role === "SUPER_ADMIN";
      const [resPipeline, resSources, resDist] = await Promise.all([
        apiFetch(`${API_BASE}/api/stats/pipeline?periode=${periode}`),
        canSeeManagerStats ? apiFetch(`${API_BASE}/api/stats/sources?periode=${jours}`) : Promise.resolve(null),
        canSeeManagerStats ? apiFetch(`${API_BASE}/api/leads/stats`).catch(() => null) : Promise.resolve(null),
      ]);
      if (resPipeline.ok) {
        const dataPipeline = await resPipeline.json();
        if (fetchSeqRef.current !== seq) return; // réponse périmée : on ignore
        setData(dataPipeline);
      } else {
        // 4xx/5xx ne throw pas chez fetch — il faut tester explicitement et toaster
        // sinon écran vide post-skeleton sans message (audit H9 P1).
        throw new Error(`Erreur ${resPipeline.status}`);
      }
      if (resSources && resSources.ok) {
        const dataSources = await resSources.json();
        if (fetchSeqRef.current === seq) setSources(dataSources);
      }
      if (resDist && resDist.ok) {
        try {
          const dataDist = await resDist.json();
          if (fetchSeqRef.current === seq) setDistribution(dataDist);
        } catch { /* graceful */ }
      }
    } catch (err) {
      if (fetchSeqRef.current !== seq) return; // erreur d'une requête périmée : on ignore
      console.warn("Stats fetch failed", err);
      setErreur(formatErreur(err));
    }
    finally {
      // Ne lève le loading que si c'est toujours la requête courante (sinon on
      // masquerait le skeleton d'une requête plus récente encore en vol).
      if (fetchSeqRef.current === seq) setLoading(false);
    }
  }, [apiFetch, periode, role]);

  useEffect(() => { fetchStats(); }, [fetchStats]);

  // Auto-refresh quand un lead est cree (toute source)
  const { lastEvent } = useRealtime();
  useEffect(() => {
    if (lastEvent?.type === "lead.created" || lastEvent?.type === "lead.batch.imported") {
      fetchStats();
    }
  }, [lastEvent, fetchStats]);

  // Max explicite (pas sources[0]) : ne dépend plus de l'ordre de tri SQL.
  const maxLeadsSource = Math.max(0, ...(sources?.sources?.map((s) => s.nbLeads) ?? [0]));

  // Taux de prise de RDV + compte de RDV : valeurs FLUX calculées par le backend
  // (transitions vers RDV_PRIS sur la période / leads de la période).
  // On NE recalcule PAS depuis l'entonnoir : celui-ci est un snapshot du statut
  // ACTUEL (all-time), donc un lead passé RDV_PRIS → TERMINE en sort et fausserait
  // le taux à 0 % malgré des RDV bien réels.
  const rdvCount = data?.resume?.rdvPris ?? 0;
  const totalLeads = data?.resume?.totalLeads ?? 0;
  const tauxRdv = Math.round(data?.resume?.tauxRdv ?? 0);

  return (
    <AppLayout>
      <div className="mx-auto max-w-6xl px-6 py-6 space-y-6">
        {/* Header */}
        <PageHeader
          eyebrow="Vos performances"
          title="Statistiques"
          actions={
            <div role="group" aria-label="Période d'analyse" className="flex rounded-lg border border-[var(--border)] bg-[var(--card)] p-0.5">
              {PERIODES.map(p => (
                <button key={p} onClick={() => setPeriode(p)} aria-pressed={p === periode}
                  className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${p === periode ? "bg-accent text-white" : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"}`}>
                  {p}
                </button>
              ))}
            </div>
          }
        />

        {/* Skeleton sous le header (jamais d'early-return sans header : le titre
            doit etre visible des le 1er paint, chargement ou erreur). */}
        {loading && !data && (
          <>
            <SkeletonGrid count={4} />
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
              <div className="card-leadrive p-5">
                <Skeleton className="h-4 w-24 mb-4" />
                <div className="space-y-2.5">
                  {[1, 2, 3, 4, 5].map((i) => (
                    <div key={i} className="flex items-center gap-3">
                      <Skeleton className="h-3 w-20" />
                      <Skeleton className="h-7 flex-1" />
                    </div>
                  ))}
                </div>
              </div>
              <div className="card-leadrive p-5">
                <Skeleton className="h-4 w-24 mb-4" />
                <Skeleton className="h-[220px]" />
              </div>
            </div>
          </>
        )}

        {erreur && !loading && !data && (
          <ErrorState message={erreur} onRetry={fetchStats} />
        )}

        {erreur && !loading && data && (
          <div role="alert">
            <Alert tone="danger" className="flex flex-wrap items-center justify-between gap-3">
              <span>{erreur}</span>
              <Button variant="secondary" size="sm" onClick={fetchStats}>Réessayer</Button>
            </Alert>
          </div>
        )}

        {data && (
          <>
            {/* HERO KPI asymétrique : Taux de prise de RDV (le chiffre que le
                manager scanne en 2s) en 72px JetBrains Mono à gauche, vue
                d'ensemble en datasheet à droite. Casse le pattern 4-KPI-identiques
                (anti-référence PRODUCT.md "blue tech SaaS générique"). */}
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-12 animate-card-mount">
              {/* Hero — Taux de transformation */}
              <div className="card-leadrive p-6 lg:col-span-7">
                <div className="flex items-center gap-1.5">
                  <p className="eyebrow">Taux de prise de RDV</p>
                  <InfoIcon>Part de vos leads de la période ayant obtenu un rendez-vous confirmé via l&apos;assistant WhatsApp. Un RDV reste comptabilisé même si le lead évolue ensuite. À ne pas confondre avec le taux de transformation RDV vers vente.</InfoIcon>
                </div>
                <div className="mt-3 flex flex-wrap items-end gap-3">
                  <p className={`font-numeric text-[64px] font-semibold leading-none tracking-[-0.04em] animate-kpi-ignite sm:text-[72px] ${tauxRdv >= 20 ? "text-success" : tauxRdv >= 10 ? "text-warning" : "text-[var(--foreground)]"}`}>
                    {tauxRdv}<span className="text-[44px]">%</span>
                  </p>
                  {totalLeads > 0 && (
                    <p className="pb-3 text-[14px] text-[var(--color-lr-text-secondary)]">
                      soit <span className="font-numeric font-semibold text-[var(--foreground)]">{rdvCount}</span> RDV sur{" "}
                      <span className="font-numeric font-semibold text-[var(--foreground)]">{totalLeads}</span> leads
                    </p>
                  )}
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-2">
                  <div className="inline-flex items-center gap-1.5 rounded-full border border-[var(--border)] bg-[var(--muted)] px-2.5 py-1 font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-[var(--color-lr-text-secondary)]">
                    <span className="h-1.5 w-1.5 rounded-full bg-[var(--color-accent)]" aria-hidden="true" />
                    Bench secteur : 15-25 %
                  </div>
                  {totalLeads > 0 && (
                    <div
                      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 font-mono text-[10px] font-semibold uppercase tracking-[0.16em] ${
                        tauxRdv >= 20
                          ? "bg-success/10 text-success"
                          : tauxRdv >= 10
                            ? "bg-warning/10 text-warning"
                            : "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]"
                      }`}
                      aria-label={
                        tauxRdv >= 20
                          ? "Performance au-dessus du benchmark"
                          : tauxRdv >= 10
                            ? "Performance dans le benchmark"
                            : "Performance en-dessous du benchmark"
                      }
                    >
                      {tauxRdv >= 20
                        ? "Au-dessus"
                        : tauxRdv >= 10
                          ? "Dans la cible"
                          : "Sous la cible"}
                    </div>
                  )}
                  {/* Delta vs période précédente — exposé par le backend
                      (tauxRdvEvolution = différence en points de %). */}
                  {data.resume.tauxRdvEvolution != null && Number.isFinite(data.resume.tauxRdvEvolution) && data.resume.tauxRdvEvolution !== 0 && (
                    <div
                      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 font-mono text-[10px] font-semibold uppercase tracking-[0.16em] ${
                        data.resume.tauxRdvEvolution > 0
                          ? "bg-success/10 text-success"
                          : "bg-danger/10 text-danger"
                      }`}
                      aria-label={`Évolution ${data.resume.tauxRdvEvolution > 0 ? "positive" : "négative"} de ${Math.abs(data.resume.tauxRdvEvolution)} points vs ${periode === "12m" ? "année précédente" : `${PERIODE_EN_JOURS[periode]} jours précédents`}`}
                      title={`Comparé à la période équivalente précédente (${periode === "12m" ? "12 mois" : `${PERIODE_EN_JOURS[periode]} jours`})`}
                    >
                      {data.resume.tauxRdvEvolution > 0 ? "↑" : "↓"} {Math.abs(Math.round(data.resume.tauxRdvEvolution * 10) / 10)} pt
                      <span className="ml-1 normal-case tracking-normal text-[9px] opacity-70">
                        vs {periode === "12m" ? "12m préc." : `${PERIODE_EN_JOURS[periode]}j préc.`}
                      </span>
                    </div>
                  )}
                </div>
              </div>

              {/* Supporting — 3 metrics en fiche technique */}
              <div className="card-leadrive p-6 lg:col-span-5">
                <p className="eyebrow-muted">Vue d&apos;ensemble · période</p>
                <DataSheet
                  variant="nude"
                  className="mt-3"
                  rows={[
                    { label: "Leads reçus", value: totalLeads },
                    { label: "RDV obtenus", value: rdvCount },
                    { label: "Leads traités", value: data.resume.leadsTermines },
                  ]}
                />
                {data.resume.tauxTermineEvolution != null && Number.isFinite(data.resume.tauxTermineEvolution) && data.resume.tauxTermineEvolution !== 0 && (
                  <p className="mt-3 text-[11.5px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                    Traitement{" "}
                    <span className={`font-mono font-semibold tabular-nums ${data.resume.tauxTermineEvolution > 0 ? "text-success" : "text-warning"}`}>
                      {data.resume.tauxTermineEvolution > 0 ? "+" : ""}{Math.round(data.resume.tauxTermineEvolution)}%
                    </span>{" "}
                    <span className="text-[var(--color-lr-text-tertiary)]">vs période précédente</span>
                  </p>
                )}
              </div>
            </div>

            {/* Entonnoir + Activité, mount delayed après les KPIs */}
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-2 animate-card-mount-delayed">
              {/* Entonnoir, couleurs résolues côté front via STATUT_COLORS
                  (on ignore `e.couleur` du backend, source de vérité unique
                  dans lib/leads/statuts.ts). Animation de fill au mount via
                  --progress-target. */}
              <div className="card-leadrive p-5">
                <div className="mb-4 flex items-center gap-1.5">
                  <h2 className="display-h2 text-[var(--foreground)]">Pipeline</h2>
                  <InfoIcon>Répartition de vos leads par étape. Chaque barre montre combien de leads sont dans chaque statut.</InfoIcon>
                </div>
                {data.entonnoir.every((e) => e.count === 0) ? (
                  <EmptyState
                    eyebrow="Pipeline vide"
                    eyebrowTone="accent"
                    title="Pas encore d'activité sur cette période."
                    description="Importez vos premiers prospects pour voir le funnel s'animer."
                    action={
                      <Link href="/import" className="text-[12px] font-medium text-accent hover:underline">
                        Importer des prospects →
                      </Link>
                    }
                    align="start"
                    size="sm"
                  />
                ) : (
                  <div className="space-y-2.5">
                    {data.entonnoir.map((e) => {
                      const width = Math.max(e.pourcentage, 3);
                      const color = STATUT_COLORS[e.statut] ?? CHART_COLORS.muted;
                      return (
                        <div key={e.statut} className="flex items-center gap-3">
                          <span className="w-20 text-xs text-[var(--color-lr-text-secondary)] truncate">{STATUT_LABELS[e.statut] ?? e.label}</span>
                          <div className="flex-1 h-7 rounded-lg bg-[var(--muted)] overflow-hidden relative">
                            <div
                              className="h-full rounded-lg animate-progress-fill"
                              style={{ width: `${width}%`, backgroundColor: color, ["--progress-target" as string]: `${width}%` }}
                            />
                            <span className="absolute inset-y-0 right-2 flex items-center text-[11px] font-mono font-medium text-[var(--foreground)]">{e.count}</span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Courbe d'activité */}
              <div className="card-leadrive p-5">
                <div className="mb-4 flex items-center gap-1.5">
                  <h2 className="display-h2 text-[var(--foreground)]">Activité</h2>
                  <InfoIcon>Évolution du nombre de nouveaux leads et de leads traités par jour sur la période.</InfoIcon>
                </div>
                {data.evolution.length > 1 ? (
                  <ResponsiveContainer width="100%" height={220}>
                    <AreaChart data={data.evolution}>
                      <XAxis dataKey="date" tickFormatter={formatAxisDate} tick={{ fontSize: 10 }} stroke="var(--color-lr-text-tertiary)" />
                      <YAxis tick={{ fontSize: 10 }} stroke="var(--color-lr-text-tertiary)" />
                      {/* cursor themé : sinon Recharts dessine une ligne de survol grise codée en dur (#ccc) qui ressort blanche en dark. */}
                      <Tooltip contentStyle={{ backgroundColor: "var(--card)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }} cursor={{ stroke: "var(--border)", strokeWidth: 1 }} />
                      <Area type="monotone" dataKey="nouveaux" stroke={CHART_COLORS.accent} fill={CHART_COLORS.accent} fillOpacity={0.12} name="Nouveaux leads" />
                      <Area type="monotone" dataKey="termines" stroke={CHART_COLORS.muted} fill={CHART_COLORS.muted} fillOpacity={0.1} name="Traités" />
                      <Legend wrapperStyle={{ fontSize: 11 }} />
                    </AreaChart>
                  </ResponsiveContainer>
                ) : (
                  <p className="py-10 text-center text-xs text-[var(--color-lr-text-tertiary)]">Pas assez de données sur cette période.</p>
                )}
              </div>
            </div>

            {/* Sources de leads */}
            {sources && sources.sources.length > 0 && (
              <div className="card-leadrive p-5">
                <div className="mb-4 flex items-center gap-1.5">
                  <h2 className="display-h2 text-[var(--foreground)]">Sources de leads</h2>
                  <InfoIcon>
                    Répartition de vos leads par plateforme d&apos;origine (Le Bon Coin, La Centrale…).
                    Le taux de conversion indique le pourcentage de leads ayant abouti à un RDV. Détectée
                    automatiquement par l&apos;IA lors de l&apos;import par email ou collage de texte.
                  </InfoIcon>
                </div>
                <div className="space-y-3">
                  {sources.sources.map((src) => (
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
                            className={`font-semibold tabular-nums ${
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
                          style={{ width: `${maxLeadsSource > 0 ? (src.nbLeads / maxLeadsSource) * 100 : 0}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Distribution all-time : horizontal stack bar 100% + Top territoires + Top marques.
                Migration de l'ancienne page /leads/stats (orpheline, dépréciée). */}
            {distribution && distribution.total > 0 && (
              <>
                {/* Separator visuel snapshot vs flux : signale clairement que
                    les sections ci-dessous sont all-time (pas affectées par la
                    période sélectionnée). Évite le "bug perçu" quand le user
                    change 30j → 7j et voit Top territoires inchangés. */}
                <div className="mt-6 flex items-center gap-3 pt-2">
                  <div className="h-px flex-1 bg-[var(--border)]" aria-hidden="true" />
                  <p className="eyebrow-muted">Aperçu base prospects · all-time</p>
                  <div className="h-px flex-1 bg-[var(--border)]" aria-hidden="true" />
                </div>
                {/* Répartition par statut, single horizontal stack bar 100% — plus dense
                    et plus pro qu'un pie chart. */}
                <div className="card-leadrive p-5">
                  <div className="mb-4 flex items-center gap-1.5">
                    <h2 className="display-h2 text-[var(--foreground)]">Répartition par statut</h2>
                    <InfoIcon>Distribution actuelle de tous vos prospects par étape du pipeline. Snapshot total, pas affecté par la période sélectionnée.</InfoIcon>
                  </div>
                  <div className="h-7 flex w-full overflow-hidden rounded-lg">
                    {STATUTS_PIPELINE.map((s) => {
                      const count = distribution.parStatut?.[s] ?? 0;
                      if (count === 0) return null;
                      const pct = (count / distribution.total) * 100;
                      return (
                        <div
                          key={s}
                          className="h-full flex items-center justify-center text-[11px] font-mono font-semibold text-white"
                          style={{ width: `${pct}%`, backgroundColor: STATUT_COLORS[s] }}
                          title={`${STATUT_LABELS[s]} : ${count}`}
                        >
                          {pct >= 8 ? count : ""}
                        </div>
                      );
                    })}
                  </div>
                  <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5">
                    {STATUTS_PIPELINE.map((s) => {
                      const count = distribution.parStatut?.[s] ?? 0;
                      if (count === 0) return null;
                      return (
                        <div key={s} className="flex items-center gap-1.5 text-[11px] text-[var(--color-lr-text-secondary)]">
                          <span className="h-2 w-2 rounded-full" style={{ backgroundColor: STATUT_COLORS[s] }} aria-hidden="true" />
                          <span>{STATUT_LABELS[s]}</span>
                          <span className="font-mono font-semibold text-[var(--foreground)]">{count}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Top territoires + Top marques côte à côte */}
                {(() => {
                  const topTerritoires = Object.entries(distribution.parTerritoire ?? {})
                    .sort(([, a], [, b]) => b - a)
                    .slice(0, 8)
                    .map(([nom, count]) => ({ nom, count }));
                  const topMarques = Object.entries(distribution.parMarque ?? {})
                    .sort(([, a], [, b]) => b - a)
                    .slice(0, 8)
                    .map(([nom, count]) => ({ nom, count }));
                  if (topTerritoires.length === 0 && topMarques.length === 0) return null;
                  return (
                    <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                      {topTerritoires.length > 0 && (
                        <div className="card-leadrive p-5">
                          <div className="mb-4 flex items-center gap-1.5">
                            <h2 className="display-h2 text-[var(--foreground)]">Top territoires</h2>
                            <InfoIcon>Code postal ou région des 8 zones d&apos;origine les plus actives.</InfoIcon>
                          </div>
                          <ResponsiveContainer width="100%" height={Math.max(topTerritoires.length * 32, 150)}>
                            <BarChart data={topTerritoires} layout="vertical" margin={{ top: 0, right: 20, left: 0, bottom: 0 }}>
                              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
                              <XAxis type="number" tick={{ fontSize: 11, fill: "var(--color-lr-text-secondary)" }} axisLine={false} tickLine={false} allowDecimals={false} />
                              <YAxis dataKey="nom" type="category" width={120} tick={{ fontSize: 11, fill: "var(--color-lr-text-secondary)" }} axisLine={false} tickLine={false} />
                              {/* cursor themé : sinon Recharts dessine un rectangle de survol gris codé en dur (#ccc) qui ressort blanc en dark. */}
                              <Tooltip contentStyle={{ backgroundColor: "var(--card)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }} cursor={{ fill: "var(--muted)", fillOpacity: 0.6 }} />
                              <Bar dataKey="count" fill={CHART_COLORS.accent} radius={[0, 4, 4, 0]} barSize={18} />
                            </BarChart>
                          </ResponsiveContainer>
                        </div>
                      )}
                      {topMarques.length > 0 && (
                        <div className="card-leadrive p-5">
                          <div className="mb-4 flex items-center gap-1.5">
                            <h2 className="display-h2 text-[var(--foreground)]">Top marques</h2>
                            <InfoIcon>Marques les plus demandées par vos prospects, en volume.</InfoIcon>
                          </div>
                          <ResponsiveContainer width="100%" height={Math.max(topMarques.length * 32, 150)}>
                            <BarChart data={topMarques} layout="vertical" margin={{ top: 0, right: 20, left: 0, bottom: 0 }}>
                              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
                              <XAxis type="number" tick={{ fontSize: 11, fill: "var(--color-lr-text-secondary)" }} axisLine={false} tickLine={false} allowDecimals={false} />
                              <YAxis dataKey="nom" type="category" width={100} tick={{ fontSize: 11, fill: "var(--color-lr-text-secondary)" }} axisLine={false} tickLine={false} />
                              {/* cursor themé : sinon Recharts dessine un rectangle de survol gris codé en dur (#ccc) qui ressort blanc en dark. */}
                              <Tooltip contentStyle={{ backgroundColor: "var(--card)", border: "1px solid var(--border)", borderRadius: 8, fontSize: 12 }} cursor={{ fill: "var(--muted)", fillOpacity: 0.6 }} />
                              <Bar dataKey="count" fill={CHART_COLORS.accentDeep} radius={[0, 4, 4, 0]} barSize={18} />
                            </BarChart>
                          </ResponsiveContainer>
                        </div>
                      )}
                    </div>
                  );
                })()}
              </>
            )}

            {/* Performance équipe (MANAGER only) — rows clickables vers
                /leads?vendeur=ID + sort par colonne + ScoreActivité jauge. */}
            {role === "MANAGER" && data.performanceVendeurs && data.performanceVendeurs.length > 0 && (() => {
              const sorted = [...data.performanceVendeurs].sort((a, b) => {
                const get = (v: typeof a): number | string => {
                  switch (sortPerfField) {
                    case "leads": return v.leadsAssignes;
                    case "traites": return v.leadsTermines;
                    case "taux": return v.tauxTermine;
                    case "activite": return v.scoreActivite ?? 0;
                    case "nom": return v.nom;
                    default: return 0;
                  }
                };
                const av = get(a); const bv = get(b);
                let cmp: number;
                if (typeof av === "string" && typeof bv === "string") {
                  cmp = sortPerfDir === "asc" ? av.localeCompare(bv) : bv.localeCompare(av);
                } else {
                  cmp = sortPerfDir === "asc" ? (av as number) - (bv as number) : (bv as number) - (av as number);
                }
                // Tie-breaker stable (vendeurId) : evite que les lignes ex-aequo
                // sautent d'ordre entre deux refetch realtime.
                return cmp !== 0 ? cmp : a.vendeurId - b.vendeurId;
              });
              // scoreActivite est un COMPTEUR brut d'actions manuelles (drag&drop /
              // changement de statut), pas un score sur 100. La jauge est donc rendue
              // RELATIVE au vendeur le plus actif de la colonne (max), et le libellé
              // affiche le nombre réel d'actions — plus de "/100" mensonger ni de barre
              // plafonnée identique pour tous les vendeurs > 100 actions.
              const maxActivite = Math.max(1, ...sorted.map((v) => v.scoreActivite ?? 0));
              return (
              <div className="card-leadrive p-5">
                <div className="mb-4 flex items-center gap-1.5">
                  <h2 className="display-h2 text-[var(--foreground)]">Performance équipe</h2>
                  <InfoIcon>Classement des vendeurs par leads traités et taux. Cliquez sur une ligne pour ouvrir ses leads, sur un en-tête pour trier.</InfoIcon>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-[var(--border)] text-[var(--color-lr-text-secondary)]">
                        <th className="px-3 py-2 font-medium w-8">#</th>
                        <PerfSortTh field="nom" current={sortPerfField} dir={sortPerfDir} onChange={(f, d) => { setSortPerfField(f); setSortPerfDir(d); }}>Vendeur</PerfSortTh>
                        <PerfSortTh field="leads" current={sortPerfField} dir={sortPerfDir} onChange={(f, d) => { setSortPerfField(f); setSortPerfDir(d); }} className="text-right">Leads</PerfSortTh>
                        <PerfSortTh field="traites" current={sortPerfField} dir={sortPerfDir} onChange={(f, d) => { setSortPerfField(f); setSortPerfDir(d); }} className="text-right">Traités</PerfSortTh>
                        <PerfSortTh field="taux" current={sortPerfField} dir={sortPerfDir} onChange={(f, d) => { setSortPerfField(f); setSortPerfDir(d); }} className="text-right">Taux</PerfSortTh>
                        <PerfSortTh field="activite" current={sortPerfField} dir={sortPerfDir} onChange={(f, d) => { setSortPerfField(f); setSortPerfDir(d); }}>Activité</PerfSortTh>
                      </tr>
                    </thead>
                    <tbody>
                      {sorted.map((v, i) => {
                        const activiteRaw = Math.max(0, v.scoreActivite ?? 0);
                        const activitePct = Math.round((activiteRaw / maxActivite) * 100);
                        const activiteLabel = `${activiteRaw} action${activiteRaw > 1 ? "s" : ""} manuelle${activiteRaw > 1 ? "s" : ""} sur la période`;
                        return (
                          <tr key={v.vendeurId} className="group border-b border-[var(--border)] transition-colors hover:bg-[var(--color-accent-subtle)]">
                            <td className="px-3 py-2">
                              {i === 0 ? (
                                <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-[var(--color-accent)] font-mono text-[10px] font-bold text-white" aria-label="1er rang">
                                  1
                                </span>
                              ) : (
                                <span className="font-mono text-[12px] text-[var(--color-lr-text-tertiary)]">{String(i + 1).padStart(2, "0")}</span>
                              )}
                            </td>
                            <td className="px-3 py-2 font-medium text-[var(--foreground)]">
                              <Link
                                href={`/leads?vendeur=${v.vendeurId}`}
                                className="inline-flex items-center gap-1.5 rounded transition-colors hover:text-[var(--color-accent)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]/40"
                                title={`Voir les leads de ${v.nom}`}
                              >
                                {v.nom}
                                <svg className="h-3 w-3 opacity-0 transition-opacity group-hover:opacity-100" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                </svg>
                              </Link>
                            </td>
                            <td className="px-3 py-2 text-right font-mono tabular-nums">{v.leadsAssignes}</td>
                            <td className="px-3 py-2 text-right font-mono tabular-nums">{v.leadsTermines}</td>
                            <td className="px-3 py-2 text-right font-mono tabular-nums">{v.tauxTermine}%</td>
                            <td className="px-3 py-2">
                              <div className="flex items-center gap-2">
                                <div
                                  className="flex h-1.5 w-20 overflow-hidden rounded-full bg-[var(--muted)]"
                                  title={activiteLabel}
                                >
                                  <div
                                    className="h-full rounded-full bg-[var(--color-accent)]"
                                    style={{ width: `${activitePct}%` }}
                                    aria-label={activiteLabel}
                                  />
                                </div>
                                <span className="font-mono text-[11px] tabular-nums text-[var(--color-lr-text-tertiary)]">{activiteRaw}</span>
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
              );
            })()}
          </>
        )}
      </div>
    </AppLayout>
  );
}

type PerfSortField = "nom" | "leads" | "traites" | "taux" | "activite";

function PerfSortTh({
  field,
  current,
  dir,
  onChange,
  className = "",
  children,
}: {
  field: PerfSortField;
  current: PerfSortField;
  dir: "asc" | "desc";
  onChange: (f: PerfSortField, d: "asc" | "desc") => void;
  className?: string;
  children: React.ReactNode;
}) {
  const active = current === field;
  const ariaSort: "ascending" | "descending" | "none" = active
    ? dir === "asc"
      ? "ascending"
      : "descending"
    : "none";
  return (
    <th scope="col" aria-sort={ariaSort} className={`px-3 py-2 font-medium ${className}`}>
      <button
        type="button"
        onClick={() => onChange(field, active && dir === "desc" ? "asc" : "desc")}
        className={`inline-flex items-center gap-1 transition-colors hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-accent)]/40 rounded ${className.includes("text-right") ? "ml-auto" : ""}`}
      >
        {children}
        {active && (
          <span className="text-[var(--color-accent)]" aria-hidden="true">
            {dir === "asc" ? "↑" : "↓"}
          </span>
        )}
      </button>
    </th>
  );
}

