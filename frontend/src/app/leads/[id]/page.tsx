"use client";

import { useEffect, useState, useRef, use } from "react";
import { useRouter } from "next/navigation";
import { useApiClient, useIsManager, formatErreur } from "@/lib/auth";
import { useToast } from "@/lib/realtime";
import { toastUndoRdv, toastUndoStatut, toastUndoEnvoiAutoAnnule, toastEnvoiDiffere, toastUndoArchiveLead, toastUndoDesarchiveLead } from "@/lib/rdvUndo";
import AppLayout from "@/components/AppLayout";
import WhatsAppPanel from "@/components/ai/WhatsAppPanel";
import FichePreparationCard from "@/components/leads/FichePreparationCard";
import ProgressionBadge from "@/components/common/ProgressionBadge";
import ScoringGauge from "@/components/common/ScoringGauge";
import SpeedLines from "@/components/common/SpeedLines";
import AlertBand from "@/components/common/AlertBand";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";
import DetailCard from "@/components/common/DetailCard";
import SectionHeader from "@/components/common/SectionHeader";
import DataSheet from "@/components/common/DataSheet";
import ArchiveIcon from "@/components/common/ArchiveIcon";
import { formatPhoneInput, toE164 } from "@/lib/phone";
import { formatEnvoiAutoLabel } from "@/lib/envoiAuto";
import { useUserDisplayName } from "@/lib/auth";
import Link from "next/link";
import ReporterModal from "@/components/common/ReporterModal";
import Modal from "@/components/common/Modal";
import FeedbackRdvSlideUp from "@/components/feedback/FeedbackRdvSlideUp";
import AbandonRdvModal from "@/components/feedback/AbandonRdvModal";
import { STATUTS_PIPELINE, STATUT_LABELS, STATUT_ORDER } from "@/lib/leads/statuts";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// Liste des statuts du pipeline (NOUVEAU → TERMINE). Labels + ordre logique
// sont importés depuis /lib/leads/statuts pour rester en sync avec /leads.
const STATUTS = [...STATUTS_PIPELINE];

interface Lead {
  idLead: number;
  nom: string;
  email: string;
  telephone: string;
  marque: string;
  modele: string;
  territoire: string;
  statut: string;
  scoring: number;
  hotnessScore: number | null;
  source: string;
  etape: string;
  devis: string;
  reprise: boolean;
  typeOpportunite: string;
  creeLe: string;
  vendeurId: number | null;
  donneesSupplementaires: Record<string, string | number | boolean> | null;
  modeImport: string | null;
  premierMessageAutoPrevuLe: string | null;
  premierMessageAutoEffectifLe: string | null;
  premierMessageAutoHorsPlage: boolean;
  archive: boolean;
}

interface FichePreparation {
  fiche: string;
}

interface Vente {
  idVente: number;
  montant: number;
  dateVente: string;
  source: string;
  commentaire: string;
}

export default function LeadDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const apiFetch = useApiClient();
  const isManager = useIsManager();
  const vendeurNom = useUserDisplayName();
  const { showToast } = useToast();
  const [lead, setLead] = useState<Lead | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refetchCount, setRefetchCount] = useState(0);
  const [editStatut, setEditStatut] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");
  // Envoi automatique du premier message WhatsApp (bandeau) : action en cours.
  const [envoiAutoLoading, setEnvoiAutoLoading] = useState<"envoyer" | "annuler" | null>(null);

  // RDV du lead
  const [tachesRdv, setTachesRdv] = useState<{ id: number; titre: string; dateEcheance: string; statut: string; typeTache: string }[]>([]);
  const [rdvActionLoading, setRdvActionLoading] = useState<number | null>(null);
  const [rdvReportModal, setRdvReportModal] = useState<number | null>(null);
  const [feedbackRdvId, setFeedbackRdvId] = useState<number | null>(null);
  const [abandonRdvId, setAbandonRdvId] = useState<number | null>(null);

  const [fiche, setFiche] = useState<FichePreparation | null>(null);
  const [ficheLoading, setFicheLoading] = useState(false);
  const [ficheError, setFicheError] = useState("");
  const [copyMessage, setCopyMessage] = useState("");
  // La fiche en cache a-t-elle ete verifiee (GET resolu) ? Tant que non, on ne
  // declenche PAS l'auto-generation : sinon on POST une generation alors qu'une
  // fiche en cache existe peut-etre, et le setFiche du GET annulait le POST en
  // vol (requete "cancelled"). Voir l'effet d'auto-generation plus bas.
  const [ficheVerifiee, setFicheVerifiee] = useState(false);
  // Garde : l'auto-generation ne doit partir qu'UNE fois par lead (evite les
  // relances/annulations sur re-render). Stocke l'id de route deja traite.
  const ficheGenTriggeredRef = useRef<string | null>(null);
  // Composant monte ? Evite les setState apres demontage sans avoir a annuler
  // (abort) la generation IA en vol.
  const mountedRef = useRef(true);
  useEffect(() => { mountedRef.current = true; return () => { mountedRef.current = false; }; }, []);


  // Vente state (lecture seule : le badge "Vente" affiche une vente existante.
  // La déclaration de vente depuis la fiche a été retirée, cf. décision produit).
  const [vente, setVente] = useState<Vente | null>(null);

  // Vendeur assignment
  const [vendeurs, setVendeurs] = useState<{ id: number; nom: string; prenom: string }[]>([]);
  const [vendeurSaving, setVendeurSaving] = useState(false);

  // Modal de confirmation pour saut de statut > 1 étape vers TERMINE (remplace
  // window.confirm — banni DESIGN.md "Don't ouvrir une modale système OS").
  const [pendingStatut, setPendingStatut] = useState<string | null>(null);

  // Archivage (suppression douce) du prospect : confirmation + action en cours.
  const [confirmArchive, setConfirmArchive] = useState(false);
  const [archiving, setArchiving] = useState(false);
  // Désarchivage du prospect (depuis la bannière "archivé") : action en cours.
  const [desarchiving, setDesarchiving] = useState(false);

  // États d'erreur par section sidebar (au lieu de silent catches). Permet
  // d'afficher un "Indisponible" inline sans toast spam.
  const [sectionErrors, setSectionErrors] = useState({
    tachesRdv: false,
    vendeurs: false,
    vente: false,
    conversations: false,
    fiche: false,
  });

  useEffect(() => {
    // AbortController : annule les fetches en cours si le user navigue avant que
    // la promesse résolve (pas de setState sur composant démonté). Audit P0 a11y.
    const controller = new AbortController();
    const { signal } = controller;
    // Réinitialise l'état d'erreur principal à chaque tentative (retry inclus).
    setError("");
    setLoading(true);
    // Nouveau lead / retry : on remet le flag "fiche verifiee" a false le temps
    // que le GET fiche ci-dessous resolve (l'auto-generation attend ce signal).
    setFicheVerifiee(false);
    // Helper : ignore les erreurs liées à l'abort (navigation normale).
    const handleErr = (section: keyof typeof sectionErrors, err: unknown) => {
      if (signal.aborted) return;
      console.warn(`Lead detail ${section} fetch failed`, err);
      setSectionErrors((prev) => ({ ...prev, [section]: true }));
    };

    apiFetch(`${API_BASE}/api/leads/${id}`, { signal })
      .then(async (res) => {
        if (!res.ok) throw new Error(`Erreur ${res.status}`);
        return res.json();
      })
      .then((data: Lead) => {
        if (signal.aborted) return;
        setLead(data); setEditStatut(data.statut || "");
      })
      .catch((err) => { if (!signal.aborted) setError(formatErreur(err)); })
      .finally(() => { if (!signal.aborted) setLoading(false); });

    // Charger les tâches RDV du lead
    apiFetch(`${API_BASE}/api/leads/${id}/taches`, { signal })
      .then((res) => res.ok ? res.json() : [])
      .then((data: { id: number; titre: string; dateEcheance: string; statut: string; typeTache: string }[]) => {
        if (signal.aborted) return;
        setTachesRdv(data.filter((t) => t.typeTache === "RDV").sort((a, b) =>
          new Date(a.dateEcheance).getTime() - new Date(b.dateEcheance).getTime()));
      })
      .catch((err) => handleErr("tachesRdv", err));

    // Charger les vendeurs pour le picker (actifs uniquement)
    apiFetch(`${API_BASE}/api/equipe?actifsUniquement=true`, { signal })
      .then((res) => res.ok ? res.json() : [])
      .then((data: { id: number; nom: string; prenom: string; role: string }[]) => {
        if (signal.aborted) return;
        setVendeurs(data.filter((v) => v.role === "VENDEUR"));
      })
      .catch((err) => handleErr("vendeurs", err));

    // Check if lead has a vente
    apiFetch(`${API_BASE}/api/leads/${id}/vente`, { signal })
      .then(async (res) => {
        if (signal.aborted) return;
        if (res.ok) {
          const data: Vente = await res.json();
          if (!signal.aborted) setVente(data);
        }
      })
      .catch((err) => handleErr("vente", err));

    // Charger la fiche cachee si elle existe
    apiFetch(`${API_BASE}/api/leads/${id}/fiche-preparation`, { signal })
      .then(async (res) => {
        if (signal.aborted) return;
        if (res.ok) {
          const data = await res.json();
          if (signal.aborted) return;
          setFiche(data);
          // Si la fiche est perimee (donnees ont change), regenerer automatiquement en arriere-plan
          if (data.stale) {
            setFicheLoading(true);
            apiFetch(`${API_BASE}/api/leads/${id}/fiche-preparation`, { method: "POST", signal })
              .then(async (r) => {
                if (signal.aborted) return;
                if (r.ok) { const fresh = await r.json(); if (!signal.aborted) setFiche(fresh); }
              })
              .catch((err) => handleErr("fiche", err))
              .finally(() => { if (!signal.aborted) setFicheLoading(false); });
          }
        }
      })
      .catch((err) => handleErr("fiche", err))
      // GET fiche resolu (avec ou sans fiche en cache) : l'auto-generation peut
      // maintenant decider en connaissance de cause (evite de POSTer une generation
      // qui serait ensuite annulee par le setFiche d'une fiche en cache -> "cancelled").
      .finally(() => { if (!signal.aborted) setFicheVerifiee(true); });

    return () => controller.abort();
  }, [id, apiFetch, refetchCount]);

  // Auto-refresh discret tant qu'un envoi automatique du premier message est en
  // attente. L'envoi part via un batch serveur (toutes les minutes), donc la fiche
  // ne "sait" pas quand il part : on refetch toutes les 20 s tant que le flag est
  // armé. Un seul refetch met à jour la bannière ET le panneau WhatsApp (via
  // refetchCount → WhatsAppPanel.refreshKey). Dès que le message est parti, le
  // serveur remet premierMessageAutoPrevuLe à null → l'intervalle est nettoyé et
  // ne se recrée pas (plus rien à attendre). Zéro coût hors période d'attente.
  useEffect(() => {
    if (!lead?.premierMessageAutoPrevuLe) return;
    const timer = setInterval(() => setRefetchCount((n) => n + 1), 20000);
    return () => clearInterval(timer);
  }, [lead?.premierMessageAutoPrevuLe]);

  // Auto-générer la fiche IA si le lead a une conversation et pas de fiche.
  //
  // IMPORTANT (fix "requete cancelled") : cette generation ne doit PAS etre annulee
  // par un re-render. Deux causes de cancel eliminees :
  //   1) On attend `ficheVerifiee` (GET fiche resolu) : sinon on POSTait une generation
  //      alors qu'une fiche en cache arrivait juste apres -> le setFiche du GET relancait
  //      l'effet et son cleanup annulait le POST en vol.
  //   2) Pas d'AbortController ici : une generation IA est longue (plusieurs secondes) ;
  //      l'annuler sur un simple changement de reference (`lead`/`fiche`) donnait un
  //      "cancelled" cote reseau. On garde plutot un flag de montage + une garde "une
  //      seule generation par lead". Cote serveur la fiche est cachee, donc rien de perdu.
  useEffect(() => {
    if (!ficheVerifiee || !lead || fiche || ficheLoading) return;
    if (lead.statut === "NOUVEAU") return;
    if (ficheGenTriggeredRef.current === id) return; // deja generee pour ce lead
    ficheGenTriggeredRef.current = id;
    setFicheLoading(true);
    apiFetch(`${API_BASE}/api/leads/${id}/fiche-preparation`, { method: "POST" })
      .then(async (r) => {
        if (r.ok && mountedRef.current) { const data = await r.json(); if (mountedRef.current) setFiche(data); }
      })
      .catch((err) => console.warn("Auto-fiche generate failed", err))
      .finally(() => { if (mountedRef.current) setFicheLoading(false); });
  }, [ficheVerifiee, lead, fiche, ficheLoading, id, apiFetch]);

  async function handleSaveStatut(newStatut?: string) {
    if (!lead) return;
    const statutToSave = newStatut || editStatut;
    if (statutToSave === lead.statut) return;
    const statutPrecedent = lead.statut; // pour l'undo
    setSaving(true);
    try {
      // On passe par PATCH /statut (deplacerLead, source SELECTBOX) et NON par
      // PUT /leads/{id} : c'est le seul chemin qui trace la transition dans
      // historique_statut (cohérence avec le Kanban) sans jamais la marquer
      // source "IA" (donc non facturable, cf. définition canonique du RDV pris).
      const res = await apiFetch(`${API_BASE}/api/leads/${id}/statut`, {
        method: "PATCH",
        headers: { "X-Source": "SELECTBOX" },
        body: JSON.stringify({ statut: statutToSave }),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      // deplacerLead renvoie une KanbanCarte, pas le lead complet : on refetch
      // le lead pour rafraîchir la fiche de façon fiable.
      const leadRes = await apiFetch(`${API_BASE}/api/leads/${id}`);
      if (leadRes.ok) {
        const updated: Lead = await leadRes.json();
        setLead(updated);
        setEditStatut(updated.statut || "");
      }
      // Toast avec "Annuler" : un statut changé par erreur (clic mal placé, surtout
      // mobile) peut être annulé, comme les actions RDV. Symétrie du contrôle utilisateur.
      toastUndoStatut({
        showToast, apiFetch, leadId: lead.idLead,
        statutPrecedent: statutPrecedent ?? "",
        labelStatut: STATUT_LABELS[statutToSave] || statutToSave,
        onReverted: () => setRefetchCount((n) => n + 1),
      });
    } catch (err) {
      showToast({ titre: "Erreur", message: formatErreur(err), tone: "danger" });
    } finally {
      setSaving(false);
    }
  }

  /** Envoie le premier message WhatsApp (annule l'envoi auto en attente). Action
   *  IRREVERSIBLE (message parti = non rappelable) : on DIFFERE l'envoi de quelques
   *  secondes avec un bouton Annuler. Rien ne part tant que le delai n'est pas ecoule. */
  function handleEnvoyerMaintenant() {
    if (!lead) return;
    // Anti double-envoi : le differe dure ~7s, pendant lesquelles le bouton doit
    // rester desactive. Sans ce garde, un double-clic declenchait 2 differes = 2 envois.
    if (envoiAutoLoading !== null) return;
    setEnvoiAutoLoading("envoyer");
    const numero = toE164(lead.telephone || "");
    toastEnvoiDiffere({
      showToast,
      onEnvoyer: async () => {
        try {
          const res = await apiFetch(`${API_BASE}/api/leads/${id}/whatsapp`, {
            method: "POST",
            body: JSON.stringify({ numeroDestinataire: numero }),
          });
          if (!res.ok) {
            const body = await res.text();
            throw new Error(body || `Erreur ${res.status}`);
          }
          showToast({ titre: "Message envoyé", message: "Le premier message WhatsApp a été envoyé.", tone: "success" });
          setRefetchCount((n) => n + 1);
        } finally {
          setEnvoiAutoLoading(null);
        }
      },
      onErreur: (err) => {
        showToast({ titre: "Envoi échoué", message: formatErreur(err), tone: "danger" });
      },
      // Annulation du differe : on reactive le bouton.
      onAnnule: () => setEnvoiAutoLoading(null),
    });
  }

  /** Annule l'envoi automatique du premier message WhatsApp en attente. Action
   *  REVERSIBLE : toast succes + "Annuler" qui RE-ARME l'envoi a l'echeance d'origine. */
  async function handleAnnulerEnvoiAuto() {
    if (!lead) return;
    const ancienne = lead.premierMessageAutoPrevuLe;
    setEnvoiAutoLoading("annuler");
    try {
      const res = await apiFetch(`${API_BASE}/api/leads/${id}/envoi-auto/annuler`, { method: "POST" });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      toastUndoEnvoiAutoAnnule({
        showToast,
        apiFetch,
        leadId: Number(id),
        anciennePrevuLe: ancienne,
        onReverted: () => setRefetchCount((n) => n + 1),
      });
      setRefetchCount((n) => n + 1);
    } catch (err) {
      showToast({ titre: "Annulation échouée", message: formatErreur(err), tone: "danger" });
    } finally {
      setEnvoiAutoLoading(null);
    }
  }

  /** Archive (supprime en douceur) le prospect. Action REVERSIBLE : toast succès + "Annuler"
   *  qui désarchive via POST /restaurer. Après succès, on quitte la fiche vers /leads
   *  (le prospect archivé disparaît des listes actives ; le toast global reste cliquable). */
  async function handleArchiverLead() {
    if (!lead) return;
    setArchiving(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/leads/${id}`, { method: "DELETE" });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setConfirmArchive(false);
      toastUndoArchiveLead({
        showToast,
        apiFetch,
        leadId: lead.idLead,
        onReverted: () => router.push(`/leads/${id}`),
      });
      router.push("/leads");
    } catch (err) {
      showToast({ titre: "Archivage échoué", message: formatErreur(err), tone: "danger" });
    } finally {
      setArchiving(false);
    }
  }

  /** Désarchive le prospect (depuis la bannière "archivé"). Action REVERSIBLE :
   *  toast succès + "Annuler" qui ré-archive via DELETE. On reste sur la fiche et
   *  on refetch pour rafraîchir la bannière / le bouton. */
  async function handleDesarchiverLead() {
    if (!lead) return;
    setDesarchiving(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/leads/${id}/restaurer`, { method: "POST" });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      toastUndoDesarchiveLead({
        showToast,
        apiFetch,
        leadId: lead.idLead,
        onReverted: () => setRefetchCount((n) => n + 1),
      });
      setRefetchCount((n) => n + 1);
    } catch (err) {
      showToast({ titre: "Désarchivage échoué", message: formatErreur(err), tone: "danger" });
    } finally {
      setDesarchiving(false);
    }
  }

  /** Marque un RDV effectué/annulé. Renvoie true si la transition a réussi (pour que
   *  l'appelant décide d'ouvrir le feedback / proposer l'undo). */
  async function handleRdvAction(tacheId: number, action: "effectue" | "abandonner"): Promise<boolean> {
    if (!lead) return false;
    setRdvActionLoading(tacheId);
    try {
      const statut = action === "effectue" ? "TERMINEE" : "ANNULEE";
      const patchRes = await apiFetch(`${API_BASE}/api/taches/${tacheId}/statut`, {
        method: "PATCH",
        body: JSON.stringify({ statut }),
      });
      // Échec de la transition (RDV dans le futur, conflit de version...) : signalé FORT.
      if (!patchRes.ok) {
        let message = "Réessayez dans un instant.";
        try {
          const contentType = patchRes.headers.get("content-type") || "";
          if (contentType.includes("application/json")) {
            const data = await patchRes.json();
            if (typeof data?.message === "string") message = data.message;
          } else {
            const text = await patchRes.text();
            if (text) message = text;
          }
        } catch { /* ignore parse error */ }
        showToast({ titre: action === "effectue" ? "RDV non clôturé" : "RDV non annulé", message, tone: "danger" });
        return false;
      }
      // Transition OK. Rechargement best-effort (un échec de refetch ne remet pas en cause
      // la clôture déjà actée côté serveur).
      try {
        const [leadRes, tachesRes] = await Promise.all([
          apiFetch(`${API_BASE}/api/leads/${id}`),
          apiFetch(`${API_BASE}/api/leads/${id}/taches`),
        ]);
        if (leadRes.ok) {
          const updated: Lead = await leadRes.json();
          setLead(updated);
          setEditStatut(updated.statut || "");
        }
        if (tachesRes.ok) {
          const data = await tachesRes.json();
          setTachesRdv(data.filter((t: { typeTache: string }) => t.typeTache === "RDV").sort((a: { dateEcheance: string }, b: { dateEcheance: string }) =>
            new Date(a.dateEcheance).getTime() - new Date(b.dateEcheance).getTime()));
        }
      } catch (err) {
        console.warn("RDV action refresh failed", err);
      }
      return true;
    } catch (err) {
      console.warn("RDV action failed", err);
      showToast({ titre: "Action RDV échouée", message: formatErreur(err), tone: "danger" });
      return false;
    }
    finally { setRdvActionLoading(null); }
  }

  /** "Effectué" = engagement explicite : on clôture le RDV TOUT DE SUITE (avec undo),
   *  puis on ouvre le volet de feedback qui devient PUREMENT optionnel. Fermer le volet
   *  (croix / Échap / clic hors-zone) ne ré-engage donc plus rien. */
  async function ouvrirFeedbackRdv(tacheId: number) {
    if (!lead) return;
    const ok = await handleRdvAction(tacheId, "effectue");
    if (!ok) return; // échec déjà signalé (toast danger), on n'ouvre pas le feedback
    toastUndoRdv({ showToast, apiFetch, leadId: lead.idLead, rdvId: tacheId,
      titre: "RDV marqué effectué", onReverted: () => setRefetchCount((n) => n + 1) });
    setFeedbackRdvId(tacheId);
  }

  async function handleAssignVendeur(newVendeurId: string) {
    if (!lead) return;
    setVendeurSaving(true);
    try {
      const body = newVendeurId ? { vendeurId: Number(newVendeurId) } : { vendeurId: null };
      const res = await apiFetch(`${API_BASE}/api/leads/${id}`, {
        method: "PUT",
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const updated: Lead = await res.json();
      setLead(updated);
      const vendeurAssigne = vendeurs.find((v) => String(v.id) === newVendeurId);
      showToast({
        titre: newVendeurId ? "Vendeur assigné" : "Lead non assigné",
        message: vendeurAssigne ? `Assigné à ${vendeurAssigne.prenom} ${vendeurAssigne.nom}.` : "Le lead n'a plus de vendeur.",
        tone: "success",
      });
    } catch (err) {
      showToast({ titre: "Erreur", message: formatErreur(err), tone: "danger" });
    } finally {
      setVendeurSaving(false);
    }
  }

  async function handleGenerateFiche() {
    setFicheLoading(true); setFicheError(""); setCopyMessage("");
    try {
      const res = await apiFetch(`${API_BASE}/api/leads/${id}/fiche-preparation`, {
        method: "POST",
      });
      if (!res.ok) {
        if (res.status === 429) {
          // Rate limit : on le DIT au lieu de repasser en silence à "Générer" (le vendeur
          // reclique sans comprendre). Message court, non bloquant.
          setFicheError("Trop de générations en peu de temps. Patientez une minute.");
          return;
        }
        throw new Error(`Erreur ${res.status}`);
      }
      const data: FichePreparation = await res.json();
      setFiche(data);
    } catch (err) { setFicheError(formatErreur(err)); }
    finally { setFicheLoading(false); }
  }

  async function handleCopyFiche() {
    if (!fiche) return;
    try {
      await navigator.clipboard.writeText(fiche.fiche);
      setCopyMessage("Copié !");
      setTimeout(() => setCopyMessage(""), 2000);
    } catch { setCopyMessage("Erreur"); }
  }


  // Note : statutColor et scoringColor étaient déclarés ici mais jamais utilisés
  // (dead code détecté par l'audit impeccable). Les statuts sont rendus via
  // <ProgressionBadge /> qui gère sa propre palette, et le scoring via
  // <ScoringGauge /> qui calcule sa couleur en interne.

  return (
    <AppLayout>
      <div className="px-6 py-6">
        {/* BREADCRUMB éditorial, back + statut en mono */}
        <div className="mb-4 flex items-center gap-3">
          <button
            onClick={() => router.back()}
            className="inline-flex items-center gap-1.5 font-mono text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)] transition-colors hover:text-[var(--foreground)]"
            aria-label="Retour aux prospects"
          >
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
            </svg>
            Prospects
          </button>
          {lead?.statut && (
            <>
              <span className="text-[var(--color-lr-text-tertiary)]">·</span>
              <ProgressionBadge statut={lead.statut} />
            </>
          )}
          {vente && (
            <>
              <span className="text-[var(--color-lr-text-tertiary)]">·</span>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-success/12 px-2.5 py-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-success">
                <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
                Vente {vente.montant.toLocaleString("fr-FR")} €
              </span>
            </>
          )}
        </div>


        {/* Skeleton, match la nouvelle structure de LeadDetailLayout :
            identity header (H1 + sous-titre + chips + score) + grille 8/4
            (Brief IA + Conversation a gauche, sidebar Pilotage/Vehicule/RDV
            a droite). */}
        {loading && (
          <>
            {/* Skeleton header : H1 + sous-titre + chips + score gauge a droite */}
            <div className="mb-6 border-b border-[var(--border)] pb-6">
              <div className="flex flex-wrap items-end justify-between gap-6">
                <div className="min-w-0 flex-1 space-y-3">
                  {/* Titre affiché immédiatement (le nom du prospect est encore
                      inconnu pendant le chargement, d'où le générique). Pas de
                      shimmer sur le titre : il doit être visible dès le 1er paint. */}
                  <h1 className="font-display text-[28px] font-bold tracking-display-tight text-[var(--foreground)]">Fiche prospect</h1>
                  <div className="h-5 w-56 rounded animate-shimmer" />
                  <div className="flex gap-2 pt-1">
                    <div className="h-7 w-40 rounded-full animate-shimmer" />
                    <div className="h-7 w-48 rounded-full animate-shimmer" />
                  </div>
                </div>
                <div className="h-[72px] w-[72px] shrink-0 rounded-full animate-shimmer" />
              </div>
            </div>

            <div className="grid grid-cols-1 gap-5 lg:grid-cols-12">
              {/* COL MAIN skeleton, Brief IA section + Conversation section */}
              <div className="space-y-5 lg:col-span-8">
                {/* Brief de préparation */}
                <div className="overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--card)]">
                  <div className="flex items-center justify-between border-b border-[var(--border)] px-6 py-4">
                    <div className="space-y-1.5">
                      <div className="h-3 w-32 rounded animate-shimmer" />
                      <div className="h-5 w-40 rounded animate-shimmer" />
                    </div>
                    <div className="h-8 w-32 rounded-lg animate-shimmer" />
                  </div>
                  <div className="space-y-3 px-6 py-6">
                    <div className="h-4 w-3/4 rounded animate-shimmer" />
                    <div className="h-4 w-full rounded animate-shimmer" />
                    <div className="h-4 w-5/6 rounded animate-shimmer" />
                    <div className="mt-4 h-4 w-1/2 rounded animate-shimmer" />
                    <div className="h-4 w-2/3 rounded animate-shimmer" />
                  </div>
                </div>

                {/* Conversation */}
                <div className="overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--card)]">
                  <div className="flex items-center justify-between border-b border-[var(--border)] px-6 py-4">
                    <div className="space-y-1.5">
                      <div className="h-3 w-28 rounded animate-shimmer" />
                      <div className="h-5 w-44 rounded animate-shimmer" />
                    </div>
                    <div className="h-4 w-32 rounded animate-shimmer" />
                  </div>
                  <div className="space-y-3 px-6 py-6" style={{ height: "240px" }}>
                    <div className="ml-auto h-12 w-3/5 rounded-2xl animate-shimmer" />
                    <div className="h-12 w-2/3 rounded-2xl animate-shimmer" />
                    <div className="ml-auto h-12 w-1/2 rounded-2xl animate-shimmer" />
                  </div>
                </div>
              </div>

              {/* COL SIDEBAR skeleton, Pilotage + Véhicule + RDV */}
              <aside className="space-y-4 lg:col-span-4">
                {/* Pilotage */}
                <div className="rounded-2xl border border-[var(--border)] bg-[var(--card)] p-5">
                  <div className="h-3 w-20 rounded animate-shimmer" />
                  <div className="mt-3 space-y-3">
                    <div className="flex items-center gap-3">
                      <div className="h-3 w-12 rounded animate-shimmer" />
                      <div className="h-7 flex-1 rounded-lg animate-shimmer" />
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="h-3 w-12 rounded animate-shimmer" />
                      <div className="h-7 flex-1 rounded-lg animate-shimmer" />
                    </div>
                  </div>
                </div>
                {/* Véhicule */}
                <div className="rounded-2xl border border-[var(--border)] bg-[var(--card)] p-5">
                  <div className="h-3 w-20 rounded animate-shimmer" />
                  <div className="mt-2 h-6 w-40 rounded animate-shimmer" />
                  <div className="mt-3 space-y-2 border-t border-[var(--border)] pt-2">
                    {Array.from({ length: 3 }).map((_, i) => (
                      <div key={i} className="flex items-center justify-between py-2">
                        <div className="h-3 w-16 rounded animate-shimmer" />
                        <div className="h-4 w-24 rounded animate-shimmer" />
                      </div>
                    ))}
                  </div>
                </div>
                {/* RDV (optionnel, montre un placeholder même si pas sur que ça apparaisse) */}
                <div className="rounded-2xl border border-[var(--border)] bg-[var(--card)] p-5">
                  <div className="h-3 w-20 rounded animate-shimmer" />
                  <div className="mt-3 h-16 w-full rounded-lg animate-shimmer" />
                </div>
              </aside>
            </div>
          </>
        )}
        {!loading && error && (
          <>
            {/* Header complet conservé en mode erreur (titre + trait inférieur),
                identique à la structure du header normal du détail (border-b pb-6).
                Le nom du prospect est inconnu (chargement échoué) d'où le titre
                générique. */}
            <div className="mb-6 border-b border-[var(--border)] pb-6">
              <h1 className="font-display text-[28px] font-bold tracking-display-tight text-[var(--foreground)]">
                Fiche prospect
              </h1>
            </div>
            <ErrorState
              message={error}
              onRetry={() => setRefetchCount((n) => n + 1)}
            />
          </>
        )}

        {lead && (
          <LeadDetailLayout
            lead={lead}
            tachesRdv={tachesRdv}
            vente={vente}
            editStatut={editStatut}
            setEditStatut={setEditStatut}
            handleSaveStatut={handleSaveStatut}
            saving={saving}
            isManager={isManager}
            vendeurs={vendeurs}
            vendeurSaving={vendeurSaving}
            handleAssignVendeur={handleAssignVendeur}
            fiche={fiche}
            ficheLoading={ficheLoading}
            ficheError={ficheError}
            handleGenerateFiche={handleGenerateFiche}
            handleCopyFiche={handleCopyFiche}
            copyMessage={copyMessage}
            vendeurNom={vendeurNom}
            rdvActionLoading={rdvActionLoading}
            onEffectueRdv={ouvrirFeedbackRdv}
            setRdvReportModal={setRdvReportModal}
            setAbandonRdvId={setAbandonRdvId}
            setPendingStatut={setPendingStatut}
            sectionErrors={sectionErrors}
            id={id}
            handleEnvoyerMaintenant={handleEnvoyerMaintenant}
            handleAnnulerEnvoiAuto={handleAnnulerEnvoiAuto}
            envoiAutoLoading={envoiAutoLoading}
            onArchiver={() => setConfirmArchive(true)}
            onDesarchiver={handleDesarchiverLead}
            desarchiving={desarchiving}
            whatsappRefreshKey={refetchCount}
          />
        )}

      </div>
      <ReporterModal open={rdvReportModal !== null} onClose={() => setRdvReportModal(null)} tacheId={rdvReportModal} apiFetch={apiFetch}
        showToast={showToast}
        ancienneDateEcheance={rdvReportModal != null ? tachesRdv.find(t => t.id === rdvReportModal)?.dateEcheance ?? null : null}
        onSuccess={async () => {
        const [lr, tr] = await Promise.all([apiFetch(`${API_BASE}/api/leads/${id}`), apiFetch(`${API_BASE}/api/leads/${id}/taches`)]);
        if (lr.ok) { const u = await lr.json(); setLead(u); setEditStatut(u.statut || ""); }
        if (tr.ok) { const d = await tr.json(); setTachesRdv(d.filter((t: { typeTache: string }) => t.typeTache === "RDV").sort((a: { dateEcheance: string }, b: { dateEcheance: string }) => new Date(a.dateEcheance).getTime() - new Date(b.dateEcheance).getTime())); }
      }} />

      {/* Modal éditorial : saut de statut direct vers TERMINE (remplace window.confirm) */}
      <Modal
        open={pendingStatut !== null}
        onClose={() => setPendingStatut(null)}
        title="Clôturer ce prospect ?"
        description={
          pendingStatut
            ? `Passage direct de "${STATUT_LABELS[lead?.statut ?? ""] ?? lead?.statut}" à "${STATUT_LABELS[pendingStatut] ?? pendingStatut}". Cela termine la fiche sans étape intermédiaire.`
            : undefined
        }
        size="sm"
        footer={
          <>
            <button
              onClick={() => setPendingStatut(null)}
              className="rounded-lg border border-[var(--border)] px-4 py-2 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--muted)]"
            >
              Annuler
            </button>
            <button
              onClick={() => {
                if (pendingStatut) {
                  setEditStatut(pendingStatut);
                  handleSaveStatut(pendingStatut);
                }
                setPendingStatut(null);
              }}
              className="btn-primary rounded-lg px-4 py-2 text-sm font-semibold"
            >
              Clôturer
            </button>
          </>
        }
      >
        <p className="text-sm leading-relaxed text-[var(--color-lr-text-secondary)]">
          Vous pouvez toujours rouvrir la fiche en changeant le statut manuellement plus tard.
        </p>
      </Modal>

      {/* Modal de confirmation : archivage du prospect. Action réversible
          (désarchivage possible à tout moment, et bouton "Annuler" dans le toast). */}
      <Modal
        open={confirmArchive}
        onClose={() => { if (!archiving) setConfirmArchive(false); }}
        title="Archiver ce prospect ?"
        size="sm"
        footer={
          <>
            <button
              onClick={() => setConfirmArchive(false)}
              disabled={archiving}
              className="rounded-lg border border-[var(--border)] px-4 py-2 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--muted)] disabled:opacity-50"
            >
              Annuler
            </button>
            <button
              onClick={handleArchiverLead}
              disabled={archiving}
              className="rounded-lg bg-danger px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-danger/90 disabled:opacity-50"
            >
              {archiving ? "Archivage…" : "Archiver"}
            </button>
          </>
        }
      >
        <p className="text-sm leading-relaxed text-[var(--color-lr-text-secondary)]">
          Il sortira de vos listes actives. Vous pourrez le désarchiver à tout moment.
        </p>
      </Modal>

      {feedbackRdvId !== null && lead && (
        <FeedbackRdvSlideUp
          open={true}
          leadId={lead.idLead}
          rdvId={feedbackRdvId}
          nomProspect={lead.nom}
          // Le RDV est DÉJÀ marqué effectué (à l'ouverture, via ouvrirFeedbackRdv).
          // Fermer OU soumettre le feedback ne fait donc que refermer le volet : aucune
          // re-transition, aucun double toast. Le feedback est purement optionnel.
          onClose={() => setFeedbackRdvId(null)}
          onSubmitted={() => setFeedbackRdvId(null)}
        />
      )}

      {abandonRdvId !== null && lead && (
        <AbandonRdvModal
          open={true}
          leadId={lead.idLead}
          rdvId={abandonRdvId}
          nomProspect={lead.nom}
          onClose={() => setAbandonRdvId(null)}
          onAbandoned={() => {
            // Le backend annule la tache atomiquement (POST /feedback/abandon).
            // On recharge + on propose l'undo.
            const tid = abandonRdvId;
            setAbandonRdvId(null);
            setRefetchCount((n) => n + 1);
            toastUndoRdv({ showToast, apiFetch, leadId: lead.idLead, rdvId: tid,
              titre: "RDV abandonné", onReverted: () => setRefetchCount((n) => n + 1) });
          }}
        />
      )}
    </AppLayout>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// LeadDetailLayout, refonte UX/UI complete de la page detail lead.
//
// Direction UX :
// - Plus de tabs (caches du contenu). Tout en hierarchie visuelle claire.
// - IDENTITY HEADER massif (nom + vehicule + source) avec score Tachograph
//   en ancre visuelle a droite.
// - SMART ALERT BAND contextuel quand il y a urgence (RDV today, late,
//   lead pas contacte). Indique a l'utilisateur QUOI FAIRE.
// - MAIN GRID 8/4 : Brief IA + Conversation a gauche (workflow vendeur),
//   Pilotage + Vehicule + RDV + Commercial a droite (overview).
// - Sidebar sticky -> on garde le contexte visible meme en scrollant.
// ─────────────────────────────────────────────────────────────────────────────

interface LeadDetailLayoutProps {
  lead: Lead;
  tachesRdv: { id: number; titre: string; dateEcheance: string; statut: string; typeTache: string }[];
  vente: Vente | null;
  editStatut: string;
  setEditStatut: (s: string) => void;
  handleSaveStatut: (s?: string) => void;
  saving: boolean;
  isManager: boolean;
  vendeurs: { id: number; nom: string; prenom: string }[];
  vendeurSaving: boolean;
  handleAssignVendeur: (id: string) => void;
  fiche: FichePreparation | null;
  ficheLoading: boolean;
  ficheError: string;
  handleGenerateFiche: () => void;
  handleCopyFiche: () => void;
  copyMessage: string;
  vendeurNom: string;
  rdvActionLoading: number | null;
  onEffectueRdv: (id: number) => void;
  setRdvReportModal: (id: number) => void;
  setAbandonRdvId: (id: number) => void;
  setPendingStatut: (s: string | null) => void;
  sectionErrors: { tachesRdv: boolean; vendeurs: boolean; vente: boolean; conversations: boolean; fiche: boolean };
  id: string;
  handleEnvoyerMaintenant: () => void;
  handleAnnulerEnvoiAuto: () => void;
  envoiAutoLoading: "envoyer" | "annuler" | null;
  onArchiver: () => void;
  onDesarchiver: () => void;
  desarchiving: boolean;
  whatsappRefreshKey: number;
}

function LeadDetailLayout({
  lead,
  tachesRdv,
  editStatut,
  setEditStatut,
  handleSaveStatut,
  saving,
  isManager,
  vendeurs,
  vendeurSaving,
  handleAssignVendeur,
  fiche,
  ficheLoading,
  ficheError,
  handleGenerateFiche,
  handleCopyFiche,
  copyMessage,
  vendeurNom,
  rdvActionLoading,
  onEffectueRdv,
  setRdvReportModal,
  setAbandonRdvId,
  setPendingStatut,
  sectionErrors,
  id,
  handleEnvoyerMaintenant,
  handleAnnulerEnvoiAuto,
  envoiAutoLoading,
  onArchiver,
  onDesarchiver,
  desarchiving,
  whatsappRefreshKey,
}: LeadDetailLayoutProps) {
  const { showToast } = useToast();
  // ── Document title dynamique : le manager garde 5 onglets de leads ouverts
  //     parallèles, doit pouvoir les distinguer dans la barre du browser.
  useEffect(() => {
    if (lead.nom) {
      document.title = `${lead.nom}, Leadrive`;
    } else {
      document.title = "Prospect, Leadrive";
    }
    return () => { document.title = "Leadrive"; };
  }, [lead.nom]);

  // ── Copy-to-clipboard avec feedback inline (le user clic phone/email, voit
  //     un check vert temporaire pendant 1.5s à la place du chip texte). ──
  const [copiedField, setCopiedField] = useState<"telephone" | "email" | null>(null);
  function copierAuPressePapier(field: "telephone" | "email", value: string) {
    navigator.clipboard.writeText(value).then(() => {
      setCopiedField(field);
      setTimeout(() => setCopiedField((prev) => (prev === field ? null : prev)), 1500);
    }).catch(() => {
      // Presse-papier indisponible (permission / contexte non sécurisé) : on le signale
      // au lieu d'un échec muet.
      showToast({ titre: "Copie impossible", message: "Copiez le numéro manuellement.", tone: "warning" });
    });
  }

  // ── Smart alert : calcule l'urgence du jour ──
  const today = new Date();
  const todayStr = today.toDateString();
  const rdvLate = tachesRdv.find((t) => t.statut !== "TERMINEE" && new Date(t.dateEcheance) < today && new Date(t.dateEcheance).toDateString() !== todayStr);
  const rdvToday = tachesRdv.find((t) => t.statut !== "TERMINEE" && new Date(t.dateEcheance).toDateString() === todayStr);
  // Math.max(0, ...) : une date de création dans le futur (import avec fuseau/date décalée)
  // ne doit jamais afficher un nombre de jours négatif ("Importé il y a -1 jours").
  const daysSinceCreated = lead.creeLe ? Math.max(0, Math.floor((Date.now() - new Date(lead.creeLe).getTime()) / (24 * 60 * 60 * 1000))) : 0;
  const isStaleNew = lead.statut === "NOUVEAU" && daysSinceCreated >= 2;
  // Quick-action visible quand le lead est encore en mode "Nouveau" et n'a pas
  // de conversation lancée (heuristique : pas de RDV non plus). Sinon le bouton
  // doublonne le panel WhatsApp à droite.
  // On masque le CTA hero quand la bannière "importé il y a X jours" porte déjà le même
  // "Lancer WhatsApp" (sinon deux boutons identiques pointant vers #conversation-whatsapp).
  const showLaunchWhatsAppCta = lead.statut === "NOUVEAU" && tachesRdv.length === 0 && !!lead.telephone && !isStaleNew;

  const hasScore = lead.scoring != null || lead.hotnessScore != null;
  const vehicule = [lead.marque, lead.modele].filter(Boolean).join(" ");

  return (
    <>
      {/* ═══════════════════════════════════════════════════════════════
         IDENTITY HEADER, nom XXL + sous-titre vehicule/source + score
         Tachograph ancre a droite. Chips contact en dessous.
         ═══════════════════════════════════════════════════════════════ */}
      <header className="mb-6 border-b border-[var(--border)] pb-6">
        <div className="flex flex-wrap items-end justify-between gap-6">
          <div className="min-w-0 flex-1">
            <h1 className="font-display text-[36px] font-bold leading-[1.02] tracking-display-tight text-[var(--foreground)] break-words sm:text-[44px]">
              {lead.nom || "Prospect"}
            </h1>
            <p className="mt-2 text-[16px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              {vehicule || <span className="italic text-[var(--color-lr-text-tertiary)]">Véhicule non renseigné</span>}
              {lead.source && (
                <>
                  {" "}<span className="text-[var(--color-lr-text-tertiary)]">·</span>{" "}
                  <span className="font-mono text-[13px] uppercase tracking-[0.12em] text-[var(--color-lr-text-tertiary)]">{lead.source}</span>
                </>
              )}
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              {lead.telephone && (
                <span className="inline-flex items-center gap-0.5">
                  <a
                    href={`tel:${lead.telephone}`}
                    className="inline-flex items-center gap-1.5 rounded-full border border-[var(--border)] bg-[var(--card)] px-3 py-2 font-mono text-[12px] text-[var(--foreground)] transition-colors hover:border-[var(--color-accent)]/40 hover:bg-[var(--muted)]"
                    title={`Appeler ${formatPhoneInput(lead.telephone)}`}
                    aria-label={`Appeler le ${formatPhoneInput(lead.telephone)}`}
                  >
                    <svg className="h-3 w-3 text-[var(--color-lr-text-tertiary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
                    </svg>
                    {formatPhoneInput(lead.telephone)}
                  </a>
                  <button
                    onClick={() => copierAuPressePapier("telephone", lead.telephone)}
                    className={`flex h-10 w-10 items-center justify-center rounded-full transition-colors ${
                      copiedField === "telephone"
                        ? "bg-success/10 text-success"
                        : "text-[var(--color-lr-text-tertiary)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                    }`}
                    title={copiedField === "telephone" ? "Copié" : "Copier le numéro"}
                    aria-label="Copier le téléphone"
                  >
                    {copiedField === "telephone" ? (
                      <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
                      </svg>
                    ) : (
                      <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 5H6a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-2M8 5a2 2 0 002 2h2a2 2 0 002-2M8 5a2 2 0 012-2h2a2 2 0 012 2m0 0h2a2 2 0 012 2v3m2 4H10m0 0l3-3m-3 3l3 3" />
                      </svg>
                    )}
                  </button>
                </span>
              )}
              {lead.email && (
                <span className="inline-flex items-center gap-0.5">
                  <a
                    href={`mailto:${lead.email}`}
                    className="inline-flex items-center gap-1.5 rounded-full border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-[12px] text-[var(--foreground)] transition-colors hover:border-[var(--color-accent)]/40 hover:bg-[var(--muted)]"
                    title={`Envoyer un email à ${lead.email}`}
                    aria-label={`Envoyer un email à ${lead.email}`}
                  >
                    <svg className="h-3 w-3 text-[var(--color-lr-text-tertiary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                    </svg>
                    {lead.email}
                  </a>
                  <button
                    onClick={() => copierAuPressePapier("email", lead.email)}
                    className={`flex h-10 w-10 items-center justify-center rounded-full transition-colors ${
                      copiedField === "email"
                        ? "bg-success/10 text-success"
                        : "text-[var(--color-lr-text-tertiary)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                    }`}
                    title={copiedField === "email" ? "Copié" : "Copier l'email"}
                    aria-label="Copier l'email"
                  >
                    {copiedField === "email" ? (
                      <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
                      </svg>
                    ) : (
                      <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 5H6a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-2M8 5a2 2 0 002 2h2a2 2 0 002-2M8 5a2 2 0 012-2h2a2 2 0 012 2m0 0h2a2 2 0 012 2v3m2 4H10m0 0l3-3m-3 3l3 3" />
                      </svg>
                    )}
                  </button>
                </span>
              )}
              {/* Quick-action "Lancer WhatsApp" : visible uniquement si lead NOUVEAU
                  et sans RDV, pour eviter le doublon avec le panel WhatsApp a droite.
                  Le clic scrolle vers la section conversation. */}
              {showLaunchWhatsAppCta && (
                <a
                  href="#conversation-whatsapp"
                  className="btn-primary inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[12px] font-semibold text-white shadow-card"
                >
                  <svg className="h-3 w-3" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51l-.57-.01c-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.263.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347zM12 .5a11.5 11.5 0 100 23 11.5 11.5 0 000-23z"/>
                  </svg>
                  Lancer le WhatsApp
                </a>
              )}
            </div>
          </div>
          {hasScore && (
            <div className="shrink-0 flex flex-col items-end gap-1.5">
              <ScoringGauge size="md" score={lead.scoring} hotnessScore={lead.hotnessScore} />
              {/* Contextualisation sémantique du score : "0.78" sur cadran ne dit
                  rien à un vendeur, "PROSPECT CHAUD" est actionnable (audit
                  provocative question). Couleur = signal d'action immédiate. */}
              {(() => {
                const score = lead.hotnessScore ?? lead.scoring ?? 0;
                if (score >= 0.7) {
                  return (
                    <p className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] text-success">
                      Prospect chaud · rappeler vite
                    </p>
                  );
                }
                if (score >= 0.4) {
                  return (
                    <p className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] text-[var(--color-accent)]">
                      Tiède · à relancer
                    </p>
                  );
                }
                return (
                  <p className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                    Froid · à qualifier
                  </p>
                );
              })()}
            </div>
          )}
        </div>
      </header>

      {/* Bannière "prospect archivé" : ce prospect est sorti des listes actives.
          Ton info/muted, action "Désarchiver" à droite (réversible). */}
      {lead.archive && (
        <div className="mb-6">
          <AlertBand
            tone="info"
            eyebrow="Archivé"
            title="Ce prospect est archivé. Il n'apparaît plus dans vos listes actives."
            icon={<ArchiveIcon className="h-4 w-4" />}
            action={
              <button
                type="button"
                onClick={onDesarchiver}
                disabled={desarchiving}
                className="rounded-lg bg-accent/10 px-3.5 py-2 text-[12.5px] font-semibold text-[var(--color-accent)] transition-colors hover:bg-accent/20 disabled:opacity-50"
              >
                {desarchiving ? "Désarchivage…" : "Désarchiver"}
              </button>
            }
          />
        </div>
      )}

      {/* ═══════════════════════════════════════════════════════════════
         SMART ALERT BAND, affiche uniquement si urgence. Priorité :
         RDV en retard > RDV aujourd'hui > Lead non contacte > rien.
         Source unique : <AlertBand>. Voir frontend/src/components/common/.
         ═══════════════════════════════════════════════════════════════ */}
      {rdvLate && (
        <div className="mb-6">
          <AlertBand
            tone="warning"
            pulse
            eyebrow="RDV à clôturer"
            title={
              <>
RDV du {new Date(rdvLate.dateEcheance).toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" })} à {new Date(rdvLate.dateEcheance).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}.
              </>
            }
            action={
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => onEffectueRdv(rdvLate.id)}
                  className="rounded-lg bg-success/10 px-3.5 py-2 text-[12.5px] font-semibold text-success transition-colors hover:bg-success/20"
                >
                  Effectué
                </button>
                {/* No-show fréquent sur un RDV en retard : "Abandonner" doit être accessible
                    depuis la bannière (seule surface visible sur desktop), pas seulement dans la sidebar. */}
                <button
                  type="button"
                  onClick={() => setAbandonRdvId(rdvLate.id)}
                  className="rounded-lg px-3 py-2 text-[12.5px] font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                >
                  Abandonner
                </button>
              </div>
            }
          />
        </div>
      )}
      {!rdvLate && rdvToday && (
        <div className="mb-6">
          <AlertBand
            tone="accent"
            eyebrow="RDV aujourd'hui"
            icon={
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            }
            title={
              <>
                {new Date(rdvToday.dateEcheance).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}, préparez l&apos;appel.
              </>
            }
            action={
              lead.telephone ? (
                <a
                  href={`tel:${lead.telephone}`}
                  className="btn-primary inline-flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-[12.5px] font-semibold"
                  aria-label={`Appeler ${formatPhoneInput(lead.telephone)}`}
                >
                  <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
                  </svg>
                  Appeler
                </a>
              ) : undefined
            }
          />
        </div>
      )}
      {!rdvLate && !rdvToday && isStaleNew && (
        <div className="mb-6">
          <AlertBand
            tone="warning"
            eyebrow="Lead pas encore contacté"
            icon={
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            }
            title={<>Importé il y a {daysSinceCreated} jours, lancez la conversation.</>}
            action={
              <a
                href="#conversation-whatsapp"
                className="btn-primary inline-flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-[12.5px] font-semibold"
              >
                <svg className="h-3.5 w-3.5" fill="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51l-.57-.01c-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.263.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347zM12 .5a11.5 11.5 0 100 23 11.5 11.5 0 000-23z"/>
                </svg>
                Lancer WhatsApp
              </a>
            }
          />
        </div>
      )}

      {/* Envoi automatique du premier message WhatsApp en attente : bandeau
          INFORMATIF calme (≠ AlertBand, réservé aux alertes fortes). Ton neutre
          pour ne pas ressembler à une erreur/urgence. Actions à droite. */}
      {lead.premierMessageAutoPrevuLe && (
        <div
          className="mb-6 flex items-center gap-3 rounded-xl border border-[var(--border)] bg-[var(--muted)] px-4 py-3"
          role="status"
          aria-live="polite"
        >
          <span
            className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[var(--card)] text-[var(--color-lr-text-secondary)]"
            aria-hidden="true"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </span>
          <p className="min-w-0 flex-1 text-[13.5px] text-[var(--color-lr-text-secondary)]">
            <span className="font-medium text-[var(--foreground)]">Premier message automatique</span>{" "}
            prévu {formatEnvoiAutoLabel(lead.premierMessageAutoEffectifLe, lead.premierMessageAutoPrevuLe)}
            {lead.premierMessageAutoHorsPlage && " (reporté car hors plage d'envoi)"}.
          </p>
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              onClick={handleEnvoyerMaintenant}
              disabled={envoiAutoLoading !== null}
              className="btn-primary inline-flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-[12.5px] font-semibold disabled:opacity-60"
            >
              {envoiAutoLoading === "envoyer" ? "Envoi…" : "Envoyer maintenant"}
            </button>
            <button
              type="button"
              onClick={handleAnnulerEnvoiAuto}
              disabled={envoiAutoLoading !== null}
              className="rounded-lg px-3 py-2 text-[12.5px] font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--card)] hover:text-[var(--foreground)] disabled:opacity-60"
            >
              {envoiAutoLoading === "annuler" ? "Annulation…" : "Annuler"}
            </button>
          </div>
        </div>
      )}

      {/* ═══════════════════════════════════════════════════════════════
         MAIN GRID, 8/4 sur desktop. Brief IA puis Conversation a gauche.
         Sidebar sticky a droite avec Pilotage / Vehicule / RDV / Detail.
         ═══════════════════════════════════════════════════════════════ */}
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-12">

        {/* COL MAIN, workflow vendeur. order-2 sur mobile pour que la sidebar
            (Pilotage, contact, RDV) soit visible en premier au scroll mobile :
            le vendeur a besoin du statut + contact + RDV AVANT le brief IA long. */}
        <div className="order-2 space-y-5 lg:order-1 lg:col-span-8">

          {/* ━━━ Brief de préparation IA ━━━ */}
          <DetailCard
            padding="lg"
            header={
              <SectionHeader
                eyebrow="Brief de préparation"
                eyebrowTone="accent"
                title="Préparez l'appel"
                bordered
                actions={
                  <>
                    {fiche && (
                      <button
                        onClick={handleCopyFiche}
                        className="rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-1.5 text-[12px] font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                      >
                        {copyMessage || "Copier"}
                      </button>
                    )}
                    <button
                      onClick={handleGenerateFiche}
                      disabled={ficheLoading}
                      className="btn-primary inline-flex items-center gap-1.5 rounded-lg px-3.5 py-1.5 text-[12.5px] font-semibold disabled:opacity-50"
                    >
                      {ficheLoading ? "Génération…" : fiche ? "Régénérer" : "Générer le brief"}
                    </button>
                  </>
                }
              />
            }
          >
            {ficheError && (
              <div className="mb-4 rounded-lg border border-danger/30 bg-danger/10 p-3 text-sm text-danger">{ficheError}</div>
            )}

            {!fiche && !ficheLoading && !ficheError && (
              <EmptyState
                title="Aucun brief généré pour ce prospect."
                description="L'IA analyse le profil du prospect (véhicule, source, conversation) et vous prépare un briefing prêt à l'emploi pour l'appel : accroche, projet, points à découvrir, stratégie."
                visual={
                  <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--color-accent-subtle)]">
                    <svg className="h-5 w-5 text-[var(--color-accent)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                  </div>
                }
                align="center"
                size="sm"
              />
            )}

            {ficheLoading && (
              <div className="flex flex-col items-center justify-center px-6 py-12">
                <SpeedLines label="L'IA analyse le profil…" />
              </div>
            )}

            {fiche && !ficheLoading && (
              <FichePreparationCard ficheRaw={fiche.fiche} lead={lead} vendeurNom={vendeurNom} />
            )}
          </DetailCard>

          {/* ━━━ Conversation WhatsApp ━━━ */}
          <DetailCard
            id="conversation-whatsapp"
            className="scroll-mt-6"
            padding="none"
            header={
              <SectionHeader
                eyebrow="Conversation"
                eyebrowTone="success"
                title="Échanges WhatsApp"
                bordered
                actions={
                  <Link
                    href="/inbox"
                    className="font-mono text-[11px] font-semibold uppercase tracking-[0.16em] text-[var(--color-accent)] transition-colors hover:text-[var(--color-accent-deep)]"
                  >
                    Voir toutes les conversations →
                  </Link>
                }
              />
            }
          >
            {/* La hauteur est désormais gérée PAR WhatsAppPanel (conditionnelle à la
                présence d'une conversation) : haute + scroll interne s'il y a une
                conversation, compacte sinon. Le parent ne force plus de hauteur pour
                ne pas créer un gouffre vide quand il n'y a pas encore d'échange. */}
            <div className="p-5">
              <WhatsAppPanel leadId={Number(id)} telephone={lead.telephone} refreshKey={whatsappRefreshKey} />
            </div>
          </DetailCard>
        </div>

        {/* COL SIDEBAR, context overview. order-1 sur mobile = visible
            en premier. Sticky desktop seulement. */}
        <aside className="order-1 space-y-4 lg:order-2 lg:col-span-4 lg:sticky lg:top-6 lg:self-start">

          {/* ★ Pilotage : statut + vendeur, la zone d'action principale */}
          <DetailCard padding="md">
            <p className="eyebrow-muted">
              Pilotage
            </p>
            <div className="mt-3 space-y-3">
              <div className="flex items-center gap-3">
                <label className="w-16 shrink-0 font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-[var(--color-lr-text-secondary)]">
                  Statut
                </label>
                <select
                  value={editStatut}
                  onChange={(e) => {
                    const next = e.target.value;
                    // Saut > 1 étape vers TERMINE : ouvrir un Modal éditorial
                    // (au lieu de window.confirm natif banni par DESIGN.md).
                    const fromIdx = STATUT_ORDER[editStatut] ?? 0;
                    const toIdx = STATUT_ORDER[next] ?? 0;
                    if (Math.abs(toIdx - fromIdx) >= 2 && next === "TERMINE") {
                      setPendingStatut(next);
                      return;
                    }
                    setEditStatut(next);
                    handleSaveStatut(next);
                  }}
                  disabled={saving}
                  className="flex-1 rounded-lg border border-[var(--border)] bg-[var(--background)] px-2.5 py-1.5 text-[13px] text-[var(--foreground)] transition-colors focus:border-[var(--color-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--color-accent)]/30 disabled:opacity-50"
                  aria-label="Statut du prospect"
                >
                  {STATUTS.map((s) => <option key={s} value={s}>{STATUT_LABELS[s] ?? s}</option>)}
                </select>
              </div>
              {isManager && vendeurs.length > 0 && (
                <div className="flex items-center gap-3">
                  <label className="w-16 shrink-0 font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-[var(--color-lr-text-secondary)]">
                    Vendeur
                  </label>
                  <select
                    value={lead.vendeurId != null ? String(lead.vendeurId) : ""}
                    onChange={(e) => handleAssignVendeur(e.target.value)}
                    disabled={vendeurSaving}
                    className="flex-1 rounded-lg border border-[var(--border)] bg-[var(--background)] px-2.5 py-1.5 text-[13px] text-[var(--foreground)] transition-colors focus:border-[var(--color-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--color-accent)]/30 disabled:opacity-50"
                  >
                    <option value="">Non assigné</option>
                    {vendeurs.map((v) => (
                      <option key={v.id} value={String(v.id)}>{v.prenom} {v.nom}</option>
                    ))}
                  </select>
                </div>
              )}
            </div>
            <div className="mt-3 flex items-baseline justify-between border-t border-[var(--border)] pt-2.5">
              <span className="eyebrow-muted">
                Importé
              </span>
              <span className="font-numeric text-[11.5px] text-[var(--color-lr-text-secondary)]">
                {lead.creeLe ? new Date(lead.creeLe).toLocaleDateString("fr-FR", { day: "2-digit", month: "short", year: "2-digit" }) : "—"}
              </span>
            </div>
            {/* Archivage / désarchivage du prospect (réversible). Le libellé et
                l'action dépendent de l'état d'archivage du prospect. */}
            <div className="mt-2 border-t border-[var(--border)] pt-2.5">
              {lead.archive ? (
                <button
                  type="button"
                  onClick={onDesarchiver}
                  disabled={desarchiving}
                  className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-[12px] font-medium text-[var(--color-accent)] transition-colors hover:bg-accent/10 disabled:opacity-50"
                  aria-label="Désarchiver ce prospect"
                >
                  <ArchiveIcon className="h-3.5 w-3.5" />
                  {desarchiving ? "Désarchivage…" : "Désarchiver ce prospect"}
                </button>
              ) : (
                <button
                  type="button"
                  onClick={onArchiver}
                  className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-[12px] font-medium text-[var(--color-lr-text-tertiary)] transition-colors hover:bg-danger/10 hover:text-danger"
                  aria-label="Archiver ce prospect"
                >
                  <ArchiveIcon className="h-3.5 w-3.5" />
                  Archiver ce prospect
                </button>
              )}
            </div>
          </DetailCard>

          {/* ★ Véhicule : fiche technique data-sheet */}
          <DetailCard padding="md">
            <p className="eyebrow-muted">
              Véhicule
            </p>
            <h3 className="display-h2 mt-1.5 text-[var(--foreground)]">
              {vehicule || "Non renseigné"}
            </h3>
            {lead.territoire && (
              <p className="mt-0.5 text-[12px] text-[var(--color-lr-text-secondary)]">{lead.territoire}</p>
            )}

            {lead.donneesSupplementaires && (() => {
              const rows = [
                { key: "kilometrage", label: "Km", format: (v: string | number | boolean) => typeof v === "number" ? v.toLocaleString("fr-FR") : String(v) },
                { key: "annee", label: "Année", format: (v: string | number | boolean) => String(v) },
                { key: "energie", label: "Énergie", format: (v: string | number | boolean) => String(v) },
                { key: "boiteVitesse", label: "Boîte", format: (v: string | number | boolean) => String(v) },
              ]
                .map((spec) => {
                  const val = lead.donneesSupplementaires?.[spec.key];
                  if (val == null || val === "") return null;
                  return { label: spec.label, value: spec.format(val) };
                })
                .filter((r): r is { label: string; value: string } => r !== null);
              if (rows.length === 0) return null;
              return (
                <div className="mt-3 border-t border-[var(--border)] pt-2">
                  <DataSheet rows={rows} variant="nude" />
                </div>
              );
            })()}

            {lead.donneesSupplementaires && Object.keys(lead.donneesSupplementaires).length > 4 && (
              <details className="mt-3 border-t border-[var(--border)] pt-3">
                <summary className="cursor-pointer eyebrow-muted transition-colors hover:text-[var(--color-accent)]">
                  Tous les détails
                </summary>
                <dl className="mt-3 space-y-0">
                  {Object.entries(lead.donneesSupplementaires).map(([key, val]) => {
                    if (["kilometrage", "annee", "energie", "boiteVitesse"].includes(key)) return null;
                    const labels: Record<string, string> = {
                      immatriculation: "Immat.", vin: "VIN", dateMiseEnCirculation: "Mise en circ.",
                      couleurExterieure: "Couleur ext.", couleurInterieure: "Couleur int.",
                      puissanceFiscale: "CV fiscaux", puissanceCh: "Puissance", carrosserie: "Carrosserie",
                      finition: "Finition", version: "Version", premiereMain: "1ère main",
                      vehiculeImporte: "Importé", entretienAJour: "Entretien", modeFinancement: "Financement",
                      intentionAchat: "Intention", numeroDossier: "N° dossier", commentaireProspect: "Message",
                      pointDeVente: "Point de vente", adresseProspect: "Adresse", civilite: "Civilité",
                      prixMarche: "Prix marché", prixReprise: "Prix reprise", prixVente: "Prix vente",
                      referenceAnnonce: "Réf. annonce", typeTransaction: "Transaction", sourceDetaillee: "Source",
                    };
                    let display = typeof val === "boolean" ? (val ? "Oui" : "Non") : String(val);
                    if (key.includes("prix") && typeof val === "number") display = val.toLocaleString("fr-FR") + " €";
                    return (
                      <div key={key} className="flex items-baseline justify-between gap-3 border-b border-[var(--border)] py-2 last:border-b-0">
                        <dt className="shrink-0 eyebrow-muted">
                          {labels[key] || key}
                        </dt>
                        <dd className="truncate text-right text-[12px] font-medium text-[var(--foreground)]" title={display}>
                          {display}
                        </dd>
                      </div>
                    );
                  })}
                </dl>
              </details>
            )}
          </DetailCard>

          {/* ★ Rendez-vous — la bannière AlertBand en tête = ALERTE (1 action
              "Effectué") pour le RDV en retard / du jour. La carte sidebar porte
              la GESTION complete (Effectué / Reporter / Abandonner). Le RDV en
              retard est donc volontairement ré-affiché ici avec ses 3 actions
              (le renvoyer vers /planning cassait le contexte). Seul le RDV
              d'aujourd'hui reste masqué (la bannière le couvre avec "Appeler"). */}
          {(() => {
            const sidebarRdvs = tachesRdv.filter((t) => {
              const dt = new Date(t.dateEcheance);
              const isDone = t.statut === "TERMINEE";
              const isToday = dt.toDateString() === todayStr;
              // On garde : terminés (historique) + futurs + retard non-clos (avec
              // ses 3 actions). On masque le RDV du jour UNIQUEMENT si la bannière
              // peut le couvrir, c.-à-d. si le lead a un numéro (action "Appeler").
              // Sans téléphone, la bannière n'a pas d'action : on ré-affiche le RDV
              // du jour ici pour ne pas perdre Effectué / Reporter / Abandonner.
              return isDone || !isToday || !lead.telephone;
            });
            const showSection = sidebarRdvs.length > 0 || sectionErrors.tachesRdv;
            if (!showSection) return null;
            return (
            <DetailCard padding="md">
              <div className="flex items-center justify-between">
                <p className="eyebrow-muted">
                  Rendez-vous
                </p>
                <Link href="/planning" className="font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-[var(--color-accent)] transition-colors hover:text-[var(--color-accent-deep)]">
                  Planning →
                </Link>
              </div>
              {sectionErrors.tachesRdv && sidebarRdvs.length === 0 && (
                <p className="mt-3 text-[12px] italic text-[var(--color-lr-text-tertiary)]">
                  RDV indisponibles, réessayez en rafraîchissant la page.
                </p>
              )}
              <div className="mt-3 space-y-2">
                {sidebarRdvs.map((t) => {
                  const date = new Date(t.dateEcheance);
                  const isPast = date < new Date();
                  const isDone = t.statut === "TERMINEE";
                  const isToday = date.toDateString() === todayStr;
                  return (
                    <div key={t.id} className={`rounded-lg border p-3 ${isPast && !isDone ? "border-warning/30 bg-warning/5" : isToday && !isDone ? "border-[var(--color-accent)]/30 bg-[var(--color-accent-subtle)]" : "border-[var(--border)]"}`}>
                      <div className="flex items-baseline justify-between gap-2">
                        <span className={`font-numeric text-[13.5px] font-semibold ${isPast && !isDone ? "text-warning" : isDone ? "text-[var(--color-lr-text-tertiary)] line-through" : "text-[var(--foreground)]"}`}>
                          {date.toLocaleDateString("fr-FR", { weekday: "short", day: "2-digit", month: "short" })} · {date.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}
                        </span>
                        {isDone && <span className="font-mono text-[9px] uppercase tracking-wider text-success">Effectué</span>}
                        {isPast && !isDone && <span className="inline-flex items-center rounded-full bg-warning/15 px-2 py-0.5 font-mono text-[9px] uppercase tracking-wider text-warning">À clôturer</span>}
                        {isToday && !isDone && !isPast && <span className="font-mono text-[9px] uppercase tracking-wider text-[var(--color-accent)]">Auj.</span>}
                      </div>
                      {!isDone && (isPast || isToday) && (
                        <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
                          {/* "Effectué" = action canonique du vendeur, en vert subtil
                              (bg-success/10) uniforme sur toutes les surfaces RDV. Pas
                              de fond plein de statut (One Attention Rule). */}
                          <button
                            onClick={() => onEffectueRdv(t.id)}
                            disabled={rdvActionLoading != null}
                            className="rounded-md bg-success/10 px-3 py-2 text-[11px] font-semibold text-success transition-colors hover:bg-success/20 disabled:opacity-50"
                          >
                            Effectué
                          </button>
                          <button
                            onClick={() => setRdvReportModal(t.id)}
                            disabled={rdvActionLoading != null}
                            className="rounded-md border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-[11px] font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] disabled:opacity-50"
                          >
                            Reporter
                          </button>
                          {isPast && (
                            <button
                              onClick={() => setAbandonRdvId(t.id)}
                              disabled={rdvActionLoading != null}
                              className="rounded-md px-2.5 py-2 text-[11px] font-medium text-[var(--color-lr-text-tertiary)] transition-colors hover:bg-danger/5 hover:text-danger disabled:opacity-50"
                            >
                              Abandonner
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </DetailCard>
            );
          })()}

          {/* ★ Détails commerciaux, repliés par défaut */}
          {(lead.etape || lead.devis || lead.typeOpportunite) && (
            <details className="rounded-2xl border border-[var(--border)] bg-[var(--card)] p-5">
              <summary className="cursor-pointer eyebrow-muted transition-colors hover:text-[var(--color-accent)]">
                Détails commerciaux
              </summary>
              <div className="mt-3">
                <DataSheet
                  variant="nude"
                  rows={[
                    lead.etape ? { label: "Étape", value: lead.etape, mono: false } : null,
                    lead.devis ? { label: "Devis", value: lead.devis, mono: false } : null,
                    lead.reprise != null ? { label: "Reprise", value: lead.reprise ? "Oui" : "Non", mono: false } : null,
                    lead.typeOpportunite ? { label: "Type", value: lead.typeOpportunite, mono: false } : null,
                  ].filter((r): r is { label: string; value: string; mono: false } => r !== null)}
                />
              </div>
            </details>
          )}
        </aside>
      </div>
    </>
  );
}

// Fiche de preparation extraite dans components/leads/FichePreparationCard.tsx (Epic 10)

