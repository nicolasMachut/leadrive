"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useApiClient, useCurrentRole, useMe, formatErreur } from "@/lib/auth";
import AppLayout from "@/components/AppLayout";
import Alert from "@/components/common/Alert";
import Button from "@/components/common/Button";
import PageHeader from "@/components/common/PageHeader";
import Modal from "@/components/common/Modal";
import Skeleton, { SkeletonTable } from "@/components/common/Skeleton";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";
import StatutBadge from "@/components/common/StatutBadge";
import { Input } from "@/components/common/Input";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface Vendeur {
  id: number;
  prenom: string;
  nom: string;
  email: string;
  role: string;
  statut: string;
  derniereConnexion: string | null;
}

interface InviteForm {
  prenom: string;
  nom: string;
  email: string;
}

interface ImpactDesactivation {
  desactivationPossible: boolean;
  raisonBlocage?: string;
  nbLeadsAssignes?: number;
  nbConversationsActives?: number;
  nbTachesNonTerminees?: number;
  nbRdvAVenir?: number;
}

interface DeleteTarget {
  userId: number;
  email: string;
  prenom: string;
  nom: string;
  impact: ImpactDesactivation | null;
}

const emptyForm: InviteForm = { prenom: "", nom: "", email: "" };

// Regex stricte aligne avec la validation backend ReqInviterManagerDto.
// Couvre les cas standards sans etre trop laxe (au moins un point dans le domaine).
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Libelles + tons user-facing des statuts vendeur (enums backend bruts).
type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info" | "accent";
const VENDEUR_STATUT_LABELS: Record<string, string> = {
  ACTIF: "Actif",
  INACTIF: "Inactif",
  INVITE: "Invité",
  EN_ATTENTE: "En attente",
};
const VENDEUR_STATUT_TONE: Record<string, BadgeTone> = {
  ACTIF: "success",
  INACTIF: "neutral",
  INVITE: "warning",
  EN_ATTENTE: "warning",
};

export default function EquipePage() {
  const apiFetch = useApiClient();
  const router = useRouter();
  const { role, isLoaded: roleLoaded } = useCurrentRole();
  const me = useMe();
  const [vendeurs, setVendeurs] = useState<Vendeur[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  // Erreur de chargement (vs erreur d'action) : seule celle-ci porte un "Réessayer".
  const [loadError, setLoadError] = useState("");
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState<InviteForm>(emptyForm);
  const [inviting, setInviting] = useState(false);
  const [toast, setToast] = useState("");
  const [deleting, setDeleting] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);
  const [impactLoading, setImpactLoading] = useState(false);

  useEffect(() => {
    if (roleLoaded && role !== "MANAGER") {
      router.replace("/dashboard");
    }
  }, [roleLoaded, role, router]);

  // Auto-dismiss du toast avec cleanup (evite setState sur composant demonte).
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(""), 6000);
    return () => clearTimeout(t);
  }, [toast]);

  const fetchEquipe = useCallback(() => {
    setLoading(true);
    setLoadError("");
    apiFetch(`${API_BASE}/api/equipe`)
      .then((res) => {
        if (!res.ok) throw new Error(`Erreur ${res.status}`);
        return res.json();
      })
      .then(setVendeurs)
      .catch((err) => setLoadError(formatErreur(err)))
      .finally(() => setLoading(false));
  }, [apiFetch]);

  useEffect(() => {
    if (role === "MANAGER") fetchEquipe();
  }, [role, fetchEquipe]);

  async function openDeleteModal(v: Vendeur) {
    setError("");
    setImpactLoading(true);
    // Pre-ouvre la modale avec impact null pour donner un feedback instantané pendant le fetch.
    setDeleteTarget({ userId: v.id, email: v.email, prenom: v.prenom, nom: v.nom, impact: null });
    try {
      const impactRes = await apiFetch(`${API_BASE}/api/equipe/${v.id}/impact-desactivation`);
      if (impactRes.ok) {
        const impact: ImpactDesactivation = await impactRes.json();
        if (impact.desactivationPossible === false) {
          setError(impact.raisonBlocage || "Désactivation impossible.");
          setDeleteTarget(null);
          return;
        }
        setDeleteTarget((prev) => prev && prev.userId === v.id ? { ...prev, impact } : prev);
      } else {
        // Échec du calcul d'impact : on NE laisse PAS confirmer une désactivation à
        // l'aveugle (le manager doit voir combien de leads/conversations sont transférés).
        console.warn("Impact desactivation fetch failed", impactRes.status);
        setError("Impossible de calculer l'impact de la désactivation. Réessayez dans un instant.");
        setDeleteTarget(null);
      }
    } catch (err) {
      console.warn("Impact desactivation fetch error", err);
      setError(formatErreur(err));
      setDeleteTarget(null);
    } finally {
      setImpactLoading(false);
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    const { userId, prenom, nom } = deleteTarget;
    setDeleting(userId);
    try {
      const res = await apiFetch(`${API_BASE}/api/equipe/${userId}?reassigner=true`, { method: "DELETE" });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Erreur ${res.status}`);
      }
      fetchEquipe();
      setToast(`Compte de ${prenom} ${nom} désactivé.`);
      setDeleteTarget(null);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setDeleting(null);
    }
  }

  async function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    setInviting(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/equipe/inviter`, {
        method: "POST",
        body: JSON.stringify(form),
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Erreur ${res.status}`);
      }
      setShowModal(false);
      setForm(emptyForm);
      setToast(`Invitation envoyée à ${form.email}.`);
      fetchEquipe();
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setInviting(false);
    }
  }

  if (!roleLoaded || role !== "MANAGER") return null;

  const emailInvalide = Boolean(form.email) && !EMAIL_REGEX.test(form.email);

  return (
    <AppLayout>
      <div className="px-6 py-6">
        <PageHeader
          eyebrow="Vos vendeurs"
          title="Mon équipe"
          actions={
            <Button variant="primary" onClick={() => setShowModal(true)}>
              + Inviter un vendeur
            </Button>
          }
        />

        {toast && (
          <div role="status" aria-live="polite">
            <Alert tone="success" className="mb-4">{toast}</Alert>
          </div>
        )}
        {loadError && <ErrorState message={loadError} onRetry={fetchEquipe} />}
        {error && (
          <div role="alert">
            <Alert tone="danger" className="mb-4">{error}</Alert>
          </div>
        )}

        {loading ? (
          <SkeletonTable rows={4} cols={6} />
        ) : loadError && vendeurs.length === 0 ? null : (
          <div className="card-leadrive overflow-hidden">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[var(--border)]">
                  <th scope="col" className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Nom</th>
                  <th scope="col" className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Email</th>
                  <th scope="col" className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Rôle</th>
                  <th scope="col" className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Statut</th>
                  <th scope="col" className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)]">Dernière connexion</th>
                  <th scope="col" className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] text-right">Action</th>
                </tr>
              </thead>
              <tbody className="stagger-children">
                {vendeurs.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-10">
                      <EmptyState
                        eyebrow="Équipe vide"
                        eyebrowTone="muted"
                        title="Aucun vendeur dans votre équipe."
                        description="Invitez vos premiers vendeurs pour qu'ils accèdent à Leadrive, gèrent leurs prospects WhatsApp et reçoivent les alertes de rappel."
                        size="md"
                        align="center"
                        divider
                      />
                    </td>
                  </tr>
                ) : (
                  vendeurs.map((v) => {
                    const initials = `${(v.prenom[0] || "").toUpperCase()}${(v.nom[0] || "").toUpperCase()}`;
                    return (
                      <tr key={v.id} className="group border-b border-[var(--border)] hover:bg-[var(--muted)]/70 transition-colors">
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-accent/10 text-[12px] font-bold text-accent ring-0 ring-accent/0 transition-all duration-300 group-hover:ring-2 group-hover:ring-accent/40 group-hover:bg-accent/15 group-hover:scale-105">
                              {initials}
                            </div>
                            <span className="font-medium text-[var(--foreground)]">{v.prenom} {v.nom}</span>
                          </div>
                        </td>
                        <td className="px-4 py-3 text-[var(--color-lr-text-secondary)]">{v.email}</td>
                        <td className="px-4 py-3">
                          <StatutBadge statut={v.role === "MANAGER" ? "Manager" : "Vendeur"} tone={v.role === "MANAGER" ? "accent" : "info"} />
                        </td>
                        <td className="px-4 py-3">
                          <VendeurStatusBadge statut={v.statut} />
                        </td>
                        <td className="px-4 py-3 text-[var(--color-lr-text-secondary)] font-mono text-xs">
                          {v.derniereConnexion && !isNaN(new Date(v.derniereConnexion).getTime())
                            ? new Date(v.derniereConnexion).toLocaleDateString("fr-FR")
                            : <span className="text-[var(--color-lr-text-tertiary)] italic">Jamais connecté</span>}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {v.statut === "INACTIF" ? (
                            <span className="text-xs text-[var(--color-lr-text-tertiary)] italic">Inactif</span>
                          ) : me?.email && v.email === me.email ? (
                            <span className="text-xs text-[var(--color-lr-text-tertiary)] italic">Vous-même</span>
                          ) : (
                            <Button
                              variant="danger"
                              size="sm"
                              onClick={() => openDeleteModal(v)}
                              disabled={deleting === v.id || impactLoading}
                            >
                              {deleting === v.id ? "…" : "Désactiver"}
                            </Button>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        <Modal
          open={!!deleteTarget}
          onClose={() => !deleting && setDeleteTarget(null)}
          title={deleteTarget ? `Désactiver ${deleteTarget.prenom} ${deleteTarget.nom} ?` : "Désactiver"}
          description={deleteTarget ? `${deleteTarget.email} perdra l'accès à la plateforme.` : undefined}
          size="md"
          footer={
            deleteTarget && (
              <>
                <Button variant="secondary" onClick={() => setDeleteTarget(null)} disabled={!!deleting}>
                  Annuler
                </Button>
                {/* Action destructive primaire : bouton plein rouge (emphase volontaire,
                    plus forte que le variant danger outline du Button partage). */}
                <button
                  type="button"
                  onClick={confirmDelete}
                  disabled={!!deleting || impactLoading}
                  className="rounded-lg bg-danger px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-danger/90 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {deleting ? "Désactivation…" : "Désactiver"}
                </button>
              </>
            )
          }
        >
          {deleteTarget && (
            <div className="space-y-4">
              {impactLoading && !deleteTarget.impact ? (
                <div className="space-y-2" role="status" aria-live="polite" aria-label="Calcul de l'impact en cours">
                  <Skeleton className="h-4 w-44" />
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-4 w-3/4" />
                </div>
              ) : deleteTarget.impact ? (
                (() => {
                  const impact = deleteTarget.impact;
                  const items: { icon: string; label: string }[] = [];
                  if ((impact.nbLeadsAssignes ?? 0) > 0) {
                    items.push({ icon: "leads", label: `${impact.nbLeadsAssignes} lead${impact.nbLeadsAssignes! > 1 ? "s" : ""} passeront en non-assignés (visibles dans votre vue manager).` });
                  }
                  if ((impact.nbConversationsActives ?? 0) > 0) {
                    items.push({ icon: "conv", label: `${impact.nbConversationsActives} conversation${impact.nbConversationsActives! > 1 ? "s" : ""} en cours vous seront réassignées.` });
                  }
                  if ((impact.nbTachesNonTerminees ?? 0) > 0) {
                    const rdvNote = (impact.nbRdvAVenir ?? 0) > 0 ? ` (dont ${impact.nbRdvAVenir} RDV à venir)` : "";
                    items.push({ icon: "task", label: `${impact.nbTachesNonTerminees} tâche${impact.nbTachesNonTerminees! > 1 ? "s" : ""} non terminée${impact.nbTachesNonTerminees! > 1 ? "s" : ""}${rdvNote} vous seront réassignées.` });
                  }
                  if (items.length === 0) {
                    return (
                      <p className="text-sm text-[var(--color-lr-text-secondary)]">
                        Aucun lead, RDV ou conversation à transférer. La désactivation est immédiate.
                      </p>
                    );
                  }
                  return (
                    <div className="space-y-3">
                      <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">Impact de la désactivation</p>
                      <ul className="space-y-2.5">
                        {items.map((it, i) => (
                          <li key={i} className="flex items-start gap-2.5 text-sm text-[var(--foreground)]">
                            <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-warning" />
                            <span>{it.label}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  );
                })()
              ) : (
                <p className="text-sm text-[var(--color-lr-text-secondary)]">
                  Impossible de calculer l&apos;impact pour ce vendeur. La désactivation reste possible, les leads et tâches éventuels vous seront automatiquement réassignés.
                </p>
              )}
            </div>
          )}
        </Modal>

        <Modal
          open={showModal}
          onClose={() => setShowModal(false)}
          title="Inviter un vendeur"
          description="Le vendeur recevra un email d'invitation pour rejoindre votre équipe."
          size="md"
        >
          <form onSubmit={handleInvite} className="space-y-4">
            <Input
              id="invite-prenom"
              label="Prénom"
              value={form.prenom}
              onChange={(e) => setForm({ ...form, prenom: e.target.value })}
              required
              autoFocus
            />
            <Input
              id="invite-nom"
              label="Nom"
              value={form.nom}
              onChange={(e) => setForm({ ...form, nom: e.target.value })}
              required
            />
            <Input
              id="invite-email"
              label="Email"
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              required
              placeholder="vendeur@etablissement.fr"
              invalid={emailInvalide}
              errorMessage="Format d'email invalide"
            />
            <div className="flex justify-end gap-3 pt-2">
              <Button type="button" variant="secondary" onClick={() => setShowModal(false)}>
                Annuler
              </Button>
              <Button
                type="submit"
                variant="primary"
                disabled={inviting || !form.prenom || !form.nom || !EMAIL_REGEX.test(form.email)}
              >
                {inviting ? "Envoi…" : "Inviter"}
              </Button>
            </div>
          </form>
        </Modal>
      </div>
    </AppLayout>
  );
}

function VendeurStatusBadge({ statut }: { statut: string }) {
  return <StatutBadge statut={VENDEUR_STATUT_LABELS[statut] ?? statut} tone={VENDEUR_STATUT_TONE[statut]} />;
}
