"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient } from "@/lib/auth";
import Link from "next/link";
import ScoringGauge from "@/components/common/ScoringGauge";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface Suggestion {
  idLead: number;
  nom: string;
  marque: string;
  modele: string;
  territoire: string;
  source: string;
  scoring: number;
  hotnessScore: number | null;
  statut: string;
  creeLe: string;
  raison: string;
  priorite: string;
}

interface SuggestionsPanelProps {
  onLancerConversation: (leadId: number, nom: string) => void;
}

const prioriteColor = (p: string) => {
  switch (p) {
    case "HAUTE": return "bg-danger/10 text-danger";
    case "MOYENNE": return "bg-warning/10 text-warning";
    default: return "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]";
  }
};

interface ConvHandoff {
  conversationId: string;
  nomProspect: string;
  marque: string;
  modele: string;
  leadId: number;
  statut: string;
}

export default function SuggestionsPanel({ onLancerConversation }: SuggestionsPanelProps) {
  const apiFetch = useApiClient();
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [handoffs, setHandoffs] = useState<ConvHandoff[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const [sugRes, convRes] = await Promise.all([
        apiFetch(`${API_BASE}/api/leads/suggestions`),
        apiFetch(`${API_BASE}/api/conversations?modePilotage=HUMAIN`),
      ]);
      if (sugRes.ok) setSuggestions(await sugRes.json());
      if (convRes.ok) {
        const convs = await convRes.json();
        const arr: ConvHandoff[] = Array.isArray(convs) ? convs : convs.content ?? [];
        setHandoffs(arr.filter((c) => c.statut !== "EN_COURS").slice(0, 10));
      }
    } catch (err) {
      console.warn("SuggestionsPanel fetch failed", err);
    }
    finally { setLoading(false); }
  }, [apiFetch]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const totalActions = suggestions.length + handoffs.length;

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-[var(--border)] border-t-accent" />
      </div>
    );
  }

  if (totalActions === 0) {
    return (
      <div className="flex flex-col items-center justify-center px-6 py-20 text-center">
        <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-success/10">
          <svg className="h-7 w-7 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <p className="text-sm font-medium text-[var(--foreground)]">Tout est à jour</p>
        <p className="mt-1 text-xs text-[var(--color-lr-text-secondary)]">Aucune action en attente.</p>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-[var(--border)] px-4 py-3">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-[var(--foreground)]">
              {totalActions} action{totalActions > 1 ? "s" : ""} en attente
            </h2>
            <p className="text-[11px] text-[var(--color-lr-text-secondary)]">Conversations, relances et ventes à traiter</p>
          </div>
          <button onClick={fetchAll} className="rounded-lg p-1.5 text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)]" title="Rafraîchir" aria-label="Rafraîchir les actions">
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {/* Section 1 : Conversations à reprendre */}
        {handoffs.length > 0 && (
          <>
            <div className="sticky top-0 z-10 bg-warning/10 px-4 py-2 border-b border-warning/30">
              <span className="text-[11px] font-bold uppercase tracking-widest text-warning">
                Conversations à reprendre ({handoffs.length})
              </span>
            </div>
            {handoffs.map((h) => (
              <Link key={h.conversationId} href={`/leads/${h.leadId}`}
                className="flex items-center gap-3 border-b border-[var(--border)] px-4 py-3 hover:bg-[var(--muted)] transition-colors">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-warning/20">
                  <svg className="h-4 w-4 text-warning" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-[var(--foreground)] truncate">{h.nomProspect}</p>
                  <p className="text-[11px] text-[var(--color-lr-text-secondary)]">
                    {[h.marque, h.modele].filter(Boolean).join(" ")}, L&apos;IA a passé la main
                  </p>
                </div>
                <span className="text-[11px] text-accent font-medium shrink-0">Reprendre →</span>
              </Link>
            ))}
          </>
        )}

        {/* Section 2 : Prospects à contacter */}
        {suggestions.length > 0 && (
          <>
            <div className="sticky top-0 z-10 bg-accent/5 dark:bg-accent/10 px-4 py-2 border-b border-accent/20">
              <span className="text-[11px] font-bold uppercase tracking-widest text-accent">
                Prospects à contacter ({suggestions.length})
              </span>
              <p className="text-[10px] text-[var(--color-lr-text-tertiary)] mt-0.5">
                Leads jamais contactés ou sans réponse, triés par scoring
              </p>
            </div>
            {suggestions.map((s) => (
              <div key={s.idLead} className="border-b border-[var(--border)] px-4 py-3 hover:bg-[var(--muted)] transition-colors">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <Link href={`/leads/${s.idLead}`} className="text-sm font-semibold text-[var(--foreground)] hover:text-accent truncate">
                        {s.nom}
                      </Link>
                      <span className={`shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-medium ${prioriteColor(s.priorite)}`}>
                        {s.priorite}
                      </span>
                    </div>
                    <p className="text-xs text-[var(--color-lr-text-secondary)] truncate">
                      {[s.marque, s.modele].filter(Boolean).join(" ") || "—"}{s.territoire ? `, ${s.territoire}` : ""}
                    </p>
                    <p className="mt-0.5 text-[11px] text-[var(--color-lr-text-tertiary)]">{s.raison}</p>
                  </div>
                  <div className="flex flex-col items-end gap-2 shrink-0">
                    <ScoringGauge score={s.scoring} hotnessScore={s.hotnessScore} size="sm" />
                    <button onClick={() => onLancerConversation(s.idLead, s.nom)}
                      className="flex items-center gap-1 rounded-lg btn-primary px-2.5 py-1.5 text-[11px] font-medium text-white">
                      Contacter
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </>
        )}

      </div>
    </div>
  );
}
