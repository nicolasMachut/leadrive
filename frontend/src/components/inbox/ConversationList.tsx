"use client";

import { useState } from "react";
import Link from "next/link";
import { ConversationInbox, ConversationCompteurs, formatTempsRelatif } from "./types";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";

const FILTERS = [
  { label: "Toutes", value: "" },
  { label: "En cours", value: "EN_COURS" },
  { label: "RDV obtenus", value: "RDV_OBTENU" },
  { label: "Non lues", value: "NON_LUES" },
  { label: "Mode humain", value: "MODE_HUMAIN" },
];

interface ConversationListProps {
  conversations: ConversationInbox[];
  compteurs: ConversationCompteurs | null;
  activeConversationId: string | null;
  loading: boolean;
  filtre: string;
  onFiltreChange: (filtre: string) => void;
  onSelect: (conversation: ConversationInbox) => void;
  /** IDs des conversations ayant une alerte de rappel immédiat en cours */
  alerteConversationIds?: string[];
  /** IDs des conversations qui viennent de recevoir un nouveau message (highlight pulse transitoire) */
  pulseConversationIds?: Set<string>;
  /** Message d'erreur de chargement de la liste (affiché inline, sans décaler le layout). */
  erreur?: string;
  /** Relance le chargement de la liste. */
  onRetry?: () => void;
}

function RobotIcon() {
  return (
    <svg className="h-3.5 w-3.5 text-[var(--color-lr-text-secondary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
    </svg>
  );
}

function PersonIcon() {
  return (
    <svg className="h-3.5 w-3.5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
    </svg>
  );
}

export default function ConversationList({
  conversations,
  compteurs,
  activeConversationId,
  loading,
  filtre,
  onFiltreChange,
  onSelect,
  alerteConversationIds = [],
  pulseConversationIds,
  erreur,
  onRetry,
}: ConversationListProps) {
  const [recherche, setRecherche] = useState("");

  const filtered = conversations.filter((c) => {
    if (recherche) {
      const q = recherche.toLowerCase();
      // Guard null : marque/modele (et nomProspect) peuvent être null côté DTO
      // (lead importé sans véhicule) → .toLowerCase() sur null jetait, liste blanche.
      const match =
        (c.nomProspect ?? "").toLowerCase().includes(q) ||
        (c.marque ?? "").toLowerCase().includes(q) ||
        (c.modele ?? "").toLowerCase().includes(q);
      if (!match) return false;
    }
    return true;
  });

  return (
    <div className="flex h-full flex-col">
      {/* Filters, chips horizontales scrollables (pas de <select> natif moche
          sur iOS/Android, et la chip active a 'bg-accent text-white' qui prolonge
          le 3px ember du tab indicator visuellement). */}
      <div className="space-y-2 border-b border-[var(--border)] p-3">
        <div className="-mx-3 flex gap-1.5 overflow-x-auto px-3 pb-0.5 scrollbar-thin">
          {FILTERS.map((f) => {
            const isActive = filtre === f.value;
            return (
              <button
                key={f.value}
                type="button"
                onClick={() => onFiltreChange(f.value)}
                aria-pressed={isActive}
                className={`flex-shrink-0 rounded-full px-3.5 py-2 text-[12px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--color-accent)] ${
                  isActive
                    ? "bg-[var(--color-accent)] text-white shadow-sm"
                    : "bg-[var(--muted)] text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)]/70 hover:text-[var(--foreground)]"
                }`}
              >
                {f.label}
              </button>
            );
          })}
        </div>
        <input
          type="text"
          placeholder="Rechercher un prospect, un véhicule…"
          value={recherche}
          onChange={(e) => setRecherche(e.target.value)}
          className="w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-1.5 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-secondary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        />
      </div>

      {/* Conversation list */}
      <div className="flex-1 overflow-y-auto">
        {loading && conversations.length === 0 && (
          <div className="space-y-1 p-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="flex items-start gap-2 rounded-lg px-2 py-3">
                <div className="mt-0.5 h-2.5 w-2.5 flex-shrink-0 rounded-full animate-shimmer" />
                <div className="min-w-0 flex-1 space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="h-4 w-28 rounded animate-shimmer" />
                    <div className="h-3 w-10 rounded animate-shimmer" />
                  </div>
                  <div className="h-3 w-36 rounded animate-shimmer" />
                  <div className="h-3 w-full rounded animate-shimmer" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Erreur de chargement : inline dans la zone liste (ne décale pas le layout
            2-colonnes), persistante, et empêche l'EmptyState "Calme plat" trompeur. */}
        {!loading && erreur && filtered.length === 0 && (
          <ErrorState message={erreur} onRetry={onRetry} />
        )}

        {!loading && !erreur && filtered.length === 0 && (
          <EmptyState
            eyebrow="Calme plat"
            eyebrowTone="muted"
            title="Personne ne vous écrit. Pour l'instant."
            description="Dès qu'un prospect répond à une relance WhatsApp ou demande à être rappelé, sa conversation apparaît ici, le plus récent en haut."
            align="start"
            size="md"
            divider
          />
        )}

        {filtered.map((conv) => {
          const isPulsing = pulseConversationIds?.has(conv.conversationId) ?? false;
          return (
          <button
            key={conv.conversationId}
            onClick={() => onSelect(conv)}
            className={`group relative w-full border-b border-[var(--border)] px-3 py-3 text-left transition-all duration-200 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--color-accent)] ${
              activeConversationId === conv.conversationId
                ? "bg-[var(--color-accent)]/12 pl-4"
                : isPulsing
                ? "bg-[var(--color-accent-subtle)] animate-pulse-attention"
                : "hover:bg-[var(--muted)]/60 hover:pl-3.5"
            }`}
          >
            {/* Tab indicator ember sur conversation active : 3px ember vertical
                à gauche, vrai signal visuel "tu es ici". */}
            {activeConversationId === conv.conversationId && (
              <span
                aria-hidden="true"
                className="pointer-events-none absolute inset-y-0 left-0 w-[3px] bg-[var(--color-accent)]"
                style={{ boxShadow: "0 0 12px rgba(217, 96, 58, 0.4)" }}
              />
            )}
            <div className="flex items-start gap-2">
              {/* Unread dot */}
              <div className="mt-1.5 flex-shrink-0">
                {conv.messageNonLu ? (
                  <>
                    <div className="h-2.5 w-2.5 rounded-full bg-accent" aria-hidden="true" />
                    <span className="sr-only">Conversation non lue</span>
                  </>
                ) : (
                  <div className="h-2.5 w-2.5" aria-hidden="true" />
                )}
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2">
                  <span className={`truncate text-sm ${conv.messageNonLu ? "font-semibold text-[var(--foreground)]" : "font-medium text-[var(--foreground)]"}`}>
                    {conv.nomProspect}
                  </span>
                  <span className="flex-shrink-0 text-[11px] text-[var(--color-lr-text-secondary)]">
                    {conv.dernierMessageLe ? formatTempsRelatif(conv.dernierMessageLe) : ""}
                  </span>
                </div>

                <div className="mt-0.5 flex items-center gap-1.5">
                  <span className="truncate text-xs text-[var(--color-lr-text-secondary)]">
                    {conv.marque} {conv.modele}
                    {conv.nomVendeur && conv.nomVendeur !== "Inconnu" && <> · <span className="text-[var(--color-lr-text-tertiary)]">{conv.nomVendeur}</span></>}
                  </span>
                  <div className="flex items-center gap-1">
                    {conv.statut === "RDV_OBTENU" && (
                      <Link href={`/leads/${conv.leadId}`} onClick={(e) => e.stopPropagation()}
                        className="rounded-full bg-success/10 px-1.5 py-0.5 text-[10px] font-medium text-success hover:bg-success/20 transition-colors">
                        RDV →
                      </Link>
                    )}
                    {conv.statut === "STOP_DEMANDE" && (
                      <span className="rounded-full bg-danger/10 px-1.5 py-0.5 text-[10px] font-medium text-danger">
                        STOP
                      </span>
                    )}
                    {alerteConversationIds.includes(conv.conversationId) && (
                      <span
                        title="Le prospect demande un rappel immédiat"
                        className="animate-pulse rounded-full bg-danger px-1.5 py-0.5 text-[10px] font-bold text-white"
                      >
                        Rappel urgent
                      </span>
                    )}
                    {conv.clientIrrite && (
                      <span
                        title="Prospect mécontent, à traiter avec précaution"
                        className="inline-flex items-center gap-1 rounded-full bg-danger/10 px-1.5 py-0.5 text-[10px] font-medium text-danger"
                      >
                        <span className="h-1.5 w-1.5 rounded-full bg-danger animate-pulse" />
                        Irrité
                      </span>
                    )}
                    {conv.modePilotage === "HUMAIN" ? (
                      conv.statut === "EN_COURS" ? (
                        <span className="flex items-center gap-0.5 rounded-full bg-[var(--muted)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--color-lr-text-secondary)]">
                          <PersonIcon /> Manuel
                        </span>
                      ) : (
                        <span className="flex items-center gap-0.5 rounded-full bg-warning/10 px-1.5 py-0.5 text-[10px] font-medium text-warning">
                          <PersonIcon /> À reprendre
                        </span>
                      )
                    ) : (
                      <RobotIcon />
                    )}
                  </div>
                </div>

                {conv.dernierMessageContenu && (
                  <p className={`mt-1 truncate text-xs ${conv.messageNonLu ? "text-[var(--foreground)]" : "text-[var(--color-lr-text-secondary)]"}`}>
                    {conv.dernierMessageContenu}
                  </p>
                )}
              </div>
            </div>
          </button>
          );
        })}
      </div>

      {/* Counter bar */}
      {compteurs && (
        <div className="flex items-center justify-between border-t border-[var(--border)] bg-[var(--muted)] px-3 py-2 text-[11px] text-[var(--color-lr-text-secondary)]">
          <span>En cours: <strong className="font-mono">{compteurs.enCours}</strong></span>
          <span>RDV: <strong className="font-mono">{compteurs.rdvObtenus}</strong></span>
          <span>Non lus: <strong className="font-mono">{compteurs.nonLues}</strong></span>
        </div>
      )}
    </div>
  );
}
