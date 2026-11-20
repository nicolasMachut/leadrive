"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useApiClient, formatErreur } from "@/lib/auth";
import { formatPhoneInput } from "@/lib/phone";
import Alert from "@/components/common/Alert";
import PageHeader from "@/components/common/PageHeader";
import { SkeletonGrid } from "@/components/common/Skeleton";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface OnboardingBloque {
  tenantId: number;
  nom: string;
  statut: string;
  whatsappFrom: string | null;
  depuis: string;
  heuresEcoulees: number;
}

export default function OnboardingsBloquesPage() {
  const apiFetch = useApiClient();
  const [items, setItems] = useState<OnboardingBloque[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchItems = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/onboardings-bloques`);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setItems(await res.json());
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => {
    void fetchItems();
  }, [fetchItems]);

  return (
    <div className="p-8 space-y-6">
      <PageHeader
        eyebrow="Admin"
        title="Onboardings bloqués"
        subtitle="Établissements en attente anormalement longue ou en erreur"
      />

        {error && <Alert tone="danger">{error}</Alert>}

        <div className="rounded-lg border border-[var(--border)] bg-[var(--card)] p-4 text-xs text-[var(--color-lr-text-secondary)]">
          <strong className="text-[var(--foreground)]">Critère :</strong>
          <ul className="mt-1 list-disc pl-5 space-y-0.5">
            <li><strong>Erreur</strong> : activation WhatsApp Business rejetée ou erreur technique, intervention requise.</li>
          </ul>
        </div>

        {loading ? (
          <SkeletonGrid count={3} />
        ) : items.length === 0 ? (
          <div className="rounded-lg border border-success/30 bg-success/5 p-8 text-center text-sm text-success">
            ✓ Aucun onboarding bloqué en ce moment.
          </div>
        ) : (
          <div className="rounded-lg border border-[var(--border)] bg-[var(--card)] overflow-hidden">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[var(--border)] bg-[var(--muted)]">
                  <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Établissement</th>
                  <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Statut</th>
                  <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Numéro</th>
                  <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Depuis</th>
                  <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {items.map((b) => (
                  <tr key={b.tenantId} className="border-b border-[var(--border)] last:border-b-0 hover:bg-[var(--muted)] transition-colors">
                    <td className="px-4 py-3 font-medium">
                      <Link href={`/admin/concessions/${b.tenantId}`} className="hover:text-accent hover:underline">
                        {b.nom}
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <StatutBloqueBadge statut={b.statut} />
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-[var(--color-lr-text-secondary)]">
                      {b.whatsappFrom ? formatPhoneInput(b.whatsappFrom) : "—"}
                    </td>
                    <td className="px-4 py-3 text-sm">
                      <div className={b.heuresEcoulees > 72 ? "text-danger font-medium" : "text-[var(--color-lr-text-secondary)]"}>
                        {b.heuresEcoulees}h
                      </div>
                      <div className="text-xs text-[var(--color-lr-text-tertiary)]">
                        {new Date(b.depuis).toLocaleString("fr-FR")}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Link
                        href={`/admin/concessions/${b.tenantId}`}
                        className="text-xs text-accent hover:underline"
                      >
                        Voir le détail →
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
    </div>
  );
}

function StatutBloqueBadge({ statut }: { statut: string }) {
  const config: Record<string, { label: string }> = {
    ERREUR: { label: "Erreur" },
  };
  const cfg = config[statut] ?? { label: statut };
  return (
    <span className="inline-flex rounded-full bg-danger/10 px-2 py-0.5 text-[10px] font-medium text-danger">
      {cfg.label}
    </span>
  );
}
