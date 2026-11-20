"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@/lib/auth";
import Link from "next/link";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

/**
 * Bandeau d'alerte affiche en haut du dashboard quand des conversations
 * sont en attente de reprise humaine (handoff IA). Interroge le compteur
 * inbox et se rafraichit toutes les 30 secondes. Pause sur onglet inactif.
 */
export default function HandoffAlert() {
  const apiFetch = useApiClient();
  const [count, setCount] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function fetch() {
      if (document.hidden) return;
      try {
        const res = await apiFetch(`${API_BASE}/api/conversations/compteurs`);
        if (res.ok && !cancelled) {
          const data = await res.json();
          setCount(data.modeHumain ?? 0);
        }
      } catch (err) {
        console.warn("HandoffAlert fetch failed", err);
      }
    }
    fetch();
    const interval = setInterval(fetch, 30_000);
    const onVisible = () => { if (!document.hidden) fetch(); };
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      cancelled = true;
      clearInterval(interval);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [apiFetch]);

  if (count === 0) return null;

  return (
    <Link
      href="/inbox?filtre=MODE_HUMAIN"
      className="flex items-center gap-3 border-b border-warning/30 bg-warning/10 px-6 py-3 transition-colors hover:bg-warning/15"
    >
      <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-warning/15">
        <svg className="h-4 w-4 text-warning" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
        </svg>
      </div>
      <div className="flex-1">
        <p className="text-sm font-semibold text-warning">
          {count} conversation{count > 1 ? "s" : ""} en attente de reprise
        </p>
        <p className="text-xs text-[var(--color-lr-text-secondary)]">
          L&apos;IA a passé la main, un conseiller doit reprendre ces échanges.
        </p>
      </div>
      <span className="flex-shrink-0 text-xs font-medium text-warning">
        Voir les conversations →
      </span>
    </Link>
  );
}
