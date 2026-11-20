"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { useApiClient, useConcessionName, formatErreur } from "@/lib/auth";
import MarkdownLite from "./MarkdownLite";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

type HelpMode = "aide" | "simulation";

interface ChatMessage {
  role: "user" | "assistant";
  contenu: string;
  /** true pour le message de bienvenue (auto-genere) : exclu de l'historique envoye au backend. */
  bienvenue?: boolean;
}

const SUGGESTIONS_AIDE = [
  "Comment importer mes leads ?",
  "C'est quoi le scoring ?",
  "À quoi sert l'analyse IA ?",
  "À quoi servent les paliers IA ?",
];

const SUGGESTIONS_SIMULATION = [
  "Bonjour, je suis intéressé par la Peugeot 3008",
  "C'est combien ? Envoyez-moi juste le prix",
  "Je ne veux pas être appelé, je préfère par message",
  "Encore vous ? Arrêtez de me contacter !",
];

const MESSAGE_BIENVENUE_AIDE: ChatMessage = {
  role: "assistant",
  contenu: "Bonjour ! Je suis Nora, l'assistant IA de Leadrive. Posez-moi vos questions sur le fonctionnement de l'outil.",
  bienvenue: true,
};

// En mode simulation, on reproduit le template WhatsApp d'ouverture que l'IA
// envoie en prod (cf. premier message du WhatsAppConversationService). On utilise
// le nom de l'etablissement courant pour rester realiste. La marque/modele du
// vehicule n'est pas dispo en simulation : on reste generique sur "projet automobile".
function messageBienvenueSimulation(concessionName: string | null): ChatMessage {
  const nom = concessionName && concessionName.trim().length > 0 ? concessionName.trim() : "notre établissement";
  return {
    role: "assistant",
    contenu: `Bonjour, je suis Nora, l'assistante virtuelle de ${nom}. L'un de nos conseillers m'a chargée de vous contacter au sujet de votre projet automobile. À quel moment de la journée pourrait-il vous appeler pour en discuter ?`,
    bienvenue: true,
  };
}

function bienvenuePour(mode: HelpMode, concessionName: string | null): ChatMessage {
  return mode === "aide" ? MESSAGE_BIENVENUE_AIDE : messageBienvenueSimulation(concessionName);
}

// Persistance localStorage
const STORAGE_KEY_MESSAGES = "leadrive_help_messages";
const STORAGE_KEY_OPEN = "leadrive_help_open";
const STORAGE_KEY_MODE = "leadrive_help_mode";

function loadMessages(fallbackMode: HelpMode, concessionName: string | null): ChatMessage[] {
  if (typeof window === "undefined") return [bienvenuePour(fallbackMode, concessionName)];
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY_MESSAGES);
    if (!raw) return [bienvenuePour(fallbackMode, concessionName)];
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed) && parsed.length > 0) return parsed;
  } catch { /* ignore */ }
  return [bienvenuePour(fallbackMode, concessionName)];
}

function loadOpen(): boolean {
  if (typeof window === "undefined") return false;
  try {
    return window.localStorage.getItem(STORAGE_KEY_OPEN) === "true";
  } catch {
    return false;
  }
}

function loadMode(): HelpMode {
  if (typeof window === "undefined") return "aide";
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY_MODE);
    return raw === "simulation" ? "simulation" : "aide";
  } catch {
    return "aide";
  }
}

/**
 * Bouton flottant en bas a droite des pages authentifiees.
 * Au clic, ouvre un mini-chat avec deux modes :
 *  - "aide" : assistant IA qui repond aux questions sur l'outil (prompt aide-app.txt).
 *  - "simulation" : repond comme l'IA WhatsApp en production (prompt whatsapp-conversation.txt), l'utilisateur joue le prospect.
 * Le mode est persiste en localStorage. Switcher de mode reinitialise la conversation.
 */
export default function HelpBubble() {
  const pathname = usePathname();
  const apiFetch = useApiClient();
  const concessionName = useConcessionName();
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<HelpMode>("aide");
  const [messages, setMessages] = useState<ChatMessage[]>([MESSAGE_BIENVENUE_AIDE]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [hydrated, setHydrated] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Hydratation depuis localStorage
  useEffect(() => {
    const initialMode = loadMode();
    setMode(initialMode);
    setMessages(loadMessages(initialMode, concessionName));
    setOpen(loadOpen());
    setHydrated(true);
    // concessionName est volontairement HORS deps : on ne veut hydrater qu'une
    // fois au mount. Si le nom change ensuite, on s'aligne via reinitialiser().
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try {
      window.localStorage.setItem(STORAGE_KEY_MESSAGES, JSON.stringify(messages));
    } catch { /* quota ou disabled */ }
  }, [messages, hydrated]);

  useEffect(() => {
    if (!hydrated) return;
    try {
      window.localStorage.setItem(STORAGE_KEY_OPEN, String(open));
    } catch { /* ignore */ }
  }, [open, hydrated]);

  useEffect(() => {
    if (!hydrated) return;
    try {
      window.localStorage.setItem(STORAGE_KEY_MODE, mode);
    } catch { /* ignore */ }
  }, [mode, hydrated]);

  // Auto-scroll
  useEffect(() => {
    if (open && scrollRef.current) {
      scrollRef.current.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
    }
  }, [messages, open]);

  // Focus auto a l'ouverture, et refocus apres chaque reponse
  // (le textarea est disabled pendant loading, ce qui retire le focus, on le restaure quand loading repasse a false).
  useEffect(() => {
    if (open && !loading && inputRef.current) {
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open, loading]);

  const envoyer = useCallback(async (question: string) => {
    if (!question.trim() || loading) return;

    const userMsg: ChatMessage = { role: "user", contenu: question.trim() };
    const newMessages = [...messages, userMsg];
    setMessages(newMessages);
    setInput("");
    setLoading(true);

    try {
      const historique = newMessages
        .slice(-11, -1)
        .filter((m) => !m.bienvenue)
        .map((m) => ({ role: m.role, contenu: m.contenu }));

      const res = await apiFetch(`${API_BASE}/api/aide/chat`, {
        method: "POST",
        body: JSON.stringify({
          question: question.trim(),
          routeCourante: pathname,
          historique,
          mode,
        }),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data = await res.json();
      setMessages([...newMessages, { role: "assistant", contenu: data.reponse || "Je n'ai pas de réponse, réessayez." }]);
    } catch (err) {
      const msg = formatErreur(err);
      setMessages([...newMessages, {
        role: "assistant",
        contenu: `Désolé, une erreur est survenue : ${msg}. Réessayez ou contactez le support.`,
      }]);
    } finally {
      setLoading(false);
    }
  }, [apiFetch, loading, messages, pathname, mode]);

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      envoyer(input);
    }
  }

  function reinitialiser(nouveauMode?: HelpMode) {
    const m = nouveauMode ?? mode;
    setMessages([bienvenuePour(m, concessionName)]);
    setInput("");
  }

  function switchMode(target: HelpMode) {
    if (target === mode) return;
    setMode(target);
    reinitialiser(target);
  }

  const isSimulation = mode === "simulation";
  const suggestions = isSimulation ? SUGGESTIONS_SIMULATION : SUGGESTIONS_AIDE;

  // Theme couleur par mode : accent (bleu app) pour Aide, success (vert WhatsApp) pour Simulation.
  // Le panneau garde un fond opaque ; ce sont les accents (header, bulles, bouton, toggle actif) qui changent.
  const theme = isSimulation
    ? {
        iconBg: "bg-success/15",
        iconText: "text-success",
        headerBg: "bg-success/5",
        toggleActive: "bg-success text-white",
        userBubble: "bg-success text-white",
        sendButton: "bg-success hover:opacity-90",
      }
    : {
        iconBg: "bg-accent/15",
        iconText: "text-accent",
        headerBg: "bg-transparent",
        toggleActive: "bg-accent text-white",
        userBubble: "bg-accent text-white",
        sendButton: "btn-primary",
      };

  return (
    <>
      {/* Bouton flottant */}
      <button
        data-tour="help-bubble"
        onClick={() => setOpen(!open)}
        aria-label={open ? "Fermer l'aide" : "Ouvrir l'aide"}
        className={`fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full text-white shadow-lg transition-all hover:scale-110 ${
          open ? "bg-[var(--color-lr-text-secondary)]" : "btn-primary"
        }`}
      >
        {open ? (
          <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        ) : (
          <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
          </svg>
        )}
      </button>

      {/* Panneau du chat */}
      {open && (
        <div
          className="fixed bottom-24 right-6 z-40 flex w-[380px] max-w-[calc(100vw-3rem)] flex-col rounded-2xl border border-[var(--border)] bg-[var(--card)] shadow-2xl animate-fade-in"
          style={{ height: "min(560px, calc(100vh - 8rem))" }}
        >
          {/* Header */}
          <div className={`flex flex-col border-b border-[var(--border)] transition-colors ${theme.headerBg}`}>
            <div className="flex items-center justify-between px-4 py-3">
              <div className="flex items-center gap-2">
                <div className={`flex h-8 w-8 items-center justify-center rounded-full transition-colors ${theme.iconBg}`}>
                  {isSimulation ? (
                    <svg className={`h-4 w-4 ${theme.iconText}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                    </svg>
                  ) : (
                    <svg className={`h-4 w-4 ${theme.iconText}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                    </svg>
                  )}
                </div>
                <div>
                  <p className="text-sm font-semibold text-[var(--foreground)]">
                    Nora, {isSimulation ? "Simulation" : "Aide"}
                  </p>
                  <p className="text-[10px] text-[var(--color-lr-text-secondary)]">
                    {isSimulation ? "Testez les réponses de l'IA WhatsApp" : "Réponses instantanées par IA"}
                  </p>
                </div>
              </div>
              <button
                onClick={() => reinitialiser()}
                className="flex items-center gap-1 text-[10px] font-medium text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"
                title={isSimulation ? "Recommencer la simulation à zéro" : "Recommencer la conversation"}
              >
                <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                Recommencer
              </button>
            </div>

            {/* Toggle mode, chaque bouton actif prend la couleur de son theme */}
            <div className="flex gap-1 px-3 pb-2">
              <button
                onClick={() => switchMode("aide")}
                className={`flex-1 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                  mode === "aide"
                    ? "bg-accent text-white"
                    : "text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)]"
                }`}
              >
                Aide
              </button>
              <button
                onClick={() => switchMode("simulation")}
                className={`flex-1 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                  mode === "simulation"
                    ? "bg-success text-white"
                    : "text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)]"
                }`}
              >
                Simulation
              </button>
            </div>
          </div>

          {/* Messages */}
          <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
            {messages.map((msg, i) => (
              <div key={i} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
                <div
                  className={`max-w-[85%] rounded-2xl px-3 py-2 text-sm leading-relaxed ${
                    msg.role === "user"
                      ? `whitespace-pre-wrap ${theme.userBubble} rounded-br-sm`
                      : "bg-[var(--muted)] text-[var(--foreground)] rounded-bl-sm"
                  }`}
                >
                  {msg.role === "assistant"
                    ? <MarkdownLite text={msg.contenu} />
                    : msg.contenu}
                </div>
              </div>
            ))}
            {loading && (
              <div className="flex justify-start">
                <div className="max-w-[85%] rounded-2xl rounded-bl-sm bg-[var(--muted)] px-3 py-2">
                  <div className="flex gap-1">
                    <span className="h-1.5 w-1.5 rounded-full bg-[var(--color-lr-text-secondary)] animate-bounce" style={{ animationDelay: "0ms" }} />
                    <span className="h-1.5 w-1.5 rounded-full bg-[var(--color-lr-text-secondary)] animate-bounce" style={{ animationDelay: "150ms" }} />
                    <span className="h-1.5 w-1.5 rounded-full bg-[var(--color-lr-text-secondary)] animate-bounce" style={{ animationDelay: "300ms" }} />
                  </div>
                </div>
              </div>
            )}

            {/* Suggestions au tout debut */}
            {messages.length === 1 && !loading && (
              <div className="space-y-1.5 pt-2">
                <p className="text-[10px] font-semibold uppercase tracking-wide text-[var(--color-lr-text-tertiary)]">
                  {isSimulation ? "Exemples de messages prospect" : "Suggestions"}
                </p>
                {suggestions.map((s) => (
                  <button
                    key={s}
                    onClick={() => envoyer(s)}
                    className="block w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-left text-xs text-[var(--foreground)] hover:bg-[var(--muted)] transition-colors"
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Input */}
          <div className="border-t border-[var(--border)] p-3">
            <div className="flex items-end gap-2">
              <textarea
                ref={inputRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={isSimulation ? "Écrivez comme un prospect..." : "Posez votre question..."}
                rows={1}
                disabled={loading}
                className="flex-1 resize-none rounded-xl border border-[var(--border)] bg-[var(--background)] px-3 py-2 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-secondary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent disabled:opacity-50"
                style={{ maxHeight: "100px" }}
              />
              <button
                onClick={() => envoyer(input)}
                disabled={loading || !input.trim()}
                aria-label="Envoyer"
                className={`flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl text-white transition-colors disabled:opacity-50 ${theme.sendButton}`}
              >
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              </button>
            </div>
            <p className="mt-1.5 text-[10px] text-[var(--color-lr-text-tertiary)] text-center">
              {isSimulation
                ? "Simulation, les messages ne sont pas envoyés à un vrai prospect"
                : "Réponses générées par IA, peuvent contenir des erreurs"}
            </p>
          </div>
        </div>
      )}
    </>
  );
}
