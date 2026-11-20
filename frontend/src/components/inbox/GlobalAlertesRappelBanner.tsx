"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useConversationStream } from "@/lib/inbox/useConversationStream";
import type { AlerteRappelImmediat } from "@/components/inbox/types";
import { formatTempsRelatif } from "@/components/inbox/types";

/**
 * Bandeau d'alertes "rappel immédiat" affiché GLOBALEMENT au-dessus du contenu
 * de l'app (monté dans AppLayout). Avant cette version, le bandeau n'apparaissait
 * que dans /inbox, donc le vendeur sur /leads, /planning ou /dashboard ratait
 * l'event "URGENT, prospect demande à être rappelé maintenant".
 *
 * <p>Comportement :
 * <ul>
 *   <li>Souscrit à l'event SSE `alerte_rappel_immediat` via `useConversationStream`</li>
 *   <li>Joue le son urgent (double tap, comme dans /inbox)</li>
 *   <li>Déclenche la notification navigateur (requireInteraction=true)</li>
 *   <li>Affiche le bandeau danger avec pulse-attention, action "Voir" et fermeture</li>
 *   <li>Au clic "Voir" : router.push("/inbox?conv=...") + dismiss</li>
 *   <li>L'utilisateur peut couper le son via mute global (à brancher au switch existant)</li>
 * </ul>
 *
 * <p>Côté inbox/page.tsx : la logique locale `alerte_rappel_immediat` a été retirée
 * du switch handleSSEEvent pour éviter le doublon audio/notification quand on est
 * effectivement sur /inbox. Le bandeau global s'affiche aussi sur /inbox.</p>
 */
export default function GlobalAlertesRappelBanner() {
  const router = useRouter();
  const [alertes, setAlertes] = useState<AlerteRappelImmediat[]>([]);
  const urgentAudioRef = useRef<HTMLAudioElement | null>(null);

  // Init audio une fois
  useEffect(() => {
    const a = new Audio("/sounds/notification.wav");
    a.volume = 1.0;
    urgentAudioRef.current = a;
  }, []);

  useConversationStream((eventType, payload) => {
    if (eventType !== "alerte_rappel_immediat") return;

    const conversationId = payload.conversationId as string;
    const leadId = payload.leadId as number;
    // On n'a pas l'accès à la liste des conversations ici (le contexte global
    // ne la connaît pas), donc on prend les infos depuis le payload SSE.
    // Le backend a été enrichi pour pousser nom/marque/modele dans le payload
    // (sinon fallback générique).
    const nomProspect = (payload.nomProspect as string) || "Un prospect";
    const marque = (payload.marque as string) || "";
    const modele = (payload.modele as string) || "";

    setAlertes((prev) => {
      if (prev.some((a) => a.conversationId === conversationId)) return prev;
      return [
        ...prev,
        { conversationId, leadId, nomProspect, marque, modele, timestamp: new Date().toISOString() },
      ];
    });

    // Double son urgent
    const audio = urgentAudioRef.current;
    if (audio) {
      audio.currentTime = 0;
      audio.play().catch(() => {});
      setTimeout(() => {
        if (audio) {
          audio.currentTime = 0;
          audio.play().catch(() => {});
        }
      }, 600);
    }

    // Notification navigateur (déjà demandé au login Inbox, sinon Chrome silencieux)
    if (typeof Notification !== "undefined" && Notification.permission === "granted") {
      const notif = new Notification("URGENT, Rappel immédiat demandé", {
        body: `${nomProspect}${marque ? ` (${marque} ${modele})` : ""} demande à être rappelé MAINTENANT`,
        icon: "/leadrive_icon.svg",
        tag: `rappel-immediat-${conversationId}`,
        requireInteraction: true,
      });
      notif.onclick = () => {
        window.focus();
        router.push(`/inbox?conv=${encodeURIComponent(conversationId)}`);
        notif.close();
      };
    }
  });

  function handleVoir(conversationId: string) {
    setAlertes((prev) => prev.filter((a) => a.conversationId !== conversationId));
    router.push(`/inbox?conv=${encodeURIComponent(conversationId)}`);
  }

  function handleDismiss(conversationId: string) {
    setAlertes((prev) => prev.filter((a) => a.conversationId !== conversationId));
  }

  if (alertes.length === 0) return null;

  return (
    <div className="space-y-2 border-b border-danger/30 bg-danger/10 px-4 py-3">
      {alertes.map((alerte) => (
        <div
          key={alerte.conversationId}
          className="flex items-center gap-3 rounded-lg border border-danger/40 bg-[var(--card)] px-3 py-2.5 shadow-card"
          style={{ animation: "pulse-attention 2s ease-in-out infinite" }}
        >
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-danger/15">
            <svg className="h-5 w-5 text-danger" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"
              />
            </svg>
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-danger">Rappel immédiat demandé</p>
            <p className="truncate text-xs text-danger/80">
              <strong className="text-danger">{alerte.nomProspect}</strong>
              {alerte.marque || alerte.modele ? `, ${alerte.marque} ${alerte.modele}`.trim() : ""}
              <span className="ml-1.5 text-[var(--color-lr-text-tertiary)]">
                {formatTempsRelatif(alerte.timestamp)}
              </span>
            </p>
          </div>
          <div className="flex flex-shrink-0 items-center gap-2">
            <button
              onClick={() => handleVoir(alerte.conversationId)}
              className="rounded-lg bg-danger px-3 py-1.5 text-xs font-semibold text-white shadow-card transition-colors hover:bg-danger/90 focus:outline-none focus:ring-2 focus:ring-danger/40 focus:ring-offset-1"
            >
              Voir
            </button>
            <button
              onClick={() => handleDismiss(alerte.conversationId)}
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
