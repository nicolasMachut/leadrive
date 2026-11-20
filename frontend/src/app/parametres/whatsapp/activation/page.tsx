"use client";

import { useState } from "react";
import Link from "next/link";
import AppLayout from "@/components/AppLayout";
import PageHeader from "@/components/common/PageHeader";
import {
  useApiClient,
  useIsManagerOrSuperAdmin,
  useIsSuperAdmin,
  formatErreur,
} from "@/lib/auth";
import { useToast } from "@/lib/realtime";
import ErrorState from "@/components/common/ErrorState";
import Modal from "@/components/common/Modal";
import Button from "@/components/common/Button";
import { formatPhoneInput } from "@/lib/phone";
import { templateLabel } from "@/lib/whatsapp/templates";
import {
  useSelfSignupStatus,
  type TemplateStatut,
} from "@/components/whatsapp/useSelfSignupStatus";
import FacebookEmbeddedSignupButton from "@/components/whatsapp/FacebookEmbeddedSignupButton";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

/**
 * Page d'activation WhatsApp Business via Self Sign-up (epic 25).
 *
 * Architecture UX a 2 niveaux :
 *  - MANAGER (concession) : 3 etapes simples, zero jargon technique.
 *    Il ne voit jamais les details de validation des templates. Si un
 *    probleme survient, un message generique l'informe que l'equipe s'en
 *    occupe.
 *  - SUPER_ADMIN (equipe LeadRive) : vue complete avec detail par template,
 *    raisons de rejet, bouton relancer. Utile pour le debug/support.
 *
 * 3 etats mentaux du manager :
 *  1. "Pas encore fait" -> bouton d'activation
 *  2. "En cours"        -> ecran d'attente
 *  3. "C'est bon"       -> confirmation
 */
export default function SelfSignupActivationPage() {
  const isManager = useIsManagerOrSuperAdmin();

  if (!isManager) {
    return (
      <AppLayout>
        <div className="mx-auto max-w-3xl px-6 py-10">
          <PageHeader
            eyebrow="Accès restreint"
            title="Activation WhatsApp Business"
            subtitle="Seul un manager peut activer la connexion WhatsApp Business."
          />
          <Link
            href="/dashboard"
            className="mt-6 inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline"
          >
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            Retour au tableau de bord
          </Link>
        </div>
      </AppLayout>
    );
  }
  return (
    <AppLayout>
      <SelfSignupContent />
    </AppLayout>
  );
}

function SelfSignupContent() {
  const { status, loading, refresh } = useSelfSignupStatus(5000);
  const apiFetch = useApiClient();
  const isSuperAdmin = useIsSuperAdmin();
  const { showToast } = useToast();
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [modaleDeco, setModaleDeco] = useState(false);

  async function demarrer() {
    setActionLoading(true);
    setActionError(null);
    try {
      const res = await apiFetch(
        `${API_BASE}/api/parametres/whatsapp/self-signup/demarrer`,
        { method: "POST" }
      );
      if (!res.ok) {
        const msg = await res.text();
        throw new Error(msg || "Echec du démarrage");
      }
      await refresh();
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Erreur inconnue");
    } finally {
      setActionLoading(false);
    }
  }

  async function relancerTemplates() {
    setActionLoading(true);
    setActionError(null);
    try {
      const res = await apiFetch(
        `${API_BASE}/api/parametres/whatsapp/self-signup/templates/relancer`,
        { method: "POST" }
      );
      if (!res.ok) {
        const msg = await res.text();
        throw new Error(msg || "Échec de la relance");
      }
      await refresh();
      showToast({
        titre: "Soumission relancée",
        message: "Les modèles vont être re-soumis à la validation Facebook.",
        tone: "success",
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Erreur inconnue";
      setActionError(msg);
      showToast({ titre: "Relance échouée", message: msg, tone: "danger" });
    } finally {
      setActionLoading(false);
    }
  }

  async function deconnecter() {
    setActionLoading(true);
    setActionError(null);
    try {
      const res = await apiFetch(
        `${API_BASE}/api/parametres/whatsapp/self-signup/deconnecter`,
        { method: "POST" }
      );
      if (!res.ok) {
        const msg = await res.text();
        throw new Error(msg || "Échec de la déconnexion");
      }
      setModaleDeco(false);
      await refresh();
      showToast({
        titre: "Compte WhatsApp déconnecté",
        message: "Vous pouvez reconnecter le bon compte.",
        tone: "success",
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Erreur inconnue";
      setActionError(msg);
      showToast({ titre: "Déconnexion échouée", message: msg, tone: "danger" });
    } finally {
      setActionLoading(false);
    }
  }

  // Etat simplifie : le manager ne voit que 3 phases.
  const facebookDone = !!status?.wabaId;
  const allReady = status?.tousTemplatesApprouves ?? false;
  // État de santé runtime : opérationnel vs restreint (envoi suspendu).
  const actif = status?.etatCompte === "ACTIF";
  const estRestreint = status?.etatCompte === "RESTREINT";
  // Numéro/sender injoignable alors que le compte est ACTIF (≠ restriction Meta).
  const numeroEnEchec = (status?.numeroEnEchec ?? false) && !estRestreint;
  const hasRejectedTemplates =
    status?.templates?.some((t) => t.statut === "REJETE") ?? false;

  return (
    <div className="mx-auto max-w-4xl px-6 py-8 space-y-6">
      <PageHeader
        eyebrow="WhatsApp Business"
        title="Activation"
        subtitle="Connectez votre compte WhatsApp Business en quelques minutes. Vous gardez le contrôle total de votre numéro et de votre image."
      />

      {/* Skeleton de chargement — visible uniquement pendant le premier fetch */}
      {loading && !status && (
        <div className="space-y-4">
          <div className="h-16 rounded-lg animate-shimmer" />
          <div className="h-48 rounded-xl animate-shimmer" />
          <div className="h-32 rounded-xl animate-shimmer" />
        </div>
      )}

      {/* Erreur de chargement — statut non disponible après fetch */}
      {!loading && !status && (
        <ErrorState
          message={formatErreur(new Error("Impossible de récupérer le statut WhatsApp. Vérifiez votre connexion ou contactez le support."))}
          onRetry={refresh}
        />
      )}

      {/* Contenu principal — affiché uniquement une fois le statut chargé */}
      {status && (
        <>
          {/* Timeline — 2 vraies étapes (Connexion, Configuration). L'écran de
              succès apparaît implicitement quand les 2 sont cochées. */}
          <div className="animate-card-mount">
            <Timeline facebookDone={facebookDone} allReady={allReady} actif={actif} />
          </div>

          {/* Compte restreint : envoi suspendu (restriction WhatsApp Business survenue
              après activation). Prime sur l'écran de succès. */}
          {estRestreint && (
            <div className="animate-card-mount rounded-xl border border-danger/30 bg-danger/10 p-4">
              <p className="text-sm font-semibold text-danger">Compte WhatsApp Business restreint</p>
              <p className="mt-1 text-xs text-danger/80">
                {status.messageEtat
                  || "L'envoi de messages WhatsApp est suspendu. Vérifiez l'état de votre compte WhatsApp Business, puis réessayez."}
              </p>
            </div>
          )}

          {/* Numéro injoignable : le compte est actif mais les envois échouent car le
              numéro n'est pas joignable (≠ restriction). Action requise côté établissement. */}
          {numeroEnEchec && (
            <div className="animate-card-mount rounded-xl border border-danger/30 bg-danger/10 p-4">
              <p className="text-sm font-semibold text-danger">Envoi WhatsApp en échec</p>
              <p className="mt-1 text-xs text-danger/80">
                {status.messageEnvoi
                  || "Vos messages WhatsApp ne partent plus : votre numéro n'est pas joignable. Vérifiez la configuration de votre numéro WhatsApp Business, puis réessayez."}
              </p>
              <ul className="mt-2 list-disc pl-4 text-xs text-danger/80 space-y-1">
                <li>Vérifiez que votre numéro est toujours actif sur WhatsApp Business.</li>
                <li>Si le problème persiste, contactez le support pour rétablir l&apos;envoi.</li>
              </ul>
            </div>
          )}

          {/* ── Phase 1 : connexion (pre-Facebook ou Facebook en cours).
              actionError passé en prop pour rendu inline sous le bouton qui a
              échoué, pas en bottom de page perdu. ── */}
          {!facebookDone && (
            <div className="animate-card-mount-delayed">
              <ConnectCard
                subAccountCree={status.subAccountCree}
                onDemarrer={demarrer}
                onFacebookSuccess={refresh}
                onError={setActionError}
                loading={actionLoading}
                error={actionError}
              />
            </div>
          )}

          {/* ── Phase 2 : configuration automatique (templates en cours) ── */}
          {facebookDone && !allReady && (
            <div className="animate-card-mount-delayed space-y-6">
              <WaitingCard hasError={hasRejectedTemplates} />
              {/* Detail technique visible uniquement par l'equipe LeadRive */}
              {isSuperAdmin && (
                <AdminTemplateDetails
                  templates={status.templates}
                  onRetry={relancerTemplates}
                  loading={actionLoading}
                  messageBlocage={status.messageBlocage}
                />
              )}
            </div>
          )}

          {/* ── Phase 3 : c'est prêt — delight pulse au moment où tout est vert.
              Masqué si le compte est restreint (la bannière d'alerte prime). ── */}
          {allReady && !estRestreint && !numeroEnEchec && (
            <div className="animate-card-mount-delayed animate-success-pulse rounded-xl">
              <SuccessCard numero={status.numeroAffiche} nom={status.nomAffiche} />
            </div>
          )}

          {/* Erreur globale (relancerTemplates etc.) — uniquement si on n'est pas
              sur la phase 1 où l'erreur est déjà rendue inline dans ConnectCard. */}
          {actionError && facebookDone && (
            <div className="rounded-lg border border-danger/30 bg-danger/10 p-3 text-sm text-danger">
              {actionError}
            </div>
          )}

          {/* Changer de compte : self-service si déjà connecté (mauvais compte, changement de numéro). */}
          {facebookDone && (
            <div className="flex justify-center pt-2">
              <button
                type="button"
                onClick={() => setModaleDeco(true)}
                className="text-xs text-[var(--color-lr-text-tertiary)] underline-offset-2 transition-colors hover:text-[var(--color-lr-text-secondary)] hover:underline"
              >
                Changer de compte WhatsApp
              </button>
            </div>
          )}
        </>
      )}

      {/* Modale de confirmation : déconnexion / changement de compte WhatsApp */}
      <Modal
        open={modaleDeco}
        onClose={() => setModaleDeco(false)}
        title="Changer de compte WhatsApp"
        description="Vous allez déconnecter le compte WhatsApp actuel pour en reconnecter un autre."
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setModaleDeco(false)} disabled={actionLoading}>
              Annuler
            </Button>
            <Button variant="danger" size="sm" onClick={() => void deconnecter()} disabled={actionLoading}>
              {actionLoading ? "Déconnexion…" : "Déconnecter"}
            </Button>
          </div>
        }
      >
        <ul className="list-disc space-y-1 pl-5 text-sm text-[var(--color-lr-text-secondary)]">
          <li>L'envoi de messages sera interrompu jusqu'à la reconnexion.</li>
          <li>Vos conversations passées sont conservées.</li>
          <li>Vous pourrez ensuite reconnecter le bon compte WhatsApp Business.</li>
        </ul>
      </Modal>

      {/* Aide / accompagnement — toujours visible (mount delayed 300ms) */}
      <div className="animate-card-mount-delayed-2">
        <DocActivationWhatsApp />
      </div>
    </div>
  );
}

// ─── Timeline — 3 étapes du parcours d'activation ──────────────────────────
// 1. Connexion : passe à `done` quand wabaId est récupéré (Facebook OK).
// 2. Configuration : passe à `done` quand allReady (tous templates approuvés).
// 3. Activé : passe à `done` quand le compte est opérationnel (numéro en ligne).
//    Distinct de Configuration : les templates peuvent être approuvés alors que
//    le numéro n'est pas encore en ligne. Si le compte devient restreint, cette
//    étape repasse `pending` (et une bannière dédiée explique pourquoi).

function Timeline({
  facebookDone,
  allReady,
  actif,
}: {
  facebookDone: boolean;
  allReady: boolean;
  actif: boolean;
}) {
  const steps: { label: string; done: boolean; pending: boolean }[] = [
    { label: "Connexion", done: facebookDone, pending: !facebookDone },
    { label: "Configuration", done: allReady, pending: facebookDone && !allReady },
    { label: "Activé", done: actif, pending: allReady && !actif },
  ];
  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-4">
      <div className="flex items-center justify-between gap-2">
        {steps.map((s, i) => (
          <div key={s.label} className="flex flex-1 items-center gap-2">
            <div
              className={`relative flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold transition-colors ${
                s.done
                  ? "bg-success text-white"
                  : s.pending
                  ? "bg-accent/15 text-accent"
                  : "bg-[var(--muted)] text-[var(--color-lr-text-tertiary)]"
              }`}
            >
              {s.done ? (
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                </svg>
              ) : s.pending ? (
                <>
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent/30" aria-hidden="true" />
                  <span className="relative font-mono">{i + 1}</span>
                </>
              ) : (
                <span className="font-mono">{i + 1}</span>
              )}
            </div>
            <span
              className={`text-xs ${
                s.done
                  ? "font-semibold text-[var(--foreground)]"
                  : s.pending
                  ? "font-semibold text-accent"
                  : "text-[var(--color-lr-text-secondary)]"
              }`}
            >
              {s.label}
            </span>
            {i < steps.length - 1 && (
              <div
                className={`mx-1 h-px flex-1 ${
                  s.done ? "bg-success" : "bg-[var(--border)]"
                }`}
              />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Card unifiée de connexion (fusionne PreFlow + Facebook) ──────────────

function ConnectCard({
  subAccountCree,
  onDemarrer,
  onFacebookSuccess,
  onError,
  loading,
  error,
}: {
  subAccountCree: boolean;
  onDemarrer: () => void;
  onFacebookSuccess: () => void;
  onError: (msg: string) => void;
  loading: boolean;
  error: string | null;
}) {
  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-6">
      <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-accent">
        Étape 1 · Connexion
      </p>
      <h2 className="mt-1 font-display text-2xl font-bold tracking-display-tight text-[var(--foreground)]">
        Connectez votre WhatsApp Business
      </h2>

      {!subAccountCree ? (
        <>
          <p className="mt-2 text-sm text-[var(--color-lr-text-secondary)]">
            Vous allez être redirigé sur Facebook pour connecter votre
            compte WhatsApp Business. Cela prend environ 5 minutes.
          </p>
          <p className="mt-3 text-sm font-medium text-[var(--foreground)]">
            Avant de commencer, vérifiez que vous avez :
          </p>
          <ul className="mt-2 space-y-2 text-sm text-[var(--foreground)]">
            <li className="flex items-start gap-2">
              <CheckBullet />
              <span>
                Un compte <strong>Facebook professionnel</strong>, ou la possibilité d&apos;en créer un (gratuit)
              </span>
            </li>
            <li className="flex items-start gap-2">
              <CheckBullet />
              <span>
                Un numéro de téléphone <strong>non utilisé sur l&apos;app WhatsApp</strong> (mobile ou fixe)
              </span>
            </li>
            <li className="flex items-start gap-2">
              <CheckBullet />
              <span>
                Les accès Facebook du <strong>responsable</strong> de l&apos;établissement
              </span>
            </li>
          </ul>
          <div className="mt-5 flex flex-wrap items-center gap-3">
            <button
              onClick={onDemarrer}
              disabled={loading}
              className="rounded-lg btn-primary px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Préparation…" : "Connecter mon WhatsApp"}
            </button>
            <span className="text-xs text-[var(--color-lr-text-tertiary)]">
              Environ 5 minutes
            </span>
          </div>
          {/* Erreur inline sous le bouton qui a échoué — pas perdu en bas de
              page après les autres blocs. */}
          {error && (
            <div className="mt-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
              {error}
            </div>
          )}
        </>
      ) : (
        <>
          <p className="mt-2 text-sm text-[var(--color-lr-text-secondary)]">
            Votre compte est prêt. Cliquez ci-dessous pour vous connecter
            via Facebook. À la fin de la procédure, vous serez redirigé
            ici automatiquement.
          </p>
          <div className="mt-4 rounded-lg border border-accent/30 bg-accent/5 p-3">
            <p className="text-xs font-medium text-accent">Pendant les étapes Facebook :</p>
            <ol className="mt-1 ml-4 list-decimal space-y-1 text-xs text-[var(--color-lr-text-secondary)]">
              <li>Choisissez votre compte professionnel Facebook (ou créez-en un)</li>
              <li>Ajoutez votre numéro de téléphone</li>
              <li>Validez le numéro via SMS ou appel</li>
              <li>Acceptez les permissions</li>
            </ol>
          </div>
          <div className="mt-5">
            <FacebookEmbeddedSignupButton
              onSuccess={onFacebookSuccess}
              onError={onError}
              onReset={() => onError("")}
            />
          </div>
          {error && (
            <div className="mt-3 rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
              {error}
            </div>
          )}
        </>
      )}
    </div>
  );
}

// ─── Ecran d'attente simplifié (manager) ──────────────────────────────────

function WaitingCard({ hasError }: { hasError: boolean }) {
  if (hasError) {
    return (
      <div className="rounded-xl border border-warning/30 bg-warning/5 p-6">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-warning/10 text-warning">
            <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <div>
            <h2 className="font-display text-xl font-bold tracking-display-tight text-[var(--foreground)]">
              Un ajustement est nécessaire
            </h2>
            <p className="mt-1 text-sm text-[var(--color-lr-text-secondary)]">
              La configuration de votre compte nécessite une intervention
              de notre part. Pas d&apos;inquiétude, notre équipe s&apos;en occupe
              et vous n&apos;avez rien à faire.
            </p>
            <p className="mt-3 text-sm text-[var(--color-lr-text-secondary)]">
              Si le problème persiste plus de 24h, contactez-nous à{" "}
              <a
                href="mailto:contact@leadrive.fr?subject=Activation%20WhatsApp%20-%20problème%20de%20configuration"
                className="font-semibold text-accent hover:underline"
              >
                contact@leadrive.fr
              </a>
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-6">
      <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-accent">
        Étape 2 · Configuration automatique
      </p>
      <h2 className="mt-1 font-display text-2xl font-bold tracking-display-tight text-[var(--foreground)]">
        Nous finalisons la configuration
      </h2>
      <p className="mt-2 text-sm text-[var(--color-lr-text-secondary)]">
        Votre connexion Facebook a bien été enregistrée. Nous configurons
        maintenant votre compte pour l&apos;envoi de messages. C&apos;est
        entièrement automatique.
      </p>

      {/* Indicateur visuel de progression */}
      <div className="mt-5 flex items-center gap-3 rounded-lg border border-accent/20 bg-accent/5 p-4">
        <div className="relative flex h-5 w-5 shrink-0 items-center justify-center">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent/40" />
          <span className="relative inline-flex h-3 w-3 rounded-full bg-accent" />
        </div>
        <div>
          <p className="text-sm font-medium text-[var(--foreground)]">
            Configuration en cours…
          </p>
          <p className="text-xs text-[var(--color-lr-text-tertiary)]">
            Généralement quelques minutes, parfois jusqu&apos;à quelques heures.
            Cette page se met à jour automatiquement.
          </p>
        </div>
      </div>

      <p className="mt-4 text-xs text-[var(--color-lr-text-tertiary)]">
        En attendant, vous pouvez déjà importer vos leads et compléter les
        informations de votre établissement (horaires, adresse, coordonnées),
        que l&apos;assistant utilise dans ses messages. Vous pourrez lancer vos
        envois dès que tout sera prêt.
      </p>
    </div>
  );
}

// ─── Détail technique templates (SUPER_ADMIN uniquement) ──────────────────

function AdminTemplateDetails({
  templates,
  onRetry,
  loading,
  messageBlocage,
}: {
  templates: TemplateStatut[];
  onRetry: () => void;
  loading: boolean;
  messageBlocage: string | null;
}) {
  const rejetes = templates.filter((t) => t.statut === "REJETE");
  const approuves = templates.filter((t) => t.statut === "APPROUVE");
  const enAttente = templates.filter((t) => t.statut === "SOUMIS" || t.statut === "EN_ATTENTE_SOUMISSION");
  return (
    <div className="rounded-xl border border-dashed border-[var(--border)] bg-[var(--card)] p-6 opacity-80">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
          Vue admin · Détail des modèles
        </p>
        {/* Compteur agrégé X/Y approuvés. Permet de comprendre où on en est
            sans devoir énumérer la liste à l'œil. */}
        <p className="font-mono text-[11px] text-[var(--color-lr-text-secondary)]">
          <span className="font-semibold text-success">{approuves.length}</span>
          <span className="text-[var(--color-lr-text-tertiary)]">/</span>
          <span className="font-semibold text-[var(--foreground)]">{templates.length}</span>
          <span className="ml-1">approuvés</span>
          {enAttente.length > 0 && (
            <>
              <span className="mx-2 text-[var(--color-lr-text-tertiary)]">·</span>
              <span className="font-semibold text-warning">{enAttente.length}</span>
              <span className="ml-1">en attente</span>
            </>
          )}
          {rejetes.length > 0 && (
            <>
              <span className="mx-2 text-[var(--color-lr-text-tertiary)]">·</span>
              <span className="font-semibold text-danger">{rejetes.length}</span>
              <span className="ml-1">rejetés</span>
            </>
          )}
        </p>
      </div>
      {messageBlocage && (
        <p className="mt-2 text-sm text-warning">{messageBlocage}</p>
      )}
      <ul className="mt-3 space-y-2">
        {templates.map((t) => (
          <li
            key={t.name}
            className="flex items-center justify-between gap-3 rounded-lg border border-[var(--border)] bg-[var(--background)] px-3 py-2.5"
          >
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-[var(--foreground)]">
                {templateLabel(t.name)}
              </p>
              <p className="text-xs text-[var(--color-lr-text-tertiary)] font-mono">
                {t.name}
              </p>
              {t.metaRejectReason && (
                <p className="mt-0.5 truncate text-xs text-danger">
                  {t.metaRejectReason}
                </p>
              )}
            </div>
            <StatutPastille statut={t.statut} />
          </li>
        ))}
      </ul>
      {rejetes.length > 0 && (
        <div className="mt-4 flex items-center justify-between gap-3 rounded-lg border border-danger/30 bg-danger/10 p-3">
          <p className="text-xs text-danger">
            {rejetes.length} modèle{rejetes.length > 1 ? "s" : ""} rejeté
            {rejetes.length > 1 ? "s" : ""} à la validation.
          </p>
          <button
            onClick={onRetry}
            disabled={loading}
            className="shrink-0 rounded-lg bg-danger px-3 py-1.5 text-xs font-semibold text-white hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? "Relance…" : "Relancer la soumission"}
          </button>
        </div>
      )}
    </div>
  );
}

// ─── Ecran de succès ──────────────────────────────────────────────────────

function SuccessCard({ numero, nom }: { numero: string | null; nom: string | null }) {
  return (
    <div className="rounded-xl border border-success/30 bg-success/5 p-6">
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-success text-white">
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <div className="flex-1 min-w-0">
          <h2 className="font-display text-xl font-bold tracking-display-tight text-[var(--foreground)]">
            WhatsApp Business activé.
          </h2>
          <p className="mt-1 text-sm text-[var(--color-lr-text-secondary)]">
            Votre compte est opérationnel. Vos prospects recevront
            désormais les messages WhatsApp automatiquement.
          </p>
          <div className="mt-4 flex flex-wrap gap-x-8 gap-y-3">
            {numero && (
              <p className="text-sm">
                <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                  Votre numéro
                </span>
                <br />
                {/* Numéro formaté FR avec espaces (règle CLAUDE.md #11). */}
                <span className="font-mono font-medium text-[var(--foreground)]">{formatPhoneInput(numero)}</span>
              </p>
            )}
            {nom && (
              <p className="text-sm">
                <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                  Nom affiché à vos prospects
                </span>
                <br />
                <span className="font-medium text-[var(--foreground)]">{nom}</span>
              </p>
            )}
          </div>
          <div className="mt-5 flex flex-wrap items-center gap-3">
            <Link
              href="/inbox"
              className="inline-flex items-center gap-1.5 rounded-lg btn-primary px-4 py-2 text-sm font-semibold text-white"
            >
              Voir vos conversations
              <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
              </svg>
            </Link>
            <Link
              href="/leads"
              className="text-sm font-medium text-accent hover:underline"
            >
              ou démarrer une relance prospect
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Helpers ──────────────────────────────────────────────────────────────

function CheckBullet() {
  return (
    <span className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-success/20 text-success">
      <svg className="h-2.5 w-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
      </svg>
    </span>
  );
}

function StatutPastille({ statut }: { statut: TemplateStatut["statut"] }) {
  const config = {
    APPROUVE: { label: "Approuvé", bg: "bg-success/10", txt: "text-success" },
    SOUMIS: { label: "En attente", bg: "bg-warning/10", txt: "text-warning" },
    EN_ATTENTE_SOUMISSION: {
      label: "À soumettre",
      bg: "bg-[var(--muted)]",
      txt: "text-[var(--color-lr-text-secondary)]",
    },
    REJETE: { label: "Refusé", bg: "bg-danger/10", txt: "text-danger" },
    DESACTIVE: {
      label: "Désactivé",
      bg: "bg-[var(--muted)]",
      txt: "text-[var(--color-lr-text-tertiary)]",
    },
  }[statut] || {
    label: statut,
    bg: "bg-[var(--muted)]",
    txt: "text-[var(--foreground)]",
  };
  return (
    <span
      className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium ${config.bg} ${config.txt}`}
    >
      {config.label}
    </span>
  );
}

// ─── Aide & accompagnement ────────────────────────────────────────────────

function DocActivationWhatsApp() {
  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-6">
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent/10 text-accent">
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div className="flex-1">
          <h2 className="font-display text-xl font-bold tracking-display-tight text-[var(--foreground)]">
            Besoin d&apos;aide ?
          </h2>
          <p className="mt-1 text-sm text-[var(--color-lr-text-secondary)]">
            On vous explique tout simplement.
          </p>
        </div>
      </div>

      {/* Bloc accompagnement */}
      <div className="mt-5 rounded-lg border border-accent/40 bg-accent/5 p-4">
        <p className="text-sm font-semibold text-[var(--foreground)]">
          Notre équipe vous accompagne gratuitement
        </p>
        <p className="mt-1 text-sm text-[var(--color-lr-text-secondary)]">
          Si vous préférez qu&apos;on configure ça avec vous au téléphone ou par email,
          écrivez-nous à{" "}
          <a
            href="mailto:contact@leadrive.fr?subject=Aide%20activation%20WhatsApp%20Business"
            className="font-semibold text-accent hover:underline"
          >
            contact@leadrive.fr
          </a>{". "}Réponse sous 24h ouvrées.
        </p>
        <p className="mt-2 text-xs text-[var(--color-lr-text-secondary)]">
          Vous pouvez aussi poser vos questions au{" "}
          <strong>chatbot d&apos;aide</strong>
          {" "}en bas à droite de l&apos;écran, il connaît tout le
          fonctionnement de Leadrive et répond en direct.
        </p>
      </div>

      {/* FAQ */}
      <div className="mt-4 space-y-2">
        <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
          Questions fréquentes
        </p>

        <details className="group rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 open:bg-[var(--muted)]/40">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--foreground)]">
            De quoi ai-je besoin pour activer WhatsApp ?
          </summary>
          <div className="mt-2 space-y-2 text-sm text-[var(--color-lr-text-secondary)]">
            <p>
              Trois choses : un <strong>compte Facebook professionnel</strong>{" "}
              (appelé « Facebook Business Suite », c&apos;est gratuit), un{" "}
              <strong>numéro de téléphone</strong> non utilisé sur l&apos;app
              WhatsApp classique, et les <strong>accès Facebook</strong> du
              responsable de l&apos;établissement.
            </p>
          </div>
        </details>

        <details className="group rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 open:bg-[var(--muted)]/40">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--foreground)]">
            Comment savoir si j&apos;ai déjà un compte Facebook professionnel ?
          </summary>
          <div className="mt-2 space-y-2 text-sm text-[var(--color-lr-text-secondary)]">
            <p>
              Allez sur{" "}
              <a
                href="https://business.facebook.com"
                target="_blank"
                rel="noopener noreferrer"
                className="font-semibold text-accent hover:underline"
              >
                business.facebook.com
              </a>{" "}
              et connectez-vous avec le compte Facebook du dirigeant
              ou du responsable digital.
            </p>
            <ul className="ml-4 list-disc space-y-1">
              <li>
                Si vous voyez un <strong>tableau de bord</strong> avec le
                nom de votre établissement → vous en avez déjà un.
              </li>
              <li>
                Si on vous propose d&apos;en <strong>créer un</strong> →
                suivez la question ci-dessous.
              </li>
            </ul>
            <p className="text-xs">
              Astuce : si vous gérez déjà une page Facebook pour
              l&apos;établissement, vous avez probablement déjà un compte
              professionnel associé.
            </p>
          </div>
        </details>

        <details className="group rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 open:bg-[var(--muted)]/40">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--foreground)]">
            Comment créer un compte Facebook professionnel ? (10 min, gratuit)
          </summary>
          <div className="mt-2 space-y-2 text-sm text-[var(--color-lr-text-secondary)]">
            <ol className="ml-4 list-decimal space-y-2">
              <li>
                Allez sur{" "}
                <a
                  href="https://business.facebook.com/overview"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="font-semibold text-accent hover:underline"
                >
                  business.facebook.com/overview
                </a>{" "}
                et cliquez sur <strong>Créer un compte</strong>.
              </li>
              <li>
                Connectez-vous avec votre <strong>compte Facebook personnel</strong>,
                c&apos;est lui qui sera l&apos;administrateur.
              </li>
              <li>
                Saisissez le <strong>nom de l&apos;établissement</strong>,
                votre nom, et un <strong>email professionnel</strong>.
              </li>
              <li>
                Acceptez les conditions, pas besoin de carte bancaire.
              </li>
              <li>
                Rattachez ou créez une{" "}
                <strong>page Facebook</strong> au nom de l&apos;établissement.
              </li>
            </ol>
            <p className="text-xs">
              C&apos;est tout. Vous pouvez ensuite revenir ici pour activer WhatsApp.
            </p>
          </div>
        </details>

        <details className="group rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 open:bg-[var(--muted)]/40">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--foreground)]">
            Quel numéro de téléphone choisir ?
          </summary>
          <div className="mt-2 space-y-2 text-sm text-[var(--color-lr-text-secondary)]">
            <p>
              Il vous faut un numéro <strong>non utilisé sur l&apos;app WhatsApp
              classique</strong>. Deux bonnes options :
            </p>
            <ul className="ml-4 list-disc space-y-1">
              <li>
                Votre <strong>numéro fixe</strong> (standard de
                l&apos;établissement), idéal car le client peut aussi appeler.
              </li>
              <li>
                Un <strong>mobile pro</strong> non enregistré sur WhatsApp.
              </li>
            </ul>
            <p>
              Un code de validation vous sera envoyé par SMS ou appel
              sur ce numéro pendant l&apos;activation. Ayez-le à portée.
            </p>
          </div>
        </details>

        <details className="group rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 open:bg-[var(--muted)]/40">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--foreground)]">
            Combien ça coûte ?
          </summary>
          <div className="mt-2 space-y-2 text-sm text-[var(--color-lr-text-secondary)]">
            <p>
              L&apos;activation est <strong>gratuite</strong>. Le coût des
              messages WhatsApp (quelques centimes par conversation) est{" "}
              <strong>inclus dans votre abonnement Leadrive</strong>, aucun
              surcoût pour vous.
            </p>
          </div>
        </details>

        <details className="group rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 open:bg-[var(--muted)]/40">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--foreground)]">
            Combien de temps ça prend ?
          </summary>
          <div className="mt-2 space-y-2 text-sm text-[var(--color-lr-text-secondary)]">
            <p>
              La connexion Facebook prend <strong>5 minutes</strong>. Ensuite,
              la configuration automatique prend généralement{" "}
              <strong>quelques minutes à quelques heures</strong> (24h max
              dans de rares cas).
            </p>
            <p>
              En attendant, vous pouvez déjà importer vos leads et compléter les
              informations de votre établissement (horaires, adresse, coordonnées)
              dans « Mon établissement ». L&apos;envoi se déclenchera dès que
              tout sera prêt.
            </p>
          </div>
        </details>

        <details className="group rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 open:bg-[var(--muted)]/40">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--foreground)]">
            Faut-il obligatoirement une page Facebook ?
          </summary>
          <div className="mt-2 space-y-2 text-sm text-[var(--color-lr-text-secondary)]">
            <p>
              <strong>Oui</strong>, c&apos;est une exigence de Facebook pour
              WhatsApp Business. La page n&apos;a pas besoin d&apos;être active,
              il suffit qu&apos;elle existe au nom de l&apos;établissement.
              Vous pouvez en créer une gratuitement en 2 minutes.
            </p>
          </div>
        </details>

        <details className="group rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 open:bg-[var(--muted)]/40">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--foreground)]">
            Quelles données sont partagées avec Facebook ?
          </summary>
          <div className="mt-2 space-y-2 text-sm text-[var(--color-lr-text-secondary)]">
            <p>
              Facebook reçoit uniquement les éléments techniques nécessaires à
              l&apos;envoi de messages WhatsApp Business :
            </p>
            <ul className="ml-4 list-disc space-y-1">
              <li>Le <strong>nom de votre établissement</strong>, le <strong>numéro de téléphone</strong> que vous activez, et l&apos;<strong>email du responsable</strong> (informations d&apos;identification).</li>
              <li>Le <strong>contenu des messages</strong> envoyés et reçus avec vos prospects (Facebook les transmet techniquement, comme un opérateur télécom).</li>
            </ul>
            <p>
              Facebook <strong>ne reçoit pas</strong> les données de vos prospects (CRM, historique, statuts) qui restent dans Leadrive. Les conversations WhatsApp sont chiffrées de bout en bout côté prospect.
            </p>
            <p className="text-xs">
              Pour le détail RGPD complet et le DPA, écrivez-nous à{" "}
              <a
                href="mailto:contact@leadrive.fr?subject=DPA%20RGPD%20WhatsApp%20Business"
                className="font-semibold text-accent hover:underline"
              >
                contact@leadrive.fr
              </a>.
            </p>
          </div>
        </details>
      </div>

      <p className="mt-5 text-xs text-[var(--color-lr-text-tertiary)]">
        Une question qui n&apos;est pas dans la liste ?{" "}
        <a
          href="mailto:contact@leadrive.fr?subject=Question%20activation%20WhatsApp%20Business"
          className="text-accent hover:underline"
        >
          contact@leadrive.fr
        </a>{", on est là pour vous aider."}
      </p>
    </div>
  );
}
