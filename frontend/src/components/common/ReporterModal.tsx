"use client";

import { useEffect, useState } from "react";
import Modal from "@/components/common/Modal";
import DatePicker from "@/components/common/DatePicker";
import TimePicker from "@/components/common/TimePicker";
import type { useToast } from "@/lib/realtime";
import { toastUndoReporter } from "@/lib/rdvUndo";

type ShowToast = ReturnType<typeof useToast>["showToast"];

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// Presets rapides. Le "vrai" besoin (reprogrammer sur un creneau precis donne par
// le prospect) est couvert par le selecteur "Choisir une date..." ci-dessous.
const REPORT_OPTIONS = [
  { label: "+1h", minutes: 60 },
  { label: "+3h", minutes: 180 },
  { label: "Demain matin", minutes: -1 },
];

/**
 * Formate une Date en datetime-local NAÏF (heure locale), PAS en UTC.
 * `toISOString()` convertissait en UTC : "Demain 9h" (Paris, été = UTC+2) devenait
 * "...T07:00", le backend (LocalDateTime, fuseau serveur) enregistrait donc 7h → RDV
 * reporté à la mauvaise heure + rappel WhatsApp J-1 faux. On garde les composants locaux.
 */
function toLocalDateTimeString(d: Date): string {
  const pad = (n: number): string => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Date cible d'un preset (Date locale). Sert au calcul ET a l'affichage de la date exacte. */
function cibleDate(minutes: number): Date {
  if (minutes === -1) {
    const demain = new Date();
    demain.setDate(demain.getDate() + 1);
    demain.setHours(9, 0, 0, 0);
    return demain;
  }
  const d = new Date();
  d.setMinutes(d.getMinutes() + minutes);
  return d;
}

/**
 * Libelle lisible de la date cible, affiche sous chaque preset pour lever toute ambiguite
 * (ex. "Demain matin" -> "mar. 9h00").
 */
function formatCible(d: Date): string {
  const now = new Date();
  const heure = d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }).replace(":", "h");
  const demain = new Date(now);
  demain.setDate(demain.getDate() + 1);
  if (d.toDateString() === now.toDateString()) return `aujourd'hui à ${heure}`;
  if (d.toDateString() === demain.toDateString()) return `demain à ${heure}`;
  return `${d.toLocaleDateString("fr-FR", { weekday: "short", day: "numeric", month: "short" })} à ${heure}`;
}

interface ReporterModalProps {
  open: boolean;
  onClose: () => void;
  tacheId: number | null;
  apiFetch: (url: string, init?: RequestInit) => Promise<Response>;
  onSuccess: () => void;
  /** Si fourni, affiche un toast succes + "Annuler" apres le report (au lieu d'un refetch silencieux). */
  showToast?: ShowToast;
  /** Date d'echeance actuelle de la tache, pour proposer l'undo (restauration). Null = toast sans Annuler. */
  ancienneDateEcheance?: string | null;
  /** true pour un RDV (titre "RDV reporté"), false pour une tache generique. Defaut true. */
  estRdv?: boolean;
}

export default function ReporterModal({ open, onClose, tacheId, apiFetch, onSuccess, showToast, ancienneDateEcheance, estRdv = true }: ReporterModalProps) {
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState(false);
  // Selecteur date/heure custom (replie par defaut).
  const [customOuvert, setCustomOuvert] = useState(false);
  const [customDate, setCustomDate] = useState("");
  const [customHeure, setCustomHeure] = useState("09:00");

  // Reset a chaque (ré)ouverture, sinon un ancien etat flotte.
  useEffect(() => {
    if (open) {
      setErreur(null);
      setEnCours(false);
      setCustomOuvert(false);
      setCustomDate("");
      setCustomHeure("09:00");
    }
  }, [open]);

  async function envoyerReport(dateEcheance: string) {
    if (!tacheId || enCours) return;
    setErreur(null);
    setEnCours(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/taches/${tacheId}/reporter`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dateEcheance }),
      });
      // Sans ce check, la modale se fermait comme un succès même en cas d'échec
      // (report non appliqué) et onSuccess rechargeait sans que la date change.
      if (!res.ok) {
        let message = "Le report a échoué, réessayez.";
        try {
          const text = await res.text();
          if (text) message = text;
        } catch { /* ignore */ }
        setErreur(message);
        return;
      }
      // La reponse est un RepTacheDto : on lit la nouvelle date appliquee (fallback sur la
      // date envoyee si la reponse ne la porte pas).
      let nouvelle = dateEcheance;
      try {
        const data = await res.json();
        if (data?.dateEcheance) nouvelle = data.dateEcheance;
      } catch { /* pas de corps JSON : on garde la date envoyee */ }
      const tid = tacheId;
      onClose();
      onSuccess();
      if (showToast && tid != null) {
        toastUndoReporter({
          showToast,
          apiFetch,
          tacheId: tid,
          ancienneDateEcheance,
          nouvelleDateEcheance: nouvelle,
          estRdv,
          onReverted: onSuccess,
        });
      }
    } catch {
      setErreur("Le report a échoué, réessayez.");
    } finally {
      setEnCours(false);
    }
  }

  function confirmerCustom() {
    if (!customDate || !customHeure) {
      setErreur("Choisissez une date et une heure.");
      return;
    }
    const dateEcheance = `${customDate}T${customHeure}`;
    // Reprogrammer dans le passé est presque toujours une erreur de saisie -> on bloque.
    if (new Date(dateEcheance).getTime() < Date.now()) {
      setErreur("Choisissez une date et une heure à venir.");
      return;
    }
    envoyerReport(dateEcheance);
  }

  return (
    <Modal open={open} onClose={onClose} title="Reporter la tâche" description="">
      {erreur && (
        <div className="mb-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
          {erreur}
        </div>
      )}
      <div className="space-y-2">
        {REPORT_OPTIONS.map(opt => (
          <button key={opt.label} onClick={() => envoyerReport(toLocalDateTimeString(cibleDate(opt.minutes)))} disabled={enCours}
            className="flex w-full items-center justify-between gap-3 rounded-lg border border-[var(--border)] px-4 py-2.5 text-left text-sm text-[var(--foreground)] transition-colors hover:bg-[var(--muted)] disabled:cursor-not-allowed disabled:opacity-50">
            <span className="font-medium">{opt.label}</span>
            <span className="text-xs text-[var(--color-lr-text-tertiary)]">{formatCible(cibleDate(opt.minutes))}</span>
          </button>
        ))}

        {/* Choix d'une date/heure precise : le besoin n°1 (creneau donne par le prospect). */}
        {!customOuvert ? (
          <button onClick={() => { setErreur(null); setCustomOuvert(true); }} disabled={enCours}
            className="flex w-full items-center justify-between gap-3 rounded-lg border border-[var(--border)] px-4 py-2.5 text-left text-sm text-[var(--foreground)] transition-colors hover:bg-[var(--muted)] disabled:cursor-not-allowed disabled:opacity-50">
            <span className="font-medium">Choisir une date…</span>
            <svg className="h-4 w-4 text-[var(--color-lr-text-tertiary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
          </button>
        ) : (
          <div className="space-y-3 rounded-lg border border-[var(--border)] p-3">
            <div className="flex flex-col gap-2 sm:flex-row">
              <div className="flex-1">
                <DatePicker value={customDate} onChange={setCustomDate} placeholder="Date" />
              </div>
              <div className="sm:w-32">
                <TimePicker value={customHeure} onChange={setCustomHeure} />
              </div>
            </div>
            <div className="flex items-center justify-end gap-2">
              <button onClick={() => setCustomOuvert(false)} disabled={enCours}
                className="rounded-lg px-3 py-2 text-sm text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] disabled:opacity-50">
                Retour
              </button>
              <button onClick={confirmerCustom} disabled={enCours || !customDate}
                className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-accent/90 disabled:cursor-not-allowed disabled:opacity-50">
                Reporter
              </button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}
