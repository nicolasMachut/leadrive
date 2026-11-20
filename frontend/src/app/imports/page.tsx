"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useApiClient, formatErreur } from "@/lib/auth";
import AppLayout from "@/components/AppLayout";
import Alert from "@/components/common/Alert";
import PageHeader from "@/components/common/PageHeader";
import Button from "@/components/common/Button";
import Card from "@/components/common/Card";
import StatutBadge from "@/components/common/StatutBadge";
import EmptyState from "@/components/common/EmptyState";
import SectionHeader from "@/components/common/SectionHeader";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface Import {
  id: string;
  idImport: number;
  nomFichier: string;
  dateImport: string;
  nombreLignes: number;
  nombreImportees: number;
  nombreErreurs: number;
  statut: string;
}

interface ImportDetail extends Import {
  nombreDoublons: number;
  mappings: { colonneSource: string; champCible: string }[];
}

export default function ImportsPage() {
  const apiFetch = useApiClient();
  const [imports, setImports] = useState<Import[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedImport, setSelectedImport] = useState<ImportDetail | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  useEffect(() => {
    apiFetch(`${API_BASE}/api/imports`)
      .then(async (res) => {
        if (!res.ok) throw new Error(`Erreur ${res.status}`);
        return res.json();
      })
      .then((data) => {
        const list = Array.isArray(data) ? data : data.content || [];
        setImports(list);
      })
      .catch((err) => setError(formatErreur(err)))
      .finally(() => setLoading(false));
  }, [apiFetch]);

  async function loadDetail(importId: string) {
    setLoadingDetail(true);
    try {
      const res = await apiFetch(`${API_BASE}/api/imports/${importId}`);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data: ImportDetail = await res.json();
      setSelectedImport(data);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setLoadingDetail(false);
    }
  }

  return (
    <AppLayout>
      <div className="px-6 py-6">
        <PageHeader eyebrow="Vos imports" title="Historique des imports" />

        {loading && <p className="text-sm text-[var(--color-lr-text-secondary)]">Chargement...</p>}

        {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

        {!loading && !error && imports.length === 0 && (
          <Card>
            <EmptyState
              eyebrow="Historique vide"
              eyebrowTone="accent"
              title="Aucun import effectué."
              description="Importez votre première liste de prospects pour démarrer."
              action={
                <Link
                  href="/import"
                  className="inline-block rounded-lg btn-primary px-6 py-2 text-sm font-medium text-white transition-colors"
                >
                  Lancer un import
                </Link>
              }
              size="md"
              align="center"
              divider
            />
          </Card>
        )}

        {imports.length > 0 && (
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-[var(--border)] bg-[var(--muted)] text-[var(--color-lr-text-secondary)]">
                    <th className="px-4 py-3 font-medium">Fichier</th>
                    <th className="px-4 py-3 font-medium">Date</th>
                    <th className="px-4 py-3 font-medium">Lignes</th>
                    <th className="px-4 py-3 font-medium">Importés</th>
                    <th className="px-4 py-3 font-medium">Erreurs</th>
                    <th className="px-4 py-3 font-medium">Statut</th>
                  </tr>
                </thead>
                <tbody>
                  {imports.map((imp, i) => (
                    <tr
                      key={imp.idImport || imp.id || i}
                      onClick={() => loadDetail(String(imp.idImport || imp.id))}
                      className="cursor-pointer border-b border-[var(--border)] text-[var(--foreground)] transition-colors hover:bg-[var(--muted)] last:border-b-0"
                    >
                      <td className="px-4 py-3 font-medium">{imp.nomFichier || "-"}</td>
                      <td className="px-4 py-3 text-[var(--color-lr-text-secondary)]">
                        {imp.dateImport
                          ? new Date(imp.dateImport).toLocaleDateString("fr-FR", {
                              day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
                            })
                          : "-"}
                      </td>
                      <td className="px-4 py-3 font-mono">{imp.nombreLignes ?? "-"}</td>
                      <td className="px-4 py-3 font-mono text-success">{imp.nombreImportees ?? "-"}</td>
                      <td className="px-4 py-3 font-mono text-danger">{imp.nombreErreurs ?? "-"}</td>
                      <td className="px-4 py-3"><StatutBadge statut={imp.statut || "-"} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}

        {/* Detail panel */}
        {selectedImport && (
          <Card padded className="mt-6">
            <div className="mb-4">
              <SectionHeader
                eyebrow="Détail de l'import"
                eyebrowTone="accent"
                title={selectedImport.nomFichier}
                size="sm"
                actions={<Button variant="ghost" size="sm" onClick={() => setSelectedImport(null)}>Fermer</Button>}
              />
            </div>

            {loadingDetail && <p className="text-sm text-[var(--color-lr-text-secondary)]">Chargement...</p>}

            {!loadingDetail && (
              <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
                <DetailItem label="Statut" value={<StatutBadge statut={selectedImport.statut} />} />
                <DetailItem label="Date d'import" value={selectedImport.dateImport ? new Date(selectedImport.dateImport).toLocaleString("fr-FR") : "-"} />
                <DetailItem label="Nombre de lignes" value={selectedImport.nombreLignes} />
                <DetailItem label="Importés" value={selectedImport.nombreImportees} valueClassName="text-success" />
                <DetailItem label="Erreurs" value={selectedImport.nombreErreurs} valueClassName="text-danger" />
                <DetailItem label="Doublons" value={selectedImport.nombreDoublons ?? "-"} valueClassName="text-warning" />
              </div>
            )}

            {!loadingDetail && selectedImport.mappings && selectedImport.mappings.length > 0 && (
              <div className="mt-4">
                <p className="mb-2 text-sm font-medium text-[var(--foreground)]">Mapping utilisé :</p>
                <div className="flex flex-wrap gap-2">
                  {selectedImport.mappings.map((m, i) => (
                    <span key={i} className="rounded-lg bg-[var(--muted)] px-2 py-1 text-xs text-[var(--color-lr-text-secondary)]">
                      {m.colonneSource} → {m.champCible}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </Card>
        )}
      </div>
    </AppLayout>
  );
}

function DetailItem({ label, value, valueClassName = "text-[var(--foreground)]" }: { label: string; value: React.ReactNode; valueClassName?: string }) {
  return (
    <div>
      <p className="text-[var(--color-lr-text-secondary)]">{label}</p>
      <p className={`font-medium ${valueClassName}`}>{value}</p>
    </div>
  );
}
