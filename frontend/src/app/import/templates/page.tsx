"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useApiClient, formatErreur } from "@/lib/auth";
import AppLayout from "@/components/AppLayout";
import PageHeader from "@/components/common/PageHeader";
import Tabs from "@/components/common/Tabs";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface MappingTemplate {
  id: number;
  nom: string;
  headersOriginaux: string[];
  mappings: Record<string, string>;
  nombreUtilisations: number;
  derniereUtilisation: string | null;
}

export default function TemplatesPage() {
  const apiFetch = useApiClient();
  const router = useRouter();
  const [templates, setTemplates] = useState<MappingTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [editId, setEditId] = useState<number | null>(null);
  const [editNom, setEditNom] = useState("");
  const [deleteConfirm, setDeleteConfirm] = useState<number | null>(null);

  const fetchTemplates = useCallback(async () => {
    setLoading(true);
    setErreur(null);
    try {
      const res = await apiFetch(`${API_BASE}/api/mapping-templates`);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data = await res.json();
      setTemplates(Array.isArray(data) ? data : []);
    } catch (err) {
      setErreur(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => { fetchTemplates(); }, [fetchTemplates]);

  async function handleRename(id: number) {
    if (!editNom.trim()) return;
    try {
      await apiFetch(`${API_BASE}/api/mapping-templates/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ nom: editNom }),
      });
      setEditId(null);
      fetchTemplates();
    } catch {
      // Silently handle
    }
  }

  async function handleDelete(id: number) {
    try {
      await apiFetch(`${API_BASE}/api/mapping-templates/${id}`, { method: "DELETE" });
      setDeleteConfirm(null);
      fetchTemplates();
    } catch {
      // Silently handle
    }
  }

  function formatRelativeDate(dateStr: string | null): string {
    if (!dateStr) return "Jamais";
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays === 0) return "Aujourd'hui";
    if (diffDays === 1) return "Hier";
    if (diffDays < 7) return `Il y a ${diffDays} jours`;
    if (diffDays < 30) return `Il y a ${Math.floor(diffDays / 7)} semaine${Math.floor(diffDays / 7) > 1 ? "s" : ""}`;
    return date.toLocaleDateString("fr-FR", { day: "2-digit", month: "2-digit", year: "numeric" });
  }

  return (
    <AppLayout>
      <div className="mx-auto max-w-4xl px-6 py-6 space-y-6">
        <PageHeader
          eyebrow="Acquisition"
          title="Templates de mapping"
          subtitle="Vos mappings sauvegardés appliqués automatiquement aux imports avec les mêmes colonnes."
        />

        {/* Onglets : Import / Historique / Templates (cohesion avec /import) */}
        <Tabs
          value="templates"
          onChange={(v) => {
            if (v === "import") router.push("/import");
            else if (v === "historique") router.push("/import?tab=historique");
            // "templates" = deja ici
          }}
          items={[
            { value: "import", label: "Nouvel import" },
            { value: "historique", label: "Historique" },
            { value: "templates", label: "Templates" },
          ]}
        />

        {/* Encart explicatif : uniquement apres chargement et seulement si on a des
            templates (sinon l'empty state porte deja l'explication). */}
        {!loading && templates.length > 0 && (
          <div className="rounded-xl border border-accent/30 bg-accent/5 p-4">
            <div className="flex items-start gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent/10 text-accent">
                <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                    d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <p className="text-sm text-[var(--color-lr-text-secondary)]">
                Les templates sont <strong className="text-[var(--foreground)]">créés automatiquement</strong>{" "}
                lors de chaque nouvel import. Quand vous importez un fichier avec les mêmes colonnes,
                le mapping est appliqué instantanément, pas besoin de re-mapper.
              </p>
            </div>
          </div>
        )}

        <div className="card-leadrive">
          {/* Erreur de chargement sans données existantes : remplace le contenu */}
          {!loading && erreur && templates.length === 0 && (
            <ErrorState message={erreur} onRetry={fetchTemplates} />
          )}

          {loading && (
            <div className="p-4 space-y-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="flex items-center gap-4 py-3">
                  <div className="h-4 w-40 rounded animate-shimmer" />
                  <div className="h-4 w-12 rounded animate-shimmer" />
                  <div className="h-4 w-24 rounded animate-shimmer" />
                  <div className="h-4 w-20 rounded animate-shimmer" />
                </div>
              ))}
            </div>
          )}

          {!loading && !erreur && templates.length === 0 && (
            <EmptyState
              eyebrow="Aucun template"
              eyebrowTone="accent"
              title="Aucun template pour l'instant."
              description="Vos templates de mapping s'affichent ici après votre premier import. Ils accélèrent les imports suivants."
              visual={
                <div className="flex h-14 w-14 items-center justify-center rounded-full bg-accent/10">
                  <svg className="h-6 w-6 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                      d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                  </svg>
                </div>
              }
              action={
                <Link
                  href="/import"
                  className="rounded-lg btn-primary px-5 py-2 text-sm font-medium text-white"
                >
                  Faire un import
                </Link>
              }
              size="md"
              align="center"
              divider
            />
          )}

          {templates.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-[var(--border)] text-[var(--color-lr-text-secondary)]">
                    <th className="px-4 py-3 font-medium">Nom</th>
                    <th className="px-4 py-3 font-medium">Colonnes</th>
                    <th className="px-4 py-3 font-medium">Utilisations</th>
                    <th className="px-4 py-3 font-medium">Dernière utilisation</th>
                    <th className="px-4 py-3 font-medium text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {templates.map((tpl) => (
                    <tr key={tpl.id} className="border-b border-[var(--border)] last:border-b-0 text-[var(--foreground)] hover:bg-[var(--muted)]/30 transition-colors">
                      <td className="px-4 py-3">
                        {editId === tpl.id ? (
                          <div className="flex items-center gap-2">
                            <input
                              type="text"
                              value={editNom}
                              onChange={(e) => setEditNom(e.target.value)}
                              className="rounded border border-[var(--border)] bg-[var(--card)] px-2 py-1 text-sm"
                              onKeyDown={(e) => { if (e.key === "Enter") handleRename(tpl.id); if (e.key === "Escape") setEditId(null); }}
                              autoFocus
                            />
                            <button onClick={() => handleRename(tpl.id)} className="text-xs text-accent hover:underline">OK</button>
                            <button onClick={() => setEditId(null)} className="text-xs text-[var(--color-lr-text-tertiary)] hover:underline">Annuler</button>
                          </div>
                        ) : (
                          <span className="font-medium">{tpl.nom}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-[var(--color-lr-text-secondary)]">{tpl.headersOriginaux?.length || 0}</td>
                      <td className="px-4 py-3 font-mono">{tpl.nombreUtilisations}</td>
                      <td className="px-4 py-3 text-[var(--color-lr-text-secondary)]">{formatRelativeDate(tpl.derniereUtilisation)}</td>
                      <td className="px-4 py-3 text-right">
                        {deleteConfirm === tpl.id ? (
                          <div className="flex items-center justify-end gap-2">
                            <span className="text-xs text-danger">Supprimer ?</span>
                            <button onClick={() => handleDelete(tpl.id)} className="text-xs font-medium text-danger hover:underline">Oui</button>
                            <button onClick={() => setDeleteConfirm(null)} className="text-xs text-[var(--color-lr-text-tertiary)] hover:underline">Non</button>
                          </div>
                        ) : (
                          <div className="flex items-center justify-end gap-3">
                            <button
                              onClick={() => { setEditId(tpl.id); setEditNom(tpl.nom); }}
                              className="text-xs text-accent hover:underline"
                            >
                              Renommer
                            </button>
                            <button
                              onClick={() => setDeleteConfirm(tpl.id)}
                              className="text-xs text-danger hover:underline"
                            >
                              Supprimer
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </AppLayout>
  );
}
