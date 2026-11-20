"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@/lib/auth";
import Alert from "@/components/common/Alert";
import EmailAddressCopyBlock from "./EmailAddressCopyBlock";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface EmailConfig {
  emailAddress: string;
  statut: string;
}

/**
 * Carte hero qui met en avant l'import automatique des leads par email
 * (epic 24). Affiche l'adresse unique du tenant, un bouton "Copier", les
 * 3 etapes pour configurer la redirection cote boite mail, et des guides
 * textuels Gmail/Outlook replies par defaut.
 *
 * <p>Utilise dans l'onboarding manager (etape "Prospects") pour transformer
 * cette etape en demonstration de valeur, plutot que de presenter a plat
 * les imports manuels.</p>
 */
export default function ImportEmailHero() {
  const apiFetch = useApiClient();
  const [config, setConfig] = useState<EmailConfig | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    apiFetch(`${API_BASE}/api/parametres/email-import`)
      .then(async (res) => (res.ok ? res.json() : null))
      .then((data) => { if (!cancelled) setConfig(data); })
      .catch(() => { if (!cancelled) setConfig(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [apiFetch]);

  return (
    <div className="card-leadrive p-6">
      <div className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-accent/10 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wide text-accent">
        <svg className="h-3 w-3" fill="currentColor" viewBox="0 0 20 20">
          <path d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z" />
        </svg>
        Recommandé
      </div>

      <h3 className="text-lg font-semibold text-[var(--foreground)] mb-1.5">
        Import automatique par email
      </h3>
      <p className="text-sm text-[var(--color-lr-text-secondary)] mb-5">
        Recevez vos leads dans Leadrive sans aucune action manuelle. L&apos;IA parse les emails de
        La Centrale, Le Bon Coin… et crée les fiches prospects en temps réel.
      </p>

      <div className="mb-5">
        <label className="block text-xs font-medium text-[var(--color-lr-text-secondary)] mb-1.5">
          Votre adresse dédiée
        </label>
        {loading ? (
          <div className="h-12 rounded-lg bg-[var(--muted)] animate-pulse" />
        ) : config?.emailAddress ? (
          <EmailAddressCopyBlock adresse={config.emailAddress} variant="primary" />
        ) : (
          <Alert tone="warning">
            Adresse non disponible. Contactez le support si le problème persiste.
          </Alert>
        )}
      </div>

      <div className="mb-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-lr-text-secondary)] mb-3">
          En 3 étapes
        </p>
        <ol className="space-y-2.5">
          {[
            "Ouvrez la boîte mail de votre établissement (celle qui reçoit déjà vos leads).",
            "Créez une règle de redirection automatique vers l'adresse ci-dessus.",
            "C'est tout. Les prochains leads sont importés en temps réel et traités par l'IA.",
          ].map((texte, i) => (
            <li key={i} className="flex gap-3">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-accent/15 text-[11px] font-semibold text-accent">
                {i + 1}
              </span>
              <span className="text-sm text-[var(--foreground)] leading-relaxed">{texte}</span>
            </li>
          ))}
        </ol>
      </div>

      <div className="space-y-1 pt-3 border-t border-[var(--border)]">
        <GuideDetails titre="Comment faire dans Gmail ?">
          <li>Ouvrez Gmail, cliquez sur l&apos;icône de paramètres puis <strong>Voir tous les paramètres</strong>.</li>
          <li>Onglet <strong>Transfert et POP/IMAP</strong> → <strong>Ajouter une adresse de transfert</strong>.</li>
          <li>Collez l&apos;adresse Leadrive et activez <strong>Transférer une copie</strong>.</li>
        </GuideDetails>

        <GuideDetails titre="Comment faire dans Outlook ?">
          <li>Ouvrez Outlook web, allez dans <strong>Paramètres</strong> puis <strong>Courrier</strong> puis <strong>Transfert</strong>.</li>
          <li>Activez <strong>Activer le transfert</strong> et collez l&apos;adresse Leadrive.</li>
          <li>Enregistrez.</li>
        </GuideDetails>

        <GuideDetails titre="Avec une autre messagerie (OVH, Orange, IONOS…) ?">
          <li>Toutes les messageries pro proposent une fonctionnalité de redirection automatique dans leurs paramètres. Cherchez « redirection », « transfert » ou « forwarding ».</li>
          <li>L&apos;adresse Leadrive est universelle : elle accepte n&apos;importe quelle source d&apos;email.</li>
        </GuideDetails>
      </div>
    </div>
  );
}

function GuideDetails({ titre, children }: { titre: string; children: React.ReactNode }) {
  return (
    <details className="group">
      <summary className="cursor-pointer flex items-center justify-between py-1.5 text-sm font-medium text-[var(--foreground)] hover:text-accent list-none [&::-webkit-details-marker]:hidden">
        <span>{titre}</span>
        <svg className="h-4 w-4 transition-transform group-open:rotate-180 text-[var(--color-lr-text-secondary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </summary>
      <ol className="mt-2 mb-2 space-y-1 pl-4 text-xs text-[var(--color-lr-text-secondary)] list-decimal leading-relaxed">
        {children}
      </ol>
    </details>
  );
}
