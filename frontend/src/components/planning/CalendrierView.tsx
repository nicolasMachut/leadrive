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
  id: number; titre: string; typeTache: string; dateEcheance: string;
  statut: string; leadId: number | null; leadNom: string | null; enRetard: boolean; source: string | null;
}

// Abandonnable = RDV lié à un lead (→ modale à motif) ou tâche auto IA (→ annulation
// directe). Aligné avec MaJourneeView. Une tâche manuelle non-RDV ne s'abandonne pas.
function peutAbandonner(tache: TacheDto): boolean {
  return (tache.typeTache === "RDV" && tache.leadId != null) || tache.source === "IA_AUTO";
}

interface JourCalendrier {
  date: string;
  taches: TacheDto[];
  nombreTotal: number;
}

interface CalendrierData {
  mois: number;
  annee: number;
  jours: JourCalendrier[];
}

const JOURS_SEMAINE = ["Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"];

// SVG path par type de tâche, pour signaler dans les chips de la grille mensuelle.
// Aligné avec MaJourneeView (mêmes paths). Sans ça, toutes les tâches du
// calendrier ressemblent à des chips orange identiques et le manager ne peut
// pas distinguer un RDV d'un Appel sans cliquer.
const TYPE_ICONS: Record<string, string> = {
  APPEL: "M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z",
  EMAIL: "M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z",
  RDV: "M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z",
  ESSAI: "M13 10V3L4 14h7v7l9-11h-7z",
  RELANCE: "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15",
  DEVIS: "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z",
  AUTRE: "M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
};

export default function CalendrierView() {
  const apiFetch = useApiClient();
  const { showToast } = useToast();
  const [mois, setMois] = useState(new Date().getMonth() + 1);
  const [annee, setAnnee] = useState(new Date().getFullYear());
  const [data, setData] = useState<CalendrierData | null>(null);
  const [loading, setLoading] = useState(true);
  const [erreur, setErreur] = useState("");
  const [selectedDay, setSelectedDay] = useState<JourCalendrier | null>(null);
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  const [reportModal, setReportModal] = useState<number | null>(null);
  const [feedbackTarget, setFeedbackTarget] = useState<TacheDto | null>(null);
  const [abandonTarget, setAbandonTarget] = useState<TacheDto | null>(null);
  const [creerModal, setCreerModal] = useState(false);
  const [newTache, setNewTache] = useState({ titre: "", typeTache: "APPEL", date: "", heure: "09:00" });

  async function creerTache() {
    if (!newTache.titre || !newTache.date) return;
    const dateEcheance = `${newTache.date}T${newTache.heure || "09:00"}`;
    await apiFetch(`${API_BASE}/api/taches`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        titre: newTache.titre,
        typeTache: newTache.typeTache,
        dateEcheance,
      }),
    });
    setCreerModal(false);
    setNewTache({ titre: "", typeTache: "APPEL", date: "", heure: "09:00" });
    fetchData();
  }

  const fetchData = useCallback(async () => {
    setLoading(true);
    setErreur("");
    try {
      const res = await apiFetch(`${API_BASE}/api/taches/calendrier?mois=${mois}&annee=${annee}`);
      if (res.ok) {
        setData(await res.json());
      } else {
        setErreur(formatErreur(new Error(`Erreur ${res.status}`)));
      }
    } catch (err) {
      setErreur(formatErreur(err));
    }
    finally { setLoading(false); }
  }, [apiFetch, mois, annee]);

  useEffect(() => { fetchData(); }, [fetchData]);

  async function refreshAfterAction() {
    setLoading(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/taches/calendrier?mois=${mois}&annee=${annee}`);
      if (res.ok) {
        const newData: CalendrierData = await res.json();
        setData(newData);
        if (selectedDay) {
          const updated = newData.jours.find(j => j.date === selectedDay.date);
          setSelectedDay(updated || null);
        }
      }
    } catch (err) {
      console.warn("Calendrier refresh failed", err);
    }
    finally { setLoading(false); }
  }

  async function marquerFait(id: number) {
    setActionLoading(id);
    try {
      await apiFetch(`${API_BASE}/api/taches/${id}/statut`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ statut: "TERMINEE" }),
      });
      await refreshAfterAction();
    } catch (err) {
      console.warn("Marquer fait failed", err);
      showToast({ titre: "Action échouée", message: formatErreur(err), tone: "danger" });
    }
    finally { setActionLoading(null); }
  }

  // Pour un RDV lie a un lead, on ouvre le slide-up de feedback avant de cloturer.
  // Pour les autres taches (Appel, Email, Relance...), on cloture directement.
  function onFaitClick(tache: TacheDto) {
    if (tache.typeTache === "RDV" && tache.leadId != null) {
      setFeedbackTarget(tache);
    } else {
      // Tache non-RDV (ou RDV sans lead) : cloture directe + toast undo generique.
      marquerFait(tache.id);
      toastUndoTache({ showToast, apiFetch, tacheId: tache.id, titre: "Tâche effectuée", onReverted: refreshAfterAction });
    }
  }

  // Abandon : RDV+lead → modale à motif (POST /feedback/abandon, annule + trace) ;
  // autres tâches → annulation directe.
  function onAbandonClick(tache: TacheDto) {
    if (tache.typeTache === "RDV" && tache.leadId != null) {
      setAbandonTarget(tache);
    } else {
      annulerTache(tache.id);
    }
  }

  // Reporter est géré par ReporterModal

  async function annulerTache(id: number) {
    setActionLoading(id);
    try {
      await apiFetch(`${API_BASE}/api/taches/${id}/statut`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ statut: "ANNULEE" }),
      });
      await refreshAfterAction();
    } catch (err) {
      console.warn("Annuler tache failed", err);
      showToast({ titre: "Annulation échouée", message: formatErreur(err), tone: "danger" });
    }
    finally { setActionLoading(null); }
  }

  function navigerMois(delta: number) {
    let m = mois + delta;
    let a = annee;
    if (m > 12) { m = 1; a++; }
    if (m < 1) { m = 12; a--; }
    setMois(m);
    setAnnee(a);
    setSelectedDay(null);
  }

  function aujourdHui() {
    const now = new Date();
    setMois(now.getMonth() + 1);
    setAnnee(now.getFullYear());
    setSelectedDay(null);
  }

  // Construire la grille du calendrier
  const premierJour = new Date(annee, mois - 1, 1);
  const dernierJour = new Date(annee, mois, 0);
  const jourDebutSemaine = (premierJour.getDay() + 6) % 7; // Lundi = 0
  const nbJours = dernierJour.getDate();

  const jourMap = new Map<string, JourCalendrier>();
  if (data) {
    for (const j of data.jours) {
      jourMap.set(j.date, j);
    }
  }

  const todayStr = new Date().toISOString().slice(0, 10);
  const moisLabel = new Intl.DateTimeFormat("fr-FR", { month: "long", year: "numeric" }).format(premierJour);

  // Compteurs mensuels agrégés à partir des jours, affichés sous le titre.
  // Donne un signal de charge mensuelle d'un coup d'oeil.
  const toutesTaches = data?.jours.flatMap(j => j.taches) ?? [];
  const moisTerminees = toutesTaches.filter(t => t.statut === "TERMINEE").length;
  const moisEnRetard = toutesTaches.filter(t => t.enRetard && t.statut !== "TERMINEE" && t.statut !== "ANNULEE").length;
  const moisEnCours = toutesTaches.filter(t => t.statut !== "TERMINEE" && t.statut !== "ANNULEE" && !t.enRetard).length;

  return (
    <>
      <div>
        {/* Header editorial : eyebrow mono + display title + actions inline.
            Pas de double H1 — la page parente ne rend plus son propre PageHeader. */}
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="inline-flex items-center gap-2 font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
              <span
                className="inline-block h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--color-accent)]"
                aria-hidden="true"
              />
              Calendrier
            </p>
            <h1 className="mt-2 font-display text-[28px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)] capitalize sm:text-[34px]">
              {moisLabel}
            </h1>
            {data && toutesTaches.length > 0 && (
              <p className="mt-1.5 font-mono text-[11px] uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)]">
                <span className="font-numeric font-semibold text-[var(--foreground)]">{moisTerminees}</span>{" "}terminées
                <span className="mx-2 text-[var(--color-lr-text-tertiary)]">·</span>
                <span className="font-numeric font-semibold text-[var(--foreground)]">{moisEnCours}</span>{" "}en cours
                {moisEnRetard > 0 && (
                  <>
                    <span className="mx-2 text-[var(--color-lr-text-tertiary)]">·</span>
                    <span className="font-numeric font-semibold text-warning">{moisEnRetard}</span>{" "}<span className="text-warning">à clôturer</span>
                  </>
                )}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => navigerMois(-1)} aria-label="Mois précédent" className="rounded-lg border border-[var(--border)] p-2 text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]">
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
            </button>
            <button onClick={aujourdHui} className="rounded-lg border border-[var(--border)] px-3 py-1.5 text-xs font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]">Aujourd&apos;hui</button>
            <button onClick={() => navigerMois(1)} aria-label="Mois suivant" className="rounded-lg border border-[var(--border)] p-2 text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]">
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" /></svg>
            </button>
            <button
              onClick={() => {
                setNewTache({ titre: "", typeTache: "APPEL", date: todayStr, heure: "09:00" });
                setCreerModal(true);
              }}
              className="ml-2 rounded-lg btn-primary px-3 py-1.5 text-xs font-medium text-white"
            >
              + Nouvelle tâche
            </button>
          </div>
        </div>

        {!loading && erreur && !data && (
          <ErrorState message={erreur} onRetry={fetchData} />
        )}

        <div className="flex gap-6">
          {/* Grille calendrier */}
          <div className="flex-1">
            <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
              {/* Jours de la semaine */}
              <div className="grid grid-cols-7 border-b border-[var(--border)]">
                {JOURS_SEMAINE.map(j => (
                  <div key={j} className="px-2 py-2 text-center text-xs font-medium text-[var(--color-lr-text-secondary)]">{j}</div>
                ))}
              </div>

              {/* Jours du mois */}
              <div className="grid grid-cols-7">
                {/* Cases vides pour le décalage */}
                {Array.from({ length: jourDebutSemaine }).map((_, i) => (
                  <div key={`empty-${i}`} className="min-h-[80px] border-b border-r border-[var(--border)] bg-[var(--muted)]/30" />
                ))}

                {/* Jours du mois */}
                {Array.from({ length: nbJours }).map((_, i) => {
                  const jour = i + 1;
                  const dateStr = `${annee}-${String(mois).padStart(2, "0")}-${String(jour).padStart(2, "0")}`;
                  const jourData = jourMap.get(dateStr);
                  const isToday = dateStr === todayStr;
                  const hasRetard = jourData?.taches.some(t => t.enRetard) || false;
                  const isSelected = selectedDay?.date === dateStr;

                  return (
                    <div
                      key={jour}
                      onClick={() => {
                        // Click sur une case = soit selectionner le jour (si taches), soit ouvrir
                        // la creation pre-remplie avec cette date. Si le jour a des taches, on
                        // privilegie l'affichage du detail (le bouton + en haut permet la creation).
                        if (jourData && jourData.taches.length > 0) {
                          setSelectedDay(jourData);
                        } else {
                          setNewTache({ titre: "", typeTache: "APPEL", date: dateStr, heure: "09:00" });
                          setCreerModal(true);
                        }
                      }}
                      className={`group relative min-h-[80px] border-b border-r border-[var(--border)] p-1.5 cursor-pointer transition-colors hover:bg-[var(--muted)]/50 ${
                        isToday ? "bg-accent/5" : ""
                      } ${isSelected ? "ring-2 ring-accent ring-inset" : ""}`}
                    >
                      <div className="flex items-center justify-between">
                        <span className={`text-xs font-medium ${isToday ? "text-accent" : "text-[var(--foreground)]"}`}>{jour}</span>
                        {hasRetard && <span className="h-1.5 w-1.5 rounded-full bg-warning" />}
                        {(!jourData || jourData.taches.length === 0) && (
                          <span className="opacity-0 group-hover:opacity-100 text-[10px] text-accent transition-opacity">+</span>
                        )}
                      </div>
                      {jourData && jourData.taches.length > 0 && (
                        <div className="mt-1 space-y-0.5">
                          {jourData.taches.slice(0, 3).map(t => {
                            const iconPath = TYPE_ICONS[t.typeTache] || TYPE_ICONS.AUTRE;
                            return (
                              <div key={t.id} className={`flex items-center gap-1 truncate rounded px-1 py-0.5 text-[9px] ${
                                t.statut === "TERMINEE" ? "bg-success/10 text-success line-through" :
                                t.enRetard ? "bg-warning/10 text-warning" :
                                "bg-accent/10 text-accent"
                              }`}>
                                <svg className="h-2.5 w-2.5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={iconPath} />
                                </svg>
                                <span className="truncate">{t.titre}</span>
                              </div>
                            );
                          })}
                          {jourData.nombreTotal > 3 && (
                            <p className="text-[8px] text-[var(--color-lr-text-tertiary)]">+{jourData.nombreTotal - 3}</p>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Panneau latéral, détail du jour sélectionné */}
          {selectedDay && (
            <div className="w-80 shrink-0 rounded-xl border border-[var(--border)] bg-[var(--card)] p-4">
              <div className="mb-3 flex items-center justify-between gap-2">
                <h2 className="text-sm font-semibold text-[var(--foreground)] truncate">
                  {new Date(selectedDay.date + "T00:00").toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" })}
                </h2>
                <button
                  onClick={() => {
                    setNewTache({ titre: "", typeTache: "APPEL", date: selectedDay.date, heure: "09:00" });
                    setCreerModal(true);
                  }}
                  className="shrink-0 rounded-md border border-accent/30 bg-accent/5 px-2 py-1 text-[11px] font-medium text-accent hover:bg-accent/10 transition-colors"
                >
                  + Tâche
                </button>
              </div>
              {selectedDay.taches.length === 0 && (
                <p className="font-mono text-[10px] uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)]">Journée libre</p>
              )}
              <div className="space-y-2">
                {selectedDay.taches.map(t => {
                  const isTerminee = t.statut === "TERMINEE" || t.statut === "ANNULEE";
                  const isLoading = actionLoading === t.id;
                  return (
                    <div key={t.id} className={`rounded-lg border border-[var(--border)] p-3 ${isTerminee ? "opacity-50" : ""}`}>
                      <p className={`text-sm font-medium ${isTerminee ? "line-through text-[var(--color-lr-text-secondary)]" : "text-[var(--foreground)]"}`}>
                        {t.titre}
                      </p>
                      <div className="mt-1 flex items-center gap-2 text-[11px] text-[var(--color-lr-text-secondary)]">
                        <span>{new Date(t.dateEcheance).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}</span>
                        {t.leadNom && t.leadId && (
                          <Link href={`/leads/${t.leadId}`} className="text-accent hover:underline truncate">{t.leadNom}</Link>
                        )}
                      </div>
                      {!isTerminee && (
                        <div className="mt-2 flex items-center gap-1.5">
                          <button onClick={() => onFaitClick(t)} disabled={isLoading}
                            className="rounded-md bg-success/10 px-3 py-2 text-[11px] font-medium text-success hover:bg-success/20 disabled:opacity-50 disabled:cursor-not-allowed">Effectué</button>
                          <button onClick={() => setReportModal(t.id)} disabled={isLoading}
                            className="rounded-md border border-[var(--border)] px-3 py-2 text-[11px] font-medium text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] disabled:opacity-50 disabled:cursor-not-allowed">Reporter</button>
                          {peutAbandonner(t) && (
                            <button onClick={() => onAbandonClick(t)} disabled={isLoading}
                              className="rounded-md px-3 py-2 text-[11px] font-medium text-[var(--color-lr-text-tertiary)] transition-colors hover:bg-danger/5 hover:text-danger disabled:opacity-50 disabled:cursor-not-allowed">Abandonner</button>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>

      <ReporterModal open={reportModal !== null} onClose={() => setReportModal(null)} tacheId={reportModal} apiFetch={apiFetch} onSuccess={refreshAfterAction}
        showToast={showToast}
        ancienneDateEcheance={
          reportModal != null && data
            ? data.jours.flatMap(j => j.taches).find(t => t.id === reportModal)?.dateEcheance ?? null
            : null
        } />

      <NouvelleTacheModal
        open={creerModal}
        newTache={newTache}
        setNewTache={setNewTache}
        onClose={() => setCreerModal(false)}
        onSubmit={creerTache}
      />

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
                titre: "RDV marqué effectué", onReverted: refreshAfterAction });
            }
          }}
          onSubmitted={() => {
            const target = feedbackTarget;
            setFeedbackTarget(null);
            if (target) {
              marquerFait(target.id);
              if (target.leadId != null) toastUndoRdv({ showToast, apiFetch, leadId: target.leadId, rdvId: target.id,
                titre: "RDV marqué effectué", onReverted: refreshAfterAction });
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
            refreshAfterAction();
            if (target && target.leadId != null) toastUndoRdv({ showToast, apiFetch, leadId: target.leadId, rdvId: target.id,
              titre: "RDV abandonné", onReverted: refreshAfterAction });
          }}
        />
      )}
    </>
  );
}
