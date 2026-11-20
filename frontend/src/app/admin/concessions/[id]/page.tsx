"use client";

import { useCallback, useEffect, useState, type ReactNode } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useApiClient, formatErreur } from "@/lib/auth";
import { formatPhoneInput } from "@/lib/phone";
import Alert from "@/components/common/Alert";
import Button from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import Modal from "@/components/common/Modal";
import PageHeader from "@/components/common/PageHeader";
import { SkeletonGrid } from "@/components/common/Skeleton";
import StatutBadge from "@/components/common/StatutBadge";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface WhatsAppDetail {
  statut: string | null;
  whatsappFrom: string | null;
  displayName: string | null;
  qualityRating: string | null;
  messagingLimit: string | null;
  metaStatus: string | null;
  metaRejectReason: string | null;
  twilioSenderSid: string | null;
  dateDerniereSynchroMeta: string | null;
  modifieLe: string | null;
}

interface NumeroHistorique {
  whatsappFrom: string;
  associeLe: string;
  dissocieLe: string | null;
  raisonDissociation: string | null;
}

interface Utilisateur {
  id: number;
  prenom: string;
  nom: string;
  email: string;
  role: string;
  statut: string;
  derniereConnexion: string | null;
}

interface Compteurs {
  leadsTotal: number;
  conversationsActives: number;
  rdvSemaine: number;
  messagesEnvoyesTotal: number;
}

interface AuditAdmin {
  id: number;
  adminUserId: number;
  action: string;
  justification: string;
  creeLe: string;
}

interface ConcessionDetail {
  id: number;
  nom: string;
  creeLe: string | null;
  clerkOrganizationId: string | null;
  whatsapp: WhatsAppDetail | null;
  historiqueNumeros: NumeroHistorique[];
  utilisateurs: Utilisateur[];
  compteurs: Compteurs;
  auditsAdmin: AuditAdmin[];
}

interface EmailImportConfig {
  id: number;
  emailAddress: string;
  statut: string;
  nbEmailsRecus: number;
  dernierEmailRecuLe: string | null;
  anciennesAdresses: { adresse: string; remplaceeLe: string }[];
  stats: {
    nbEmailsRecus: number;
    nbLeadsCrees: number;
    nbLeadsDedupliques: number;
    nbParseEchec: number;
    nbRejetSpam: number;
    tauxParsingOk: number;
  } | null;
}

interface InviteForm {
  prenom: string;
  nom: string;
  email: string;
}

const EMPTY_INVITE: InviteForm = { prenom: "", nom: "", email: "" };

export default function ConcessionDetailPage() {
  const params = useParams();
  const router = useRouter();
  const apiFetch = useApiClient();
  const id = String(params?.id ?? "");

  const [detail, setDetail] = useState<ConcessionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [actionEnCours, setActionEnCours] = useState(false);

  // Modale invitation (manager ou vendeur, selon inviteRole)
  const [showInviteModal, setShowInviteModal] = useState(false);
  const [inviteForm, setInviteForm] = useState<InviteForm>(EMPTY_INVITE);
  const [inviteRole, setInviteRole] = useState<"MANAGER" | "VENDEUR">("MANAGER");
  const [inviting, setInviting] = useState(false);

  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [confirmNomTape, setConfirmNomTape] = useState("");
  const [deleteError, setDeleteError] = useState("");

  // Modale de confirmation generique (remplace les `confirm()` natifs du navigateur
  // pour les actions destructives non-radicales : desactiver/regenerer adresse email,
  // re-pousser profil WhatsApp, retirer un manager).
  const [confirmAction, setConfirmAction] = useState<{
    title: string;
    body: ReactNode;
    buttonLabel: string;
    buttonVariant?: "danger" | "primary";
    onConfirm: () => Promise<void>;
  } | null>(null);
  const [confirmRunning, setConfirmRunning] = useState(false);
  const [confirmError, setConfirmError] = useState("");

  async function executerConfirm() {
    if (!confirmAction) return;
    setConfirmRunning(true);
    setConfirmError("");
    try {
      await confirmAction.onConfirm();
      setConfirmAction(null);
    } catch (err) {
      setConfirmError(formatErreur(err));
    } finally {
      setConfirmRunning(false);
    }
  }

  function fermerConfirm() {
    if (confirmRunning) return;
    setConfirmAction(null);
    setConfirmError("");
  }

  // Config email d'import (epic 24)
  const [emailConfig, setEmailConfig] = useState<EmailImportConfig | null>(null);
  const [emailConfigLoaded, setEmailConfigLoaded] = useState(false);
  const [emailActionEnCours, setEmailActionEnCours] = useState(false);

  /**
   * Recharge le detail. Le parametre {@code silent} evite d'afficher le squelette
   * (utile pour les refetch declenches apres une action user : on veut juste
   * rafraichir les chiffres, pas faire clignoter toute la page).
   */
  const fetchDetail = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/concessions/${id}/details`);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setDetail(await res.json());
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      if (!silent) setLoading(false);
    }
  }, [apiFetch, id]);

  const fetchEmailConfig = useCallback(async () => {
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/concessions/${id}/email-config`);
      if (res.status === 404) {
        setEmailConfig(null);
      } else if (res.ok) {
        setEmailConfig(await res.json());
      }
    } catch {
      // Silent : la section reste cachee si erreur
    } finally {
      setEmailConfigLoaded(true);
    }
  }, [apiFetch, id]);

  async function creerEmailConfig() {
    setEmailActionEnCours(true);
    setError("");
    setInfo("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/concessions/${id}/email-config`, {
        method: "POST",
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Erreur ${res.status}`);
      }
      setInfo("Adresse email d'import créée.");
      await fetchEmailConfig();
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setEmailActionEnCours(false);
    }
  }

  function desactiverEmailConfig() {
    setConfirmAction({
      title: "Désactiver l'adresse email d'import",
      body: "Les emails futurs reçus à cette adresse seront rejetés. Vous pourrez en regénérer une nouvelle plus tard.",
      buttonLabel: "Désactiver",
      buttonVariant: "danger",
      onConfirm: async () => {
        setEmailActionEnCours(true);
        setError("");
        setInfo("");
        try {
          const res = await apiFetch(`${API_BASE}/api/admin/concessions/${id}/email-config`, {
            method: "DELETE",
          });
          if (!res.ok) {
            const text = await res.text();
            throw new Error(text || `Erreur ${res.status}`);
          }
          setInfo("Adresse email désactivée.");
          await fetchEmailConfig();
        } finally {
          setEmailActionEnCours(false);
        }
      },
    });
  }

  function regenererEmailConfig() {
    setConfirmAction({
      title: "Régénérer l'adresse email d'import",
      body: (
        <>
          <p>L&apos;ancienne adresse sera <strong>immédiatement invalidée</strong>.</p>
          <p className="mt-2">
            Le manager devra mettre à jour la redirection email côté boîte mail
            (Gmail / Outlook / OVH...).
          </p>
          <p className="mt-2 text-[var(--color-lr-text-secondary)]">
            L&apos;historique des emails reçus est conservé.
          </p>
        </>
      ),
      buttonLabel: "Régénérer",
      buttonVariant: "danger",
      onConfirm: async () => {
        setEmailActionEnCours(true);
        setError("");
        setInfo("");
        try {
          const res = await apiFetch(`${API_BASE}/api/admin/concessions/${id}/email-config/regenerer`, {
            method: "POST",
          });
          if (!res.ok) {
            const text = await res.text();
            throw new Error(text || `Erreur ${res.status}`);
          }
          setInfo("Nouvelle adresse email générée. Pensez à prévenir le manager pour mettre à jour la redirection.");
          await fetchEmailConfig();
        } finally {
          setEmailActionEnCours(false);
        }
      },
    });
  }

  useEffect(() => {
    void fetchDetail();
    void fetchEmailConfig();
  }, [fetchDetail, fetchEmailConfig]);

  // rePousserProfil() supprime — le re-push profil via Twilio Senders API
  // n'est plus pertinent depuis le sunset LEGACY (V064). Le profil Meta Business
  // est gere directement par le manager via Facebook Embedded Signup.

  async function synchroniserStatutTwilio() {
    setActionEnCours(true);
    setError("");
    setInfo("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/concessions/${id}/synchroniser-statut-twilio`, {
        method: "POST",
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Erreur ${res.status}`);
      }
      setInfo("Statut Twilio rafraîchi. Les valeurs ci-dessous reflètent l'état actuel chez Twilio.");
      await fetchDetail(true);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setActionEnCours(false);
    }
  }

  function ouvrirConfirmationDesactivation() {
    if (!detail) return;
    setConfirmNomTape("");
    setDeleteError("");
    setShowDeleteModal(true);
  }

  async function confirmerDesactivation() {
    if (!detail) return;
    if (confirmNomTape.trim() !== detail.nom.trim()) return;
    setActionEnCours(true);
    setDeleteError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/concessions/${id}`, { method: "DELETE" });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Erreur ${res.status}`);
      }
      router.push("/admin/concessions");
    } catch (err) {
      // Erreur affichee DANS la modale : on ne perd pas le contexte (nom tape,
      // intention de l'admin). L'utilisateur peut corriger et reessayer.
      setDeleteError(formatErreur(err));
      setActionEnCours(false);
    }
  }

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    setInviting(true);
    setError("");
    try {
      const endpoint = inviteRole === "MANAGER" ? "managers" : "vendeurs";
      const libelle = inviteRole === "MANAGER" ? "Manager" : "Vendeur";
      const res = await apiFetch(`${API_BASE}/api/admin/concessions/${id}/${endpoint}`, {
        method: "POST",
        body: JSON.stringify(inviteForm),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Erreur ${res.status}`);
      }
      setShowInviteModal(false);
      setInviteForm(EMPTY_INVITE);
      setInfo(`${libelle} invité : ${inviteForm.email}. Il recevra un email de Clerk pour rejoindre l'établissement.`);
      await fetchDetail(true);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setInviting(false);
    }
  }

  function ouvrirInvitation(role: "MANAGER" | "VENDEUR") {
    setInviteRole(role);
    setInviteForm(EMPTY_INVITE);
    setShowInviteModal(true);
  }

  async function retirerUtilisateur(u: Utilisateur) {
    const isManager = u.role === "MANAGER";
    const libelleRole = isManager ? "manager" : "vendeur";
    const endpoint = isManager ? "managers" : "vendeurs";

    // Pour les VENDEUR, on recupere d'abord l'impact (leads / conversations / RDV)
    // pour afficher dans la modale ce qui sera transfere et a qui. Pour les MANAGER,
    // l'endpoint impact n'existe pas et le garde-fou "dernier manager" s'applique
    // cote backend.
    type Impact = {
      desactivationPossible: boolean;
      raisonBlocage?: string;
      nbLeadsAssignes?: number;
      nbConversationsActives?: number;
      nbTachesNonTerminees?: number;
      nbRdvAVenir?: number;
      destinataireNom?: string;
    };
    let impact: Impact | null = null;
    if (!isManager) {
      try {
        const impactRes = await apiFetch(
          `${API_BASE}/api/admin/concessions/${id}/vendeurs/${u.id}/impact-desactivation`
        );
        if (impactRes.ok) {
          impact = await impactRes.json() as Impact;
          if (impact.desactivationPossible === false) {
            setError(impact.raisonBlocage || "Désactivation impossible.");
            return;
          }
        }
      } catch {
        // Si l'impact echoue, on continue avec le DELETE, le backend
        // renverra l'erreur claire si necessaire.
      }
    }

    const lignesImpact: string[] = [];
    if (impact) {
      if ((impact.nbLeadsAssignes ?? 0) > 0) {
        lignesImpact.push(`${impact.nbLeadsAssignes} lead(s) passeront en non-assignés (visibles par les managers).`);
      }
      if ((impact.nbConversationsActives ?? 0) > 0) {
        lignesImpact.push(`${impact.nbConversationsActives} conversation(s) en cours seront réassignées à ${impact.destinataireNom}.`);
      }
      if ((impact.nbTachesNonTerminees ?? 0) > 0) {
        const rdvNote = (impact.nbRdvAVenir ?? 0) > 0 ? ` (dont ${impact.nbRdvAVenir} RDV à venir)` : "";
        lignesImpact.push(`${impact.nbTachesNonTerminees} tâche(s) non terminée(s)${rdvNote} seront réassignées à ${impact.destinataireNom}.`);
      }
    }

    setConfirmAction({
      title: `Retirer ce ${libelleRole} de l'équipe`,
      body: (
        <>
          <p>
            <strong>{u.prenom} {u.nom}</strong> ({u.email}) sera retiré
            de l&apos;établissement.
          </p>
          {lignesImpact.length > 0 && (
            <ul className="mt-2 list-disc pl-5 text-[var(--color-lr-text-secondary)]">
              {lignesImpact.map((ligne, i) => <li key={i}>{ligne}</li>)}
            </ul>
          )}
          <p className="mt-2 text-[var(--color-lr-text-secondary)]">
            Son accès est révoqué côté Clerk et son compte local est anonymisé pour
            libérer l&apos;adresse email en cas de réinvitation future.
          </p>
        </>
      ),
      buttonLabel: "Retirer",
      buttonVariant: "danger",
      onConfirm: async () => {
        setActionEnCours(true);
        setError("");
        try {
          // Pour les vendeurs, on declenche le mode reassigner (le manager appelant
          // recupere conversations/taches/RDV, les leads passent en non-assignes).
          // Pour les managers, garde-fou backend "dernier manager" applicable.
          const reassignerParam = !isManager ? "?reassigner=true" : "";
          const res = await apiFetch(
            `${API_BASE}/api/admin/concessions/${id}/${endpoint}/${u.id}${reassignerParam}`,
            { method: "DELETE" }
          );
          if (!res.ok) {
            const text = await res.text();
            throw new Error(text || `Erreur ${res.status}`);
          }
          setInfo(`${u.prenom} ${u.nom} retiré de l'équipe.`);
          await fetchDetail(true);
        } finally {
          setActionEnCours(false);
        }
      },
    });
  }

  // Equipe = managers + vendeurs, dans un seul tableau, managers d'abord puis
  // vendeurs (ordre alphabetique a l'interieur de chaque role).
  const equipe = detail
    ? [...detail.utilisateurs].sort((a, b) => {
        if (a.role !== b.role) return a.role === "MANAGER" ? -1 : 1;
        return `${a.nom} ${a.prenom}`.localeCompare(`${b.nom} ${b.prenom}`, "fr");
      })
    : [];

  return (
    <div className="p-8 space-y-6">
        {/* Header rendu en permanence : visible dès le 1er paint (skeleton, erreur
            et contenu chargé). Jamais d'early-return avant ce header. */}
        <PageHeader
          eyebrow="Admin"
          title={detail?.nom ?? "Établissement"}
          subtitle={
            detail?.creeLe
              ? `Créé le ${new Date(detail.creeLe).toLocaleDateString("fr-FR")}`
              : undefined
          }
          actions={
            <Link
              href="/admin/concessions"
              className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"
            >
              ← Retour à la liste
            </Link>
          }
        />

        {/* Skeleton sous le header pendant le chargement initial */}
        {loading && <SkeletonGrid count={3} />}

        {/* Erreur sans données : bloc d'alerte + retour */}
        {!loading && !detail && (
          <div>
            <Alert tone="danger">{error || "Établissement introuvable."}</Alert>
            <div className="mt-4">
              <Button variant="ghost" onClick={() => router.push("/admin/concessions")}>← Retour</Button>
            </div>
          </div>
        )}

        {detail && (
          <>
        {error && <Alert tone="danger">{error}</Alert>}
        {info && <Alert tone="success">{info}</Alert>}

        {/* Barre d'actions principales, placee tout en haut pour etre accessible immediatement */}
        <div className="flex flex-wrap items-center gap-2 rounded-lg border border-[var(--border)] bg-[var(--card)] p-3">
          <Button onClick={() => ouvrirInvitation("MANAGER")} disabled={actionEnCours}>
            <span className="inline-flex items-center gap-1.5">
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Inviter un manager
            </span>
          </Button>
          <Button variant="secondary" onClick={() => ouvrirInvitation("VENDEUR")} disabled={actionEnCours}>
            <span className="inline-flex items-center gap-1.5">
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              Inviter un vendeur
            </span>
          </Button>
          {detail.whatsapp && (
            <>
              <Button variant="secondary" onClick={synchroniserStatutTwilio} disabled={actionEnCours}>
                <span className="inline-flex items-center gap-1.5">
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 4l4 4-4 4M20 8H10m-2 4l-4 4 4 4M4 16h10" />
                  </svg>
                  Rafraîchir le statut Twilio
                </span>
              </Button>
            </>
          )}
          <div className="flex-1" />
          <Button variant="danger" onClick={ouvrirConfirmationDesactivation} disabled={actionEnCours}>
            <span className="inline-flex items-center gap-1.5">
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
              </svg>
              Désactiver l&apos;établissement
            </span>
          </Button>
        </div>

        {/* Compteurs avec stagger entry (40ms entre cards) */}
        <section className="grid grid-cols-2 sm:grid-cols-4 gap-3 stagger-children">
          <KpiCard label="Leads importés" value={detail.compteurs.leadsTotal} mono />
          <KpiCard label="Conversations actives" value={detail.compteurs.conversationsActives} mono valueColor="text-accent" />
          <KpiCard label="RDV 7 jours" value={detail.compteurs.rdvSemaine} mono valueColor="text-success" />
          <KpiCard label="Conversations totales" value={detail.compteurs.messagesEnvoyesTotal} mono />
        </section>

        {/* WhatsApp Business (mount delayed 150ms après compteurs) */}
        <section className="card-leadrive p-5 animate-card-mount-delayed">
          <h2 className="font-display text-[16px] font-bold tracking-display-tight text-[var(--foreground)] mb-3">WhatsApp Business</h2>
          {!detail.whatsapp ? (
            <p className="text-sm text-[var(--color-lr-text-secondary)]">
              Aucune configuration WhatsApp pour cet établissement.
            </p>
          ) : (
            <>
              {/* Vue d'ensemble : les 3 infos qui parlent au manager/admin sans technique. */}
              <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <DescriptionItem label="Statut" value={detail.whatsapp.statut || "—"} />
                <DescriptionItem
                  label="Numéro"
                  value={detail.whatsapp.whatsappFrom ? formatPhoneInput(detail.whatsapp.whatsappFrom) : "—"}
                />
                <DescriptionItem label="Nom affiché" value={detail.whatsapp.displayName || "—"} />
                <DescriptionItem
                  label="Dernière synchro"
                  value={
                    detail.whatsapp.dateDerniereSynchroMeta
                      ? new Date(detail.whatsapp.dateDerniereSynchroMeta).toLocaleString("fr-FR")
                      : "—"
                  }
                />
                {detail.whatsapp.metaRejectReason && (
                  <div className="sm:col-span-2">
                    <OfflineReasonsBlock
                      metaStatus={detail.whatsapp.metaStatus}
                      statut={detail.whatsapp.statut}
                      rawReason={detail.whatsapp.metaRejectReason}
                    />
                  </div>
                )}
              </dl>

              {/* Diagnostic technique repliable : tout ce qui est interne et anxiogene
                  pour un manager (validation, qualité, plafond, ID interne) reste accessible
                  mais ne pollue plus la vue principale. */}
              <details className="mt-4 group rounded-lg border border-[var(--border)] bg-[var(--background)] open:bg-[var(--muted)]/30">
                <summary className="cursor-pointer px-3 py-2 text-xs font-mono font-semibold uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)] hover:text-[var(--foreground)] transition-colors flex items-center gap-2">
                  <svg className="h-3 w-3 transition-transform group-open:rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                  Diagnostic technique
                </summary>
                <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm px-3 pb-3 pt-2">
                  <DescriptionItem label="Statut validation" value={detail.whatsapp.metaStatus || "—"} />
                  <DescriptionItem label="Qualité des envois" value={detail.whatsapp.qualityRating || "—"} />
                  <DescriptionItem label="Quantité quotidienne autorisée" value={detail.whatsapp.messagingLimit || "—"} />
                  <DescriptionItem label="Identifiant technique" value={detail.whatsapp.twilioSenderSid || "—"} mono />
                </dl>
              </details>
            </>
          )}
        </section>

        {/* Import par email (epic 24) */}
        {emailConfigLoaded && (
          <section className="card-leadrive p-5">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-display text-[16px] font-bold tracking-display-tight text-[var(--foreground)]">Import par email</h2>
              <div className="flex gap-2">
                {!emailConfig && (
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={creerEmailConfig}
                    disabled={emailActionEnCours}
                  >
                    Créer l&apos;adresse
                  </Button>
                )}
                {emailConfig && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={regenererEmailConfig}
                    disabled={emailActionEnCours}
                  >
                    Régénérer
                  </Button>
                )}
                {emailConfig?.statut === "ACTIVE" && (
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={desactiverEmailConfig}
                    disabled={emailActionEnCours}
                  >
                    Désactiver
                  </Button>
                )}
                {emailConfig && (
                  <Link
                    href={`/admin/email-logs?tenantId=${detail.id}`}
                    className="text-xs text-accent hover:underline self-center"
                  >
                    Voir les logs →
                  </Link>
                )}
              </div>
            </div>
            {!emailConfig ? (
              <p className="text-sm text-[var(--color-lr-text-secondary)]">
                Aucune adresse email d&apos;import. Créer rétroactivement pour activer cette fonctionnalité.
              </p>
            ) : (
              <>
                <div className="flex items-center gap-3 rounded-lg border border-[var(--border)] bg-[var(--muted)] px-4 py-3 mb-3">
                  <code className="flex-1 font-mono text-sm text-[var(--foreground)] truncate">
                    {emailConfig.emailAddress}
                  </code>
                  <StatutBadge
                    statut={emailConfig.statut}
                    tone={emailConfig.statut === "ACTIVE" ? "success" : "neutral"}
                  />
                </div>
                <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
                  <DescriptionItem label="Emails reçus (total)" value={String(emailConfig.nbEmailsRecus ?? 0)} />
                  <DescriptionItem
                    label="Dernier email"
                    value={
                      emailConfig.dernierEmailRecuLe
                        ? new Date(emailConfig.dernierEmailRecuLe).toLocaleString("fr-FR")
                        : "—"
                    }
                  />
                  {emailConfig.stats && (
                    <>
                      <DescriptionItem
                        label="Leads créés (30j)"
                        value={String(emailConfig.stats.nbLeadsCrees ?? 0)}
                      />
                      <DescriptionItem
                        label="Taux parsing OK"
                        value={`${emailConfig.stats.tauxParsingOk}%`}
                      />
                    </>
                  )}
                </dl>
                {emailConfig.anciennesAdresses && emailConfig.anciennesAdresses.length > 0 && (
                  <details className="mt-4 text-sm">
                    <summary className="cursor-pointer text-xs font-medium text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]">
                      Anciennes adresses ({emailConfig.anciennesAdresses.length})
                    </summary>
                    <ul className="mt-2 space-y-1.5">
                      {emailConfig.anciennesAdresses.map((a, i) => (
                        <li key={i} className="flex items-baseline gap-3 text-xs">
                          <code className="font-mono text-[var(--color-lr-text-secondary)] line-through">
                            {a.adresse}
                          </code>
                          <span className="text-[var(--color-lr-text-tertiary)] whitespace-nowrap">
                            remplacée le {new Date(a.remplaceeLe).toLocaleString("fr-FR")}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </details>
                )}
              </>
            )}
          </section>
        )}

        {/* Historique numéros */}
        {detail.historiqueNumeros.length > 0 && (
          <section className="card-leadrive p-5">
            <h2 className="font-display text-[16px] font-bold tracking-display-tight text-[var(--foreground)] mb-3">Historique des numéros</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-[var(--color-lr-text-secondary)] text-xs uppercase tracking-wider">
                  <tr className="border-b border-[var(--border)]">
                    <th className="py-2">Numéro</th>
                    <th className="py-2">Associé le</th>
                    <th className="py-2">Dissocié le</th>
                    <th className="py-2">Raison</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.historiqueNumeros.map((h, i) => (
                    <tr key={i} className="border-b border-[var(--border)] last:border-b-0">
                      <td className="py-2 font-mono text-xs">{formatPhoneInput(h.whatsappFrom)}</td>
                      <td className="py-2 text-[var(--color-lr-text-secondary)]">
                        {new Date(h.associeLe).toLocaleString("fr-FR")}
                      </td>
                      <td className="py-2 text-[var(--color-lr-text-secondary)]">
                        {h.dissocieLe ? new Date(h.dissocieLe).toLocaleString("fr-FR") : "—"}
                      </td>
                      <td className="py-2 text-[var(--color-lr-text-secondary)] text-xs">{h.raisonDissociation || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        {/* Equipe (managers + vendeurs dans un seul tableau, tries manager d'abord) */}
        <section className="card-leadrive p-5">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display text-[16px] font-bold tracking-display-tight text-[var(--foreground)]">
              Équipe <span className="text-xs font-normal text-[var(--color-lr-text-secondary)]">({equipe.length})</span>
            </h2>
            <div className="inline-flex gap-2">
              <Button variant="ghost" size="sm" onClick={() => ouvrirInvitation("MANAGER")} disabled={actionEnCours}>+ Manager</Button>
              <Button variant="ghost" size="sm" onClick={() => ouvrirInvitation("VENDEUR")} disabled={actionEnCours}>+ Vendeur</Button>
            </div>
          </div>
          {equipe.length === 0 ? (
            <p className="text-sm text-[var(--color-lr-text-secondary)]">Aucun utilisateur pour le moment. Invitez un manager pour commencer.</p>
          ) : (
            <UtilisateursTable utilisateurs={equipe} onRetirer={retirerUtilisateur} actionEnCours={actionEnCours} />
          )}
        </section>

        {/* Audits admin */}
        {detail.auditsAdmin.length > 0 && (
          <section className="card-leadrive p-5">
            <h2 className="font-display text-[16px] font-bold tracking-display-tight text-[var(--foreground)] mb-3">Actions admin récentes</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-[var(--color-lr-text-secondary)] text-xs uppercase tracking-wider">
                  <tr className="border-b border-[var(--border)]">
                    <th className="py-2">Date</th>
                    <th className="py-2">Action</th>
                    <th className="py-2">Justification</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.auditsAdmin.map((a) => (
                    <tr key={a.id} className="border-b border-[var(--border)] last:border-b-0">
                      <td className="py-2 text-[var(--color-lr-text-secondary)] text-xs">
                        {new Date(a.creeLe).toLocaleString("fr-FR")}
                      </td>
                      <td className="py-2 font-mono text-xs">{a.action}</td>
                      <td className="py-2 text-[var(--color-lr-text-secondary)] text-xs">{a.justification}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        {/* Section technique : Clerk org ID, etc., repliable */}
        <details className="rounded-lg border border-[var(--border)] bg-[var(--card)] p-4">
          <summary className="cursor-pointer text-sm font-medium text-[var(--color-lr-text-secondary)] list-none flex items-center gap-2">
            <svg className="h-4 w-4 transition-transform group-open:rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
            Informations techniques
          </summary>
          <dl className="mt-3 grid grid-cols-1 gap-2 text-xs">
            <DescriptionItem label="Tenant ID" value={String(detail.id)} mono />
            <DescriptionItem label="Clerk Organization ID" value={detail.clerkOrganizationId || "—"} mono />
          </dl>
        </details>
          </>
        )}

      {/* Modal: inviter manager ou vendeur (selon inviteRole) */}
      <Modal
        open={showInviteModal}
        onClose={() => setShowInviteModal(false)}
        title={inviteRole === "MANAGER" ? "Inviter un manager" : "Inviter un vendeur"}
        description={<>Un email Clerk sera envoyé à l&apos;adresse renseignée pour rejoindre <strong>{detail?.nom}</strong>.</>}
      >
        <form onSubmit={handleInvite} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Prénom"
              value={inviteForm.prenom}
              onChange={(e) => setInviteForm({ ...inviteForm, prenom: e.target.value })}
              required
            />
            <Input
              label="Nom"
              value={inviteForm.nom}
              onChange={(e) => setInviteForm({ ...inviteForm, nom: e.target.value })}
              required
            />
          </div>
          <Input
            label="Email"
            type="email"
            value={inviteForm.email}
            onChange={(e) => setInviteForm({ ...inviteForm, email: e.target.value })}
            required
          />
          <div className="flex justify-end gap-3 pt-3">
            <Button type="button" variant="ghost" onClick={() => setShowInviteModal(false)}>Annuler</Button>
            <Button type="submit" disabled={inviting}>{inviting ? "Invitation…" : "Inviter"}</Button>
          </div>
        </form>
      </Modal>

      {/* Modal: confirmer la désactivation (l'utilisateur doit taper le nom de la concession) */}
      <Modal
        open={showDeleteModal}
        onClose={() => !actionEnCours && setShowDeleteModal(false)}
        title="Désactiver l'établissement"
        description={
          <>
            Cette action est <strong>irréversible</strong>. Tous les utilisateurs perdront l&apos;accès,
            l&apos;organisation Clerk sera supprimée et l&apos;établissement sera archivé.
          </>
        }
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            confirmerDesactivation();
          }}
          className="space-y-4"
        >
          {deleteError && <Alert tone="danger">{deleteError}</Alert>}
          <p className="text-sm text-[var(--color-lr-text-secondary)]">
            Pour confirmer, tapez exactement le nom de l&apos;établissement&nbsp;:
            <br />
            <span className="font-mono text-[var(--foreground)]">{detail?.nom}</span>
          </p>
          <div>
            <Input
              label="Nom de l'établissement"
              value={confirmNomTape}
              onChange={(e) => setConfirmNomTape(e.target.value)}
              autoFocus
              autoComplete="off"
              aria-invalid={confirmNomTape.length > 0 && confirmNomTape.trim() !== (detail?.nom ?? "").trim()}
            />
            {confirmNomTape.length > 0 && confirmNomTape.trim() !== (detail?.nom ?? "").trim() && (
              <p className="mt-1 text-xs text-danger">
                Le nom saisi ne correspond pas exactement.
              </p>
            )}
          </div>
          <div className="flex justify-end gap-3 pt-1">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setShowDeleteModal(false)}
              disabled={actionEnCours}
            >
              Annuler
            </Button>
            <Button
              type="submit"
              variant="danger"
              disabled={actionEnCours || confirmNomTape.trim() !== (detail?.nom ?? "").trim()}
            >
              {actionEnCours ? "Désactivation…" : "Désactiver"}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modale de confirmation generique (actions destructives non-radicales) */}
      <Modal
        open={confirmAction !== null}
        onClose={fermerConfirm}
        title={confirmAction?.title ?? ""}
      >
        <div className="space-y-4">
          {confirmError && <Alert tone="danger">{confirmError}</Alert>}
          <div className="text-sm text-[var(--color-lr-text-secondary)]">
            {confirmAction?.body}
          </div>
          <div className="flex justify-end gap-3 pt-1">
            <Button
              type="button"
              variant="ghost"
              onClick={fermerConfirm}
              disabled={confirmRunning}
            >
              Annuler
            </Button>
            <Button
              type="button"
              variant={confirmAction?.buttonVariant ?? "primary"}
              onClick={executerConfirm}
              disabled={confirmRunning}
            >
              {confirmRunning ? "En cours…" : (confirmAction?.buttonLabel ?? "Confirmer")}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

function UtilisateursTable({
  utilisateurs,
  onRetirer,
  actionEnCours,
}: {
  utilisateurs: Utilisateur[];
  onRetirer?: (u: Utilisateur) => void;
  actionEnCours: boolean;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="text-[var(--color-lr-text-secondary)] text-xs uppercase tracking-wider">
          <tr className="border-b border-[var(--border)]">
            <th className="py-2">Nom</th>
            <th className="py-2">Email</th>
            <th className="py-2">Rôle</th>
            <th className="py-2">Statut</th>
            <th className="py-2">Dernière connexion</th>
            {onRetirer && <th className="py-2 text-right">&nbsp;</th>}
          </tr>
        </thead>
        <tbody>
          {utilisateurs.map((u) => (
            <tr key={u.id} className="border-b border-[var(--border)] last:border-b-0">
              <td className="py-2 font-medium">{u.prenom} {u.nom}</td>
              <td className="py-2 text-[var(--color-lr-text-secondary)] text-xs">{u.email}</td>
              <td className="py-2"><RoleBadge role={u.role} /></td>
              <td className="py-2"><StatutBadge statut={u.statut} /></td>
              <td className="py-2 text-[var(--color-lr-text-secondary)] text-xs">
                {u.derniereConnexion ? new Date(u.derniereConnexion).toLocaleString("fr-FR") : "Jamais"}
              </td>
              {onRetirer && (
                <td className="py-2 text-right">
                  <button
                    onClick={() => onRetirer(u)}
                    disabled={actionEnCours}
                    className="text-xs text-[var(--color-lr-text-secondary)] hover:text-danger transition-colors disabled:opacity-40"
                  >
                    Retirer
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RoleBadge({ role }: { role: string }) {
  if (role === "MANAGER") {
    return (
      <span className="inline-flex items-center rounded-full bg-[var(--color-accent-subtle)] px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-[var(--color-accent-deep)]">
        Manager
      </span>
    );
  }
  return (
    <span className="inline-flex items-center rounded-full bg-[var(--muted)] px-2.5 py-0.5 text-[11px] font-medium uppercase tracking-wide text-[var(--color-lr-text-secondary)]">
      Vendeur
    </span>
  );
}

function DescriptionItem({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-[var(--color-lr-text-secondary)]">{label}</dt>
      <dd className={`text-sm text-[var(--foreground)] ${mono ? "font-mono text-xs break-all" : ""}`}>{value}</dd>
    </div>
  );
}

function KpiCard({ label, value, mono = false, valueColor }: { label: string; value: number; mono?: boolean; valueColor?: string }) {
  // `mono` est un alias pour la classe `number-hero` (deja en mono+tabular).
  // `valueColor` permet de teinter strategiquement (accent/success) certains compteurs.
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--card)] p-4">
      <div className="text-xs text-[var(--color-lr-text-secondary)]">{label}</div>
      <div className={`mt-1 number-hero tabular-nums ${valueColor ?? ""} ${mono ? "" : ""}`}>{value}</div>
    </div>
  );
}

/**
 * Dictionnaire des raisons Meta connues (codes renvoyes par Twilio Senders API
 * dans offline_reasons[].type ou .error_message). Permet d'afficher une
 * interpretation humaine + l'action a faire, au lieu d'un code cryptique.
 *
 * Cf. doc Twilio Senders v2 : https://www.twilio.com/docs/whatsapp/api/senders
 */
const META_REASON_DICT: Record<string, { label: string; action: string; tone: "warning" | "danger" | "info" }> = {
  RECENT_ACCOUNT_DELETION: {
    label: "Compte récemment supprimé",
    action: "Attendre 24-72h sans toucher à la configuration. Les recréations rapides sont bloquées par WhatsApp.",
    tone: "warning",
  },
  RATE_LIMITED: {
    label: "Trop d'appels techniques sur ce numéro",
    action: "Attendre 1-2h. Si récurrent, vérifier qu'aucun script ne sollicite la connexion en boucle.",
    tone: "warning",
  },
  MISSING_DISPLAY_NAME: {
    label: "Nom affiché manquant",
    action: "Le nom affiché est géré côté Facebook via le flow d'activation guidée. Le manager doit le corriger dans son Facebook Business Suite.",
    tone: "warning",
  },
  PENDING_DISPLAY_NAME: {
    label: "Nom affiché en attente de validation",
    action: "Patienter quelques heures à quelques jours, c'est automatique.",
    tone: "info",
  },
  MISSING_BUSINESS_PROFILE: {
    label: "Profil business incomplet",
    action: "Le profil business est géré côté Facebook. Le manager doit compléter logo, description et adresse dans son Facebook Business Suite.",
    tone: "warning",
  },
  INACTIVE: {
    label: "Aucun message envoyé/reçu depuis trop longtemps",
    action: "Envoyer 1 message de test depuis l'app pour réactiver le numéro.",
    tone: "info",
  },
  QUALITY_RATING_RED: {
    label: "Qualité des envois : ROUGE",
    action: "Plusieurs jours sans nouveau signal spam. Vérifier les messages envoyés récemment.",
    tone: "danger",
  },
};

function interpretMetaReasons(raw: string): { label: string; action: string; tone: "warning" | "danger" | "info" }[] {
  if (!raw) return [];
  const tokens = raw.split(/[·,]/).map((t) => t.trim()).filter(Boolean);
  return tokens.map((token) => {
    const key = Object.keys(META_REASON_DICT).find((k) =>
      token.toUpperCase().includes(k),
    );
    if (key) return META_REASON_DICT[key];
    return { label: token, action: "Vérifier les logs côté backend pour plus de contexte.", tone: "info" as const };
  });
}

function OfflineReasonsBlock({
  metaStatus,
  statut,
  rawReason,
}: {
  metaStatus: string | null;
  statut: string | null;
  rawReason: string;
}) {
  const interpretations = interpretMetaReasons(rawReason);
  // Label adaptatif : OFFLINE = temporaire, REJECTED/MISSING = terminal/critique
  const isCritical = metaStatus === "REJECTED" || metaStatus === "MISSING" || statut === "ERREUR";
  const label = isCritical ? "Raison du blocage validation" : "Diagnostic validation (hors ligne)";
  const bgTone = isCritical ? "bg-danger/5 border-danger/30" : "bg-warning/5 border-warning/30";
  const labelTone = isCritical ? "text-danger" : "text-warning";

  return (
    <div className={`rounded-lg border p-3 ${bgTone}`}>
      <div className="flex items-center gap-2 mb-2">
        <svg className={`h-4 w-4 ${labelTone}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
        </svg>
        <span className={`text-xs font-semibold uppercase tracking-wide ${labelTone}`}>{label}</span>
      </div>
      <ul className="space-y-2 text-sm">
        {interpretations.map((item, i) => {
          const dotColor =
            item.tone === "danger" ? "bg-danger" :
            item.tone === "warning" ? "bg-warning" :
            "bg-accent";
          return (
            <li key={i} className="flex gap-2">
              <span className={`mt-1.5 h-1.5 w-1.5 rounded-full shrink-0 ${dotColor}`} />
              <div className="flex-1">
                <div className="text-[var(--foreground)] font-medium">{item.label}</div>
                <div className="text-xs text-[var(--color-lr-text-secondary)] mt-0.5">{item.action}</div>
              </div>
            </li>
          );
        })}
      </ul>
      <details className="mt-3 text-xs text-[var(--color-lr-text-tertiary)]">
        <summary className="cursor-pointer hover:text-[var(--color-lr-text-secondary)]">Message brut Twilio</summary>
        <pre className="mt-2 whitespace-pre-wrap break-words font-mono">{rawReason}</pre>
      </details>
    </div>
  );
}
