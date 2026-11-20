"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useApiClient, formatErreur } from "@/lib/auth";
import { useToast } from "@/lib/realtime";
import ErrorState from "@/components/common/ErrorState";
import ReporterModal from "@/components/common/ReporterModal";
import FeedbackRdvSlideUp from "@/components/feedback/FeedbackRdvSlideUp";
import AbandonRdvModal from "@/components/feedback/AbandonRdvModal";
import NouvelleTacheModal from "@/components/planning/NouvelleTacheModal";
import { toastUndoRdv, toastUndoTache } from "@/lib/rdvUndo";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface TacheDto {
  id: number; titre: string; description: string | null; typeTache: string;
  dateEcheance: string; dureeMinutes: number | null;
  statut: string; source: string; leadId: number | null; leadNom: string | null;
  leadStatut: string | null; enRetard: boolean; tempsRestant: string | null;
}

interface MaJourneeData {
  date: string; enRetard: TacheDto[]; aujourdhui: TacheDto[];
  demain: TacheDto[]; totalAujourdhui: number; terminees: number; nbEnRetard: number;
}

const TYPE_ICONS: Record<string, string> = {
  APPEL: "M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z",
  EMAIL: "M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z",
  RDV: "M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z",
  ESSAI: "M13 10V3L4 14h7v7l9-11h-7z",
  RELANCE: "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15",
  DEVIS: "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z",
  AUTRE: "M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
};

// Date locale au format YYYY-MM-DD. PAS toISOString() qui renvoie l'UTC : entre minuit
// et ~02h heure de Paris, l'UTC est encore la veille → "Ma journée" affichait hier.
function formatDateLocale(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// Une tâche est "abandonnable" si c'est un RDV lié à un lead (→ modale à motif)
// ou une tâche auto de l'IA (→ annulation directe). Une tâche manuelle non-RDV
// se termine ou se reporte, elle ne s'"abandonne" pas.
function peutAbandonner(tache: TacheDto): boolean {
  return (tache.typeTache === "RDV" && tache.leadId != null) || tache.source === "IA_AUTO";
}

export default function MaJourneeView() {
  const apiFetch = useApiClient();
  const { showToast } = useToast();
  const [data, setData] = useState<MaJourneeData | null>(null);
  const [loading, setLoading] = useState(true);
  const [erreur, setErreur] = useState("");
  const [dateOffset, setDateOffset] = useState(0);
  const [reportModal, setReportModal] = useState<number | null>(null);
  const [feedbackTarget, setFeedbackTarget] = useState<TacheDto | null>(null);
  const [abandonTarget, setAbandonTarget] = useState<TacheDto | null>(null);
  const [creerModal, setCreerModal] = useState(false);
  const [newTache, setNewTache] = useState({ titre: "", typeTache: "APPEL", date: "", heure: "09:00" });
  // ID de la tâche en cours de mutation (Effectué/Annuler). Désactive les
  // boutons + opacity-50 pendant la requête pour éviter le double-click.
  const [actionLoading, setActionLoading] = useState<number | null>(null);

  const currentDate = new Date();
  currentDate.setDate(currentDate.getDate() + dateOffset);
  const dateStr = formatDateLocale(currentDate);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setErreur("");
    try {
      const res = await apiFetch(`${API_BASE}/api/taches/ma-journee?date=${dateStr}`);
      if (res.ok) {
        setData(await res.json());
      } else {
        setErreur(formatErreur(new Error(`Erreur ${res.status}`)));
      }
    } catch (err) {
      setErreur(formatErreur(err));
    }
    finally { setLoading(false); }
  }, [apiFetch, dateStr]);

  useEffect(() => { fetchData(); }, [fetchData]);

  async function marquerFait(id: number) {
    setActionLoading(id);
    try {
      const res = await apiFetch(`${API_BASE}/api/taches/${id}/statut`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ statut: "TERMINEE" }),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      await fetchData();
    } catch (err) {
      showToast({ titre: "Action échouée", message: formatErreur(err), tone: "danger" });
    } finally {
      setActionLoading(null);
    }
  }

  // Pour un RDV lie a un lead, on ouvre le slide-up de feedback avant de cloturer.
  // Pour les autres taches (Appel, Email, Relance...), on cloture directement.
  function onFaitClick(tache: TacheDto) {
    if (tache.typeTache === "RDV" && tache.leadId != null) {
      setFeedbackTarget(tache);
    } else {
      // Tache non-RDV (ou RDV sans lead) : cloture directe + toast undo generique.
      marquerFait(tache.id);
      toastUndoTache({ showToast, apiFetch, tacheId: tache.id, titre: "Tâche effectuée", onReverted: fetchData });
    }
  }

  async function annuler(id: number) {
    setActionLoading(id);
    try {
      const res = await apiFetch(`${API_BASE}/api/taches/${id}/statut`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ statut: "ANNULEE" }),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      await fetchData();
    } catch (err) {
      showToast({ titre: "Annulation échouée", message: formatErreur(err), tone: "danger" });
    } finally {
      setActionLoading(null);
    }
  }

  // Abandon : pour un RDV lié à un lead, on ouvre la modale à motif (même flux que
  // dashboard + fiche lead → POST /feedback/abandon qui annule ET trace le motif,
  // nourrissant la précision IA). Pour les autres tâches (relance IA, etc.), on
  // annule directement (pas de motif métier à capturer).
  function onAbandonClick(tache: TacheDto) {
    if (tache.typeTache === "RDV" && tache.leadId != null) {
      setAbandonTarget(tache);
    } else {
      annuler(tache.id);
    }
  }

  // Reporter est géré par ReporterModal

  async function creerTache() {
    if (!newTache.titre || !newTache.date) return;
    // Concatene date + heure en format ISO datetime-local que le backend
    // parse correctement (YYYY-MM-DDTHH:mm).
    const dateEcheance = `${newTache.date}T${newTache.heure || "09:00"}`;
    try {
      const res = await apiFetch(`${API_BASE}/api/taches`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          titre: newTache.titre,
          typeTache: newTache.typeTache,
          dateEcheance,
        }),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setCreerModal(false);
      setNewTache({ titre: "", typeTache: "APPEL", date: "", heure: "09:00" });
      fetchData();
    } catch (err) {
      showToast({ titre: "Tâche non créée", message: formatErreur(err), tone: "danger" });
    }
  }

  const jourLabel = new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long" }).format(currentDate);

  return (
      <div className="space-y-6 max-w-3xl mx-auto">
        {/* Header editorial, eyebrow mono + display title + status mono compact + nav */}
        <div className="flex items-end justify-between gap-4 border-b border-[var(--border)] pb-4">
          <div className="min-w-0">
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
              Journée
            </p>
            <h1 className="mt-1 font-display text-[28px] font-bold capitalize leading-[1.05] tracking-display-tight text-[var(--foreground)]">
              {jourLabel}
            </h1>
            {data && (
              <p className="mt-1.5 font-mono text-[11px] uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)]">
                <span className="font-numeric font-semibold text-[var(--foreground)]">{data.terminees}</span>
                <span className="text-[var(--color-lr-text-tertiary)]">/</span>
                <span className="font-numeric">{data.totalAujourdhui}</span>{" "}terminées
                {data.nbEnRetard > 0 && (
                  <>
                    <span className="mx-2 text-[var(--color-lr-text-tertiary)]">·</span>
                    <span className="font-numeric font-semibold text-warning">{data.nbEnRetard}</span>{" "}<span className="text-warning">à clôturer</span>
                  </>
                )}
              </p>
            )}
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
            <button
              onClick={() => setDateOffset(d => d - 1)}
              className="rounded-lg border border-[var(--border)] p-2 text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
              aria-label="Jour précédent"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 19l-7-7 7-7" /></svg>
            </button>
            <button
              onClick={() => setDateOffset(0)}
              className="rounded-lg border border-[var(--border)] px-3 py-2 font-mono text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
            >
              Aujourd&apos;hui
            </button>
            <button
              onClick={() => setDateOffset(d => d + 1)}
              className="rounded-lg border border-[var(--border)] p-2 text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
              aria-label="Jour suivant"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5l7 7-7 7" /></svg>
            </button>
          </div>
        </div>

        {loading && !data && (
          /* Skeleton 3 sections × 2-3 rows. Mime la structure finale, plus rapide
             perçu qu'un spinner rond générique. */
          <div className="space-y-6" aria-busy="true" aria-label="Chargement de votre journée">
            {[1, 2, 3].map((sec) => (
              <div key={sec}>
                <div className="mb-2 h-3 w-20 rounded animate-shimmer" />
                <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] divide-y divide-[var(--border)] overflow-hidden">
                  {[1, 2].map((row) => (
                    <div key={row} className="flex items-start gap-3 px-4 py-3">
                      <div className="mt-0.5 h-4 w-4 shrink-0 rounded animate-shimmer" />
                      <div className="min-w-0 flex-1 space-y-2">
                        <div className="h-4 w-3/4 rounded animate-shimmer" />
                        <div className="h-3 w-1/3 rounded animate-shimmer" />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && erreur && !data && (
          <ErrorState message={erreur} onRetry={fetchData} />
        )}

        {data && (
          <>
            {/* En retard */}
            {data.enRetard.length > 0 && (
              <Section title={`À clôturer (${data.enRetard.length})`} color="warning">
                {data.enRetard.map(t => (
                  <TacheRow key={t.id} tache={t} loading={actionLoading === t.id} onFait={() => onFaitClick(t)} onReporter={() => setReportModal(t.id)} onAnnuler={peutAbandonner(t) ? () => onAbandonClick(t) : undefined} />
                ))}
              </Section>
            )}

            {/* Aujourd'hui */}
            <Section title={`Aujourd'hui (${data.aujourdhui.length})`} color="accent">
              {data.aujourdhui.length === 0 && (
                <div className="flex flex-col items-start px-5 py-8">
                  <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                    Calendrier libre
                  </span>
                  <p className="mt-2 font-display text-[16px] font-bold tracking-display-tight text-[var(--foreground)]">
                    Aucune tâche aujourd&apos;hui.
                  </p>
                  <p className="mt-1 text-[12.5px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                    Planifiez un appel ou un RDV avec le bouton « + Nouvelle tâche », ou attendez qu&apos;un prospect demande à être rappelé. Vous serez prévenu en temps réel dans les conversations.
                  </p>
                  <div className="divider-gradient mt-4 w-20" />
                </div>
              )}
              {data.aujourdhui.map(t => (
                <TacheRow key={t.id} tache={t} loading={actionLoading === t.id} onFait={() => onFaitClick(t)} onReporter={() => setReportModal(t.id)} onAnnuler={peutAbandonner(t) ? () => onAbandonClick(t) : undefined} />
              ))}
            </Section>

            {/* Demain */}
            {data.demain.length > 0 && (
              <Section title={`Demain (${data.demain.length})`} color="muted">
                {data.demain.map(t => (
                  <TacheRow key={t.id} tache={t} compact />
                ))}
              </Section>
            )}
          </>
        )}

        {/* Bouton nouvelle tâche */}
        <button onClick={() => {
            setCreerModal(true);
            // Pre-remplit avec la date du jour courant affiche, heure 09:00 par defaut
            setNewTache({ ...newTache, date: formatDateLocale(currentDate), heure: "09:00" });
          }}
          className="w-full rounded-xl border-2 border-dashed border-[var(--border)] py-3 text-sm font-medium text-[var(--color-lr-text-secondary)] hover:border-accent hover:text-accent transition-colors">
          + Nouvelle tâche
        </button>

        {/* Report modal */}
        <ReporterModal open={reportModal !== null} onClose={() => setReportModal(null)} tacheId={reportModal} apiFetch={apiFetch} onSuccess={fetchData}
          showToast={showToast}
          ancienneDateEcheance={
            reportModal != null && data
              ? [...data.enRetard, ...data.aujourdhui, ...data.demain].find(t => t.id === reportModal)?.dateEcheance ?? null
              : null
          } />

        {/* Feedback RDV slide-up : meme comportement que sur le dashboard.
            Si l'utilisateur ferme sans repondre, on cloture quand meme. */}
        {feedbackTarget && (
          <FeedbackRdvSlideUp
            open={true}
            leadId={feedbackTarget.leadId as number}
            rdvId={feedbackTarget.id}
            nomProspect={feedbackTarget.leadNom || feedbackTarget.titre}
            onClose={() => {
              // Fermer le slide-up = marquer effectué (documenté /aide). Toast avec undo.
              const target = feedbackTarget;
              setFeedbackTarget(null);
              if (target) {
                marquerFait(target.id);
                if (target.leadId != null) toastUndoRdv({ showToast, apiFetch, leadId: target.leadId, rdvId: target.id,
                  titre: "RDV marqué effectué", onReverted: fetchData });
              }
            }}
            onSubmitted={() => {
              const target = feedbackTarget;
              setFeedbackTarget(null);
              if (target) {
                marquerFait(target.id);
                if (target.leadId != null) toastUndoRdv({ showToast, apiFetch, leadId: target.leadId, rdvId: target.id,
                  titre: "RDV marqué effectué", onReverted: fetchData });
              }
            }}
          />
        )}

        {/* Abandon RDV : même modale à motif que dashboard + fiche lead. Le backend
            annule la tâche atomiquement (POST /feedback/abandon), on rafraîchit ensuite. */}
        {abandonTarget && abandonTarget.leadId != null && (
          <AbandonRdvModal
            open={true}
            leadId={abandonTarget.leadId}
            rdvId={abandonTarget.id}
            nomProspect={abandonTarget.leadNom || abandonTarget.titre}
            onClose={() => setAbandonTarget(null)}
            onAbandoned={() => {
              const target = abandonTarget;
              setAbandonTarget(null);
              fetchData();
              if (target && target.leadId != null) toastUndoRdv({ showToast, apiFetch, leadId: target.leadId, rdvId: target.id,
                titre: "RDV abandonné", onReverted: fetchData });
            }}
          />
        )}

        {/* Créer modal */}
        <NouvelleTacheModal
          open={creerModal}
          newTache={newTache}
          setNewTache={setNewTache}
          onClose={() => setCreerModal(false)}
          onSubmit={creerTache}
        />
      </div>
  );
}

function Section({ title, color, children }: { title: string; color: "danger" | "warning" | "accent" | "muted"; children: React.ReactNode }) {
  const borderColor = color === "danger" ? "border-danger/30" : color === "warning" ? "border-warning/30" : color === "accent" ? "border-[var(--color-accent)]/30" : "border-[var(--border)]";
  const titleColor = color === "danger" ? "text-danger" : color === "warning" ? "text-warning" : color === "accent" ? "text-[var(--color-accent)]" : "text-[var(--color-lr-text-tertiary)]";
  return (
    <div>
      <h2 className={`mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] ${titleColor}`}>{title}</h2>
      <div className={`rounded-xl border ${borderColor} bg-[var(--card)] divide-y divide-[var(--border)] overflow-hidden`}>
        {children}
      </div>
    </div>
  );
}

function TacheRow({ tache, onFait, onReporter, onAnnuler, compact, loading }: {
  tache: TacheDto; onFait?: () => void; onReporter?: () => void; onAnnuler?: () => void; compact?: boolean; loading?: boolean;
}) {
  const done = tache.statut === "TERMINEE";
  const icon = TYPE_ICONS[tache.typeTache] || TYPE_ICONS.AUTRE;
  const heure = new Date(tache.dateEcheance).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });

  return (
    <div className={`flex items-start gap-3 px-4 py-3 ${done ? "opacity-50" : ""} ${loading ? "opacity-60" : ""}`}>
      <svg className={`mt-0.5 h-4 w-4 shrink-0 ${tache.enRetard ? "text-warning" : "text-[var(--color-lr-text-secondary)]"}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d={icon} />
      </svg>
      <div className="min-w-0 flex-1">
        <p className={`text-sm font-medium ${done ? "line-through text-[var(--color-lr-text-secondary)]" : "text-[var(--foreground)]"}`}>
          {tache.titre}
        </p>
        <div className="flex items-center gap-2 mt-0.5">
          <span className="text-[11px] text-[var(--color-lr-text-secondary)]">{heure}</span>
          {tache.tempsRestant && <span className={`text-[11px] ${tache.enRetard ? "text-warning" : "text-[var(--color-lr-text-tertiary)]"}`}>{tache.tempsRestant}</span>}
          {tache.source === "IA_AUTO" && <span className="rounded bg-[var(--muted)] px-1 py-0.5 text-[9px] font-medium text-[var(--color-lr-text-secondary)]">IA</span>}
          {tache.leadNom && (
            <Link href={`/leads/${tache.leadId}`} className="text-[11px] text-accent hover:underline truncate">{tache.leadNom}</Link>
          )}
        </div>
      </div>
      {!compact && !done && (
        <div className="flex items-center gap-1.5 shrink-0">
          {onFait && (
            <button onClick={onFait} disabled={loading} className="rounded-md bg-success/10 px-3 py-2 text-[11px] font-medium text-success hover:bg-success/20 disabled:opacity-50 disabled:cursor-not-allowed">Effectué</button>
          )}
          {onReporter && (
            <button onClick={onReporter} disabled={loading} className="rounded-md border border-[var(--border)] px-3 py-2 text-[11px] font-medium text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] disabled:opacity-50 disabled:cursor-not-allowed">Reporter</button>
          )}
          {onAnnuler && (
            <button onClick={onAnnuler} disabled={loading} className="rounded-md px-3 py-2 text-[11px] font-medium text-[var(--color-lr-text-tertiary)] transition-colors hover:bg-danger/5 hover:text-danger disabled:opacity-50 disabled:cursor-not-allowed">Abandonner</button>
          )}
        </div>
      )}
    </div>
  );
}
