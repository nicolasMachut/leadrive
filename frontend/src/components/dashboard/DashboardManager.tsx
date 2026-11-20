"use client";

import { useEffect, useState, useCallback } from "react";
import { useApiClient, useMe, formatErreur } from "@/lib/auth";
import { useToast } from "@/lib/realtime";
import { useCountUp } from "@/lib/useCountUp";
import { SkeletonInstrument } from "@/components/common/Skeleton";
import BriefingCard from "@/components/dashboard/BriefingCard";
import type { BriefingData } from "@/components/dashboard/BriefingCard";
import ActionCenter from "@/components/dashboard/ActionCenter";
import DashboardHero from "@/components/dashboard/DashboardHero";
import SectionHeader from "@/components/common/SectionHeader";
import Alert from "@/components/common/Alert";
import RefreshButton from "@/components/common/RefreshButton";
import PrecisionIaCard from "@/components/feedback/PrecisionIaCard";
import ImpactToast from "@/components/feedback/ImpactToast";
import ErrorState from "@/components/common/ErrorState";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// Cockpit KPI manager. Le backend peut ne pas exposer l'endpoint au moment du
// déploiement : on dégrade gracieusement, le hero reste minimal sans le bloc
// data-sheet. Au shipping du endpoint /api/dashboard/manager, les KPI
// apparaissent automatiquement.
interface ManagerDashboardData {
  rdvSemaineEquipe: number;
  rdvMoisEquipe: number;
  prospectsEnCours: number;
  vendeursActifs: number;
  tauxConversionPct: number;
  /** Objectif RDV équipe ce mois (depuis Tenant). Null si non configuré. */
  objectifMoisEquipe?: number | null;
}

export default function DashboardManager() {
  const apiFetch = useApiClient();
  const { showToast } = useToast();
  const me = useMe();
  const [data, setData] = useState<ManagerDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [briefing, setBriefing] = useState<BriefingData | null>(null);
  const [briefingLoading, setBriefingLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setBriefingLoading(true);
    setError("");
    try {
      // Briefing et cockpit en parallèle. Le cockpit est best-effort : si
      // l'endpoint n'est pas (encore) déployé, on n'affiche pas les KPIs
      // mais on ne bloque pas le rendu du hero.
      const [briefingRes, cockpitRes] = await Promise.all([
        apiFetch(`${API_BASE}/api/briefing`),
        apiFetch(`${API_BASE}/api/dashboard/manager`).catch(() => null),
      ]);
      if (briefingRes.ok) setBriefing(await briefingRes.json());
      if (cockpitRes && cockpitRes.ok) {
        try {
          setData(await cockpitRes.json());
        } catch { /* endpoint pas encore au format attendu, on dégrade */ }
      }
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setLoading(false);
      setBriefingLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // Lit `prenom` directement depuis /api/me, pas via useUserDisplayName() qui
  // fallback sur l'email (long, overflow le hero). Tant que `me` n'est pas
  // chargé, prenom reste vide et le hero affiche "Tableau de pilotage."
  const prenom = me?.prenom?.trim() ?? "";
  const today = new Date().toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" });
  // Count-up uniquement sur le KPI hero (cohérent avec le pattern vendeur).
  const rdvSemaineEquipe = data?.rdvSemaineEquipe ?? 0;
  const rdvSemaineDisplay = useCountUp(rdvSemaineEquipe, 900);
  // Progress bar équipe : visible uniquement si l'objectif est configuré côté
  // Tenant. Sinon dégrade gracieusement (pas d'élément vide).
  const objectifMoisEquipe = data?.objectifMoisEquipe ?? null;
  const rdvMoisEquipe = data?.rdvMoisEquipe ?? 0;
  const progressPercent = objectifMoisEquipe && objectifMoisEquipe > 0
    ? Math.min(100, Math.round((rdvMoisEquipe / objectifMoisEquipe) * 100))
    : null;

  // Échec du chargement principal SANS données : un seul ErrorState global,
  // les widgets enfants (ActionCenter, etc.) ne sont pas montés.
  if (error && !data) {
    return (
      <div className="px-6 py-6 space-y-6">
        {/* Même en-tête que le mode normal (DashboardHero minimal, sans KPI ni
            DataSheet), comme les autres pages conservent leur titre en cas
            d'erreur. Seule la zone de données est remplacée par un ErrorState. */}
        <DashboardHero
          eyebrowLeft="Bureau de pilotage"
          eyebrowDate={today}
          greeting={prenom ? `Bonjour ${prenom}.` : "Tableau de pilotage."}
        />
        <ErrorState message={error} onRetry={fetchData} />
      </div>
    );
  }

  return (
    <div className="px-6 py-6 space-y-6">
      <ImpactToast />
      {error && (
        <div role="alert">
          <Alert tone="danger">
            <div className="flex items-center justify-between gap-3">
              <span>{error}</span>
              <button
                type="button"
                onClick={() => fetchData()}
                className="shrink-0 rounded-md border border-danger/30 bg-[var(--card)] px-3 py-1 text-xs font-semibold text-danger transition-colors hover:bg-danger/10"
              >
                Réessayer
              </button>
            </div>
          </Alert>
        </div>
      )}
      {/* Signal explicite si le cockpit data n'est pas dispo (API 404 / down) :
          avant ce signal le manager voyait juste le greeting sans savoir si
          l'équipe travaille ou si le backend est cassé. Audit P1. */}
      {!loading && !error && !data && (
        <Alert tone="warning">
          <div className="flex items-center justify-between gap-3">
            <span>Données équipe indisponibles. Vous voyez uniquement les informations de base.</span>
            <button
              type="button"
              onClick={() => fetchData()}
              className="shrink-0 rounded-md border border-warning/30 bg-[var(--card)] px-3 py-1 text-xs font-semibold text-warning transition-colors hover:bg-warning/10"
            >
              Réessayer
            </button>
          </div>
        </Alert>
      )}

      {/* HERO EDITORIAL — DashboardHero, symétrique au vendeur. Si le backend
          /api/dashboard/manager n'expose pas encore data, on dégrade sur le
          hero minimal (greeting seul, ni KPI ni DataSheet). */}
      <DashboardHero
        eyebrowLeft="Bureau de pilotage"
        eyebrowDate={today}
        greeting={prenom ? `Bonjour ${prenom}.` : "Tableau de pilotage."}
        subtitle={
          data ? (
            <>
              <span className="font-numeric font-semibold text-[var(--foreground)]">{data.vendeursActifs}</span>{" "}
              vendeur{data.vendeursActifs > 1 ? "s" : ""} actif{data.vendeursActifs > 1 ? "s" : ""}
              <span className="mx-2 text-[var(--color-lr-text-tertiary)]">·</span>
              <span className="font-numeric font-semibold text-[var(--foreground)]">{data.prospectsEnCours}</span>{" "}
              prospect{data.prospectsEnCours > 1 ? "s" : ""} en cours
              {progressPercent != null && objectifMoisEquipe && (
                <>
                  <span className="mx-2 text-[var(--color-lr-text-tertiary)]">·</span>
                  Objectif équipe&nbsp;:{" "}
                  <span className="font-numeric font-semibold text-[var(--foreground)]">{rdvMoisEquipe}</span>
                  <span className="text-[var(--color-lr-text-tertiary)]"> / </span>
                  <span className="font-numeric text-[var(--color-lr-text-secondary)]">{objectifMoisEquipe}</span>
                  <span className={`ml-2 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-mono font-semibold uppercase tracking-wider ${
                    progressPercent >= 100
                      ? "bg-[var(--color-accent)] text-white animate-success-pulse"
                      : "bg-[var(--color-accent-subtle)] text-[var(--color-accent-deep)]"
                  }`}>
                    {progressPercent}%
                  </span>
                </>
              )}
            </>
          ) : undefined
        }
        progress={progressPercent != null ? { value: progressPercent } : undefined}
        hero={
          data
            ? {
                label: "RDV équipe cette semaine",
                value: rdvSemaineDisplay,
                isEmpty: rdvSemaineEquipe === 0,
                emptyTitle: "Semaine vide.",
                emptyHint: "Aucun RDV pris par l'équipe. À surveiller.",
              }
            : undefined
        }
        sheet={
          data
            ? [
                { label: "RDV ce mois", value: rdvMoisEquipe },
                {
                  label: "Taux conversion",
                  value: data.tauxConversionPct != null ? `${data.tauxConversionPct}%` : "—",
                },
              ]
            : undefined
        }
      />

      {/* Hiérarchie : ACTIONS (urgent) → BRIEFING (informatif) → PRECISION IA (footer ambient). */}

      {/* 1. Centre d'actions, le cœur opérationnel — en tête, mount immediat. */}
      <div data-tour="action-center" className="animate-card-mount">
        <ActionCenter onRefresh={fetchData} />
      </div>

      {/* 2. Briefing, header via SectionHeader bordered (mount delayed 150ms).
          Date non répétée : portée par le hero. */}
      {!loading && (
        <div data-tour="briefing" className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden animate-card-mount-delayed">
          <SectionHeader
            eyebrow="Briefing"
            eyebrowTone="muted"
            title="Lecture de l'activité"
            size="sm"
            bordered
            className="!px-5 !py-3.5"
            actions={
              <RefreshButton
                loading={briefingLoading}
                label="Régénérer le briefing"
                onClick={async () => {
                  setBriefingLoading(true);
                  try {
                    const res = await apiFetch(`${API_BASE}/api/briefing/regenerer`, { method: "POST" });
                    if (res.ok) {
                      setBriefing(await res.json());
                      showToast({ titre: "Briefing régénéré", tone: "success" });
                    } else throw new Error(`Erreur ${res.status}`);
                  } catch (err) {
                    console.warn("Briefing regenerate failed", err);
                    showToast({ titre: "Briefing non régénéré", message: formatErreur(err), tone: "danger" });
                  } finally { setBriefingLoading(false); }
                }}
              />
            }
          />
          <BriefingCard
            briefing={briefing}
            loading={briefingLoading}
            onRegenerate={() => {}}
            isAdmin={false}
            hideHeader
          />
        </div>
      )}

      {/* 3. Precision IA, différenciateur, en footer ambient (mount delayed 300ms). */}
      <div className="animate-card-mount-delayed-2">
        <PrecisionIaCard />
      </div>

      {/* Skeleton : speed-streaks signature Atelier au lieu du shimmer plat */}
      {loading && (
        <div className="space-y-4">
          <SkeletonInstrument label="Pilotage en démarrage" className="h-32" />
          <SkeletonInstrument label="Briefing en préparation" className="h-48" />
          <SkeletonInstrument className="h-24" />
        </div>
      )}
    </div>
  );
}
