"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useApiClient, formatErreur } from "@/lib/auth";
import { useRealtime } from "@/lib/realtime";
import AppLayout from "@/components/AppLayout";
import PageHeader from "@/components/common/PageHeader";
import Alert from "@/components/common/Alert";
import Button from "@/components/common/Button";
import InfoIcon from "@/components/common/InfoIcon";
import StatutBadge from "@/components/common/StatutBadge";
import EmailAddressCopyBlock from "@/components/imports/EmailAddressCopyBlock";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";
import Skeleton, { SkeletonTable } from "@/components/common/Skeleton";
import { KpiCard } from "@/components/common/Card";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface StatsPeriode {
  periodeJours: number;
  nbEmailsRecus: number;
  nbLeadsCrees: number;
  nbLeadsDedupliques: number;
  nbParseEchec: number;
  nbRejetSpam: number;
  tauxParsingOk: number;
}

interface EmailConfig {
  id: number;
  tenantId: number;
  emailAddress: string;
  statut: string;
  nbEmailsRecus: number;
  dernierEmailRecuLe: string | null;
  creeLe: string;
  anciennesAdresses: { adresse: string; remplaceeLe: string }[];
  stats: StatsPeriode | null;
}

interface LogEntry {
  id: number;
  emailFrom: string | null;
  emailSubject: string | null;
  statut: string;
  leadId: number | null;
  sourceDetectee: string | null;
  raisonRejet: string | null;
  creeLe: string;
}

interface PageLogs {
  content: LogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
}

const STATUT_LABEL: Record<string, string> = {
  LEAD_CREE: "Lead créé",
  LEAD_DEDUPLIQUE: "Lead enrichi",
  LEAD_INCOMPLET: "Lead incomplet",
  PARSE_OK: "Parse OK",
  PARSE_ECHEC: "Échec parsing",
  REJETE_SPAM: "Spam rejeté",
  REJETE_RATE_LIMIT: "Rate limit",
};

const STATUT_TONE: Record<string, "success" | "warning" | "danger" | "neutral" | "info"> = {
  LEAD_CREE: "success",
  LEAD_DEDUPLIQUE: "info",
  LEAD_INCOMPLET: "warning",
  PARSE_OK: "success",
  PARSE_ECHEC: "danger",
  REJETE_SPAM: "neutral",
  REJETE_RATE_LIMIT: "warning",
};

export default function EmailImportPage() {
  const apiFetch = useApiClient();
  const [config, setConfig] = useState<EmailConfig | null>(null);
  const [logs, setLogs] = useState<PageLogs | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [erreur, setErreur] = useState("");

  const charger = useCallback(async () => {
    setLoading(true);
    setErreur("");
    try {
      const [configRes, logsRes] = await Promise.all([
        apiFetch(`${API_BASE}/api/parametres/email-import`),
        apiFetch(`${API_BASE}/api/parametres/email-import/logs?page=${page}&size=20`),
      ]);
      if (configRes.status === 404) {
        setConfig(null);
      } else if (configRes.ok) {
        setConfig(await configRes.json());
      } else {
        setErreur("Impossible de charger la configuration");
      }
      if (logsRes.ok) setLogs(await logsRes.json());
    } catch (e) {
      setErreur(formatErreur(e));
    } finally {
      setLoading(false);
    }
  }, [apiFetch, page]);

  useEffect(() => { charger(); }, [charger]);

  // Auto-refresh quand un nouveau lead arrive par email (epic 24)
  const { lastEvent } = useRealtime();
  useEffect(() => {
    if (lastEvent?.type === "lead.created") {
      charger();
    }
  }, [lastEvent, charger]);

  return (
    <AppLayout>
      <div className="mx-auto max-w-5xl px-6 py-6 space-y-6">
        <PageHeader
          eyebrow="Acquisition"
          title="Import automatique par email"
        />

        {loading && !config && !logs && (
          <>
            <Skeleton className="h-28 w-full rounded-xl" />
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-24 rounded-xl" />
              ))}
            </div>
            <SkeletonTable rows={5} cols={6} />
          </>
        )}

        {!loading && erreur && config && (
          <div role="alert">
            <Alert tone="danger" className="flex flex-wrap items-center justify-between gap-3">
              <span>{erreur}</span>
              <Button variant="secondary" size="sm" onClick={charger}>Réessayer</Button>
            </Alert>
          </div>
        )}

        {!loading && !config ? (
          erreur ? (
            <ErrorState message={erreur} onRetry={charger} />
          ) : (
            <Alert tone="warning">
              Aucune adresse email d&apos;import n&apos;est encore configurée pour votre établissement. Contactez le support Leadrive pour activer cette fonctionnalité.
            </Alert>
          )
        ) : !loading && config ? (
          <>
            {/* Carte adresse + bouton copier */}
            <div className="card-leadrive p-6">
              <div className="flex items-center gap-2 mb-3">
                <h2 className="display-h3 text-[var(--foreground)]">Votre adresse dédiée</h2>
                <InfoIcon>
                  Cette adresse est unique à votre établissement. Configurez une redirection automatique depuis votre boîte mail (Gmail, Outlook, OVH…) vers cette adresse pour recevoir vos leads en temps réel.
                </InfoIcon>
                {config.statut === "ACTIVE" ? (
                  <span className="ml-2 inline-flex items-center gap-1 rounded-full bg-success/10 text-success px-2 py-0.5 text-[10px] font-semibold">
                    <span className="h-1.5 w-1.5 rounded-full bg-success" />
                    Active
                  </span>
                ) : (
                  <span className="ml-2 inline-flex items-center gap-1 rounded-full bg-[var(--muted)] text-[var(--color-lr-text-secondary)] px-2 py-0.5 text-[10px] font-semibold">
                    Désactivée
                  </span>
                )}
              </div>
              <EmailAddressCopyBlock adresse={config.emailAddress} variant="secondary" />
              <p className="mt-3 text-xs text-[var(--color-lr-text-tertiary)]">
                Total des emails reçus depuis la création : <strong>{config.nbEmailsRecus ?? 0}</strong>
                {config.dernierEmailRecuLe && (
                  <> · Dernier : {new Date(config.dernierEmailRecuLe).toLocaleString("fr-FR")}</>
                )}
              </p>
              {config.anciennesAdresses && config.anciennesAdresses.length > 0 && (
                <details className="mt-3">
                  <summary className="cursor-pointer text-xs font-medium text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]">
                    Anciennes adresses ({config.anciennesAdresses.length})
                  </summary>
                  <p className="mt-2 mb-2 text-xs text-[var(--color-lr-text-tertiary)]">
                    Ces adresses ne reçoivent plus d&apos;email. Pensez à mettre à jour votre redirection
                    si nécessaire.
                  </p>
                  <ul className="space-y-1.5">
                    {config.anciennesAdresses.map((a, i) => (
                      <li key={i} className="flex flex-wrap items-baseline gap-x-3 text-xs">
                        <code className="font-mono text-[var(--color-lr-text-secondary)] line-through">
                          {a.adresse}
                        </code>
                        <span className="text-[var(--color-lr-text-tertiary)]">
                          remplacée le {new Date(a.remplaceeLe).toLocaleString("fr-FR")}
                        </span>
                      </li>
                    ))}
                  </ul>
                </details>
              )}
            </div>

            {/* Stats 30j */}
            {config.stats && (
              <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                <KpiCard
                  label="Emails reçus (30j)"
                  value={String(config.stats.nbEmailsRecus)}
                  info="Total des emails reçus sur cette adresse durant les 30 derniers jours."
                />
                <KpiCard
                  label="Leads créés"
                  value={String(config.stats.nbLeadsCrees)}
                  valueColor="text-success"
                  info="Nouveaux leads créés à partir des emails."
                />
                <KpiCard
                  label="Doublons enrichis"
                  value={String(config.stats.nbLeadsDedupliques)}
                  valueColor="text-accent"
                  info="Emails matchés sur un lead existant (téléphone ou email) et qui l'ont enrichi au lieu de créer un doublon."
                />
                <KpiCard
                  label="Taux parsing OK"
                  value={`${config.stats.tauxParsingOk}%`}
                  valueColor={
                    config.stats.tauxParsingOk >= 80
                      ? "text-success"
                      : config.stats.tauxParsingOk >= 50
                        ? "text-warning"
                        : "text-danger"
                  }
                  info="Pourcentage d'emails ayant abouti à un lead. Les emails rejetés en spam ne comptent pas comme un échec."
                />
              </div>
            )}

            {/* Bloc guide */}
            <details className="group card-leadrive">
              <summary className="flex cursor-pointer items-center gap-2 px-5 py-4 text-sm font-medium text-accent list-none">
                <span className="inline-flex h-4 w-4 items-center justify-center rounded-full border border-accent text-[10px] font-semibold">?</span>
                Comment configurer la redirection depuis votre boîte mail ?
                <svg
                  className="ml-auto h-4 w-4 transition-transform group-open:rotate-180"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </summary>
              <div className="px-5 pb-5 space-y-4 text-sm text-[var(--color-lr-text-secondary)]">
                  <GuideRedirection
                    titre="Gmail / Google Workspace"
                    etapes={[
                      "Ouvrir Gmail > Paramètres (roue dentée) > Voir tous les paramètres",
                      "Onglet « Transfert et POP/IMAP »",
                      "Cliquer sur « Ajouter une adresse de transfert » et coller votre adresse Leadrive",
                      "Valider le code de confirmation envoyé par Google",
                      "Choisir « Transférer une copie » (l'original reste dans votre boîte)",
                    ]}
                  />
                  <GuideRedirection
                    titre="Outlook / Office 365"
                    etapes={[
                      "Ouvrir Outlook > Paramètres > Courrier > Transfert",
                      "Activer « Activer le transfert »",
                      "Coller votre adresse Leadrive",
                      "Cocher « Conserver une copie des messages transférés »",
                    ]}
                  />
                  <GuideRedirection
                    titre="OVH Mail / Roundcube"
                    etapes={[
                      "Espace client OVH > Emails > Gestion des redirections",
                      "Cliquer sur « Ajouter une redirection »",
                      "Adresse source : celle qui reçoit les leads",
                      "Adresse destination : votre adresse Leadrive",
                      "Choisir « Conserver une copie locale »",
                    ]}
                  />
                  <GuideRedirection
                    titre="1&1 / IONOS"
                    etapes={[
                      "Espace client IONOS > Email > Gestion des comptes",
                      "Sélectionner le compte email",
                      "Transfert de mails > Activer",
                      "Coller votre adresse Leadrive",
                      "Cocher « Conserver les originaux »",
                    ]}
                  />
                <p className="text-xs text-[var(--color-lr-text-tertiary)]">
                  Besoin d&apos;aide ? <Link href="/aide" className="text-accent hover:underline">Consultez la page d&apos;aide</Link>.
                </p>
              </div>
            </details>

            {/* Historique des emails */}
            <div className="card-leadrive p-6">
              <div className="flex items-center gap-2 mb-4">
                <h2 className="display-h3 text-[var(--foreground)]">Historique des emails reçus</h2>
                <InfoIcon>
                  Tous les emails reçus sur votre adresse Leadrive. Si un email apparaît en « Échec parsing », contactez le support.
                </InfoIcon>
              </div>
              {!logs || logs.content.length === 0 ? (
                <EmptyState
                  eyebrow="Boîte vide"
                  eyebrowTone="muted"
                  title="Aucun email reçu pour l'instant."
                  description="Dès qu'un email arrive sur votre adresse Leadrive, il apparaît ici avec son statut de traitement."
                  size="sm"
                  align="center"
                />
              ) : (
                <>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead>
                        <tr className="border-b border-[var(--border)] text-[var(--color-lr-text-secondary)]">
                          <th scope="col" className="px-3 py-2 font-medium">Date</th>
                          <th scope="col" className="px-3 py-2 font-medium">Expéditeur</th>
                          <th scope="col" className="px-3 py-2 font-medium">Objet</th>
                          <th scope="col" className="px-3 py-2 font-medium">Source</th>
                          <th scope="col" className="px-3 py-2 font-medium">Statut</th>
                          <th scope="col" className="px-3 py-2 font-medium">Lead</th>
                        </tr>
                      </thead>
                      <tbody>
                        {logs.content.map((log) => (
                          <tr key={log.id} className="border-b border-[var(--border)]">
                            <td className="px-3 py-2 text-xs text-[var(--color-lr-text-secondary)] whitespace-nowrap">
                              {new Date(log.creeLe).toLocaleString("fr-FR")}
                            </td>
                            <td className="px-3 py-2 text-xs text-[var(--foreground)] truncate max-w-[180px]">
                              {log.emailFrom ?? "—"}
                            </td>
                            <td className="px-3 py-2 text-xs text-[var(--foreground)] truncate max-w-[260px]">
                              {log.emailSubject ?? "—"}
                            </td>
                            <td className="px-3 py-2 text-xs">
                              {log.sourceDetectee ? (
                                <span className="rounded bg-[var(--muted)] px-2 py-0.5 text-[10px] font-mono">
                                  {log.sourceDetectee}
                                </span>
                              ) : (
                                <span className="text-[var(--color-lr-text-tertiary)]">—</span>
                              )}
                            </td>
                            <td className="px-3 py-2">
                              <StatutBadge
                                statut={STATUT_LABEL[log.statut] || log.statut}
                                tone={STATUT_TONE[log.statut] || "neutral"}
                              />
                            </td>
                            <td className="px-3 py-2">
                              {log.leadId ? (
                                <Link
                                  href={`/leads/${log.leadId}`}
                                  className="text-xs text-accent hover:underline"
                                >
                                  Voir #{log.leadId}
                                </Link>
                              ) : (
                                <span className="text-xs text-[var(--color-lr-text-tertiary)]">—</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {logs.totalPages > 1 && (
                    <div className="mt-4 flex items-center justify-between text-xs">
                      <span className="text-[var(--color-lr-text-secondary)]">
                        Page {logs.number + 1} / {logs.totalPages} · {logs.totalElements} emails
                      </span>
                      <div className="flex gap-2">
                        <Button
                          variant="secondary"
                          size="sm"
                          disabled={logs.number === 0}
                          onClick={() => setPage(p => Math.max(0, p - 1))}
                        >
                          Précédent
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          disabled={logs.number >= logs.totalPages - 1}
                          onClick={() => setPage(p => p + 1)}
                        >
                          Suivant
                        </Button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          </>
        ) : null}
      </div>
    </AppLayout>
  );
}

function GuideRedirection({ titre, etapes }: { titre: string; etapes: string[] }) {
  return (
    <div>
      <h3 className="text-sm font-semibold text-[var(--foreground)] mb-2">{titre}</h3>
      <ol className="list-decimal list-inside space-y-1 text-xs text-[var(--color-lr-text-secondary)] pl-2">
        {etapes.map((etape, i) => <li key={i}>{etape}</li>)}
      </ol>
    </div>
  );
}
