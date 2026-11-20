"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient, formatErreur } from "@/lib/auth";
import ErrorState from "@/components/common/ErrorState";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface AnalyseData {
  id: number;
  conversationId: string;
  intention: "RDV" | "STOP" | "REFUS" | "AUTRE";
  signauxAchat: string[] | null;
  objections: string[] | null;
  besoinHandoff: boolean;
  raisonHandoff: string | null;
  resumeVendeur: string | null;
  tonProspect: string | null;
  questionEnAttente: string | null;
  rdvPropose: boolean;
  rdvConfirme: boolean;
  rdvCreneau: string | null;
  rdvDateHeure: string | null;
  /** Date du RDV REEL en cours (tache, mutable). Prioritaire sur rdvDateHeure (extraction figee). */
  rdvTacheDateHeure: string | null;
  creeLe: string;
}

interface ConversationAnalysePanelProps {
  conversationId: string;
  /** Compteur incremente quand l'inbox recoit un evenement SSE "analyse_mise_a_jour" pour cette conversation */
  refreshTrigger?: number;
}

// Style intention, pastille dot couleur + label, plus d'emoji (impeccable/gpt-taste).
const INTENTION_STYLES: Record<string, { bg: string; text: string; label: string; dot: string }> = {
  RDV: { bg: "bg-success/10", text: "text-success", label: "RDV en cours", dot: "bg-success" },
  STOP: { bg: "bg-danger/10", text: "text-danger", label: "Demande STOP", dot: "bg-danger" },
  REFUS: { bg: "bg-warning/10", text: "text-warning", label: "Refus exprimé", dot: "bg-warning" },
  AUTRE: { bg: "bg-[var(--muted)]", text: "text-[var(--color-lr-text-secondary)]", label: "Échange en cours", dot: "bg-[var(--color-lr-text-tertiary)]" },
};

const SIGNAL_LABELS: Record<string, string> = {
  budget_mentionne: "Budget mentionné",
  urgence_forte: "Urgence forte",
  financement_ok: "Financement OK",
  reprise_vehicule: "Reprise véhicule",
  modele_precis: "Modèle précis",
  essai_demande: "Essai demandé",
  comparatif_concurrent: "Compare concurrent",
};

const OBJECTION_LABELS: Record<string, string> = {
  prix_trop_eleve: "Prix trop élevé",
  delai_trop_long: "Délai trop long",
  disponibilite_vehicule: "Dispo véhicule",
  financement_refuse: "Financement refusé",
  reprise_insuffisante: "Reprise insuffisante",
  concurrent_moins_cher: "Concurrent moins cher",
  hesitation_modele: "Hésitation modèle",
};

const TON_STYLES: Record<string, { dot: string; text: string; label: string }> = {
  enthousiaste: { dot: "bg-success", text: "text-success", label: "Enthousiaste" },
  neutre: { dot: "bg-[var(--color-lr-text-secondary)]", text: "text-[var(--color-lr-text-secondary)]", label: "Neutre" },
  impatient: { dot: "bg-warning", text: "text-warning", label: "Impatient" },
  mecontent: { dot: "bg-danger", text: "text-danger", label: "Mécontent" },
  hesitant: { dot: "bg-accent", text: "text-accent", label: "Hésitant" },
};

function labelize(key: string, dict: Record<string, string>): string {
  return dict[key] ?? key.replace(/_/g, " ");
}

function formatRdvDateHeure(isoDate: string): { label: string; badge: string; badgeStyle: string; passe: boolean } {
  const rdv = new Date(isoDate);
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const rdvDayStart = new Date(rdv.getFullYear(), rdv.getMonth(), rdv.getDate());
  const diffDays = Math.round((rdvDayStart.getTime() - todayStart.getTime()) / (1000 * 60 * 60 * 24));

  const heure = rdv.toLocaleString("fr-FR", { hour: "2-digit", minute: "2-digit" });
  const dateAbsolue = rdv.toLocaleString("fr-FR", { weekday: "long", day: "numeric", month: "long" });
  const label = dateAbsolue.charAt(0).toUpperCase() + dateAbsolue.slice(1) + " à " + heure;

  if (diffDays < 0) return { label, badge: "Passé", badgeStyle: "bg-danger/10 text-danger", passe: true };
  if (diffDays === 0) return { label, badge: "Aujourd'hui", badgeStyle: "bg-warning/10 text-warning", passe: false };
  if (diffDays === 1) return { label, badge: "Demain", badgeStyle: "bg-success/10 text-success", passe: false };
  return { label, badge: `Dans ${diffDays} jours`, badgeStyle: "bg-success/10 text-success", passe: false };
}

function formatRelatif(isoDate: string): string {
  const now = Date.now();
  const ts = new Date(isoDate).getTime();
  const diffMs = now - ts;
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return "à l'instant";
  if (diffMin < 60) return `il y a ${diffMin} min`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `il y a ${diffH}h`;
  return new Date(isoDate).toLocaleDateString("fr-FR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}

export default function ConversationAnalysePanel({ conversationId, refreshTrigger = 0 }: ConversationAnalysePanelProps) {
  const apiFetch = useApiClient();
  const [analyse, setAnalyse] = useState<AnalyseData | null>(null);
  const [loading, setLoading] = useState(true);
  const [empty, setEmpty] = useState(false);
  const [erreur, setErreur] = useState("");
  const [briefCopied, setBriefCopied] = useState(false);
  const [briefError, setBriefError] = useState(false);

  const fetchAnalyse = useCallback(async () => {
    setLoading(true);
    setEmpty(false);
    setErreur("");
    try {
      const res = await apiFetch(`${API_BASE}/api/conversations/${conversationId}/analyse`);
      if (res.status === 204) {
        // 204 = pas encore d'analyse (cas légitime), distinct d'une vraie erreur.
        setAnalyse(null);
        setEmpty(true);
        return;
      }
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data: AnalyseData = await res.json();
      setAnalyse(data);
    } catch (err) {
      // Silent catch interdit : une 500/réseau ne doit pas s'afficher comme
      // "analyse à venir" (cas 204). On distingue les 3 états.
      setAnalyse(null);
      setErreur(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [apiFetch, conversationId]);

  useEffect(() => {
    fetchAnalyse();
  }, [fetchAnalyse, refreshTrigger]);

  async function copierBrief() {
    if (!analyse?.resumeVendeur) return;
    try {
      await navigator.clipboard.writeText(analyse.resumeVendeur);
      setBriefError(false);
      setBriefCopied(true);
      setTimeout(() => setBriefCopied(false), 1500);
    } catch {
      // Le presse-papier peut échouer (contexte non sécurisé, permission refusée).
      // On le signale au lieu de faire croire que c'est copié.
      setBriefError(true);
      setTimeout(() => setBriefError(false), 2000);
    }
  }

  const intentionStyle = analyse ? INTENTION_STYLES[analyse.intention] ?? INTENTION_STYLES.AUTRE : null;
  const tonStyle = analyse?.tonProspect ? TON_STYLES[analyse.tonProspect] : null;

  // Compte le nombre d'actions urgentes à mener pour le badge en header
  const nbActions = analyse
    ? (analyse.besoinHandoff ? 1 : 0)
      + (analyse.rdvPropose ? 1 : 0)
      + (analyse.questionEnAttente ? 1 : 0)
    : 0;

  return (
    <div className="flex h-full flex-col">
      {/* Header sidebar, editorial : eyebrow + display title compact */}
      <div className="border-b border-[var(--border)] bg-[var(--card)] px-4 py-3.5">
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
              Analyse
            </p>
            <div className="mt-0.5 flex items-baseline gap-2">
              <h2 className="font-display text-[15px] font-bold tracking-display-tight text-[var(--foreground)]">
                IA
              </h2>
              <span className="text-[10px] text-[var(--color-lr-text-secondary)]">
                à chaque message
              </span>
            </div>
          </div>
          {nbActions > 0 && (
            <span
              title={`${nbActions} action${nbActions > 1 ? "s" : ""} en attente`}
              className="flex h-[20px] min-w-[22px] items-center justify-center rounded-full bg-[var(--color-accent)] px-1.5 font-mono text-[10px] font-semibold tabular-nums text-white"
            >
              {nbActions}
            </span>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-3 space-y-4">
        {loading && (
          /* Skeleton de forme qui calque la structure réelle (identité du moment +
             action à traiter + brief). Cohérent avec les skeletons de la liste et
             du détail — plus rapide à percevoir qu'un spinner rond générique. */
          <div className="space-y-4" aria-busy="true" aria-label="Chargement de l'analyse">
            {/* Carte identité du moment */}
            <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-3">
              <div className="flex items-center gap-2.5">
                <span className="h-2 w-2 shrink-0 rounded-full animate-shimmer" />
                <div className="flex-1 space-y-1.5">
                  <div className="h-3 w-28 rounded animate-shimmer" />
                  <div className="h-2.5 w-20 rounded animate-shimmer" />
                </div>
              </div>
            </div>
            {/* Bloc action à traiter */}
            <div className="space-y-2">
              <div className="ml-1 h-2 w-16 rounded animate-shimmer" />
              <div className="space-y-2 rounded-lg border border-[var(--border)] p-3">
                <div className="h-3 w-24 rounded animate-shimmer" />
                <div className="h-2.5 w-full rounded animate-shimmer" />
                <div className="h-2.5 w-4/5 rounded animate-shimmer" />
              </div>
            </div>
            {/* Bloc brief */}
            <div className="space-y-2">
              <div className="ml-1 h-2 w-20 rounded animate-shimmer" />
              <div className="space-y-2 rounded-lg border border-[var(--border)] bg-[var(--muted)]/40 p-3">
                <div className="h-2.5 w-full rounded animate-shimmer" />
                <div className="h-2.5 w-full rounded animate-shimmer" />
                <div className="h-2.5 w-2/3 rounded animate-shimmer" />
              </div>
            </div>
          </div>
        )}

        {!loading && erreur && !analyse && (
          <ErrorState message={erreur} onRetry={fetchAnalyse} />
        )}

        {!loading && !erreur && empty && (
          <div className="rounded-lg border border-dashed border-[var(--border)] bg-[var(--muted)]/30 p-6 text-center">
            {/* Pulse signal, animation subtile au lieu d'emoji */}
            <div className="mx-auto mb-3 flex items-end gap-1" aria-hidden="true">
              <span className="h-2 w-1 rounded-sm bg-[var(--color-accent)]/40 animate-pulse-stripe-1" />
              <span className="h-3 w-1 rounded-sm bg-[var(--color-accent)]/60 animate-pulse-stripe-2" />
              <span className="h-2 w-1 rounded-sm bg-[var(--color-accent)]/40 animate-pulse-stripe-1" />
            </div>
            <p className="font-display text-[14px] font-semibold tracking-display-tight text-[var(--foreground)]">
              L&apos;analyse arrive.
            </p>
            <p className="mt-1 text-[12px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              L&apos;IA générera l&apos;analyse dès la première réponse du prospect.
            </p>
          </div>
        )}

        {!loading && analyse && intentionStyle && (
          <>
            {/* ============================================================ */}
            {/* SECTION 1, IDENTITÉ DU MOMENT (statut + ton + horodatage)    */}
            {/* ============================================================ */}
            <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-3">
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-2.5">
                  <span className={`h-2 w-2 shrink-0 rounded-full ${intentionStyle.dot}`} aria-hidden />
                  <div>
                    <div className={`text-[13px] font-semibold ${intentionStyle.text}`}>
                      {intentionStyle.label}
                    </div>
                    {tonStyle && (
                      <div className="mt-0.5 flex items-center gap-1.5 text-[11px] text-[var(--color-lr-text-secondary)]">
                        <span className={`h-1.5 w-1.5 rounded-full ${tonStyle.dot}`} />
                        <span>Ton {tonStyle.label.toLowerCase()}</span>
                      </div>
                    )}
                  </div>
                </div>
                <span
                  title={new Date(analyse.creeLe).toLocaleString("fr-FR")}
                  className="font-mono text-[10px] uppercase tracking-wider text-[var(--color-lr-text-tertiary)]"
                >
                  {formatRelatif(analyse.creeLe)}
                </span>
              </div>
            </div>

            {/* ============================================================ */}
            {/* SECTION 2, ACTIONS À MENER (handoff, RDV, question)          */}
            {/* ============================================================ */}
            {nbActions > 0 && (
              <section>
                <h3 className="mb-2 px-1 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                  À traiter
                </h3>
                <div className="space-y-2">
                  {/* Handoff prioritaire */}
                  {analyse.besoinHandoff && (
                    <div className="rounded-lg border border-warning/30 bg-warning/5 p-3">
                      <div className="mb-1 flex items-center gap-1.5">
                        <svg className="h-4 w-4 text-warning" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                        </svg>
                        <p className="text-xs font-semibold text-warning">Reprenez la main</p>
                      </div>
                      <p className="text-xs text-[var(--foreground)] leading-relaxed">
                        {analyse.raisonHandoff || "L'IA a passé la main, le prospect attend une réponse humaine."}
                      </p>
                    </div>
                  )}

                  {/* RDV */}
                  {analyse.rdvPropose && (() => {
                    // Source de verite = la tache RDV en cours (mutable), pas l'extraction IA
                    // figee. Apres un report, rdvTacheDateHeure reflete la nouvelle date ; sans
                    // ca on affichait l'ancienne date en rouge "Passé" a tort.
                    const rdvDateAffichee = analyse.rdvTacheDateHeure ?? analyse.rdvDateHeure;
                    const rdvInfo = rdvDateAffichee ? formatRdvDateHeure(rdvDateAffichee) : null;
                    const passe = rdvInfo?.passe ?? false;
                    const borderColor = passe ? "border-danger/30" : analyse.rdvConfirme ? "border-success/30" : "border-accent/30";
                    const bgColor = passe ? "bg-danger/5" : analyse.rdvConfirme ? "bg-success/5" : "bg-accent/5";
                    const iconColor = passe ? "text-danger" : analyse.rdvConfirme ? "text-success" : "text-accent";
                    const titleColor = passe ? "text-danger" : analyse.rdvConfirme ? "text-success" : "text-accent";
                    const rdvLabel = passe
                      ? "RDV passé"
                      : analyse.rdvConfirme
                      ? "RDV confirmé"
                      : "RDV proposé, à confirmer";
                    return (
                      <div className={`rounded-lg border ${borderColor} ${bgColor} p-3`}>
                        <div className="mb-1 flex items-center gap-1.5">
                          <svg className={`h-4 w-4 ${iconColor}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                          <p className={`text-xs font-semibold ${titleColor}`}>{rdvLabel}</p>
                          {rdvInfo && (
                            <span className={`ml-auto rounded-full px-2 py-0.5 text-[10px] font-medium ${rdvInfo.badgeStyle}`}>
                              {rdvInfo.badge}
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-[var(--foreground)] leading-relaxed">
                          {rdvInfo ? rdvInfo.label : analyse.rdvCreneau}
                        </p>
                      </div>
                    );
                  })()}

                  {/* Question en attente */}
                  {analyse.questionEnAttente && (
                    <div className="rounded-lg border border-accent/30 bg-accent/5 p-3">
                      <div className="mb-1 flex items-center gap-1.5">
                        <svg className="h-4 w-4 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        <p className="text-xs font-semibold text-accent">Question en attente</p>
                      </div>
                      <p className="text-xs text-[var(--foreground)] leading-relaxed">{analyse.questionEnAttente}</p>
                    </div>
                  )}
                </div>
              </section>
            )}

            {nbActions === 0 && (
              <div className="rounded-lg border border-success/20 bg-success/5 p-3">
                <div className="flex items-center gap-2">
                  <svg className="h-4 w-4 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  <p className="text-xs font-medium text-success">Tout est sous contrôle</p>
                </div>
                <p className="mt-1 pl-6 text-[11px] text-[var(--color-lr-text-secondary)]">
                  L&apos;IA gère la conversation. Aucune action de votre part n&apos;est requise pour l&apos;instant.
                </p>
              </div>
            )}

            {/* ============================================================ */}
            {/* SECTION 3, BRIEF VENDEUR                                    */}
            {/* ============================================================ */}
            {analyse.resumeVendeur && (
              <section>
                <div className="mb-2 flex items-center justify-between px-1">
                  <h3 className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                    Brief avant l&apos;appel
                  </h3>
                  <button
                    onClick={copierBrief}
                    className="text-[10px] font-medium text-accent hover:text-accent-hover transition-colors"
                    title="Copier le brief"
                  >
                    {briefCopied ? "✓ Copié" : briefError ? "Échec" : "Copier"}
                  </button>
                </div>
                <div className="rounded-lg border border-[var(--border)] bg-[var(--muted)]/40 p-3">
                  <p className="text-xs leading-relaxed text-[var(--foreground)]">{analyse.resumeVendeur}</p>
                </div>
              </section>
            )}

            {/* ============================================================ */}
            {/* SECTION 4, DIAGNOSTIC (signaux + objections)                 */}
            {/* ============================================================ */}
            {((analyse.signauxAchat && analyse.signauxAchat.length > 0)
              || (analyse.objections && analyse.objections.length > 0)) && (
              <section>
                <h3 className="mb-2 px-1 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                  Diagnostic
                </h3>
                <div className="space-y-2">
                  {analyse.signauxAchat && analyse.signauxAchat.length > 0 && (
                    <div className="rounded-lg border border-success/20 bg-success/[0.03] p-3">
                      <p className="mb-1.5 text-[10px] font-semibold text-success uppercase tracking-wide">
                        Signaux d&apos;achat ({analyse.signauxAchat.length})
                      </p>
                      <div className="flex flex-wrap gap-1">
                        {analyse.signauxAchat.map((s) => (
                          <span key={s} className="rounded-full bg-success/10 px-2 py-0.5 text-[11px] font-medium text-success">
                            {labelize(s, SIGNAL_LABELS)}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  {analyse.objections && analyse.objections.length > 0 && (
                    <div className="rounded-lg border border-danger/20 bg-danger/[0.03] p-3">
                      <p className="mb-1.5 text-[10px] font-semibold text-danger uppercase tracking-wide">
                        Objections ({analyse.objections.length})
                      </p>
                      <div className="flex flex-wrap gap-1">
                        {analyse.objections.map((o) => (
                          <span key={o} className="rounded-full bg-danger/10 px-2 py-0.5 text-[11px] font-medium text-danger">
                            {labelize(o, OBJECTION_LABELS)}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </section>
            )}
          </>
        )}
      </div>
    </div>
  );
}
