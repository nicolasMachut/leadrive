"use client";

import { useEffect, useRef, useState } from "react";
import { useApiClient, formatErreur } from "@/lib/auth";
import { useToast } from "@/lib/realtime";
import { ConversationInbox, ConversationDetail, ConversationMessage, formatDateSeparator } from "./types";
import Link from "next/link";
import ScoringGauge from "@/components/common/ScoringGauge";
import ProgressionBadge from "@/components/common/ProgressionBadge";
import Tooltip from "@/components/common/Tooltip";
import { useSelfSignupStatus } from "@/components/whatsapp/useSelfSignupStatus";
import ErrorState from "@/components/common/ErrorState";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface ConversationDetailPanelProps {
  conversation: ConversationInbox;
  detail: ConversationDetail | null;
  loading: boolean;
  onToggleMode: () => void;
  /** Bascule IA/manuel en cours : désactive le bouton pour éviter le double-clic. */
  toggleLoading?: boolean;
  onBack?: () => void;
  erreur?: string;
  onRetry?: () => void;
  /** Ouvre le panneau d'analyse IA en drawer (uniquement <xl, où le panneau lateral est masque). */
  onOpenAnalyse?: () => void;
}


function MessageBubble({ message, onRetryMessage }: { message: ConversationMessage; onRetryMessage?: (msgId: number) => void }) {
  // La nature d'un message sortant vient de `expediteur` (IA / VENDEUR / SYSTEME),
  // pas de `direction` (qui vaut toujours SORTANT pour les 3). Sans ça, vendeur et
  // système s'affichaient comme des bulles "IA" (coaching cassé + message système
  // rendu comme envoyé au prospect).
  const isEntrant = message.direction === "ENTRANT";
  const isSysteme = message.expediteur === "SYSTEME" || message.direction === "SYSTEME";
  const isVendeur = !isEntrant && message.expediteur === "VENDEUR";
  const isIa = !isEntrant && !isSysteme && !isVendeur;

  const time = message.creeLe
    ? new Date(message.creeLe).toLocaleString("fr-FR", { hour: "2-digit", minute: "2-digit" })
    : "";

  if (isSysteme) {
    return (
      <div className="flex justify-center py-1">
        <div className="flex items-center gap-2 rounded-lg bg-warning/10 border border-warning/30 px-4 py-2 max-w-[85%]">
          <svg className="h-4 w-4 flex-shrink-0 text-warning" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span className="text-xs text-warning">{message.contenu}</span>
          <span className="text-[10px] text-warning/70 flex-shrink-0">{time}</span>
        </div>
      </div>
    );
  }

  if (isEntrant) {
    // Prospect = LEFT, muted neutral. Aligne sur le mental model WhatsApp natif :
    // l'interlocuteur (autre) est à gauche en gris/blanc, vendeur/IA à droite.
    return (
      <div className="flex justify-start">
        <div className="max-w-[75%]">
          <div className="mb-0.5 flex items-center gap-1.5">
            <span className="text-[10px] font-medium text-[var(--color-lr-text-secondary)]">Prospect</span>
            <span className="text-[10px] text-[var(--color-lr-text-tertiary)]">{time}</span>
          </div>
          <div className="whitespace-pre-wrap rounded-2xl rounded-tl-sm bg-[var(--muted)] px-4 py-3 text-sm leading-relaxed text-[var(--foreground)]">
            {message.contenu}
          </div>
        </div>
      </div>
    );
  }

  // Indicateurs de statut pour les messages sortants
  const statutIndicator = (
    <span className="inline-flex items-center gap-0.5 ml-1">
      {message.statut === "LU" && <span className="text-[9px] text-accent" title="Lu" aria-label="Message lu">✓✓</span>}
      {message.statut === "DELIVRE" && <span className="text-[9px] text-[var(--color-lr-text-tertiary)]" title="Délivré" aria-label="Message délivré">✓✓</span>}
      {message.statut === "ENVOYE" && <span className="text-[9px] text-[var(--color-lr-text-tertiary)]" title="Envoyé" aria-label="Message envoyé">✓</span>}
      {message.statut === "ECHEC" && <span className="text-[9px] text-danger" title="Échec" aria-label="Échec d'envoi">✗</span>}
    </span>
  );

  // Bloc d'echec explicite sous les bulles sortantes (vendeur ou IA) : libelle clair
  // + motif lisible + bouton Reessayer. Sans ca, l'echec n'etait signale que par une
  // micro-croix de 9px, illisible pour un manager qui scanne la conversation.
  const isSortantEchec = !isEntrant && !isSysteme && message.statut === "ECHEC";
  const echecBlock = isSortantEchec ? (
    <div className="mt-1 flex flex-col items-end gap-0.5">
      <span className="font-mono text-[10px] font-semibold uppercase tracking-wider text-danger">
        Échec de l&apos;envoi
      </span>
      {message.motifEchec && (
        <span className="max-w-[260px] text-right text-[11px] leading-snug text-[var(--color-lr-text-secondary)]">
          {message.motifEchec}
        </span>
      )}
      {onRetryMessage && message.id != null && (
        <button
          onClick={() => message.id != null && onRetryMessage(message.id)}
          className="font-mono text-[10px] font-semibold uppercase tracking-wider text-[var(--color-accent)] transition-colors hover:text-[var(--color-accent-deep)]"
        >
          Réessayer
        </button>
      )}
    </div>
  ) : null;

  if (isVendeur) {
    // Vendeur = RIGHT, paper bordé neutre. Une bulle humaine — pas d'attention ember.
    // La distinction visuelle Vendeur/IA permet au manager de coacher d'un coup d'œil.
    return (
      <div className="flex justify-end">
        <div className="max-w-[75%]">
          <div className="mb-0.5 flex items-center justify-end gap-1.5">
            <span className="text-[10px] font-medium text-[var(--color-lr-text-secondary)]">Vendeur</span>
          </div>
          <div className={`whitespace-pre-wrap rounded-2xl rounded-tr-sm border px-4 py-2.5 text-sm leading-relaxed ${
            isSortantEchec
              ? "border-danger/30 bg-danger/10 text-[var(--foreground)]"
              : "border-[var(--border)] bg-[var(--card)] text-[var(--foreground)]"
          }`}>
            {message.contenu}
            <div className="mt-1 flex items-center justify-end gap-0.5 text-[var(--color-lr-text-tertiary)]">
              <span className="text-[9px]">{time}</span>
              {statutIndicator}
            </div>
          </div>
          {echecBlock}
        </div>
      </div>
    );
  }

  // IA outgoing (default) = RIGHT, ember-subtle bordé accent. La signature
  // Leadrive : l'ember marque l'IA, "the one thing that makes this product
  // special" (One Attention Rule DESIGN.md).
  return (
    <div className="flex justify-end">
      <div className="max-w-[75%]">
        <div className="mb-0.5 flex items-center justify-end gap-1.5">
          <span className="text-[10px] font-medium text-[var(--color-accent)]">IA</span>
        </div>
        <div className={`whitespace-pre-wrap rounded-2xl rounded-tr-sm border px-4 py-2.5 text-sm leading-relaxed text-[var(--foreground)] ${
          isSortantEchec
            ? "border-danger/30 bg-danger/10"
            : "border-[var(--color-accent)]/20 bg-[var(--color-accent-subtle)]"
        }`}>
          {message.contenu}
          <div className="mt-1 flex items-center justify-end gap-0.5 text-[var(--color-lr-text-tertiary)]">
            <span className="text-[9px]">{time}</span>
            {statutIndicator}
          </div>
        </div>
        {echecBlock}
      </div>
    </div>
  );
}

export default function ConversationDetailPanel({
  conversation,
  detail,
  loading,
  onToggleMode,
  toggleLoading = false,
  onBack,
  erreur,
  onRetry,
  onOpenAnalyse,
}: ConversationDetailPanelProps) {
  const apiFetch = useApiClient();
  const { showToast } = useToast();
  const [messageInput, setMessageInput] = useState("");
  const [sending, setSending] = useState(false);
  const [retryingId, setRetryingId] = useState<number | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const { status: whatsappStatus, loading: whatsappLoading } = useSelfSignupStatus();
  // Optimiste pendant le chargement (eviter de griser les boutons sur le 1er render).
  // Envoi possible uniquement si le tenant a termine son self-signup et que tous ses
  // templates Meta sont approuves.
  const whatsappActif = whatsappLoading
    ? true
    : whatsappStatus?.tousTemplatesApprouves ?? false;

  const isHumain = conversation.modePilotage === "HUMAIN";
  const inputEnabled = isHumain && whatsappActif;

  // Auto-scroll to bottom
  useEffect(() => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [detail?.messages]);

  // Autofocus le textarea quand on passe en mode humain (1 friction click en moins
  // à 50 conversations/jour).
  useEffect(() => {
    if (inputEnabled && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [inputEnabled]);

  // Placeholder personnalisé avec le prénom du prospect (extraction du premier
  // mot du nom complet — Sophie Martin → "Sophie"). Si pas de nom, fallback générique.
  const prenomProspect = conversation.nomProspect?.trim().split(/\s+/)[0] || "";
  const placeholderText = prenomProspect
    ? `Écrivez à ${prenomProspect}…`
    : "Votre réponse…";

  async function handleSendMessage() {
    if (!messageInput.trim() || sending) return;
    setSendError(null);
    setSending(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/conversations/${conversation.conversationId}/message`, {
        method: "POST",
        body: JSON.stringify({ contenu: messageInput.trim() }),
      });
      if (res.ok) {
        setMessageInput("");
        if (textareaRef.current) {
          textareaRef.current.style.height = "auto";
        }
        // Filet SSE-down : on ne compte pas QUE sur l'event SSE pour afficher la bulle
        // vendeur. Un refetch du detail garantit qu'elle apparaisse meme si le flux SSE
        // est coupe (sinon message envoye mais invisible jusqu'au changement de conv).
        onRetry?.();
      } else {
        // Le backend renvoie 422 avec un payload JSON { code, message, action } si
        // WhatsApp n'est pas configure, ou un message texte pour les autres erreurs.
        const contentType = res.headers.get("content-type") || "";
        let message = "L'envoi a échoué. Réessayez dans un instant.";
        try {
          if (contentType.includes("application/json")) {
            const data = await res.json();
            if (typeof data?.message === "string") message = data.message;
          } else {
            const text = await res.text();
            if (text) message = text;
          }
        } catch { /* ignore parse error */ }
        setSendError(message);
      }
    } catch (err) {
      setSendError(formatErreur(err));
    } finally {
      setSending(false);
    }
  }

  async function handleRetryMessage(msgId: number) {
    if (retryingId != null) return;
    setRetryingId(msgId);
    try {
      const res = await apiFetch(`${API_BASE}/api/conversations/${conversation.conversationId}/messages/${msgId}/retry`, {
        method: "POST",
      });
      if (res.ok) {
        showToast({ titre: "Message renvoyé", tone: "success" });
        // Refresh du detail : le message repasse en "envoyé".
        onRetry?.();
      } else {
        // 422 (WhatsApp non configuré, fenêtre 24h, consentement) ou autre : message clair.
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
      showToast({ titre: "Renvoi du message échoué", message: formatErreur(err), tone: "danger" });
    } finally {
      setRetryingId(null);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  }

  function handleTextareaChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    setMessageInput(e.target.value);
    // Auto-resize
    const el = e.target;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 120) + "px";
  }

  // Group messages by day
  const messagesWithSeparators: { type: "separator" | "message"; label?: string; message?: ConversationMessage }[] = [];
  if (detail?.messages) {
    let lastDay = "";
    for (const msg of detail.messages) {
      const msgDay = msg.creeLe ? new Date(msg.creeLe).toDateString() : "";
      if (msgDay && msgDay !== lastDay) {
        messagesWithSeparators.push({ type: "separator", label: formatDateSeparator(msg.creeLe) });
        lastDay = msgDay;
      }
      messagesWithSeparators.push({ type: "message", message: msg });
    }
  }

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[var(--border)] bg-[var(--card)] px-4 py-3">
        <div className="flex items-center gap-3">
          {onBack && (
            <button
              onClick={onBack}
              className="mr-1 rounded-lg p-1.5 text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] lg:hidden"
              aria-label="Retour à la liste des conversations"
            >
              <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
              </svg>
            </button>
          )}
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <Link
                href={`/leads/${conversation.leadId}`}
                className="text-sm font-semibold text-[var(--foreground)] hover:text-accent hover:underline"
              >
                {conversation.nomProspect}
              </Link>
              <ScoringGauge score={conversation.scoring} hotnessScore={conversation.hotnessScore} size="sm" />
              {conversation.leadStatut && <ProgressionBadge statut={conversation.leadStatut} />}
            </div>
            <p className="text-xs text-[var(--color-lr-text-secondary)]">
              {conversation.marque} {conversation.modele} {conversation.territoire ? `- ${conversation.territoire}` : ""}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
        {onOpenAnalyse && (
          <button
            onClick={onOpenAnalyse}
            className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)] xl:hidden"
            aria-label="Ouvrir l'analyse IA"
          >
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            Analyse IA
          </button>
        )}
        <Tooltip
          content={
            <div className="space-y-1.5">
              <p className="font-semibold">
                {!whatsappActif
                  ? "WhatsApp Business non configuré"
                  : isHumain ? "Mode manuel actif" : "Mode IA actif"}
              </p>
              <p className="text-[var(--color-lr-text-secondary)]">
                {!whatsappActif
                  ? "Configurez WhatsApp Business avant de pouvoir reprendre la main."
                  : isHumain
                    ? "Vous répondez vous-même aux messages. Cliquez pour rendre la main à l'IA."
                    : "L'assistant IA répond automatiquement au prospect. Cliquez pour reprendre la main et taper vos messages."}
              </p>
            </div>
          }
        >
          <button
            onClick={onToggleMode}
            disabled={(!whatsappActif && !isHumain) || toggleLoading}
            className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${
              isHumain
                ? "bg-accent/10 text-accent hover:bg-accent/20 dark:text-accent"
                : "bg-accent/10 text-accent hover:bg-accent/20"
            }`}
          >
            {isHumain ? (
              <span className="flex items-center gap-1.5">
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                Rendre à l&apos;IA
              </span>
            ) : (
              <span className="flex items-center gap-1.5">
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
                Reprendre la main
              </span>
            )}
          </button>
        </Tooltip>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-4">
        {erreur && !detail && !loading && (
          <ErrorState message={erreur} onRetry={onRetry} className="h-full justify-center" />
        )}

        {loading && !detail && (
          /* Skeleton bulles, alternance prospect/IA + animation shimmer interne.
             Plus rapide à percevoir qu'un spinner rond générique. */
          <div className="space-y-3" aria-busy="true" aria-label="Chargement de la conversation">
            <div className="flex justify-end">
              <div className="h-12 w-[55%] rounded-2xl rounded-tr-sm bg-[var(--muted)] animate-shimmer" />
            </div>
            <div className="flex justify-start">
              <div className="h-16 w-[65%] rounded-2xl rounded-tl-sm bg-accent/10 animate-shimmer" />
            </div>
            <div className="flex justify-end">
              <div className="h-10 w-[45%] rounded-2xl rounded-tr-sm bg-[var(--muted)] animate-shimmer" />
            </div>
            <div className="flex justify-start">
              <div className="h-14 w-[58%] rounded-2xl rounded-tl-sm bg-accent/10 animate-shimmer" />
            </div>
          </div>
        )}

        {detail && (
          <div className="space-y-3">
            {messagesWithSeparators.map((item, i) => {
              if (item.type === "separator") {
                return (
                  <div key={`sep-${i}`} className="flex items-center gap-3 py-2">
                    <div className="h-px flex-1 bg-[var(--border)]" />
                    <span className="text-[11px] font-medium text-[var(--color-lr-text-secondary)]">{item.label}</span>
                    <div className="h-px flex-1 bg-[var(--border)]" />
                  </div>
                );
              }
              return <MessageBubble key={item.message!.id != null ? `m-${item.message!.id}` : `o-${item.message!.ordre}-${i}`} message={item.message!} onRetryMessage={handleRetryMessage} />;
            })}
            <div ref={messagesEndRef} />
          </div>
        )}

        {detail && detail.messages.length === 0 && (
          <div className="flex flex-col items-start py-10">
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
              Conversation vide
            </p>
            <h3 className="mt-2 font-display text-[18px] font-bold tracking-display-tight text-[var(--foreground)]">
              Aucun message pour le moment.
            </h3>
            <p className="mt-2 text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              {isHumain
                ? "Vous avez la main. Le premier message brisera la glace."
                : "L'IA n'a pas encore engagé la conversation, ou le prospect n'a pas répondu."}
            </p>
            <div className="divider-gradient mt-6 w-20" />
          </div>
        )}
      </div>

      {/* Input area */}
      <div className="border-t border-[var(--border)] bg-[var(--card)] p-3 space-y-2">
        {sendError && (
          <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
            {sendError}
          </div>
        )}
        {inputEnabled ? (
          <div className="flex items-end gap-2">
            <textarea
              ref={textareaRef}
              value={messageInput}
              onChange={handleTextareaChange}
              onKeyDown={handleKeyDown}
              placeholder={placeholderText}
              rows={1}
              className="flex-1 resize-none rounded-xl border border-[var(--border)] bg-[var(--background)] px-4 py-2.5 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-secondary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
            <button
              onClick={handleSendMessage}
              disabled={sending || !messageInput.trim()}
              className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl btn-primary text-white transition-colors disabled:opacity-50"
            >
              {sending ? (
                <div className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
              ) : (
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              )}
            </button>
          </div>
        ) : !whatsappActif ? (
          <div className="rounded-xl border border-warning/30 bg-warning/10 px-4 py-2.5 text-sm text-warning">
            WhatsApp Business n&apos;est pas configuré. {" "}
            <Link href="/parametres/whatsapp/activation" className="font-semibold underline">
              Configurer maintenant
            </Link>
          </div>
        ) : (
          <div className="rounded-xl border border-[var(--border)] bg-[var(--muted)] px-4 py-2.5 text-sm text-[var(--color-lr-text-secondary)]">
            Passez en mode manuel pour répondre
          </div>
        )}
      </div>
    </div>
  );
}
