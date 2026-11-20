"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient, formatErreur } from "@/lib/auth";
import { useToast } from "@/lib/realtime";
import { toastUndoRdv, toastUndoEnvoiAutoAnnule, toastEnvoiDiffere } from "@/lib/rdvUndo";
import Link from "next/link";
import InfoIcon from "@/components/common/InfoIcon";
import ReporterModal from "@/components/common/ReporterModal";
import RefreshButton from "@/components/common/RefreshButton";
import FeedbackRdvSlideUp from "@/components/feedback/FeedbackRdvSlideUp";
import AbandonRdvModal from "@/components/feedback/AbandonRdvModal";
import ErrorState from "@/components/common/ErrorState";
import { formatEnvoiAutoLabel } from "@/lib/envoiAuto";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface RdvAVenir {
  idLead: number;
  tacheId: number | null;
  nom: string;
  marque: string;
  modele: string;
  vendeurNom: string | null;
  creneau: string | null;
  rdvDateHeure: string | null;
  resumeVendeur: string | null;
}

interface ConvHandoff {
  conversationId: string;
  nomProspect: string;
  marque: string;
  modele: string;
  leadId: number;
  statut: string;
}

interface EnvoiAuto {
  idLead: number;
  nom: string;
  premierMessageAutoPrevuLe: string;
  premierMessageAutoEffectifLe: string | null;
  premierMessageAutoHorsPlage: boolean;
}


function isEnRetard(rdv: RdvAVenir): boolean {
  if (!rdv.rdvDateHeure) return false;
  return new Date(rdv.rdvDateHeure) < new Date();
}

function isAujourdhui(rdv: RdvAVenir): boolean {
  if (!rdv.rdvDateHeure) return false;
  const d = new Date(rdv.rdvDateHeure);
  const now = new Date();
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
}

// Anciennete en jours pleins du RDV en retard (>= 0). Sert a moduler l'opacite du fond.
function joursDeRetard(rdv: RdvAVenir): number {
  if (!rdv.rdvDateHeure) return 0;
  const diff = Date.now() - new Date(rdv.rdvDateHeure).getTime();
  return Math.max(0, Math.floor(diff / 86400000));
}

// 1j -> ~5%, 7j+ -> 12%. Wash ambre TRES leger : l'etat "en retard" est une
// action A FAIRE (cloturer le RDV), pas une erreur. Ambre discret + chip
// "A cloturer" + CTA positif suffisent a le mettre en avant. Fond volontairement
// clair pour que le texte (neutre) et les boutons restent lisibles (pas de
// "meme teinte sur meme teinte").
function opaciteRetard(jours: number): number {
  if (jours <= 0) return 0;
  if (jours >= 7) return 0.12;
  return Math.min(0.12, jours * 0.015 + 0.04);
}

// Tri : RDV en retard d'abord (les plus anciens en premier), puis aujourd'hui, puis le futur.
function trierParPriorite(rdvs: RdvAVenir[]): RdvAVenir[] {
  return [...rdvs].sort((a, b) => {
    const aRetard = isEnRetard(a);
    const bRetard = isEnRetard(b);
    if (aRetard && !bRetard) return -1;
    if (!aRetard && bRetard) return 1;
    const ta = a.rdvDateHeure ? new Date(a.rdvDateHeure).getTime() : Number.MAX_SAFE_INTEGER;
    const tb = b.rdvDateHeure ? new Date(b.rdvDateHeure).getTime() : Number.MAX_SAFE_INTEGER;
    return ta - tb;
  });
}

export default function ActionCenter({ onRefresh }: { onRefresh?: () => void } = {}) {
  const apiFetch = useApiClient();
  const { showToast } = useToast();
  const [rdvAVenir, setRdvAVenir] = useState<RdvAVenir[]>([]);
  const [handoffs, setHandoffs] = useState<ConvHandoff[]>([]);
  const [envoisAuto, setEnvoisAuto] = useState<EnvoiAuto[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>("");
  const [actionLoading, setActionLoading] = useState<number | null>(null);
  // Action en cours sur un envoi auto (envoyer / annuler) : idLead + type.
  const [envoiAutoLoading, setEnvoiAutoLoading] = useState<{ idLead: number; type: "envoyer" | "annuler" } | null>(null);
  const [reportModal, setReportModal] = useState<number | null>(null);
  const [feedbackTarget, setFeedbackTarget] = useState<RdvAVenir | null>(null);
  const [abandonTarget, setAbandonTarget] = useState<RdvAVenir | null>(null);

  const fetchAll = useCallback(async () => {
    setLoadError("");
    try {
      const [rdvRes, convRes, envoiRes] = await Promise.all([
        apiFetch(`${API_BASE}/api/leads/rdv-a-venir`),
        apiFetch(`${API_BASE}/api/conversations?modePilotage=HUMAIN`),
        apiFetch(`${API_BASE}/api/leads/envois-auto-a-venir`),
      ]);
      if (rdvRes.ok) setRdvAVenir(await rdvRes.json());
      if (convRes.ok) {
        const convs = await convRes.json();
        const arr: ConvHandoff[] = Array.isArray(convs) ? convs : convs.content ?? [];
        setHandoffs(arr.filter((c) => c.statut !== "EN_COURS").slice(0, 5));
      }
      if (envoiRes.ok) setEnvoisAuto(await envoiRes.json());
    } catch (err) {
      console.warn("ActionCenter fetch failed", err);
      setLoadError(formatErreur(err));
    }
    finally { setLoading(false); }
  }, [apiFetch]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  // Auto-refresh discret tant qu'un envoi automatique du premier message est en
  // attente : l'envoi part via un batch serveur (toutes les minutes), donc cette
  // carte ne "sait" pas quand il part. On refetch toutes les 30 s tant qu'il y a
  // au moins un envoi en attente -> une fois le message parti, le lead sort de la
  // liste (flag serveur remis à null) et l'intervalle se nettoie (plus rien à
  // attendre). Zéro coût quand aucun envoi n'est programmé.
  useEffect(() => {
    if (envoisAuto.length === 0) return;
    const timer = setInterval(() => fetchAll(), 30000);
    return () => clearInterval(timer);
  }, [envoisAuto.length, fetchAll]);

  // Marque le RDV comme effectue/abandonne cote API. Appele apres feedback,
  // ou directement si l'utilisateur ferme le slide-up sans repondre (jamais bloquer).
  async function marquerRdv(rdv: RdvAVenir, action: "effectue" | "abandonner") {
    setActionLoading(rdv.idLead);
    try {
      if (rdv.tacheId) {
        const statut = action === "effectue" ? "TERMINEE" : "ANNULEE";
        await apiFetch(`${API_BASE}/api/taches/${rdv.tacheId}/statut`, {
          method: "PATCH",
          body: JSON.stringify({ statut }),
        });
      } else {
        // PATCH /statut (deplacerLead) et non PUT /leads/{id} : seul ce chemin trace
        // la transition dans historique_statut (source SELECTBOX, non facturable).
        await apiFetch(`${API_BASE}/api/leads/${rdv.idLead}/statut`, {
          method: "PATCH",
          headers: { "X-Source": "SELECTBOX" },
          body: JSON.stringify({ statut: "TERMINE" }),
        });
      }
      fetchAll();
      if (onRefresh) onRefresh();
    } catch (err) {
      console.warn("RDV action failed", err);
      showToast({ titre: "Action RDV échouée", message: formatErreur(err), tone: "danger" });
    }
    finally { setActionLoading(null); }
  }

  // Envoi du premier message auto depuis le dashboard. Action IRREVERSIBLE (message
  // parti = non rappelable) : on DIFFERE l'envoi de quelques secondes avec un bouton
  // Annuler. Le DTO léger ne porte pas le téléphone : on recharge le lead pour
  // récupérer le numéro E.164 dans le onEnvoyer (au moment de l'envoi reel), puis
  // on POST /whatsapp. Rien ne part tant que le delai n'est pas ecoule.
  function envoyerMaintenant(idLead: number) {
    // Anti double-envoi : pendant le differe (~7s) le bouton de cette ligne doit
    // rester desactive. Sans ce garde, un double-clic declenchait 2 differes = 2 envois.
    if (envoiAutoLoading !== null) return;
    setEnvoiAutoLoading({ idLead, type: "envoyer" });
    toastEnvoiDiffere({
      showToast,
      onEnvoyer: async () => {
        try {
          const leadRes = await apiFetch(`${API_BASE}/api/leads/${idLead}`);
          if (!leadRes.ok) throw new Error(`Erreur ${leadRes.status}`);
          const leadData = await leadRes.json();
          const res = await apiFetch(`${API_BASE}/api/leads/${idLead}/whatsapp`, {
            method: "POST",
            body: JSON.stringify({ numeroDestinataire: leadData.telephone ?? "" }),
          });
          if (!res.ok) {
            const body = await res.text();
            throw new Error(body || `Erreur ${res.status}`);
          }
          showToast({ titre: "Message envoyé", message: "Le premier message WhatsApp a été envoyé.", tone: "success" });
          fetchAll();
          if (onRefresh) onRefresh();
        } finally {
          setEnvoiAutoLoading(null);
        }
      },
      onErreur: (err) => {
        showToast({ titre: "Envoi échoué", message: formatErreur(err), tone: "danger" });
      },
      // Annulation du differe : on reactive le bouton de la ligne.
      onAnnule: () => setEnvoiAutoLoading(null),
    });
  }

  // Annulation de l'envoi auto. Action REVERSIBLE : toast succes + "Annuler" qui
  // RE-ARME l'envoi a l'echeance d'origine (premierMessageAutoPrevuLe de la ligne).
  async function annulerEnvoiAuto(idLead: number, anciennePrevuLe: string) {
    setEnvoiAutoLoading({ idLead, type: "annuler" });
    try {
      const res = await apiFetch(`${API_BASE}/api/leads/${idLead}/envoi-auto/annuler`, { method: "POST" });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      toastUndoEnvoiAutoAnnule({
        showToast,
        apiFetch,
        leadId: idLead,
        anciennePrevuLe,
        onReverted: () => {
          fetchAll();
          if (onRefresh) onRefresh();
        },
      });
      fetchAll();
      if (onRefresh) onRefresh();
    } catch (err) {
      showToast({ titre: "Annulation échouée", message: formatErreur(err), tone: "danger" });
    } finally {
      setEnvoiAutoLoading(null);
    }
  }

  const totalActions = handoffs.length + rdvAVenir.length + envoisAuto.length;

  if (loading) {
    /* Skeleton 2-cards qui mime la structure finale (header card + rows).
       Plus rapide perçu qu'un spinner rond générique. */
    return (
      <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2" aria-busy="true" aria-label="Chargement des actions">
        {[1, 2].map((i) => (
          <div key={i} className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
            <div className="flex items-center gap-3 border-b border-[var(--border)] px-4 py-3">
              <div className="h-8 w-8 rounded-lg animate-shimmer" />
              <div className="flex-1">
                <div className="h-3 w-32 rounded animate-shimmer" />
              </div>
              <div className="h-5 w-6 rounded-full animate-shimmer" />
            </div>
            <div className="space-y-2 p-3">
              {[1, 2].map((j) => (
                <div key={j} className="space-y-1.5">
                  <div className="h-3.5 w-3/4 rounded animate-shimmer" />
                  <div className="h-2.5 w-1/2 rounded animate-shimmer" />
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (loadError && totalActions === 0) {
    return (
      <div className="mb-4 rounded-xl border border-[var(--border)] bg-[var(--card)]">
        <ErrorState message={loadError} onRetry={fetchAll} />
      </div>
    );
  }

  if (totalActions === 0) {
    return (
      <div className="mb-4 flex items-start gap-3 rounded-xl border border-[var(--border)] bg-[var(--card)] px-5 py-5">
        {/* Voyant dot ember pulsant subtil, instrument cluster vibe */}
        <span className="relative mt-1 flex h-2 w-2 shrink-0">
          <span className="absolute inset-0 rounded-full bg-success animate-ping opacity-60" />
          <span className="relative rounded-full bg-success h-2 w-2" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-success">
            Pipeline à jour
          </p>
          <h3 className="mt-1.5 font-display text-[18px] font-bold tracking-display-tight text-[var(--foreground)]">
            Rien à traiter pour l&apos;instant.
          </h3>
          <p className="mt-1 text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)]">
            Aucune conversation à reprendre, aucun RDV à clôturer. L&apos;IA continue ses relances en arrière-plan.
          </p>
          <div className="divider-gradient mt-4 w-20" />
        </div>
        <RefreshButton onClick={() => fetchAll()} />
      </div>
    );
  }

  return (
    <>


      {/* Grille 2 colonnes */}
      <div className="mb-4 grid grid-cols-1 gap-3 md:grid-cols-2">

        {/* Carte : Conversations à reprendre */}
        <ActionCard
          tone="warning"
          label="Conversations à reprendre"
          count={handoffs.length}
          info="Quand l'IA détecte une situation complexe (demande de prix, ton négatif, sujet sensible), elle passe la main au vendeur."
          icon={<path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />}
          empty="Aucune conversation en attente"
        >
          {handoffs.map((h) => (
            <Link key={h.conversationId} href={`/inbox?conv=${h.conversationId}`}
              className="flex items-center justify-between gap-2 rounded-lg px-3 py-2 hover:bg-warning/5 transition-colors">
              <div className="min-w-0">
                <p className="text-sm font-medium text-[var(--foreground)] truncate">{h.nomProspect}</p>
                <p className="text-[11px] text-[var(--color-lr-text-secondary)]">{[h.marque, h.modele].filter(Boolean).join(" ")}</p>
              </div>
              <span className="shrink-0 text-[11px] font-medium text-warning">Reprendre →</span>
            </Link>
          ))}
        </ActionCard>

        {/* Carte : RDV */}
        <ActionCard
          tone="accent"
          label="Rendez-vous"
          count={rdvAVenir.length}
          alertCount={rdvAVenir.filter(isEnRetard).length}
          info="Leads avec un RDV pris par l'IA. Triés par date : les plus anciens (en retard) en premier."
          icon={<path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />}
          empty="Aucun RDV"
        >
          {trierParPriorite(rdvAVenir).map((r, idx) => {
            const retard = isEnRetard(r);
            const aujourdhui = isAujourdhui(r);
            const showActions = retard || aujourdhui;
            const isLoading = actionLoading === r.idLead;
            const jours = retard ? joursDeRetard(r) : 0;
            const opacite = opaciteRetard(jours);
            // Pulse une seule fois au chargement pour les RDV en retard (max 3 cartes pour ne pas spammer)
            const pulse = retard && idx < 3;
            // Backgrounds via color-mix OKLCH au lieu de rgba() hex hardcoded.
            // Pas de borderLeft side-stripe (absolute ban impeccable design laws).
            // Etat "en retard" = action A FAIRE (cloturer), PAS une erreur : on
            // utilise l'ambre (warning = "a traiter"), jamais le rouge (danger =
            // "casse"). Wash tres leger (cf. opaciteRetard) + bordure ambre + chip
            // + CTA positif suffisent a le mettre en avant sans l'alarmer.
            const styleRetard = retard
              ? { backgroundColor: `color-mix(in oklch, var(--color-warning) ${Math.round(opacite * 100)}%, transparent)` }
              : undefined;
            return (
              <div
                key={r.idLead}
                style={styleRetard}
                className={`rounded-lg px-3 py-2 transition-colors ${pulse ? "animate-pulse-attention" : ""} ${retard ? "border border-warning/25" : "hover:bg-accent/5"}`}
              >
                <div className="flex items-center justify-between gap-2">
                  <Link href={`/leads/${r.idLead}`} className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-[var(--foreground)] truncate">{r.nom}</p>
                    <p className="text-[11px] text-[var(--color-lr-text-secondary)]">
                      {[r.marque, r.modele].filter(Boolean).join(" ")}
                      {r.vendeurNom && <> · <span className="text-[var(--color-lr-text-tertiary)]">{r.vendeurNom}</span></>}
                      {r.creneau && <>, <span className={`font-medium ${retard ? "text-[var(--color-lr-text-secondary)]" : "text-accent"}`}>{r.creneau}</span></>}
                    </p>
                    {retard && (
                      <div className="mt-1">
                        {/* Chip de statut "tache a faire", pas un message d'erreur :
                            pastille ambre discrete qui met en avant sans alarmer. */}
                        <span className="inline-flex items-center gap-1 rounded-full bg-warning/15 px-2 py-0.5 text-[10px] font-semibold text-warning">
                          <svg className="h-2.5 w-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                          </svg>
                          À clôturer{jours >= 1 && ` · ${jours}j`}
                        </span>
                      </div>
                    )}
                  </Link>
                  {!showActions && (
                    <Link href={`/leads/${r.idLead}`} className="shrink-0 text-[11px] font-medium text-accent">Voir →</Link>
                  )}
                </div>
                {showActions && (
                  <div className="mt-2 flex items-center gap-1.5">
                    <button onClick={() => setFeedbackTarget(r)} disabled={isLoading}
                      className="rounded-md px-3 py-2 text-[11.5px] font-semibold bg-success/10 text-success hover:bg-success/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
                      Effectué
                    </button>
                    <button onClick={() => r.tacheId && setReportModal(r.tacheId)} disabled={isLoading || !r.tacheId}
                      title={!r.tacheId ? "Report indisponible pour ce RDV" : undefined}
                      className="rounded-md border border-[var(--border)] px-3 py-2 text-[11.5px] font-medium text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
                      Reporter
                    </button>
                    {retard && (
                      <button onClick={() => setAbandonTarget(r)} disabled={isLoading}
                        className="rounded-md px-3 py-2 text-[11.5px] font-medium text-[var(--color-lr-text-tertiary)] hover:bg-danger/5 hover:text-danger disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
                        Abandonner
                      </button>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </ActionCard>

        {/* Carte : Messages auto à venir (masquée si aucun envoi programmé). */}
        {envoisAuto.length > 0 && (
          <ActionCard
            tone="accent"
            label={`Messages auto à venir`}
            count={envoisAuto.length}
            info="Leads reçus par email pour lesquels l'IA enverra automatiquement le premier message WhatsApp. Vous pouvez l'envoyer maintenant ou l'annuler."
            icon={<path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />}
            empty="Aucun envoi programmé"
          >
            {envoisAuto.map((e) => {
              const isLoading = envoiAutoLoading?.idLead === e.idLead;
              return (
                <div key={e.idLead} className="rounded-lg px-3 py-2 transition-colors hover:bg-accent/5">
                  <div className="flex items-center justify-between gap-2">
                    <Link href={`/leads/${e.idLead}`} className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-[var(--foreground)]">{e.nom}</p>
                      <p className="text-[11px] text-[var(--color-lr-text-secondary)]">
                        Envoi <span className="font-medium text-accent">{formatEnvoiAutoLabel(e.premierMessageAutoEffectifLe, e.premierMessageAutoPrevuLe)}</span>
                        {e.premierMessageAutoHorsPlage && (
                          <span className="text-[var(--color-lr-text-tertiary)]"> · hors plage</span>
                        )}
                      </p>
                    </Link>
                  </div>
                  <div className="mt-2 flex items-center gap-1.5">
                    <button
                      onClick={() => envoyerMaintenant(e.idLead)}
                      disabled={isLoading}
                      className="rounded-md bg-accent/10 px-3 py-2 text-[11.5px] font-semibold text-accent transition-colors hover:bg-accent/20 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {isLoading && envoiAutoLoading?.type === "envoyer" ? "Envoi…" : "Envoyer maintenant"}
                    </button>
                    <button
                      onClick={() => annulerEnvoiAuto(e.idLead, e.premierMessageAutoPrevuLe)}
                      disabled={isLoading}
                      className="rounded-md px-3 py-2 text-[11.5px] font-medium text-[var(--color-lr-text-tertiary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)] disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {isLoading && envoiAutoLoading?.type === "annuler" ? "Annulation…" : "Annuler"}
                    </button>
                  </div>
                </div>
              );
            })}
          </ActionCard>
        )}


      </div>

      <ReporterModal open={reportModal !== null} onClose={() => setReportModal(null)} tacheId={reportModal} apiFetch={apiFetch}
        showToast={showToast}
        ancienneDateEcheance={reportModal != null ? rdvAVenir.find(r => r.tacheId === reportModal)?.rdvDateHeure ?? null : null}
        onSuccess={() => { fetchAll(); if (onRefresh) onRefresh(); }} />

      {feedbackTarget && (
        <FeedbackRdvSlideUp
          open={true}
          leadId={feedbackTarget.idLead}
          rdvId={feedbackTarget.tacheId ?? undefined}
          nomProspect={feedbackTarget.nom}
          onClose={() => {
            // Fermer le slide-up = marquer effectue (documente /aide). Toast avec undo.
            const target = feedbackTarget;
            setFeedbackTarget(null);
            if (target) {
              marquerRdv(target, "effectue");
              toastUndoRdv({ showToast, apiFetch, leadId: target.idLead, rdvId: target.tacheId,
                titre: "RDV marqué effectué", onReverted: () => { fetchAll(); if (onRefresh) onRefresh(); } });
            }
          }}
          onSubmitted={() => {
            const target = feedbackTarget;
            setFeedbackTarget(null);
            if (target) {
              marquerRdv(target, "effectue");
              toastUndoRdv({ showToast, apiFetch, leadId: target.idLead, rdvId: target.tacheId,
                titre: "RDV marqué effectué", onReverted: () => { fetchAll(); if (onRefresh) onRefresh(); } });
            }
          }}
        />
      )}

      {abandonTarget && (
        <AbandonRdvModal
          open={true}
          leadId={abandonTarget.idLead}
          rdvId={abandonTarget.tacheId ?? undefined}
          nomProspect={abandonTarget.nom}
          onClose={() => setAbandonTarget(null)}
          onAbandoned={() => {
            // Le backend annule la tache atomiquement (POST /feedback/abandon).
            // On rafraichit + on propose l'undo.
            const target = abandonTarget;
            setAbandonTarget(null);
            fetchAll();
            if (onRefresh) onRefresh();
            if (target) toastUndoRdv({ showToast, apiFetch, leadId: target.idLead, rdvId: target.tacheId,
              titre: "RDV abandonné", onReverted: () => { fetchAll(); if (onRefresh) onRefresh(); } });
          }}
        />
      )}
    </>
  );
}

// ── Composant carte réutilisable ──────────────────────────────────────────

// Mapping des tons sémantiques vers les classes Tailwind utilisant les tokens
// du design system (cf. globals.css). Naming aligné sur la sémantique
// (warning / accent / success), pas sur les anciennes couleurs Tailwind
// "amber / cyan / emerald".
type Tone = "warning" | "accent" | "success";

const toneMap: Record<Tone, { bg: string; border: string; text: string; icon: string; badge: string }> = {
  warning: {
    bg: "bg-warning/5",
    border: "border-warning/30",
    text: "text-warning",
    icon: "bg-warning/10 text-warning",
    badge: "bg-warning/10 text-warning",
  },
  accent: {
    bg: "bg-accent/5",
    border: "border-accent/20",
    text: "text-accent",
    icon: "bg-accent/10 text-accent",
    badge: "bg-accent/10 text-accent",
  },
  success: {
    bg: "bg-success/5",
    border: "border-success/30",
    text: "text-success",
    icon: "bg-success/10 text-success",
    badge: "bg-success/10 text-success",
  },
};

function ActionCard({
  tone,
  label,
  count,
  alertCount = 0,
  info,
  icon,
  empty,
  children,
}: {
  tone: Tone;
  label: string;
  count: number;
  alertCount?: number;
  info: string;
  icon: React.ReactNode;
  empty: string;
  children: React.ReactNode;
}) {
  const c = toneMap[tone];

  return (
    <div className={`rounded-xl border ${c.border} ${c.bg} overflow-hidden`}>
      {/* Header editorial, eyebrow mono uppercase + count en JetBrains Mono */}
      <div className="flex items-center gap-3 border-b border-[var(--border)] px-4 py-3">
        <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${c.icon}`}>
          <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">{icon}</svg>
        </div>
        <div className="min-w-0 flex-1">
          <p className={`flex items-center gap-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] ${c.text}`}>
            {label}
            <InfoIcon>{info}</InfoIcon>
          </p>
        </div>
        {alertCount > 0 && (
          <span
            className="flex h-[20px] min-w-[22px] items-center justify-center rounded-full bg-warning/15 px-1.5 font-mono text-[10px] font-semibold tabular-nums text-warning"
            title={`${alertCount} RDV à clôturer`}
          >
            {alertCount}
          </span>
        )}
        {count > 0 && count > alertCount && (
          <span
            className={`flex h-[20px] min-w-[22px] items-center justify-center rounded-full px-1.5 font-mono text-[10px] font-semibold tabular-nums ${c.badge}`}
            title={`${count} ${label.toLowerCase()} au total`}
          >
            {count}
          </span>
        )}
      </div>

      {/* Items, scroll interne avec mask-fade-bottom pour signaler "il y a plus
          en dessous" quand la liste dépasse 200px. */}
      <div
        className="max-h-[200px] overflow-y-auto"
        style={count > 3 ? { maskImage: "linear-gradient(to bottom, black 82%, transparent)", WebkitMaskImage: "linear-gradient(to bottom, black 82%, transparent)" } : undefined}
      >
        {count === 0 ? (
          <p className="px-4 py-3 text-[12px] italic text-[var(--color-lr-text-tertiary)]">{empty}</p>
        ) : (
          <div className="p-1.5">{children}</div>
        )}
      </div>
    </div>
  );
}
