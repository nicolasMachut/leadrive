"use client";

import { useEffect, useRef } from "react";
import { useApiClient } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

/**
 * Callback générique invoqué pour chaque évènement SSE reçu sur le stream
 * `/api/conversations/stream`. Le composant consommateur fait son switch
 * sur `eventType` et met à jour ses états.
 */
export type ConversationStreamHandler = (
  eventType: string,
  payload: Record<string, unknown>,
) => void;

/**
 * Hook qui ouvre et maintient une connexion SSE persistante vers
 * `/api/conversations/stream` (events conversation : nouveau_message,
 * statut_change, nouvelle_conversation, handoff_humain, analyse_mise_a_jour,
 * alerte_rappel_immediat). Gère la reconnexion automatique avec backoff de 3s
 * et l'abort propre au unmount.
 *
 * <p>Le {@code handler} est wrappé dans une ref pour rester stable : le useEffect
 * de connexion ne se relance JAMAIS suite à un changement de handler. Ça évite
 * le pattern dangereux où chaque render fermerait/rouvrirait le stream + le
 * stale closure classique (le handler capture des states figés au mount).</p>
 *
 * <p>Le consommateur est responsable de l'utilisation de refs si ses handlers
 * lisent du state React (cf. {@code conversationsRef} dans inbox/page.tsx).</p>
 *
 * Usage :
 * ```ts
 * useConversationStream((type, payload) => {
 *   switch (type) {
 *     case "nouveau_message": ...
 *     case "alerte_rappel_immediat": ...
 *   }
 * });
 * ```
 */
export function useConversationStream(handler: ConversationStreamHandler): void {
  const apiFetch = useApiClient();
  const handlerRef = useRef(handler);

  // Garde le handler à jour sans relancer la connexion SSE.
  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useEffect(() => {
    let retryTimeout: ReturnType<typeof setTimeout> | null = null;
    let currentAbort: AbortController | null = null;
    let cancelled = false;
    // Backoff exponentiel : 3s, 6s, 12s, 24s, 48s, max 60s. Une connexion qui
    // réussit (response.ok ET stream lu) reset le delay à 3s pour ne pas
    // pénaliser une reconnexion légitime après un long usage.
    let echecsConsecutifs = 0;
    function nextDelay(): number {
      const base = 3000 * Math.pow(2, echecsConsecutifs);
      return Math.min(base, 60000);
    }

    function connect() {
      if (cancelled) return;
      const abortController = new AbortController();
      currentAbort = abortController;

      apiFetch(`${API_BASE}/api/conversations/stream`, {
        signal: abortController.signal,
      })
        .then(async (response) => {
          if (!response.ok || !response.body) {
            echecsConsecutifs += 1;
            return;
          }
          // Reset du compteur uniquement quand le stream s'ouvre vraiment.
          echecsConsecutifs = 0;
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const parts = buffer.split("\n\n");
            buffer = parts.pop() || "";
            for (const part of parts) {
              let eventType = "";
              let eventData = "";
              const lines = part.split("\n");
              for (const line of lines) {
                if (line.startsWith("event:")) {
                  eventType = line.slice(6).trim();
                } else if (line.startsWith("data:")) {
                  eventData = line.slice(5).trim();
                }
              }
              if (!eventType || !eventData) continue;
              try {
                const payload = JSON.parse(eventData);
                handlerRef.current(eventType, payload);
              } catch (err) {
                console.warn("ConversationStream non-JSON event", err);
              }
            }
          }
        })
        .catch((err) => {
          echecsConsecutifs += 1;
          if (!cancelled) console.warn("ConversationStream connection lost", err);
        })
        .finally(() => {
          // Reconnect avec backoff exponentiel sauf si on a été annulé.
          // Évite le martèlement du backend en cas de 500 persistant (auth
          // pas encore prête, redeploy en cours, etc.).
          if (!cancelled && !abortController.signal.aborted) {
            retryTimeout = setTimeout(connect, nextDelay());
          }
        });
    }

    connect();

    return () => {
      cancelled = true;
      if (currentAbort) currentAbort.abort();
      if (retryTimeout) clearTimeout(retryTimeout);
    };
  }, [apiFetch]);
}
