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
import ErrorState from "@/components/common/ErrorState";
import RefreshButton from "@/components/common/RefreshButton";
import ImpactToast from "@/components/feedback/ImpactToast";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface VendeurDashboardData {
  mesAppelsAujourdhui: number;
  mesRdvSemaine: number;
  aRappelerMaintenant: number;
  objectifMensuelRdv: number;
  mesRdvMois: number;
}

export default function DashboardVendeur() {
  const apiFetch = useApiClient();
  const { showToast } = useToast();
  const me = useMe();
  const [data, setData] = useState<VendeurDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [briefing, setBriefing] = useState<BriefingData | null>(null);
  const [briefingLoading, setBriefingLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setBriefingLoading(true);
    setError("");
    try {
      const [dashRes, briefingRes] = await Promise.all([
        apiFetch(`${API_BASE}/api/dashboard/vendeur`),
        apiFetch(`${API_BASE}/api/briefing`),
      ]);
      if (!dashRes.ok) throw new Error(`Erreur: ${dashRes.status}`);
      setData(await dashRes.json());
      if (briefingRes.ok) setBriefing(await briefingRes.json());
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setLoading(false);
      setBriefingLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const objectif = data?.objectifMensuelRdv ?? 10;
  const rdvMois = data?.mesRdvMois ?? 0;
  const rdvSemaine = data?.mesRdvSemaine ?? 0;
  const appels = data?.mesAppelsAujourdhui ?? 0;
  const progressPercent = objectif > 0 ? Math.min(100, Math.round((rdvMois / objectif) * 100)) : 0;

  // Count-up uniquement sur le KPI HERO (RDV semaine 64px) pour l'effet
  // "instrument qui s'allume". Les 3 autres KPIs supporting (objectif, appels,
  // progress) restent statiques : sur 6 F5/jour le theater devient bruit.
  const rdvSemaineDisplay = useCountUp(rdvSemaine, 900);
  const rdvMoisDisplay = rdvMois;
  const appelsDisplay = appels;
  const progressPercentDisplay = progressPercent;
  // Lit `prenom` directement depuis /api/me, pas via useUserDisplayName() qui
  // fallback sur l'email (long, overflow le hero). Tant que `me` n'est pas
  // chargé, prenom reste vide et le hero affiche "Votre journée."
  const prenom = me?.prenom?.trim() ?? "";
  const today = new Date().toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" });

  // Échec du chargement principal SANS données : un seul ErrorState global,
  // on ne monte pas les widgets enfants (ActionCenter, briefing) sinon chacun
  // afficherait sa propre erreur => empilement de messages. Si des données sont
  // déjà présentes (refetch échoué), on garde le contenu + une bannière en haut.
  if (error && !data) {
    return (
      <div className="px-6 py-6 space-y-6">
        {/* Même en-tête que le mode normal (DashboardHero minimal, sans KPI ni
            DataSheet), comme les autres pages conservent leur titre en cas
            d'erreur. Seule la zone de données est remplacée par un ErrorState. */}
        <DashboardHero
          eyebrowLeft="Cockpit"
          eyebrowDate={today}
          greeting={prenom ? `Bonjour ${prenom}.` : "Votre journée."}
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
          <Alert tone="danger" className="flex flex-wrap items-center justify-between gap-3">
            <span>{error}</span>
            <button
              type="button"
              onClick={() => fetchData()}
              className="shrink-0 rounded-md border border-danger/30 bg-[var(--card)] px-3 py-1 text-xs font-semibold text-danger transition-colors hover:bg-danger/10"
            >
              Réessayer
            </button>
          </Alert>
        </div>
      )}

      {/* HERO COCKPIT — DashboardHero centralise le pattern eyebrow date +
          display greeting + KPI géant 64px + DataSheet 2 lignes.
          Mode minimal (sans KPI) pendant le chargement initial pour que le
          titre soit visible dès le 1er paint. */}
      {data ? (
        <DashboardHero
          eyebrowLeft="Cockpit"
          eyebrowDate={today}
          greeting={prenom ? `Bonjour ${prenom}.` : "Votre journée."}
          subtitle={
            progressPercent >= 100 ? (
              <>
                <span className="font-display font-semibold text-[var(--color-accent-deep)]">Objectif décroché.</span>{" "}
                <span className="font-numeric font-semibold text-[var(--foreground)]">{rdvMoisDisplay}</span>
                <span className="text-[var(--color-lr-text-tertiary)]"> / </span>
                <span className="font-numeric text-[var(--color-lr-text-secondary)]">{objectif}</span>
                <span className="ml-2 inline-flex items-center gap-1 rounded-full bg-[var(--color-accent)] px-2 py-0.5 text-[11px] font-mono font-semibold uppercase tracking-wider text-white animate-success-pulse">
                  {progressPercentDisplay}%
                </span>
              </>
            ) : (
              <>
                Objectif du mois&nbsp;: <span className="font-numeric font-semibold text-[var(--foreground)]">{rdvMoisDisplay}</span>
                <span className="text-[var(--color-lr-text-tertiary)]"> / </span>
                <span className="font-numeric text-[var(--color-lr-text-secondary)]">{objectif}</span>
                <span className="ml-2 inline-flex items-center gap-1 rounded-full bg-[var(--color-accent-subtle)] px-2 py-0.5 text-[11px] font-mono font-semibold uppercase tracking-wider text-[var(--color-accent-deep)]">
                  {progressPercentDisplay}%
                </span>
              </>
            )
          }
          progress={{ value: progressPercent }}
          hero={{
            label: "RDV cette semaine",
            value: rdvSemaineDisplay,
            isEmpty: rdvSemaine === 0,
            emptyTitle: "Aucun RDV pour l'instant.",
            emptyHint: "Une relance bien placée, et la semaine démarre.",
          }}
          sheet={[
            { label: "Appels aujourd'hui", value: appelsDisplay },
            { label: "RDV ce mois", value: rdvMoisDisplay },
          ]}
        />
      ) : (
        <DashboardHero
          eyebrowLeft="Cockpit"
          eyebrowDate={today}
          greeting={prenom ? `Bonjour ${prenom}.` : "Votre journée."}
        />
      )}

      {/* Centre d'actions, le coeur du dashboard (mount delayed 150ms apres hero) */}
      <div data-tour="action-center" className="animate-card-mount-delayed">
        <ActionCenter onRefresh={fetchData} />
      </div>

      {/* Briefing, header editorial via SectionHeader bordered (zero emoji).
          La date n'est PAS répétée ici, elle est déjà portée par le hero
          cockpit ("Cockpit · today"). */}
      {!loading && (
        <div data-tour="briefing" className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden animate-card-mount-delayed-2">
          <SectionHeader
            eyebrow="Briefing"
            eyebrowTone="muted"
            title="Préparation de votre journée"
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

      {/* Skeleton loading : on remplace les shimmer plats par le loader signature
          Atelier (speed-streaks) — meme attente, mais pendant que ça charge le
          vendeur voit "ça avance, ça accélère" cohérent avec le North Star. */}
      {loading && (
        <div className="space-y-4">
          <SkeletonInstrument label="Cockpit en démarrage" className="h-32" />
          <SkeletonInstrument label="Briefing du jour" className="h-48" />
          <SkeletonInstrument className="h-24" />
        </div>
      )}
    </div>
  );
}
