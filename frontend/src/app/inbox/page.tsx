"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { useApiClient, formatErreur } from "@/lib/auth";
import { useToast } from "@/lib/realtime";
import { useConversationStream } from "@/lib/inbox/useConversationStream";
import AppLayout from "@/components/AppLayout";
import ConversationList from "@/components/inbox/ConversationList";
import ConversationDetailPanel from "@/components/inbox/ConversationDetailPanel";
import ConversationAnalysePanel from "@/components/inbox/ConversationAnalysePanel";
import EmptyState from "@/components/common/EmptyState";
import SectionHeader from "@/components/common/SectionHeader";
import {
  ConversationInbox,
  ConversationCompteurs,
  ConversationDetail,
} from "@/components/inbox/types";
// Note : <AlerteRappelBanner /> n'est plus monte ici, le bandeau global vit
// dans AppLayout via <GlobalAlertesRappelBanner /> pour etre visible sur toutes
// les pages (pas seulement /inbox).

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export default function InboxPage() {
  return (
    <Suspense>
      <InboxContent />
    </Suspense>
  );
}

function InboxContent() {
  const apiFetch = useApiClient();
  const { showToast } = useToast();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [conversations, setConversations] = useState<ConversationInbox[]>([]);
  const [compteurs, setCompteurs] = useState<ConversationCompteurs | null>(null);
  const [activeConversation, setActiveConversation] = useState<ConversationInbox | null>(null);
  const [detail, setDetail] = useState<ConversationDetail | null>(null);
  const [loadingList, setLoadingList] = useState(true);
  const [erreurListe, setErreurListe] = useState("");
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [erreurDetail, setErreurDetail] = useState("");
  const [filtre, setFiltre] = useState(searchParams.get("filtre") || "");
  const [autoSelectDone, setAutoSelectDone] = useState(false);
  const [muted, setMuted] = useState(false);
  const [mobileView, setMobileView] = useState<"list" | "detail">("list");
  // Bascule IA/manuel en cours : empêche le double-clic (sinon reprendre+rendre-ia
  // s'enchaînent et l'état final est indéterminé / OptimisticLockException).
  const [togglingMode, setTogglingMode] = useState(false);
  // Compteur incremente quand on recoit un evenement SSE "analyse_mise_a_jour"
  // pour la conversation active. Force le ConversationAnalysePanel a refetch.
  const [analyseRefreshTrigger, setAnalyseRefreshTrigger] = useState(0);
  // Drawer d'analyse IA pour les ecrans <xl (le panneau lateral droit est masque en dessous
  // de 1280px, l'analyse etait donc inaccessible sur laptop/mobile).
  const [analyseDrawerOpen, setAnalyseDrawerOpen] = useState(false);
  // Set des conversations qui viennent de recevoir un nouveau message (highlight transitoire)
  const [pulseConvIds, setPulseConvIds] = useState<Set<string>>(new Set());

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const activeConversationRef = useRef<string | null>(null);
  const fetchDetailTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Ref qui suit la liste des conversations. Le handler SSE est defini une seule
  // fois au mount (deps stables) donc il fermerait sur la version vide du state.
  // En lisant via la ref on recupere systematiquement la liste a jour quand
  // l'event arrive (correction du bug "notification dit 'Un prospect' au lieu
  // du vrai nom car la liste etait vide au moment ou le SSE a ete connecte").
  const conversationsRef = useRef<ConversationInbox[]>([]);

  // Keep refs in sync
  useEffect(() => {
    activeConversationRef.current = activeConversation?.conversationId ?? null;
  }, [activeConversation]);

  useEffect(() => {
    conversationsRef.current = conversations;
  }, [conversations]);

  // Audio elements. Le son urgent (rappel immediat) vit maintenant dans
  // <GlobalAlertesRappelBanner /> pour eviter le doublon quand on est /inbox.
  useEffect(() => {
    const audio = new Audio("/sounds/notification.wav");
    audio.volume = 0.5;
    audioRef.current = audio;
  }, []);

  // Fetch conversations
  const fetchConversations = useCallback(async (filtreParam?: string) => {
    const f = filtreParam ?? filtre;
    let url = `${API_BASE}/api/conversations`;
    const params = new URLSearchParams();
    if (f === "EN_COURS") params.set("statut", "EN_COURS");
    else if (f === "RDV_OBTENU") params.set("statut", "RDV_OBTENU");
    else if (f === "NON_LUES") params.set("nonLuSeulement", "true");
    else if (f === "MODE_HUMAIN") params.set("modePilotage", "HUMAIN");
    if (params.toString()) url += `?${params}`;

    setErreurListe("");
    try {
      const res = await apiFetch(url);
      if (res.ok) {
        const data = await res.json();
        setConversations(Array.isArray(data) ? data : data.content ?? []);
      } else {
        throw new Error(`Erreur ${res.status}`);
      }
    } catch (err) {
      console.warn("Inbox list fetch failed", err);
      // Erreur affichee inline dans la colonne liste (ConversationList), pas en toast :
      // un toast est ephemere + se duplique (StrictMode), l'inline est persistant et contextuel.
      setErreurListe(formatErreur(err));
    } finally {
      setLoadingList(false);
    }
  }, [filtre, apiFetch]);

  // Fetch compteurs
  const fetchCompteurs = useCallback(async () => {
    try {
      const res = await apiFetch(`${API_BASE}/api/conversations/compteurs`);
      if (res.ok) {
        const data: ConversationCompteurs = await res.json();
        setCompteurs(data);
      }
    } catch (err) {
      console.warn("Compteurs fetch failed", err);
    }
  }, [apiFetch]);

  // Rafraîchissement débounce de la liste + compteurs : sur une rafale d'events SSE
  // (handoff suivi d'un nouveau_message, import en masse…), on ne déclenche qu'un
  // seul refetch par fenêtre de 400ms au lieu de plusieurs qui se courent après et
  // peuvent écraser l'ajout optimiste.
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scheduleListRefresh = useCallback(() => {
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current);
    refreshTimerRef.current = setTimeout(() => {
      fetchConversations();
      fetchCompteurs();
    }, 400);
  }, [fetchConversations, fetchCompteurs]);

  useEffect(() => () => { if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current); }, []);

  // Fetch conversation detail avec retry si les messages ne sont pas encore committés.
  // Lit conversations via la ref (pas via la closure) : sinon fetchDetail capture
  // une snapshot stale de la liste au moment de sa création par useCallback ;
  // le retry timing devient cassé sur message arrivé pendant la nav.
  const fetchDetail = useCallback(async (conversationId: string, retryCount = 0) => {
    setLoadingDetail(true);
    setErreurDetail("");
    try {
      const res = await apiFetch(`${API_BASE}/api/conversations/${conversationId}`);
      if (res.ok) {
        const data: ConversationDetail = await res.json();
        setDetail(data);
        // Si le nombre de messages semble inférieur à ce que la liste affiche,
        // retry après 1s (la transaction backend n'a peut-être pas committé).
        // conversationsRef.current = lecture fraîche, pas stale.
        const convInList = conversationsRef.current.find((c) => c.conversationId === conversationId);
        if (convInList && data.messages.length < convInList.nombreMessages && retryCount < 2) {
          setTimeout(() => fetchDetail(conversationId, retryCount + 1), 1000);
        }
      } else {
        throw new Error(`Erreur ${res.status}`);
      }
    } catch (err) {
      console.warn("Inbox detail fetch failed", err);
      // Erreur de CHARGEMENT : affichée inline dans le panneau détail, pas en toast.
      setErreurDetail(formatErreur(err));
    } finally {
      setLoadingDetail(false);
    }
  }, [apiFetch]);

  // Reconciliation autoritaire du detail apres un nouveau_message sur la conversation
  // active. L'ajout optimiste (setDetail dans le handler SSE) donne le feedback instantane,
  // mais s'il est rate (event recu pendant que le detail se chargeait, ou detail null au
  // moment du push) le message n'apparaissait qu'au F5. Ce refetch debounce se resynchronise
  // avec le serveur (la donnee y est committee) et rattrape tout message manque.
  const detailRefreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scheduleDetailRefresh = useCallback((conversationId: string) => {
    if (detailRefreshTimerRef.current) clearTimeout(detailRefreshTimerRef.current);
    detailRefreshTimerRef.current = setTimeout(() => {
      if (activeConversationRef.current === conversationId) fetchDetail(conversationId);
    }, 600);
  }, [fetchDetail]);

  useEffect(() => () => { if (detailRefreshTimerRef.current) clearTimeout(detailRefreshTimerRef.current); }, []);

  // Mark as read
  const markAsRead = useCallback(async (conversationId: string) => {
    try {
      const res = await apiFetch(`${API_BASE}/api/conversations/${conversationId}/lire`, {
        method: "POST",
      });
      // Rafraîchit le badge "Non lues" de la sidebar : sans ça il restait stale
      // jusqu'au prochain event SSE.
      if (res.ok) fetchCompteurs();
    } catch (err) {
      console.warn("Mark as read failed", err);
    }
  }, [apiFetch, fetchCompteurs]);

  // Initial load
  useEffect(() => {
    fetchConversations();
    fetchCompteurs();
    // Note : Chrome 80+ ignore Notification.requestPermission() s'il n'est pas
    // declenche par une interaction utilisateur (click, touch...). On le
    // declenche donc dans handleSelectConversation() au premier clic sur une
    // conversation (cf. plus bas), pas au mount.
  }, [fetchConversations, fetchCompteurs]);

  // Auto-select une conversation si on arrive avec un filtre ou un conv= en query param
  useEffect(() => {
    if (autoSelectDone || conversations.length === 0 || activeConversation) return;
    const convId = searchParams.get("conv");
    if (convId) {
      const conv = conversations.find((c) => c.conversationId === convId);
      if (conv) { handleSelectConversation(conv); setAutoSelectDone(true); return; }
    }
    if (searchParams.get("filtre")) {
      handleSelectConversation(conversations[0]);
      setAutoSelectDone(true);
    }
  }, [conversations, autoSelectDone, searchParams, activeConversation]);

  // SSE connection for real-time updates : la connexion + reconnect + abort sont
  // gérés par useConversationStream. Le handler ci-dessous fait le switch sur
  // event type + applique les mises à jour de state. Toutes les lectures de state
  // dans le handler passent par des refs (conversationsRef, activeConversationRef)
  // pour bypass le stale closure (le handler est une snapshot au mount du useEffect).
  useConversationStream((eventType, payload) => {
      if (eventType === "nouveau_message") {
        const conversationId = payload.conversationId as string;

        // Highlight transitoire de la conversation dans la liste (animation visuelle)
        // pour que l'oeil du vendeur soit attire vers la conv qui vient de bouger.
        // Auto-clear apres 1.5s.
        if (payload.direction === "ENTRANT") {
          setPulseConvIds((prev) => {
            const next = new Set(prev);
            next.add(conversationId);
            return next;
          });
          setTimeout(() => {
            setPulseConvIds((prev) => {
              const next = new Set(prev);
              next.delete(conversationId);
              return next;
            });
          }, 1500);
        }

        // Update the conversation list - bump to top and update preview
        setConversations((prev) => {
          const idx = prev.findIndex((c) => c.conversationId === conversationId);
          if (idx === -1) return prev;
          const updated = { ...prev[idx] };
          if (payload.contenu) updated.dernierMessageContenu = payload.contenu as string;
          if (payload.direction) updated.dernierMessageDirection = payload.direction as string;
          updated.dernierMessageLe = new Date().toISOString();
          if (payload.direction === "ENTRANT") {
            updated.messageNonLu = true;
            updated.nombreMessages = (updated.nombreMessages || 0) + 1;
          }
          // Si message vendeur, le statut peut avoir change (ex: RDV_OBTENU -> EN_COURS)
          if ((payload as Record<string, unknown>).expediteur === "VENDEUR") {
            updated.statut = "EN_COURS";
          }
          const newList = [...prev];
          newList.splice(idx, 1);
          return [updated, ...newList];
        });

        // If this is the active conversation, append + refetch
        if (activeConversationRef.current === conversationId) {
          // Ajout optimiste instantane (avant que la transaction backend committe)
          setDetail((prev) => {
            if (!prev) return prev;
            const newMessage = {
              direction: (payload.direction as string) || "ENTRANT",
              contenu: (payload.contenu as string) || "",
              statut: payload.direction === "SORTANT" ? "ENVOYE" : "RECU",
              ordre: prev.messages.length,
              creeLe: (payload.creeLe as string) || new Date().toISOString(),
            };
            // Anti-echo : on ne saute que si le dernier message est identique ET tres recent
            // (<3s) = probable echo du meme event. Un 2e message identique LEGITIME (prospect
            // qui renvoie "ok" plus tard) n'est plus injustement masque ; le refetch debounce
            // reconcilie de toute facon avec le serveur (source autoritaire).
            const lastMsg = prev.messages[prev.messages.length - 1];
            if (lastMsg && lastMsg.contenu === newMessage.contenu && lastMsg.direction === newMessage.direction) {
              const dernierLe = lastMsg.creeLe ? new Date(lastMsg.creeLe).getTime() : 0;
              const nouveauLe = new Date(newMessage.creeLe).getTime();
              if (Math.abs(nouveauLe - dernierLe) < 3000) return prev;
            }
            return {
              ...prev,
              messages: [...prev.messages, newMessage],
              nombreMessages: prev.messages.length + 1,
              dernierMessageLe: newMessage.creeLe,
            };
          });
          // Filet de securite : resynchronise le detail avec le serveur (debounce) pour
          // rattraper un ajout optimiste manque (sinon message visible seulement au F5).
          scheduleDetailRefresh(conversationId);
        } else if (payload.direction === "ENTRANT" && !muted && audioRef.current) {
          // Play notification sound for non-active conversations
          audioRef.current.play().catch(() => {});
        }

        // Refresh compteurs
        fetchCompteurs();
      } else if (eventType === "nouvelle_conversation") {
        // Refresh debounce de la liste complète
        scheduleListRefresh();
      } else if (eventType === "handoff_humain") {
        const conversationId = payload.conversationId as string;
        const raison = (payload.raison as string) || "L'IA a passe la main.";

        // Trouver le nom du prospect pour la notification (lecture via ref pour
        // eviter le stale closure : handleSSEEvent est figé au mount du useEffect).
        const conv = conversationsRef.current.find((c) => c.conversationId === conversationId);
        const nomProspect = conv?.nomProspect || "Un prospect";

        // Notification navigateur (si autorise)
        if (typeof Notification !== "undefined" && Notification.permission === "granted") {
          const notif = new Notification("Conversation à reprendre", {
            body: `${nomProspect}, ${raison}`,
            icon: "/globe.svg",
            tag: `handoff-${conversationId}`,
          });
          notif.onclick = () => {
            window.focus();
            if (conv) handleSelectConversation(conv);
            notif.close();
          };
        }

        // Refresh debounce la liste (pas de fetchDetail, le message systeme arrive via nouveau_message)
        scheduleListRefresh();
      } else if (eventType === "statut_message") {
        // Statut de livraison pousse par le webhook Twilio (ENVOYE/DELIVRE/LU/ECHEC) :
        // patch la coche de la bulle concernee (par ordre) dans la conversation ouverte,
        // sans F5. Indispensable pour qu'un ECHEC asynchrone (hors 24h) soit visible.
        const conversationId = payload.conversationId as string;
        const ordre = payload.ordre as number | undefined;
        const statut = payload.statut as string | undefined;
        if (activeConversationRef.current === conversationId && ordre != null && statut) {
          setDetail((prev) => prev
            ? { ...prev, messages: prev.messages.map((m) => m.ordre === ordre ? { ...m, statut } : m) }
            : prev);
        }
        // Compteurs inchanges, mais un ECHEC peut interesser la liste (badge) : refresh leger.
        if (statut === "ECHEC") scheduleListRefresh();
      } else if (eventType === "analyse_mise_a_jour") {
        const conversationId = payload.conversationId as string;
        // Refresh le panneau d'analyse
        if (activeConversationRef.current === conversationId) {
          setAnalyseRefreshTrigger((t) => t + 1);
        }
        // Refresh la liste et les compteurs (statut, scoring peuvent avoir change).
        // PAS de fetchDetail ici, le nouveau_message qui suit s'en charge via le debounce.
        // Un fetchDetail ici provoquerait du flickering (ecrase l'ajout optimiste).
        scheduleListRefresh();
      }
      // Note : "alerte_rappel_immediat" n'est plus geré ici. Il l'est globalement
      // dans <GlobalAlertesRappelBanner /> monté dans AppLayout, pour que les
      // vendeurs reçoivent l'alerte URGENT même quand ils ne sont pas sur /inbox.
      // Garder le handler ici provoquait double audio + double notification.
  });

  // Update page title with unread count
  useEffect(() => {
    const unread = compteurs?.nonLues ?? 0;
    document.title = unread > 0 ? `(${unread}) Conversations, Leadrive` : "Conversations, Leadrive";
    return () => {
      document.title = "Leadrive";
    };
  }, [compteurs?.nonLues]);

  // Keyboard shortcuts, J/K (ou ↑↓) pour naviguer, M mute, / focus search.
  // Skip si le focus est dans un input/textarea/contenteditable (saisie reponse).
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (!target) return;
      const tag = target.tagName;
      const isEditing = tag === "INPUT" || tag === "TEXTAREA" || target.isContentEditable;
      if (e.metaKey || e.ctrlKey || e.altKey) return;

      // `/` focus la recherche conversations même quand l'utilisateur est hors d'un input.
      if (e.key === "/" && !isEditing) {
        e.preventDefault();
        const searchInput = document.querySelector<HTMLInputElement>(
          'input[placeholder^="Rechercher un prospect"]',
        );
        searchInput?.focus();
        searchInput?.select();
        return;
      }

      if (isEditing) return;
      if (conversations.length === 0) return;

      if (e.key === "j" || e.key === "ArrowDown") {
        e.preventDefault();
        const idx = conversations.findIndex((c) => c.conversationId === activeConversation?.conversationId);
        const nextIdx = idx === -1 ? 0 : Math.min(idx + 1, conversations.length - 1);
        const next = conversations[nextIdx];
        if (next && next.conversationId !== activeConversation?.conversationId) {
          handleSelectConversation(next);
        }
      } else if (e.key === "k" || e.key === "ArrowUp") {
        e.preventDefault();
        const idx = conversations.findIndex((c) => c.conversationId === activeConversation?.conversationId);
        const prevIdx = idx === -1 ? 0 : Math.max(idx - 1, 0);
        const prev = conversations[prevIdx];
        if (prev && prev.conversationId !== activeConversation?.conversationId) {
          handleSelectConversation(prev);
        }
      } else if (e.key === "m" && !e.shiftKey) {
        e.preventDefault();
        setMuted((m) => !m);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversations, activeConversation]);

  // Select a conversation
  function handleSelectConversation(conv: ConversationInbox) {
    // Premier clic = interaction utilisateur = bon moment pour demander la
    // permission notification (Chrome bloque la requestPermission sans interaction).
    if (typeof Notification !== "undefined" && Notification.permission === "default") {
      Notification.requestPermission().catch(() => {});
    }
    setActiveConversation(conv);
    setDetail(null);
    fetchDetail(conv.conversationId);
    markAsRead(conv.conversationId);
    setMobileView("detail");
    // Persiste dans l'URL pour que F5 garde la conversation ouverte
    const params = new URLSearchParams();
    if (filtre) params.set("filtre", filtre);
    params.set("conv", conv.conversationId);
    router.replace(`/inbox?${params.toString()}`, { scroll: false });

    // Mark as read in local state
    setConversations((prev) =>
      prev.map((c) =>
        c.conversationId === conv.conversationId ? { ...c, messageNonLu: false } : c
      )
    );
  }

  // Filter change
  function handleFiltreChange(newFiltre: string) {
    setFiltre(newFiltre);
    setLoadingList(true);
    fetchConversations(newFiltre);
    // Persiste le filtre dans l'URL
    const params = new URLSearchParams();
    if (newFiltre) params.set("filtre", newFiltre);
    router.replace(`/inbox${params.toString() ? "?" + params.toString() : ""}`, { scroll: false });
  }

  // Toggle mode
  async function handleToggleMode() {
    if (!activeConversation || togglingMode) return;

    const isHumain = activeConversation.modePilotage === "HUMAIN";
    const endpoint = isHumain ? "rendre-ia" : "reprendre";
    const newMode = isHumain ? "IA_AUTO" : "HUMAIN";

    setTogglingMode(true);
    try {
      const res = await apiFetch(
        `${API_BASE}/api/conversations/${activeConversation.conversationId}/${endpoint}`,
        { method: "POST" }
      );
      if (res.ok) {
        setActiveConversation((prev) => prev ? { ...prev, modePilotage: newMode } : prev);
        setConversations((prev) =>
          prev.map((c) =>
            c.conversationId === activeConversation.conversationId
              ? { ...c, modePilotage: newMode }
              : c
          )
        );
        fetchCompteurs();
      } else {
        // On remonte le message metier du backend (ex. conversation STOP/terminee,
        // WhatsApp non configure) plutot qu'un "Erreur 409" opaque.
        let message = "Réessayez dans un instant.";
        try {
          const contentType = res.headers.get("content-type") || "";
          if (contentType.includes("application/json")) {
            const data = await res.json();
            if (typeof data?.message === "string") message = data.message;
          } else {
            const text = await res.text();
            if (text) message = text;
          }
        } catch { /* ignore parse error */ }
        // Le mode a peut-etre change entre-temps (autre onglet / IA) : on resynchronise.
        fetchConversations();
        showToast({ titre: "Basculement de mode échoué", message, tone: "danger" });
      }
    } catch (err) {
      console.warn("Mode toggle failed", err);
      showToast({ titre: "Basculement de mode échoué", message: formatErreur(err), tone: "danger" });
    } finally {
      setTogglingMode(false);
    }
  }


  // Mobile back
  function handleBack() {
    setMobileView("list");
    setActiveConversation(null);
    setDetail(null);
  }

  // Mobile swipe-back : geste natif iOS/Android. Swipe right depuis le bord
  // gauche (50px) avec amplitude > 80px → retour à la liste. Implémentation
  // custom sans lib (gain de poids, gesture-handler trop lourd pour ce cas).
  useEffect(() => {
    if (mobileView !== "detail") return;
    let startX = 0;
    let startY = 0;
    let startTime = 0;
    function onTouchStart(e: TouchEvent) {
      if (e.touches.length !== 1) return;
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
      startTime = Date.now();
    }
    function onTouchEnd(e: TouchEvent) {
      if (e.changedTouches.length !== 1) return;
      const endX = e.changedTouches[0].clientX;
      const endY = e.changedTouches[0].clientY;
      const dx = endX - startX;
      const dy = endY - startY;
      const elapsed = Date.now() - startTime;
      // Critères : swipe depuis le bord gauche (<50px), amplitude horizontale
      // >80px, déplacement vertical négligeable (<60px), durée <500ms.
      if (startX < 50 && dx > 80 && Math.abs(dy) < 60 && elapsed < 500) {
        handleBack();
      }
    }
    window.addEventListener("touchstart", onTouchStart, { passive: true });
    window.addEventListener("touchend", onTouchEnd, { passive: true });
    return () => {
      window.removeEventListener("touchstart", onTouchStart);
      window.removeEventListener("touchend", onTouchEnd);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mobileView]);

  // Ferme le drawer d'analyse quand on change de conversation (le contenu ne correspondrait plus).
  useEffect(() => {
    setAnalyseDrawerOpen(false);
  }, [activeConversation?.conversationId]);

  // Echap ferme le drawer d'analyse.
  useEffect(() => {
    if (!analyseDrawerOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setAnalyseDrawerOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [analyseDrawerOpen]);

  return (
    <AppLayout>
      {/* Bandeau d'alerte rappel immediat = <GlobalAlertesRappelBanner /> monte
          dans AppLayout (cross-page). Plus de banner local ici. */}
      <div className="flex h-full overflow-hidden">
        {/* Left panel - conversation list */}
        <div
          className={`h-full w-full flex-shrink-0 overflow-hidden border-r border-[var(--border)] bg-[var(--card)] lg:block lg:w-[350px] ${
            mobileView === "list" ? "block" : "hidden lg:block"
          }`}
        >
          <div className="flex h-full flex-col">
            {/* Inbox header — SectionHeader (size sm, bordered) avec compteur
                non-lues à côté du titre et bouton mute en action. */}
            <SectionHeader
              eyebrow="Messagerie"
              eyebrowTone="muted"
              title="Conversations"
              size="sm"
              bordered
              className="!px-4 !py-3.5"
              count={
                compteurs && compteurs.nonLues > 0 ? (
                  <span className="text-[var(--color-accent)]">{compteurs.nonLues}</span>
                ) : null
              }
              actions={
                <button
                  onClick={() => setMuted(!muted)}
                  className={`flex h-10 w-10 items-center justify-center rounded-lg transition-colors ${
                    muted
                      ? "bg-warning/10 text-warning hover:bg-warning/20"
                      : "text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                  }`}
                  title={muted ? "Notifications coupées (M pour réactiver)" : "Notifications actives (M pour couper)"}
                  aria-label={muted ? "Réactiver le son" : "Couper le son"}
                >
                  {muted ? (
                    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M17 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2" />
                    </svg>
                  ) : (
                    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                    </svg>
                  )}
                </button>
              }
            />
            {/* min-h-0 flex-1 : la liste doit prendre l'espace RESTANT entre l'en-tete et le
                pied "raccourcis" (sinon sa racine h-full = 100% du parent ignore ces 2 blocs,
                deborde -> derniere conversation coupee, pas de scroll, pied pousse hors ecran). */}
            <div className="min-h-0 flex-1 overflow-hidden">
              <ConversationList
                conversations={conversations}
                compteurs={compteurs}
                activeConversationId={activeConversation?.conversationId ?? null}
                loading={loadingList}
                filtre={filtre}
                onFiltreChange={handleFiltreChange}
                onSelect={handleSelectConversation}
                pulseConversationIds={pulseConvIds}
                erreur={erreurListe}
                onRetry={() => { setLoadingList(true); fetchConversations(); }}
              />
            </div>
            {/* Hint clavier, discret, en bas de la liste, lecture instant pour les power-users */}
            <div className="hidden border-t border-[var(--border)] px-4 py-2 lg:block">
              <p className="flex items-center gap-3 font-mono text-[9.5px] uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)]">
                <span className="inline-flex items-center gap-1">
                  <kbd className="rounded border border-[var(--border)] bg-[var(--card)] px-1 py-0.5 font-mono text-[9px] font-semibold">J</kbd>
                  <kbd className="rounded border border-[var(--border)] bg-[var(--card)] px-1 py-0.5 font-mono text-[9px] font-semibold">K</kbd>
                  naviguer
                </span>
                <span className="inline-flex items-center gap-1">
                  <kbd className="rounded border border-[var(--border)] bg-[var(--card)] px-1 py-0.5 font-mono text-[9px] font-semibold">M</kbd>
                  muet
                </span>
              </p>
            </div>
          </div>
        </div>

        {/* Middle panel - conversation detail */}
        <div
          className={`h-full min-w-0 flex-1 overflow-hidden bg-[var(--background)] lg:block ${
            mobileView === "detail" ? "block" : "hidden lg:block"
          }`}
        >
          {activeConversation ? (
            <ConversationDetailPanel
              conversation={activeConversation}
              detail={detail}
              loading={loadingDetail}
              onToggleMode={handleToggleMode}
              toggleLoading={togglingMode}
              onBack={handleBack}
              erreur={erreurDetail}
              onRetry={() => fetchDetail(activeConversation.conversationId)}
              onOpenAnalyse={() => setAnalyseDrawerOpen(true)}
            />
          ) : (
            <div className="relative flex h-full flex-col items-center justify-center px-6 text-center bg-stripes-pattern-faint">
              <EmptyState
                eyebrow="En attente"
                eyebrowTone="accent"
                title="Choisissez un prospect."
                description="Les conversations en cours sont à gauche. L'analyse IA s'ouvre à droite. Vous, vous concluez."
                visual={
                  <div className="flex items-end gap-2" aria-hidden="true">
                    <span className="h-2.5 w-2.5 rounded-full bg-[var(--color-accent)]/40 animate-pulse-stripe-1" />
                    <span className="h-4 w-4 rounded-full bg-[var(--color-accent)]/60 animate-pulse-stripe-2" />
                    <span className="h-2.5 w-2.5 rounded-full bg-[var(--color-accent)]/40 animate-pulse-stripe-1" />
                  </div>
                }
                size="lg"
                divider
                action={
                  <p className="font-mono text-[9.5px] uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                    <kbd className="rounded border border-[var(--border)] bg-[var(--card)] px-1.5 py-0.5">J</kbd>
                    <span className="mx-1">·</span>
                    <kbd className="rounded border border-[var(--border)] bg-[var(--card)] px-1.5 py-0.5">K</kbd>
                    <span className="ml-2 normal-case tracking-normal">pour naviguer dans la liste</span>
                  </p>
                }
                className="relative z-10"
              />
            </div>
          )}
        </div>

        {/* Right panel - analyse IA (visible uniquement sur ecrans larges quand une conversation est selectionnee) */}
        {activeConversation && (
          <div className="hidden h-full w-[300px] flex-shrink-0 overflow-hidden border-l border-[var(--border)] bg-[var(--card)] xl:block">
            <ConversationAnalysePanel
              conversationId={activeConversation.conversationId}
              refreshTrigger={analyseRefreshTrigger}
            />
          </div>
        )}

        {/* Drawer analyse IA — <xl uniquement (le panneau lateral droit est masque en dessous
            de 1280px). Meme contenu, accessible via le bouton "Analyse IA" du header. */}
        {activeConversation && analyseDrawerOpen && (
          <div className="fixed inset-0 z-40 xl:hidden" role="dialog" aria-modal="true" aria-label="Analyse IA">
            <button
              className="absolute inset-0 h-full w-full cursor-default bg-[var(--foreground)]/40 backdrop-blur-[1px]"
              onClick={() => setAnalyseDrawerOpen(false)}
              aria-label="Fermer l'analyse IA"
            />
            <div className="absolute inset-y-0 right-0 flex w-full max-w-[340px] flex-col overflow-hidden border-l border-[var(--border)] bg-[var(--card)] shadow-2xl">
              <div className="flex items-center justify-between border-b border-[var(--border)] px-4 py-3">
                <p className="text-sm font-semibold text-[var(--foreground)]">Analyse IA</p>
                <button
                  onClick={() => setAnalyseDrawerOpen(false)}
                  className="rounded-lg p-1.5 text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                  aria-label="Fermer"
                >
                  <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <div className="min-h-0 flex-1 overflow-hidden">
                <ConversationAnalysePanel
                  conversationId={activeConversation.conversationId}
                  refreshTrigger={analyseRefreshTrigger}
                />
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Modale de saisie du numero pour lancer une conversation depuis les suggestions */}
    </AppLayout>
  );
}
