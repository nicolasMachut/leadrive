"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams, useRouter } from "next/navigation";
import { useApiClient } from "@/lib/auth";
import PageHeader from "@/components/common/PageHeader";
import Alert from "@/components/common/Alert";
import Button from "@/components/common/Button";
import StatutBadge from "@/components/common/StatutBadge";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface LogEntry {
  id: number;
  tenantId: number;
  emailFrom: string | null;
  emailSubject: string | null;
  statut: string;
  leadId: number | null;
  sourceDetectee: string | null;
  dureeParsingMs: number | null;
  raisonRejet: string | null;
  creeLe: string;
}

interface PageLogs {
  content: LogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
}

const STATUTS = ["LEAD_CREE", "LEAD_DEDUPLIQUE", "LEAD_INCOMPLET", "PARSE_ECHEC", "REJETE_SPAM", "REJETE_RATE_LIMIT"];

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

function PageContenu() {
  const apiFetch = useApiClient();
  const router = useRouter();
  const searchParams = useSearchParams();

  const tenantIdParam = searchParams.get("tenantId") ?? "";
  const statutParam = searchParams.get("statut") ?? "";
  const pageParam = Number(searchParams.get("page") ?? "0");

  const [logs, setLogs] = useState<PageLogs | null>(null);
  const [loading, setLoading] = useState(true);
  const [erreur, setErreur] = useState("");
  const [detailLog, setDetailLog] = useState<LogEntry & { emailBodyRaw?: string; resultatParsing?: string } | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const charger = useCallback(async () => {
    setLoading(true);
    setErreur("");
    try {
      const url = new URL(`${API_BASE}/api/admin/email-logs`);
      url.searchParams.set("page", String(pageParam));
      url.searchParams.set("size", "30");
      if (tenantIdParam) url.searchParams.set("tenantId", tenantIdParam);
      if (statutParam) url.searchParams.set("statut", statutParam);
      const res = await apiFetch(url.toString());
      if (res.ok) setLogs(await res.json());
      else setErreur("Impossible de charger les logs");
    } catch {
      setErreur("Erreur de chargement");
    } finally {
      setLoading(false);
    }
  }, [apiFetch, pageParam, tenantIdParam, statutParam]);

  useEffect(() => { charger(); }, [charger]);

  function setFiltre(name: string, value: string | null) {
    const params = new URLSearchParams(searchParams.toString());
    if (value) params.set(name, value);
    else params.delete(name);
    if (name !== "page") params.delete("page");
    router.replace(`/admin/email-logs?${params.toString()}`, { scroll: false });
  }

  async function ouvrirDetail(id: number) {
    setDetailLoading(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/email-logs/${id}`);
      if (res.ok) setDetailLog(await res.json());
    } finally {
      setDetailLoading(false);
    }
  }

  // Retour contextuel : si on est arrive depuis une concession precise,
  // retour vers son detail ; sinon retour vers la liste.
  const retourHref = tenantIdParam ? `/admin/concessions/${tenantIdParam}` : "/admin/concessions";
  const retourLabel = tenantIdParam ? "← Retour à l'établissement" : "← Concessions";

  return (
    <div className="p-8 space-y-6">
      <PageHeader
        eyebrow="Admin"
        title="Logs d'import email"
        subtitle="Tous les emails reçus via SendGrid Inbound Parse, cross-tenant"
        actions={
          <Link href={retourHref} className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]">
            {retourLabel}
          </Link>
        }
      />

      {erreur && <Alert tone="danger">{erreur}</Alert>}

      {/* Filtres */}
      <div className="flex flex-wrap items-center gap-3 rounded-lg border border-[var(--border)] bg-[var(--card)] p-3">
        <label className="text-xs text-[var(--color-lr-text-secondary)]">
          Tenant ID :
          <input
            type="number"
            value={tenantIdParam}
            onChange={(e) => setFiltre("tenantId", e.target.value || null)}
            placeholder="tous"
            className="ml-2 w-24 rounded-md border border-[var(--border)] bg-[var(--background)] px-2 py-1 text-sm font-mono"
          />
        </label>
        <label className="text-xs text-[var(--color-lr-text-secondary)]">
          Statut :
          <select
            value={statutParam}
            onChange={(e) => setFiltre("statut", e.target.value || null)}
            className="ml-2 rounded-md border border-[var(--border)] bg-[var(--background)] px-2 py-1 text-sm"
          >
            <option value="">tous</option>
            {STATUTS.map(s => <option key={s} value={s}>{STATUT_LABEL[s] || s}</option>)}
          </select>
        </label>
        {(tenantIdParam || statutParam) && (
          <Button variant="ghost" size="sm" onClick={() => router.replace("/admin/email-logs", { scroll: false })}>
            Réinitialiser
          </Button>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-[var(--border)] border-t-accent" />
        </div>
      ) : !logs || logs.content.length === 0 ? (
        <div className="rounded-lg border border-[var(--border)] bg-[var(--card)] p-12 text-center text-sm text-[var(--color-lr-text-secondary)]">
          Aucun email avec ces filtres.
        </div>
      ) : (
        <div className="rounded-lg border border-[var(--border)] bg-[var(--card)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[var(--border)] bg-[var(--muted)] text-[var(--color-lr-text-secondary)] text-xs uppercase tracking-wider">
                  <th className="px-3 py-2 font-medium">Date</th>
                  <th className="px-3 py-2 font-medium">Tenant</th>
                  <th className="px-3 py-2 font-medium">Expéditeur</th>
                  <th className="px-3 py-2 font-medium">Objet</th>
                  <th className="px-3 py-2 font-medium">Source</th>
                  <th className="px-3 py-2 font-medium">Statut</th>
                  <th className="px-3 py-2 font-medium">Durée</th>
                  <th className="px-3 py-2 font-medium" />
                </tr>
              </thead>
              <tbody>
                {logs.content.map((log) => (
                  <tr key={log.id} className="border-b border-[var(--border)]">
                    <td className="px-3 py-2 text-xs text-[var(--color-lr-text-secondary)] whitespace-nowrap">
                      {new Date(log.creeLe).toLocaleString("fr-FR")}
                    </td>
                    <td className="px-3 py-2 text-xs font-mono">
                      <Link
                        href={`/admin/concessions/${log.tenantId}`}
                        className="text-accent hover:underline"
                      >
                        #{log.tenantId}
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-xs truncate max-w-[180px]">
                      {log.emailFrom ?? "—"}
                    </td>
                    <td className="px-3 py-2 text-xs truncate max-w-[260px]">
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
                    <td className="px-3 py-2 text-xs text-[var(--color-lr-text-secondary)] tabular-nums">
                      {log.dureeParsingMs != null ? `${log.dureeParsingMs} ms` : "—"}
                    </td>
                    <td className="px-3 py-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => ouvrirDetail(log.id)}
                        disabled={detailLoading}
                      >
                        Détail
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {logs.totalPages > 1 && (
            <div className="flex items-center justify-between px-4 py-3 border-t border-[var(--border)] text-xs">
              <span className="text-[var(--color-lr-text-secondary)]">
                Page {logs.number + 1} / {logs.totalPages} · {logs.totalElements} emails
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={logs.number === 0}
                  onClick={() => setFiltre("page", String(Math.max(0, logs.number - 1)))}
                >
                  Précédent
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={logs.number >= logs.totalPages - 1}
                  onClick={() => setFiltre("page", String(logs.number + 1))}
                >
                  Suivant
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Detail modal */}
      {detailLog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => setDetailLog(null)}>
          <div
            className="relative max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-xl bg-[var(--card)] p-6 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              onClick={() => setDetailLog(null)}
              className="absolute right-4 top-4 text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"
            >
              ×
            </button>
            <h3 className="text-base font-semibold mb-4">Détail email #{detailLog.id}</h3>
            <dl className="grid grid-cols-2 gap-3 text-sm mb-4">
              <div>
                <dt className="text-xs text-[var(--color-lr-text-secondary)]">Date</dt>
                <dd>{new Date(detailLog.creeLe).toLocaleString("fr-FR")}</dd>
              </div>
              <div>
                <dt className="text-xs text-[var(--color-lr-text-secondary)]">Tenant</dt>
                <dd className="font-mono">#{detailLog.tenantId}</dd>
              </div>
              <div>
                <dt className="text-xs text-[var(--color-lr-text-secondary)]">De</dt>
                <dd className="truncate">{detailLog.emailFrom ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-xs text-[var(--color-lr-text-secondary)]">Source détectée</dt>
                <dd>{detailLog.sourceDetectee ?? "—"}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-xs text-[var(--color-lr-text-secondary)]">Objet</dt>
                <dd>{detailLog.emailSubject ?? "—"}</dd>
              </div>
              {detailLog.raisonRejet && (
                <div className="col-span-2">
                  <dt className="text-xs text-[var(--color-lr-text-secondary)]">Raison rejet</dt>
                  <dd className="text-danger">{detailLog.raisonRejet}</dd>
                </div>
              )}
              {detailLog.leadId && (
                <div className="col-span-2">
                  <dt className="text-xs text-[var(--color-lr-text-secondary)]">Lead créé</dt>
                  <dd className="font-mono text-xs">#{detailLog.leadId}</dd>
                </div>
              )}
            </dl>
            {detailLog.emailBodyRaw && (
              <div className="mb-4">
                <h4 className="text-xs font-semibold text-[var(--color-lr-text-secondary)] mb-1">Body brut</h4>
                <pre className="text-xs whitespace-pre-wrap rounded border border-[var(--border)] bg-[var(--muted)] p-3 max-h-60 overflow-y-auto">
                  {detailLog.emailBodyRaw}
                </pre>
              </div>
            )}
            {detailLog.resultatParsing && (
              <div>
                <h4 className="text-xs font-semibold text-[var(--color-lr-text-secondary)] mb-1">Résultat IA</h4>
                <pre className="text-xs whitespace-pre-wrap rounded border border-[var(--border)] bg-[var(--muted)] p-3 max-h-60 overflow-y-auto font-mono">
                  {detailLog.resultatParsing}
                </pre>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default function AdminEmailLogsPage() {
  return (
    <Suspense fallback={<div className="p-8">Chargement…</div>}>
      <PageContenu />
    </Suspense>
  );
}
