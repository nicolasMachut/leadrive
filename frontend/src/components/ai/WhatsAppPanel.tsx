"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useApiClient, formatErreur } from "@/lib/auth";
import { useToast } from "@/lib/realtime";
import { formatPhoneInput, isValidPhone, toE164 } from "@/lib/phone";
import { useSelfSignupStatus } from "@/components/whatsapp/useSelfSignupStatus";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface WhatsAppMessage {
  id?: number;
  ordre?: number;
  direction: "SORTANT" | "ENTRANT";
  contenu: string;
  dateEnvoi?: string;
  creeLe?: string;
  statut?: string;
  /** Motif d'échec lisible (FR, sans jargon) quand statut === "ECHEC". */
  motifEchec?: string;
}

interface WhatsAppConversation {
  id: number;
  conversationId: string;
  statut: string;
  messages: WhatsAppMessage[];
}

interface WhatsAppConversationSummary {
  id: number;
  conversationId: string;
  statut: string;
  creeLe: string;
  nombreMessages: number;
}

// Style des badges de statut conversation. Palette Atelier semantique.
const STATUT_STYLES: Record<string, { bg: string; text: string; dot: string; label: string }> = {
  EN_COURS: { bg: "bg-[var(--color-accent-subtle)]", text: "text-[var(--color-accent)]", dot: "bg-[var(--color-accent)]", label: "En cours" },
  RDV_OBTENU: { bg: "bg-success/10", text: "text-success", dot: "bg-success", label: "RDV obtenu" },
  STOP_DEMANDE: { bg: "bg-danger/10", text: "text-danger", dot: "bg-danger", label: "STOP demandé" },
  EXPIREE: { bg: "bg-[var(--muted)]", text: "text-[var(--color-lr-text-secondary)]", dot: "bg-[var(--color-lr-text-tertiary)]", label: "Expirée" },
  ECHEC: { bg: "bg-warning/10", text: "text-warning", dot: "bg-warning", label: "Échec" },
  TERMINEE: { bg: "bg-[var(--muted)]", text: "text-[var(--color-lr-text-secondary)]", dot: "bg-[var(--color-lr-text-tertiary)]", label: "Terminée" },
};

// Regroupe les messages par tranche de temps pour afficher un separateur
// chronologique entre les bursts de conversation. Sortie : [{date, messages[]}]
function groupMessagesByDay(messages: WhatsAppMessage[]): { dayKey: string; dayLabel: string; messages: WhatsAppMessage[] }[] {
  const groups: { dayKey: string; dayLabel: string; messages: WhatsAppMessage[] }[] = [];
  const today = new Date();
  const todayStr = today.toDateString();
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  const yesterdayStr = yesterday.toDateString();

  for (const msg of messages) {
    const ts = msg.creeLe || msg.dateEnvoi;
    if (!ts) continue;
    const d = new Date(ts);
    const dayKey = d.toDateString();
    let dayLabel: string;
    if (dayKey === todayStr) dayLabel = "Aujourd'hui";
    else if (dayKey === yesterdayStr) dayLabel = "Hier";
    else dayLabel = d.toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" });
    const last = groups[groups.length - 1];
    if (last && last.dayKey === dayKey) {
      last.messages.push(msg);
    } else {
      groups.push({ dayKey, dayLabel, messages: [msg] });
    }
  }
  return groups;
}

export default function WhatsAppPanel({ leadId, telephone, refreshKey = 0 }: { leadId: number; telephone?: string | null; refreshKey?: number }) {
  const apiFetch = useApiClient();
  const [numero, setNumero] = useState(telephone || "");
  const [sending, setSending] = useState(false);
  const { showToast } = useToast();
  const [error, setError] = useState("");

  const [activeConversation, setActiveConversation] = useState<WhatsAppConversation | null>(null);
  const [initialLoading, setInitialLoading] = useState(true);

  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const messagesContainerRef = useRef<HTMLDivElement | null>(null);

  const fetchConversationDetail = useCallback(async (conversationId: string) => {
    try {
      const res = await apiFetch(`${API_BASE}/api/whatsapp/${conversationId}`);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data: WhatsAppConversation = await res.json();
      setActiveConversation(data);
    } catch (err) {
      setError(formatErreur(err));
    }
  }, [apiFetch]);

  // Charger directement la derniere conversation du lead.
  // refreshKey : incremente par le parent apres un envoi (ex. "Envoyer maintenant"
  // du premier message auto) pour re-decouvrir la conversation nouvellement creee
  // sans recharger la page (sinon, faute de conversation active, aucun polling ne
  // demarre et le message n'apparait qu'apres un F5).
  useEffect(() => {
    (async () => {
      try {
        const res = await apiFetch(`${API_BASE}/api/leads/${leadId}/whatsapp`);
        if (!res.ok) return;
        const data: WhatsAppConversationSummary[] = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          await fetchConversationDetail(data[0].conversationId);
        }
      } catch (err) {
        console.warn("WhatsApp panel initial load failed", err);
      }
      finally { setInitialLoading(false); }
    })();
  }, [leadId, apiFetch, fetchConversationDetail, refreshKey]);

  // Auto-scroll vers le bas quand les messages changent. IMPORTANT : on scrolle
  // UNIQUEMENT le conteneur interne des messages (scrollTop), pas via scrollIntoView
  // qui, sur la fiche lead (longue page), scrollait toute la page tout en bas au
  // chargement. Ici le scroll reste confine au panneau de conversation.
  useEffect(() => {
    const c = messagesContainerRef.current;
    if (c) c.scrollTop = c.scrollHeight;
  }, [activeConversation?.messages?.length]);

  // Auto-refresh polling quand la conversation est EN_COURS.
  // On met le polling en PAUSE quand l'onglet est caché (document.hidden) : sinon
  // une fiche lead laissée ouverte en arrière-plan continue de poller toutes les 5s
  // indéfiniment (N onglets manager = N polls permanents, charge backend inutile).
  // Reprise + refetch immédiat au retour de l'onglet.
  useEffect(() => {
    const actif = activeConversation && activeConversation.statut === "EN_COURS";
    if (!actif) return;
    const convId = activeConversation.conversationId;

    const demarrer = () => {
      if (pollingRef.current) return;
      pollingRef.current = setInterval(() => fetchConversationDetail(convId), 5000);
    };
    const arreter = () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
    const onVisibilite = () => {
      if (document.hidden) {
        arreter();
      } else {
        fetchConversationDetail(convId);
        demarrer();
      }
    };

    if (!document.hidden) demarrer();
    document.addEventListener("visibilitychange", onVisibilite);
    return () => {
      document.removeEventListener("visibilitychange", onVisibilite);
      arreter();
    };
  }, [activeConversation?.conversationId, activeConversation?.statut, fetchConversationDetail]);

  async function handleSendWhatsApp() {
    const cleanNumero = toE164(numero);
    if (!isValidPhone(cleanNumero)) return;

    setSending(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/leads/${leadId}/whatsapp`, {
        method: "POST",
        body: JSON.stringify({ numeroDestinataire: cleanNumero }),
      });
      if (!res.ok) {
        const body = await res.text();
        throw new Error(body || `Erreur ${res.status}`);
      }
      const data: WhatsAppConversation = await res.json();
      setActiveConversation(data);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setSending(false);
    }
  }

  async function handleRetryMessage(msgId: number) {
    if (!activeConversation) return;
    try {
      const res = await apiFetch(`${API_BASE}/api/conversations/${activeConversation.conversationId}/messages/${msgId}/retry`, {
        method: "POST",
      });
      if (res.ok) {
        showToast({ titre: "Message renvoyé", tone: "success" });
        fetchConversationDetail(activeConversation.conversationId);
      } else {
        // Surfacer le message métier du backend (fenêtre 24h, consentement, numéro non prêt…)
        // plutôt qu'un générique "Erreur 400". Le backend renvoie soit du JSON { message },
        // soit du texte brut selon le handler.
        const contentType = res.headers.get("content-type") || "";
        let message = "Le renvoi a échoué. Réessayez dans un instant.";
        try {
          if (contentType.includes("application/json")) {
            const data = await res.json();
            if (typeof data?.message === "string") message = data.message;
          } else {
            const text = await res.text();
            if (text) message = text;
          }
        } catch { /* ignore parse error */ }
        showToast({ titre: "Renvoi du message échoué", message, tone: "danger" });
      }
    } catch (err) {
      console.warn("Retry message failed", err);
      showToast({ titre: "Renvoi du message échoué", message: formatErreur(err), tone: "danger" });
    }
  }

  const hasConversation = activeConversation != null;
  const { status: whatsappStatus, loading: whatsappLoading } = useSelfSignupStatus();
  // Optimiste pendant le chargement pour eviter le flash "WhatsApp inactif" au F5.
  // Envoi possible uniquement si le tenant a termine son self-signup et que tous ses
  // templates Meta sont approuves.
  const whatsappActif = whatsappLoading
    ? true
    : whatsappStatus?.tousTemplatesApprouves ?? false;
  // Même normalisation que l'envoi (toE164) : sinon un numéro FR national ("06 12...")
  // passait isValidPhone au format brut différemment et pouvait griser le bouton alors
  // que l'envoi (toE164 -> +33...) l'aurait accepté. Une seule source de vérité.
  const phoneValid = isValidPhone(toE164(numero));
  const sendDisabled = sending || !phoneValid || hasConversation || !whatsappActif;
  const statutMeta = activeConversation ? (STATUT_STYLES[activeConversation.statut] || STATUT_STYLES.EXPIREE) : null;
  const messageGroups = activeConversation?.messages ? groupMessagesByDay(activeConversation.messages) : [];

  return (
    // Hauteur conditionnelle : haute + scrollable UNIQUEMENT quand une conversation
    // existe (ou pendant le chargement, pour éviter le saut de layout). Sans
    // conversation, hauteur auto = le formulaire de lancement ne laisse plus un
    // gouffre vide de 440px (catastrophique sur mobile showroom).
    <div className={`flex flex-col overflow-hidden ${hasConversation || initialLoading ? "h-[72vh] max-h-[760px] min-h-[360px]" : ""}`}>
      {/* ═════ FORM DE LANCEMENT, visible uniquement si pas encore de conversation ═════ */}
      {!hasConversation && !initialLoading && (
        <div className="mb-4 rounded-xl border border-[var(--border)] bg-[var(--muted)]/40 p-4">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
            Lancer une conversation
          </p>
          <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-stretch">
            <input
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              value={numero}
              onChange={(e) => setNumero(formatPhoneInput(e.target.value))}
              disabled={!whatsappActif}
              placeholder={whatsappActif ? "06 12 34 56 78" : "WhatsApp Business non configuré"}
              className="flex-1 rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-[13.5px] text-[var(--foreground)] placeholder-[var(--color-lr-text-tertiary)] transition-colors focus:border-[var(--color-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--color-accent)]/30 disabled:cursor-not-allowed disabled:opacity-50"
            />
            <button
              onClick={handleSendWhatsApp}
              disabled={sendDisabled}
              className="btn-primary shrink-0 whitespace-nowrap rounded-lg px-4 py-2 text-[13px] font-semibold disabled:opacity-50"
            >
              {sending ? "Envoi…" : "Envoyer le 1er message"}
            </button>
          </div>
          {!whatsappActif && (
            <p className="mt-2 text-[11.5px] text-warning">
              WhatsApp Business n&apos;est pas encore activé.{" "}
              <Link href="/parametres/whatsapp/activation" className="font-semibold underline">
                Configurer maintenant
              </Link>
            </p>
          )}
          {numero.length > 0 && !phoneValid && (
            <p className="mt-1.5 text-[11.5px] text-danger">
              Numéro invalide (ex&nbsp;: 0612345678, +33612345678).
            </p>
          )}
        </div>
      )}

      {/* ═════ ERROR BANNER ═════ */}
      {error && (
        <div className="mb-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-[13px] text-danger">
          {error}
        </div>
      )}

      {/* ═════ CONVERSATION ACTIVE ═════ */}
      {activeConversation && statutMeta && (
        <div className="flex min-h-0 flex-1 flex-col">
          {/* Header conversation, eyebrow + chip statut + indicateur de polling */}
          <div className="mb-3 flex shrink-0 items-center justify-between gap-2">
            <div className="flex items-center gap-2.5">
              <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 ${statutMeta.bg}`}>
                <span className={`h-1.5 w-1.5 rounded-full ${statutMeta.dot} ${activeConversation.statut === "EN_COURS" ? "animate-pulse" : ""}`} />
                <span className={`font-mono text-[10px] font-semibold uppercase tracking-[0.16em] ${statutMeta.text}`}>
                  {statutMeta.label}
                </span>
              </span>
            </div>
            {activeConversation.statut === "EN_COURS" && (
              <span className="font-mono text-[9.5px] uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)]">
                Actualisation auto · 5s
              </span>
            )}
          </div>

          {/* Messages, fond cream/muted, bulles WhatsApp-style avec separateurs jour */}
          <div ref={messagesContainerRef} className="min-h-0 flex-1 overflow-y-auto rounded-xl border border-[var(--border)] bg-[var(--muted)]/50 p-4">
            {messageGroups.length > 0 ? (
              <div className="space-y-3">
                {messageGroups.map((group) => (
                  <div key={group.dayKey} className="space-y-2">
                    {/* Separateur jour */}
                    <div className="flex items-center justify-center py-1">
                      <span className="rounded-full bg-[var(--card)] px-2.5 py-0.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)] shadow-card">
                        {group.dayLabel}
                      </span>
                    </div>
                    {group.messages.map((msg, i) => (
                      <WhatsAppBubble key={msg.ordre ?? `${group.dayKey}-${i}`} message={msg} onRetry={handleRetryMessage} />
                    ))}
                  </div>
                ))}
              </div>
            ) : (
              <p className="py-8 text-center font-mono text-[11px] uppercase tracking-wider text-[var(--color-lr-text-tertiary)]">
                Aucun message dans cette conversation
              </p>
            )}
          </div>
        </div>
      )}

      {/* ═════ EMPTY STATE, editorial avec pulse stripes ═════ */}
      {!activeConversation && !initialLoading && (
        <div className="flex flex-1 flex-col items-center justify-center py-12 text-center">
          {/* Pulse stripes, signal "en attente" sans emoji */}
          <div className="mb-4 flex items-end gap-1.5" aria-hidden="true">
            <span className="h-3 w-1 rounded-sm bg-[var(--color-accent)]/40 animate-pulse-stripe-1" />
            <span className="h-5 w-1 rounded-sm bg-[var(--color-accent)]/60 animate-pulse-stripe-2" />
            <span className="h-3 w-1 rounded-sm bg-[var(--color-accent)]/40 animate-pulse-stripe-1" />
          </div>
          <p className="font-display text-[16px] font-bold tracking-display-tight text-[var(--foreground)]">
            Pas encore de conversation.
          </p>
          <p className="mt-1.5 max-w-sm text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)]">
            Lancez le premier message WhatsApp via le formulaire ci-dessus. L&apos;IA prendra ensuite le relais et gérera les échanges.
          </p>
        </div>
      )}

      {/* ═════ LOADING, skeleton conversation pour eviter le flash blanc ═════ */}
      {initialLoading && (
        <div className="flex-1 space-y-3 rounded-xl border border-[var(--border)] bg-[var(--muted)]/30 p-4">
          <div className="mr-auto h-12 w-3/5 rounded-2xl animate-shimmer" />
          <div className="ml-auto h-12 w-2/3 rounded-2xl animate-shimmer" />
          <div className="mr-auto h-12 w-1/2 rounded-2xl animate-shimmer" />
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// WhatsAppBubble, bulle de message style WhatsApp standard :
//   IA (sortant, "notre cote") → DROITE, ember accent
//   Prospect (entrant, "leur cote") → GAUCHE, paper cream
// Ticks d'envoi/lecture en bas-droite de la bulle.
// ─────────────────────────────────────────────────────────────────────────────
function WhatsAppBubble({ message, onRetry }: { message: WhatsAppMessage; onRetry?: (msgId: number) => void }) {
  const isSortant = message.direction === "SORTANT"; // IA = nous
  const isEchec = message.statut === "ECHEC";
  const isLu = message.statut === "LU";
  const isDelivre = message.statut === "DELIVRE" || isLu;
  const hour = (message.creeLe || message.dateEnvoi) ? new Date(message.creeLe || message.dateEnvoi || "").toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) : null;

  return (
    <div className={`flex animate-message-in ${isSortant ? "justify-end" : "justify-start"}`}>
      <div className="max-w-[78%]">
        <div className={`relative rounded-2xl px-3.5 py-2 text-[13.5px] leading-relaxed ${
          isEchec
            ? "rounded-tr-md border border-danger/30 bg-danger/10 text-danger"
            : isSortant
              ? "rounded-tr-md bg-[var(--color-accent)] text-white shadow-card"
              : "rounded-tl-md bg-[var(--card)] text-[var(--foreground)] shadow-card"
        }`}>
          <p className="whitespace-pre-wrap">{message.contenu}</p>
          <div className={`mt-1 flex items-center justify-end gap-1 font-mono text-[9.5px] tabular-nums ${
            isSortant ? "text-white/70" : "text-[var(--color-lr-text-tertiary)]"
          }`}>
            {hour && <span>{hour}</span>}
            {isSortant && !isEchec && (
              <>
                {message.statut === "ENVOYE" && <span title="Envoyé" aria-label="Envoyé">✓</span>}
                {isDelivre && !isLu && <span title="Délivré" aria-label="Délivré">✓✓</span>}
                {isLu && <span title="Lu" aria-label="Lu" className="text-white">✓✓</span>}
              </>
            )}
          </div>
        </div>
        {/* Sender label compact en mono sous la bulle */}
        <p className={`mt-1 px-1 font-mono text-[9.5px] uppercase tracking-[0.16em] ${
          isSortant ? "text-right text-[var(--color-lr-text-tertiary)]" : "text-left text-[var(--color-lr-text-tertiary)]"
        }`}>
          {isSortant ? "IA" : "Prospect"}
        </p>
        {/* Retry sur echec */}
        {isEchec && (
          <div className="mt-1 flex flex-col items-end gap-0.5">
            <div className="flex items-center justify-end gap-2">
              <span className="font-mono text-[10px] uppercase tracking-wider text-danger">Échec de l&apos;envoi</span>
              {onRetry && message.id && (
                <button
                  onClick={() => message.id && onRetry(message.id)}
                  className="font-mono text-[10px] font-semibold uppercase tracking-wider text-[var(--color-accent)] transition-colors hover:text-[var(--color-accent-deep)]"
                >
                  Réessayer
                </button>
              )}
            </div>
            {message.motifEchec && (
              <span className="max-w-[260px] text-right text-[11px] leading-snug text-[var(--color-lr-text-secondary)]">
                {message.motifEchec}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
