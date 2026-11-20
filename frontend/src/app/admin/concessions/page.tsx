"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { useApiClient, formatErreur } from "@/lib/auth";
import { formatPhoneInput } from "@/lib/phone";
import Alert from "@/components/common/Alert";
import Button from "@/components/common/Button";
import { Input } from "@/components/common/Input";
import Modal from "@/components/common/Modal";
import PageHeader from "@/components/common/PageHeader";
import { SkeletonTable } from "@/components/common/Skeleton";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface ConcessionSimple {
  id: number | null;
  nom: string;
  creeLe: string | null;
  whatsappStatut: string | null;
  whatsappFrom: string | null;
}

interface CreateForm { nomConcession: string; }
const emptyForm: CreateForm = { nomConcession: "" };

/**
 * Page admin (SUPER_ADMIN) : liste des etablissements.
 *
 * Pour les actions (inviter manager, retirer manager, desactiver, re-pousser
 * profil WhatsApp), cliquer sur le nom -> page detail
 * /admin/concessions/[id] qui regroupe tout dans une UI dediee.
 */
export default function ConcessionsPage() {
  const apiFetch = useApiClient();
  const [concessions, setConcessions] = useState<ConcessionSimple[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [form, setForm] = useState<CreateForm>(emptyForm);
  const [creating, setCreating] = useState(false);

  const fetchConcessions = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/concessions`);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setConcessions(await res.json());
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => {
    void fetchConcessions();
  }, [fetchConcessions]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!form.nomConcession.trim()) {
      setError("Le nom de l'établissement est obligatoire.");
      return;
    }
    setCreating(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/concessions`, {
        method: "POST",
        body: JSON.stringify({ nomConcession: form.nomConcession.trim() }),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Erreur ${res.status}`);
      }
      setShowCreateModal(false);
      setForm(emptyForm);
      await fetchConcessions();
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="p-8">
      <PageHeader
        eyebrow="Admin"
        title="Établissements"
        subtitle="Tous les établissements clients de la plateforme."
        actions={<Button onClick={() => setShowCreateModal(true)}>+ Ajouter</Button>}
      />

      {error && <Alert tone="danger" className="mb-4">{error}</Alert>}

      {loading ? (
        <SkeletonTable rows={5} cols={4} />
      ) : (
        <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-[var(--border)] bg-[var(--muted)]">
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Nom</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">WhatsApp</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Créé le</th>
                <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] text-right">&nbsp;</th>
              </tr>
            </thead>
            <tbody>
              {concessions.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-4 py-8 text-center text-[var(--color-lr-text-secondary)]">
                    Aucun établissement. Cliquez sur &quot;+ Ajouter&quot;.
                  </td>
                </tr>
              ) : (
                concessions.map((c) => (
                  <tr
                    key={c.id ?? c.nom}
                    className={`border-b border-[var(--border)] transition-colors last:border-b-0 ${
                      c.id != null ? "hover:bg-[var(--muted)]" : ""
                    }`}
                  >
                    <td className="px-4 py-3 font-medium text-[var(--foreground)]">
                      {c.id != null ? (
                        <Link
                          href={`/admin/concessions/${c.id}`}
                          className="hover:text-accent hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 rounded"
                        >
                          {c.nom}
                        </Link>
                      ) : (
                        <>
                          {c.nom}
                          <span className="ml-2 text-[10px] uppercase text-warning">non synchro</span>
                        </>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <WhatsAppStatutCell statut={c.whatsappStatut} numero={c.whatsappFrom} />
                    </td>
                    <td className="px-4 py-3 text-[var(--color-lr-text-secondary)] font-mono text-xs">
                      {c.creeLe ? new Date(c.creeLe).toLocaleDateString("fr-FR") : "-"}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {c.id != null && (
                        <Link
                          href={`/admin/concessions/${c.id}`}
                          className="text-xs text-accent hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 rounded"
                        >
                          Détails →
                        </Link>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Modal: creer un etablissement */}
      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Ajouter un établissement"
      >
        <form onSubmit={handleCreate} className="space-y-3">
          <Input
            label="Nom de l'établissement"
            value={form.nomConcession}
            onChange={(e) => setForm({ ...form, nomConcession: e.target.value })}
            required
          />
          <p className="text-xs text-[var(--color-lr-text-secondary)]">
            Le manager configurera son numéro WhatsApp Business lui-même depuis
            « Mon établissement » après sa première connexion.
          </p>
          <div className="flex justify-end gap-3 pt-3">
            <Button type="button" variant="ghost" onClick={() => setShowCreateModal(false)}>Annuler</Button>
            <Button type="submit" disabled={creating}>{creating ? "Création..." : "Créer"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

/** Cell admin : badge couleur du statut WhatsApp + numero formate. */
function WhatsAppStatutCell({ statut, numero }: { statut: string | null; numero: string | null }) {
  if (!statut) {
    return <span className="text-xs text-[var(--color-lr-text-tertiary)]">—</span>;
  }
  const libelles: Record<string, { label: string; tone: "success" | "warning" | "danger" | "neutral" }> = {
    ACTIF: { label: "Actif", tone: "success" },
    ERREUR: { label: "Erreur", tone: "danger" },
    DESACTIVE: { label: "À activer", tone: "neutral" },
  };
  const cfg = libelles[statut] ?? { label: statut, tone: "neutral" as const };
  const toneClasses: Record<string, string> = {
    success: "bg-success/10 text-success",
    warning: "bg-warning/10 text-warning",
    danger: "bg-danger/10 text-danger",
    neutral: "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]",
  };
  return (
    <div className="flex flex-col gap-1">
      <span className={`inline-flex w-fit rounded-full px-2 py-0.5 text-[10px] font-medium ${toneClasses[cfg.tone]}`}>
        {cfg.label}
      </span>
      {numero && (
        <span className="font-mono text-[11px] text-[var(--color-lr-text-secondary)]">
          {formatPhoneInput(numero)}
        </span>
      )}
    </div>
  );
}
