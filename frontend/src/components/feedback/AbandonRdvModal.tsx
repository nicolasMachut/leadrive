"use client";

import { useState } from "react";
import Modal from "@/components/common/Modal";
import { useApiClient } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface AbandonRdvModalProps {
  open: boolean;
  leadId: number;
  rdvId?: number;
  nomProspect: string;
  onClose: () => void;
  onAbandoned: () => void;
}

type Raison = "NO_SHOW" | "PROSPECT_ANNULE" | "DOUBLON" | "AUTRE";

const RAISONS: { value: Raison; iconPath: string; label: string; tone: string }[] = [
  { value: "NO_SHOW", iconPath: "M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636", label: "No-show (le prospect ne s'est pas présenté)", tone: "text-danger" },
  { value: "PROSPECT_ANNULE", iconPath: "M6 18L18 6M6 6l12 12", label: "Prospect a annulé", tone: "text-warning" },
  { value: "DOUBLON", iconPath: "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z", label: "Doublon (prospect déjà connu)", tone: "text-[var(--color-lr-text-secondary)]" },
  { value: "AUTRE", iconPath: "M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z", label: "Autre raison", tone: "text-[var(--color-lr-text-secondary)]" },
];

export default function AbandonRdvModal({ open, leadId, rdvId, nomProspect, onClose, onAbandoned }: AbandonRdvModalProps) {
  const apiFetch = useApiClient();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClick(raison: Raison) {
    if (submitting) return;
    setError(null);
    setSubmitting(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/feedback/abandon`, {
        method: "POST",
        body: JSON.stringify({ leadId, rdvId, raisonAbandon: raison }),
      });
      if (!res.ok) {
        // Lire le message métier du backend (404 lead introuvable, RDV futur, etc.)
        // plutôt que d'afficher un "Erreur 4xx" générique illisible.
        const msg = await res.text().catch(() => "");
        throw new Error(msg || `Erreur ${res.status}`);
      }
      // Success : ferme + remonte au parent
      setSubmitting(false);
      onAbandoned();
      onClose();
    } catch (err) {
      // L'utilisateur doit savoir que l'abandon n'a pas ete enregistre, sinon il pense
      // que c'est fait alors que le backend n'a rien sauvegarde (incoherence statut).
      setError(err instanceof Error ? err.message : "Impossible d'enregistrer l'abandon. Réessayez.");
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Pourquoi abandonner ce RDV ?"
      description={`Pour ${nomProspect}`}
      size="sm"
    >
      {error && (
        <div className="mb-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
          {error}
        </div>
      )}
      <div className="space-y-2">
        {RAISONS.map((r) => (
          <button
            key={r.value}
            onClick={() => handleClick(r.value)}
            disabled={submitting}
            className="flex w-full items-center gap-3 rounded-lg border border-[var(--border)] bg-[var(--card)] px-4 py-3 text-left text-sm text-[var(--foreground)] transition-colors hover:border-[var(--color-accent)]/30 hover:bg-[var(--muted)] disabled:opacity-50"
          >
            <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[var(--muted)] ${r.tone}`}>
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.6} d={r.iconPath} />
              </svg>
            </span>
            <span className="leading-snug">{r.label}</span>
          </button>
        ))}
      </div>
    </Modal>
  );
}
