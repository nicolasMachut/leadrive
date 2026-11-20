"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useApiClient, formatErreur } from "@/lib/auth";
import { useToast } from "@/lib/realtime";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface FeedbackRdvSlideUpProps {
  open: boolean;
  leadId: number;
  rdvId?: number;
  nomProspect: string;
  onClose: () => void;
  onSubmitted: () => void;
}

interface RepFeedback {
  feedbackId: number;
  totalFeedbacks: number;
  precisionIa: number;
  precisionVisible: boolean;
  palierActuel: string;
  palierSuivant: string | null;
  feedbacksRestantsPalier: number;
  nouveauPalier: boolean;
}

type Qualite = "CHAUD" | "OK" | "RATE";
type Suite = "DEVIS_ENVOYE" | "ESSAI_PREVU" | "RDV_CONCESSION" | "REPRISE_A_EVALUER" | "VENTE_EN_COURS" | "PERDU";

// Visualisation par "dots filled" (3 niveaux). Plus editorial qu'un emoji.
const QUALITES: { value: Qualite; level: 3 | 2 | 1; label: string; bg: string; bgSelected: string; border: string; tone: string }[] = [
  { value: "CHAUD", level: 3, label: "Parfait", bg: "bg-success/10", bgSelected: "bg-success/20", border: "border-success/30", tone: "bg-success" },
  { value: "OK", level: 2, label: "Correct", bg: "bg-warning/10", bgSelected: "bg-warning/20", border: "border-warning/30", tone: "bg-warning" },
  { value: "RATE", level: 1, label: "À côté", bg: "bg-danger/10", bgSelected: "bg-danger/20", border: "border-danger/30", tone: "bg-danger" },
];

// Suites avec icones Phosphor stroke-based, coherent avec le reste de l'app.
const SUITES: { value: Suite; iconPath: string; label: string; bg: string; border: string }[] = [
  { value: "DEVIS_ENVOYE", iconPath: "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z", label: "Devis envoyé", bg: "bg-accent/10", border: "border-accent/30" },
  { value: "ESSAI_PREVU", iconPath: "M9 17a2 2 0 11-4 0 2 2 0 014 0zM19 17a2 2 0 11-4 0 2 2 0 014 0z M13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10", label: "Essai prévu", bg: "bg-success/10", border: "border-success/30" },
  { value: "RDV_CONCESSION", iconPath: "M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4", label: "RDV en agence", bg: "bg-accent/10", border: "border-accent/30" },
  { value: "REPRISE_A_EVALUER", iconPath: "M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15", label: "Reprise à évaluer", bg: "bg-warning/10", border: "border-warning/30" },
  { value: "VENTE_EN_COURS", iconPath: "M5 13l4 4L19 7", label: "Vente en cours", bg: "bg-success/10", border: "border-success/30" },
  { value: "PERDU", iconPath: "M6 18L18 6M6 6l12 12", label: "Perdu", bg: "bg-danger/10", border: "border-danger/30" },
];

export default function FeedbackRdvSlideUp({ open, leadId, rdvId, nomProspect, onClose, onSubmitted }: FeedbackRdvSlideUpProps) {
  const apiFetch = useApiClient();
  const { showToast } = useToast();
  const [mounted, setMounted] = useState(false);
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [qualite, setQualite] = useState<Qualite | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [response, setResponse] = useState<RepFeedback | null>(null);

  useEffect(() => { setMounted(true); }, []);

  useEffect(() => {
    if (open) {
      setStep(1);
      setQualite(null);
      setResponse(null);
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => { document.body.style.overflow = ""; };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  async function handleSuiteClick(suite: Suite) {
    if (!qualite || submitting) return;
    setSubmitting(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/feedback/rdv`, {
        method: "POST",
        body: JSON.stringify({ leadId, rdvId, qualiteAppel: qualite, suitePrevue: suite }),
      });
      if (res.ok) {
        const data: RepFeedback = await res.json();
        setResponse(data);
        setStep(3);
        setTimeout(() => {
          // onSubmitted marque le RDV effectue ET ferme le volet (cote parent).
          // NE PAS aussi appeler onClose : il re-declencherait "marquer effectue"
          // en parallele -> 2 PATCH concurrents sur la meme tache -> conflit de
          // version optimiste (409) + toast "echec" alors que le RDV est bien passe.
          onSubmitted();
        }, 2400);
      } else {
        // API fail : on toast pour signaler que le feedback n'a pas été pris en compte,
        // mais on ne bloque pas le vendeur (le RDV reste marqué effectué côté parent).
        showToast({ titre: "Feedback non enregistré", message: `Erreur ${res.status}, votre RDV reste marqué effectué.`, tone: "warning" });
        onSubmitted();
      }
    } catch (err) {
      showToast({ titre: "Feedback non enregistré", message: formatErreur(err), tone: "warning" });
      onSubmitted();
    } finally {
      setSubmitting(false);
    }
  }

  if (!mounted || !open) return null;

  return createPortal(
    <>
      <div className="fixed inset-0 z-[100] bg-black/40 animate-fade-in" onClick={onClose} />
      <div className="fixed inset-x-0 bottom-0 z-[101] flex justify-center px-2 pb-2 pt-4 sm:px-4 sm:pb-4">
        <div
          className="relative w-full max-w-lg rounded-t-2xl border border-[var(--border)] bg-[var(--card)] p-6 shadow-2xl animate-slide-up"
          role="dialog"
          aria-modal="true"
        >
          {/* Poignée */}
          <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-[var(--border)]" />

          {/* Bouton fermer */}
          <button
            type="button"
            onClick={onClose}
            className="absolute right-4 top-4 rounded-lg p-1 text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
            aria-label="Fermer"
          >
            <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>

          {step === 1 && (
            <div>
              <h2 className="mb-1 text-base font-semibold text-[var(--foreground)]">
                Le RDV avec {nomProspect}
              </h2>
              <p className="mb-5 text-sm text-[var(--color-lr-text-secondary)]">
                L&apos;IA avait bien qualifié&nbsp;?
              </p>
              <div className="grid grid-cols-3 gap-3">
                {QUALITES.map((q) => (
                  <button
                    key={q.value}
                    onClick={() => { setQualite(q.value); setStep(2); }}
                    className={`flex flex-col items-center justify-center gap-2 rounded-xl border ${q.border} ${q.bg} px-3 py-4 transition-all hover:scale-[1.03] active:scale-95`}
                  >
                    {/* 3 dots filled selon le niveau de qualite, visuel signature data-sheet */}
                    <span className="flex items-center gap-1" aria-hidden="true">
                      {[1, 2, 3].map((n) => (
                        <span
                          key={n}
                          className={`h-1.5 w-1.5 rounded-full ${n <= q.level ? q.tone : "bg-[var(--border)]"}`}
                        />
                      ))}
                    </span>
                    <span className="font-display text-[14px] font-bold tracking-display-tight text-[var(--foreground)]">{q.label}</span>
                  </button>
                ))}
              </div>
              <p className="mt-4 text-center text-[11px] text-[var(--color-lr-text-tertiary)]">
                Optionnel, vous pouvez fermer ce volet, le RDV restera marqué comme effectué.
              </p>
            </div>
          )}

          {step === 2 && (
            <div>
              <h2 className="mb-1 text-base font-semibold text-[var(--foreground)]">Suite prévue&nbsp;?</h2>
              <p className="mb-5 text-sm text-[var(--color-lr-text-secondary)]">
                Une dernière info et c&apos;est terminé.
              </p>
              <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3">
                {SUITES.map((s) => (
                  <button
                    key={s.value}
                    onClick={() => handleSuiteClick(s.value)}
                    disabled={submitting}
                    className={`flex flex-col items-center justify-center gap-2 rounded-xl border ${s.border} ${s.bg} px-2 py-3 text-center transition-all hover:scale-[1.02] active:scale-95 disabled:opacity-50`}
                  >
                    <svg className="h-5 w-5 text-[var(--foreground)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.6} d={s.iconPath} />
                    </svg>
                    <span className="text-[12px] font-semibold leading-tight text-[var(--foreground)]">{s.label}</span>
                  </button>
                ))}
              </div>
              <div className="mt-4 flex items-center justify-between">
                <button
                  onClick={() => setStep(1)}
                  disabled={submitting}
                  className="text-xs text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"
                >
                  ← Revenir
                </button>
                {/* Porte de sortie explicite : le RDV est de toute façon marqué
                    effectué à la fermeture ; on le dit clairement ici pour ne pas
                    coincer le vendeur qui ne veut pas préciser la suite. */}
                <button
                  onClick={onClose}
                  disabled={submitting}
                  className="text-xs text-[var(--color-lr-text-tertiary)] hover:text-[var(--foreground)]"
                >
                  Terminer sans préciser
                </button>
              </div>
            </div>
          )}

          {step === 3 && response && (
            <ConfirmationView data={response} />
          )}
        </div>
      </div>
    </>,
    document.body
  );
}

function ConfirmationView({ data }: { data: RepFeedback }) {
  const cap = data.totalFeedbacks > 0 ? Math.min(100, (data.totalFeedbacks / 50) * 100) : 0;

  return (
    <div className="text-center">
      {/* Check stroke (pas d'emoji) : coherent avec le reste de l'app. */}
      <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-success/10">
        <svg className="h-6 w-6 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
        </svg>
      </div>

      {data.nouveauPalier ? (
        <>
          <p className="eyebrow-muted mb-1">Nouveau palier</p>
          <h2 className="mb-1 font-display text-[22px] font-bold tracking-display-tight text-[var(--foreground)]">
            {data.palierActuel}
          </h2>
          <p className="mb-3 text-sm text-[var(--color-lr-text-secondary)]">Votre IA gagne en précision.</p>
        </>
      ) : (
        <>
          <h2 className="mb-1 text-base font-semibold text-[var(--foreground)]">RDV clôturé</h2>
          <p className="mb-3 inline-block rounded-full border border-[var(--border)] bg-[var(--muted)] px-3 py-1 text-xs font-medium text-[var(--foreground)]">
            {data.palierActuel}
          </p>
        </>
      )}

      {/* Précision : phrase discrète, PAS de grand chiffre en accent (template hero-metric banni). */}
      {data.precisionVisible && (
        <p className="mb-3 text-sm text-[var(--color-lr-text-secondary)]">
          Précision de votre IA : <span className="font-mono font-semibold tabular-nums text-[var(--foreground)]">{data.precisionIa}%</span>
        </p>
      )}

      <div className="mx-auto mb-3 h-1.5 w-3/4 overflow-hidden rounded-full bg-[var(--muted)]">
        <div className="h-full bg-accent transition-all" style={{ width: `${cap}%` }} />
      </div>

      {data.palierSuivant && data.feedbacksRestantsPalier > 0 && (
        <p className="text-xs text-[var(--color-lr-text-secondary)]">
          Encore {data.feedbacksRestantsPalier} retour{data.feedbacksRestantsPalier > 1 ? "s" : ""} pour atteindre {data.palierSuivant}
        </p>
      )}

      <p className="mt-2 text-[11px] text-[var(--color-lr-text-tertiary)]">
        Votre IA a appris de {data.totalFeedbacks} échange{data.totalFeedbacks > 1 ? "s" : ""}
      </p>
    </div>
  );
}
