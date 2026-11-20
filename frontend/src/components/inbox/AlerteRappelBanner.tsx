"use client";

import { AlerteRappelImmediat, formatTempsRelatif } from "./types";

interface AlerteRappelBannerProps {
  alertes: AlerteRappelImmediat[];
  onSelect: (conversationId: string) => void;
  onDismiss: (conversationId: string) => void;
}

export default function AlerteRappelBanner({
  alertes,
  onSelect,
  onDismiss,
}: AlerteRappelBannerProps) {
  if (alertes.length === 0) return null;

  return (
    <div className="space-y-2 border-b border-danger/30 bg-danger/10 px-4 py-3">
      {alertes.map((alerte) => (
        <div
          key={alerte.conversationId}
          className="flex items-center gap-3 rounded-lg border border-danger/40 bg-[var(--card)] px-3 py-2.5 shadow-card"
          style={{ animation: "pulse-attention 2s ease-in-out infinite" }}
        >
          {/* Icone telephone urgente */}
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-danger/15">
            <svg
              className="h-5 w-5 text-danger"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"
              />
            </svg>
          </div>

          {/* Contenu */}
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-danger">
              Rappel immediat demande
            </p>
            <p className="truncate text-xs text-danger/80">
              <strong className="text-danger">{alerte.nomProspect}</strong>, {alerte.marque} {alerte.modele}
              <span className="ml-1.5 text-[var(--color-lr-text-tertiary)]">
                {formatTempsRelatif(alerte.timestamp)}
              </span>
            </p>
          </div>

          {/* Actions */}
          <div className="flex flex-shrink-0 items-center gap-2">
            <button
              onClick={() => onSelect(alerte.conversationId)}
              className="rounded-lg bg-danger px-3 py-1.5 text-xs font-semibold text-white shadow-card transition-colors hover:bg-danger/90 focus:outline-none focus:ring-2 focus:ring-danger/40 focus:ring-offset-1"
            >
              Voir
            </button>
            <button
              onClick={() => onDismiss(alerte.conversationId)}
              className="rounded-lg px-2 py-1.5 text-xs text-danger/70 transition-colors hover:bg-danger/10 hover:text-danger"
              title="Fermer l'alerte"
              aria-label="Fermer l'alerte"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
