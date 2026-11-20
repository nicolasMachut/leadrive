"use client";

import { useEffect, useState, useCallback } from "react";
import { useApiClient, formatErreur } from "@/lib/auth";
import Alert from "@/components/common/Alert";
import PageHeader from "@/components/common/PageHeader";
import StatutBadge from "@/components/common/StatutBadge";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface DemandeDemo {
  id: number;
  nom: string;
  concession: string;
  email: string;
  telephone: string | null;
  volumeLeads: string | null;
  statut: string;
  creeLe: string;
  traiteLe: string | null;
  traitePar: string | null;
}

export default function DemandesDemoPage() {
  const apiFetch = useApiClient();
  const [demandes, setDemandes] = useState<DemandeDemo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchDemandes = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/demandes-demo`);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setDemandes(await res.json());
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => {
    fetchDemandes();
  }, [fetchDemandes]);

  return (
    <div className="p-8 animate-fade-in">
      <PageHeader
        eyebrow="Back-office"
        title="Demandes de démo"
        subtitle={`${demandes.length} demande${demandes.length !== 1 ? "s" : ""} reçue${demandes.length !== 1 ? "s" : ""}`}
      />

      {error && <Alert tone="danger" className="mb-6">{error}</Alert>}

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-16 rounded-xl border border-[var(--border)] bg-[var(--card)] animate-shimmer" />
          ))}
        </div>
      ) : (
        <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-[var(--border)] bg-[var(--muted)]">
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Nom</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Concession</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Email</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Téléphone</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Volume leads</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Statut</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Date</th>
              </tr>
            </thead>
            <tbody>
              {demandes.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-[var(--color-lr-text-secondary)]">
                    Aucune demande de démo
                  </td>
                </tr>
              ) : (
                demandes.map((d) => (
                  <tr key={d.id} className="border-b border-[var(--border)] hover:bg-[var(--muted)] transition-colors last:border-b-0">
                    <td className="px-4 py-3 font-medium text-[var(--foreground)]">{d.nom}</td>
                    <td className="px-4 py-3 text-[var(--foreground)]">{d.concession}</td>
                    <td className="px-4 py-3 text-[var(--foreground)]">
                      <a href={`mailto:${d.email}`} className="text-accent hover:underline">{d.email}</a>
                    </td>
                    <td className="px-4 py-3 text-[var(--color-lr-text-secondary)]">{d.telephone || "—"}</td>
                    <td className="px-4 py-3 text-[var(--color-lr-text-secondary)]">{d.volumeLeads || "—"}</td>
                    <td className="px-4 py-3"><StatutBadge statut={d.statut} /></td>
                    <td className="px-4 py-3 text-[var(--color-lr-text-secondary)] text-xs">
                      {new Date(d.creeLe).toLocaleDateString("fr-FR", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
