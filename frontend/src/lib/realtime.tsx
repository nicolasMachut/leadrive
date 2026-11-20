"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, ReactNode } from "react";
import { useAuth, useClerk } from "@clerk/nextjs";
import { useCurrentRole } from "./auth";
import { jouerSonNotification } from "./notificationSound";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
const LS_KEY_NOUVEAUX_LEADS = "leadrive.nouveauxLeads";

export interface RealtimeEvent {
  type: string;
  data: Record<string, unknown>;
  emisLe: string;
}

export type ToastTone = "info" | "success" | "warning" | "danger";

/** Bouton d'action optionnel dans un toast (ex. "Annuler" pour un undo). */
export interface ToastAction {
  label: string;
  onClick: () => void;
}

export interface ToastNotification {
  id: number;
  titre: string;
  message: string;
  cible?: string;
  /** Tonalite visuelle. Default "info" (accent ember). */
  tone?: ToastTone;
  /** Duree d'auto-dismiss en ms. Default 4000 pour les action-toasts, 6000 pour
   *  les notifications realtime. 0 pour persistant (dismiss manuel uniquement). */
  duration?: number;
  /** Bouton d'action (ex. undo). Le clic execute onClick puis ferme le toast. */
  action?: ToastAction;
}

interface RealtimeContextValue {
  /** Compteur de leads recus depuis la derniere visite de /leads. Persiste en localStorage. */
  nouveauxLeads: number;
  /** Reset le compteur (appele au mount de /leads). */
  resetNouveauxLeads: () => void;
  /** Liste de toasts visibles a l'ecran. */
  toasts: ToastNotification[];
  /** Retire un toast manuellement (clic croix ou auto-dismiss). */
  dismissToast: (id: number) => void;
  /** Affiche un toast manuel (action feedback : sauvegarde, erreur, etc.).
   *  Retourne l'ID du toast au cas ou on veut le dismiss programmatiquement. */
  showToast: (opts: { titre: string; message?: string; tone?: ToastTone; cible?: string; duration?: number; action?: ToastAction }) => number;
  /** Dernier event recu, permet aux pages de re-fetch sur changement. */
  lastEvent: RealtimeEvent | null;
}

const RealtimeContext = createContext<RealtimeContextValue | null>(null);

/**
 * Provider global : ouvre une connexion SSE persistante des qu'un utilisateur
 * est connecte, ecoute les events lead.created / lead.batch.imported, met a
 * jour le compteur badge, declenche un toast + son, et expose le dernier event
 * pour permettre aux pages d'auto-refresh leurs donnees.
 *
 * <p>Reconnexion : on regenere un JWT a chaque fois car le token Clerk
 * (template leadrevive-backend) expire en ~60s. EventSource gere les
 * deconnexions automatiquement mais on doit recreer un EventSource avec un
 * token frais pour eviter une 401 lors de la reco initiale.</p>
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { isSignedIn, isLoaded } = useAuth();
  const clerk = useClerk();
  // Le SUPER_ADMIN (back-office) n'a pas de tenant : le stream SSE, scopé par
  // tenant, lui serait refusé en boucle (reconnexion toutes les 3s). On ne
  // l'ouvre donc pas pour lui.
  const { role, isLoaded: roleLoaded } = useCurrentRole();

  const [toasts, setToasts] = useState<ToastNotification[]>([]);
  const [lastEvent, setLastEvent] = useState<RealtimeEvent | null>(null);
  const [nouveauxLeads, setNouveauxLeads] = useState<number>(() => {
    if (typeof window === "undefined") return 0;
    const stored = localStorage.getItem(LS_KEY_NOUVEAUX_LEADS);
    return stored ? parseInt(stored, 10) || 0 : 0;
  });

  const esRef = useRef<EventSource | null>(null);
  const toastIdRef = useRef<number>(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Persiste le compteur a chaque changement
  useEffect(() => {
    if (typeof window === "undefined") return;
    localStorage.setItem(LS_KEY_NOUVEAUX_LEADS, String(nouveauxLeads));
  }, [nouveauxLeads]);

  const ajouterToast = useCallback((titre: string, message: string, cible?: string) => {
    const id = ++toastIdRef.current;
    setToasts((prev) => [...prev, { id, titre, message, cible, tone: "info", duration: 6000 }]);
    // Auto-dismiss 6s
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 6000);
  }, []);

  const dismissToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  /** Show toast manuel, pour les action feedback (statut sauvegarde, copie, erreur, etc.) */
  const showToast = useCallback((opts: { titre: string; message?: string; tone?: ToastTone; cible?: string; duration?: number; action?: ToastAction }): number => {
    const id = ++toastIdRef.current;
    const duration = opts.duration ?? 4000;
    setToasts((prev) => [...prev, {
      id,
      titre: opts.titre,
      message: opts.message ?? "",
      tone: opts.tone ?? "info",
      cible: opts.cible,
      duration,
      action: opts.action,
    }]);
    if (duration > 0) {
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, duration);
    }
    return id;
  }, []);

  const resetNouveauxLeads = useCallback(() => {
    setNouveauxLeads(0);
  }, []);

  // Ouvre / ferme la connexion SSE en fonction de l'etat d'authentification
  useEffect(() => {
    if (!isLoaded || !isSignedIn) return;
    // Attendre que le rôle soit résolu, puis ne pas ouvrir le stream pour le
    // SUPER_ADMIN (pas de tenant → refus en boucle côté backend).
    if (!roleLoaded || role === "SUPER_ADMIN") return;

    let cancelled = false;

    const connect = async () => {
      if (cancelled) return;

      // Recupere un JWT frais (template leadrevive-backend = celui qu'accepte le backend)
      let token: string | null = null;
      try {
        token = await clerk.session?.getToken({ template: "leadrevive-backend" }) ?? null;
      } catch {
        // Erreur recuperation token : on retentera dans 5s
      }
      if (cancelled) return;
      if (!token) {
        reconnectTimerRef.current = setTimeout(connect, 5000);
        return;
      }

      const url = `${API_BASE}/api/realtime/stream?token=${encodeURIComponent(token)}`;
      const es = new EventSource(url);
      esRef.current = es;

      es.addEventListener("lead.created", (event: MessageEvent) => {
        try {
          const payload: RealtimeEvent = JSON.parse(event.data);
          const nom = String(payload.data.nom || "Nouveau prospect");
          const source = String(payload.data.source || "");
          ajouterToast(
            "Nouveau lead reçu",
            source ? `${nom} (${source})` : nom,
            "/leads"
          );
          jouerSonNotification();
          setNouveauxLeads((n) => n + 1);
          setLastEvent(payload);
        } catch {
          // event mal forme : ignore
        }
      });

      // lead.updated : un lead existant a change (statut, ex. envoi auto du premier
      // message NOUVEAU -> CONTACTE). Signal de refresh pour les vues liste / Kanban
      // ouvertes. Pas de toast ni de son (ce n'est pas un nouveau lead), pas
      // d'increment du badge "nouveaux leads".
      es.addEventListener("lead.updated", (event: MessageEvent) => {
        try {
          const payload: RealtimeEvent = JSON.parse(event.data);
          setLastEvent(payload);
        } catch {
          // event mal forme : ignore
        }
      });

      es.addEventListener("lead.batch.imported", (event: MessageEvent) => {
        try {
          const payload: RealtimeEvent = JSON.parse(event.data);
          const nombre = Number(payload.data.nombre || 0);
          const source = String(payload.data.source || "");
          if (nombre > 0) {
            ajouterToast(
              `${nombre} nouveau${nombre > 1 ? "x" : ""} prospect${nombre > 1 ? "s" : ""}`,
              source || "Import termine",
              "/leads"
            );
            jouerSonNotification();
            setNouveauxLeads((n) => n + nombre);
            setLastEvent(payload);
          }
        } catch {
          // ignore
        }
      });

      es.onerror = () => {
        // EventSource passe en CLOSED en cas d'erreur reseau ou 401.
        // Ferme proprement et reconnecte avec un nouveau token apres 3s.
        if (es.readyState === EventSource.CLOSED) {
          es.close();
          if (esRef.current === es) esRef.current = null;
          if (!cancelled) {
            reconnectTimerRef.current = setTimeout(connect, 3000);
          }
        }
      };
    };

    connect();

    return () => {
      cancelled = true;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      if (esRef.current) {
        esRef.current.close();
        esRef.current = null;
      }
    };
  }, [isLoaded, isSignedIn, roleLoaded, role, clerk, ajouterToast]);

  const value = useMemo<RealtimeContextValue>(() => ({
    nouveauxLeads,
    resetNouveauxLeads,
    toasts,
    dismissToast,
    showToast,
    lastEvent,
  }), [nouveauxLeads, resetNouveauxLeads, toasts, dismissToast, showToast, lastEvent]);

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

const FALLBACK_VALUE: RealtimeContextValue = {
  nouveauxLeads: 0,
  resetNouveauxLeads: () => {},
  toasts: [],
  dismissToast: () => {},
  showToast: () => 0,
  lastEvent: null,
};

/** Hook public, utiliser dans n'importe quel composant pour declencher un toast. */
export function useRealtime(): RealtimeContextValue {
  return useContext(RealtimeContext) ?? FALLBACK_VALUE;
}

// Hook focalise sur les toasts pour les usages action-feedback. Wrap useRealtime
// pour ne retourner que showToast/dismissToast, evite d'importer tout le context.
export function useToast(): { showToast: RealtimeContextValue["showToast"]; dismissToast: RealtimeContextValue["dismissToast"] } {
  const { showToast, dismissToast } = useRealtime();
  return { showToast, dismissToast };
}

