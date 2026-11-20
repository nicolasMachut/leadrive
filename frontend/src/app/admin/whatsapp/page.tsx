"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useApiClient, formatErreur } from "@/lib/auth";
import { formatPhoneInput } from "@/lib/phone";
import PageHeader from "@/components/common/PageHeader";
import Alert from "@/components/common/Alert";
import Button from "@/components/common/Button";
import Modal from "@/components/common/Modal";
import StatutBadge from "@/components/common/StatutBadge";
import { SkeletonGrid } from "@/components/common/Skeleton";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// ── Types (miroir de RepAdminWhatsappActivationDto cote back) ───────────────
interface Modele {
  nom: string;
  statut: string;
  templateSid: string | null;
  metaRejectReason: string | null;
  nbTentatives: number;
  soumisLe: string | null;
  prochaineTentativeLe: string | null;
  dateDerniereSynchro: string | null;
}

interface Activation {
  tenantId: number;
  nomEtablissement: string;
  statut: string | null;
  etape: number;
  etapesTotal: number;
  etapeLibelle: string;
  bloque: boolean;
  raisonBlocage: string | null;
  numero: string | null;
  displayName: string | null;
  twilioSubAccountSid: string | null;
  twilioSenderSid: string | null;
  wabaId: string | null;
  metaBusinessId: string | null;
  metaStatus: string | null;
  /** Statut d'examen du nom affiché côté WhatsApp (APPROVED, PENDING_REVIEW, DECLINED…). */
  nameStatus: string | null;
  qualityRating: string | null;
  messagingLimit: string | null;
  metaRejectReason: string | null;
  dateDerniereSynchroMeta: string | null;
  modifieLe: string | null;
  /** Nombre de messages en ECHEC sur les 7 derniers jours (rollup à la volée). */
  nbEchecsRecents: number;
  /** Horodatage du dernier message en ECHEC (ou null). */
  dernierEchecLe: string | null;
  /** Répartition des échecs récents par motif (cause + nombre), triée du plus fréquent. */
  motifsEchec: MotifEchec[];
  /** Numéro/sender WhatsApp indisponible (envois cassés à corriger, ≠ restriction Meta). */
  numeroEnEchec: boolean;
  modeles: Modele[];
}

interface MotifEchec {
  motif: string;
  nombre: number;
}

// ── Helpers d'affichage ─────────────────────────────────────────────────────
const ETAPES_LABELS = [
  "Sous-compte Twilio",
  "Compte WhatsApp connecté",
  "Modèles soumis",
  "Modèles approuvés",
  "Numéro en ligne",
  "Actif",
];

const MODELE_LABELS: Record<string, string> = {
  leadrive_premier_contact: "Premier contact",
  leadrive_rappel_rdv: "Rappel RDV",
  leadrive_relance_j1: "Relance J+1",
  leadrive_relance_j3: "Relance J+3",
};

function labelModele(nom: string): string {
  return MODELE_LABELS[nom] ?? nom.replace(/^leadrive_/, "").replace(/_/g, " ");
}

// Tons StatutBadge (composant partagé, style instrument JetBrains Mono uppercase).
type BadgeTone = "neutral" | "success" | "warning" | "danger" | "info" | "accent";

const STATUT_CONFIG_TONE: Record<string, BadgeTone> = {
  ACTIF: "success",
  EN_VALIDATION: "accent",
  RESTREINT: "danger",
  ERREUR: "danger",
  DESACTIVE: "neutral",
};

const STATUT_MODELE_TONE: Record<string, BadgeTone> = {
  APPROUVE: "success",
  SOUMIS: "accent",
  EN_ATTENTE_SOUMISSION: "neutral",
  REJETE: "danger",
  DESACTIVE: "neutral",
};

const QUALITY_TONE: Record<string, BadgeTone> = {
  GREEN: "success",
  YELLOW: "warning",
  RED: "danger",
};

// Statut d'examen du nom affiché (verified name) côté WhatsApp.
const NAME_STATUS_TONE: Record<string, BadgeTone> = {
  APPROVED: "success",
  AVAILABLE_WITHOUT_REVIEW: "success",
  PENDING_REVIEW: "warning",
  DECLINED: "danger",
  EXPIRED: "danger",
  NONE: "neutral",
};

const NAME_STATUS_LABELS: Record<string, string> = {
  APPROVED: "Approuvé",
  AVAILABLE_WITHOUT_REVIEW: "Approuvé",
  PENDING_REVIEW: "En cours d'examen",
  DECLINED: "Refusé",
  EXPIRED: "Expiré",
  NONE: "Non soumis",
};

// Libellés FR lisibles (l'enum brut reste accessible en title pour le debug).
const STATUT_LABELS: Record<string, string> = {
  ACTIF: "Actif",
  EN_VALIDATION: "En validation",
  RESTREINT: "Restreint",
  ERREUR: "En erreur",
  DESACTIVE: "Désactivé",
};

const STATUT_MODELE_LABELS: Record<string, string> = {
  EN_ATTENTE_SOUMISSION: "En attente",
  SOUMIS: "Soumis",
  APPROUVE: "Approuvé",
  REJETE: "Refusé",
  DESACTIVE: "Désactivé",
};

const QUALITY_LABELS: Record<string, string> = {
  GREEN: "Bonne",
  YELLOW: "Moyenne",
  RED: "Dégradée",
};

function formatDate(value: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleString("fr-FR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// ── Page (Suspense pour useSearchParams) ────────────────────────────────────
export default function AdminWhatsAppPage() {
  return (
    <Suspense fallback={<div className="p-8"><SkeletonGrid count={4} /></div>}>
      <Console />
    </Suspense>
  );
}

function Console() {
  const apiFetch = useApiClient();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [items, setItems] = useState<Activation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Etat depuis l'URL (F5 restitue la vue exacte, cf. regle CLAUDE.md #10).
  const recherche = searchParams.get("q") ?? "";
  const filtreStatut = searchParams.get("statut") ?? "TOUS";
  const bloquesUniquement = searchParams.get("bloques") === "1";
  const tenantSelectionne = searchParams.get("tenant");

  const majUrl = useCallback(
    (patch: Record<string, string | null>) => {
      const params = new URLSearchParams(searchParams.toString());
      Object.entries(patch).forEach(([cle, valeur]) => {
        if (valeur === null || valeur === "") params.delete(cle);
        else params.set(cle, valeur);
      });
      router.replace(`/admin/whatsapp?${params.toString()}`, { scroll: false });
    },
    [router, searchParams]
  );

  const charger = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/admin/whatsapp/activation`);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setItems(await res.json());
    } catch (e) {
      setError(formatErreur(e));
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => {
    void charger();
  }, [charger]);

  // Compteurs calcules cote client (une seule requete).
  const compteurs = useMemo(() => {
    const parStatut: Record<string, number> = {};
    let bloques = 0;
    for (const it of items) {
      const s = it.statut ?? "INCONNU";
      parStatut[s] = (parStatut[s] ?? 0) + 1;
      if (it.bloque) bloques += 1;
    }
    return { parStatut, bloques, total: items.length };
  }, [items]);

  const itemsFiltres = useMemo(() => {
    const q = recherche.trim().toLowerCase();
    return items.filter((it) => {
      if (filtreStatut !== "TOUS" && it.statut !== filtreStatut) return false;
      if (bloquesUniquement && !it.bloque) return false;
      if (q) {
        const hay = [
          it.nomEtablissement,
          String(it.tenantId),
          it.numero,
          it.wabaId,
          it.displayName,
        ]
          .filter(Boolean)
          .join(" ")
          .toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [items, recherche, filtreStatut, bloquesUniquement]);

  const selection = useMemo(
    () => itemsFiltres.find((it) => String(it.tenantId) === tenantSelectionne) ?? null,
    [itemsFiltres, tenantSelectionne]
  );
  // L'établissement sélectionné existe-t-il encore (avant filtrage) ? Sert à
  // distinguer « filtré » de « disparu » et à nettoyer un ?tenant= mort.
  const selectionGlobale = useMemo(
    () => items.find((it) => String(it.tenantId) === tenantSelectionne) ?? null,
    [items, tenantSelectionne]
  );

  // Nettoie un ?tenant= qui ne pointe plus sur aucun établissement existant
  // (ex: lien partagé périmé), pour ne pas laisser un paramètre mort dans l'URL.
  useEffect(() => {
    if (tenantSelectionne && !loading && items.length > 0 && !selectionGlobale) {
      majUrl({ tenant: null });
    }
  }, [tenantSelectionne, loading, items.length, selectionGlobale, majUrl]);

  return (
    <div className="p-6 lg:p-8 space-y-6">
      <PageHeader
        eyebrow="Admin"
        title="WhatsApp"
        subtitle="Suivi et déblocage de l'activation WhatsApp de tous les établissements."
        actions={
          <Button variant="secondary" size="sm" onClick={() => void charger()} disabled={loading}>
            {loading ? "Actualisation…" : "Actualiser"}
          </Button>
        }
      />

      {error && <Alert tone="danger">{error}</Alert>}

      {/* Compteurs */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <Compteur
          label="Établissements"
          valeur={compteurs.total}
          active={filtreStatut === "TOUS" && !bloquesUniquement}
          onClick={() => majUrl({ statut: null, bloques: null })}
        />
        <Compteur
          label="Actifs"
          valeur={compteurs.parStatut.ACTIF ?? 0}
          tone="success"
          active={filtreStatut === "ACTIF"}
          onClick={() => majUrl({ statut: filtreStatut === "ACTIF" ? null : "ACTIF" })}
        />
        <Compteur
          label="En validation"
          valeur={compteurs.parStatut.EN_VALIDATION ?? 0}
          tone="accent"
          active={filtreStatut === "EN_VALIDATION"}
          onClick={() => majUrl({ statut: filtreStatut === "EN_VALIDATION" ? null : "EN_VALIDATION" })}
        />
        <Compteur
          label="En erreur"
          valeur={compteurs.parStatut.ERREUR ?? 0}
          tone="danger"
          active={filtreStatut === "ERREUR"}
          onClick={() => majUrl({ statut: filtreStatut === "ERREUR" ? null : "ERREUR" })}
        />
        <Compteur
          label="Désactivés"
          valeur={compteurs.parStatut.DESACTIVE ?? 0}
          active={filtreStatut === "DESACTIVE"}
          onClick={() => majUrl({ statut: filtreStatut === "DESACTIVE" ? null : "DESACTIVE" })}
        />
        <Compteur
          label="Bloqués"
          valeur={compteurs.bloques}
          tone="warning"
          active={bloquesUniquement}
          onClick={() => majUrl({ bloques: bloquesUniquement ? null : "1" })}
        />
      </div>

      {/* Filtres */}
      <div className="flex flex-wrap items-center gap-3">
        <input
          type="search"
          value={recherche}
          onChange={(e) => majUrl({ q: e.target.value })}
          placeholder="Rechercher (nom, ID, numéro, WABA…)"
          className="h-9 w-full max-w-xs rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 text-sm text-[var(--foreground)] outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
        />
        <select
          value={filtreStatut}
          onChange={(e) => majUrl({ statut: e.target.value === "TOUS" ? null : e.target.value })}
          className="h-9 rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 text-sm text-[var(--foreground)] outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
        >
          <option value="TOUS">Tous les statuts</option>
          <option value="EN_VALIDATION">En validation</option>
          <option value="ACTIF">Actif</option>
          <option value="RESTREINT">Restreint</option>
          <option value="ERREUR">Erreur</option>
          <option value="DESACTIVE">Désactivé</option>
        </select>
        <label className="flex items-center gap-2 text-sm text-[var(--color-lr-text-secondary)]">
          <input
            type="checkbox"
            checked={bloquesUniquement}
            onChange={(e) => majUrl({ bloques: e.target.checked ? "1" : null })}
            className="h-4 w-4 rounded border-[var(--border)] accent-[var(--color-accent)]"
          />
          Bloqués uniquement
        </label>
      </div>

      {/* Master-detail */}
      {loading ? (
        <SkeletonGrid count={4} />
      ) : (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)]">
          <ListeClients
            items={itemsFiltres}
            selectionId={selection?.tenantId ?? null}
            onSelect={(id) => majUrl({ tenant: String(id) })}
          />
          {selection ? (
            <PanneauDetail
              activation={selection}
              onRefresh={charger}
              onError={setError}
            />
          ) : selectionGlobale ? (
            <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--color-lr-text-secondary)]">
              <span>
                <span className="font-medium text-[var(--foreground)]">{selectionGlobale.nomEtablissement}</span>{" "}
                ne correspond plus aux filtres actuels.
              </span>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => majUrl({ q: null, statut: null, bloques: null })}
              >
                Réinitialiser les filtres
              </Button>
            </div>
          ) : (
            <div className="flex items-center justify-center rounded-xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--color-lr-text-secondary)]">
              {itemsFiltres.length === 0
                ? "Aucun établissement ne correspond aux filtres."
                : "Sélectionnez un établissement pour voir le détail de son activation."}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── Compteur KPI ────────────────────────────────────────────────────────────
function Compteur({
  label,
  valeur,
  tone,
  active = false,
  onClick,
}: {
  label: string;
  valeur: number;
  tone?: "success" | "danger" | "warning" | "accent";
  active?: boolean;
  onClick?: () => void;
}) {
  const toneColor =
    tone === "success"
      ? "text-success"
      : tone === "danger"
        ? "text-danger"
        : tone === "warning"
          ? "text-warning"
          : tone === "accent"
            ? "text-accent"
            : "text-[var(--foreground)]";
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      title={active ? "Filtre actif (cliquer pour retirer)" : `Filtrer : ${label}`}
      className={`rounded-xl border bg-[var(--card)] p-3 text-left transition-colors focus:outline-none focus-visible:ring-1 focus-visible:ring-accent/40 ${
        active ? "border-accent ring-1 ring-accent/30" : "border-[var(--border)] hover:border-accent/40"
      }`}
    >
      <div className="text-xs text-[var(--color-lr-text-secondary)]">{label}</div>
      <div className={`font-mono text-xl font-semibold tabular-nums ${toneColor}`}>{valeur}</div>
    </button>
  );
}

// ── Liste des clients ───────────────────────────────────────────────────────
function ListeClients({
  items,
  selectionId,
  onSelect,
}: {
  items: Activation[];
  selectionId: number | null;
  onSelect: (id: number) => void;
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-[var(--border)] bg-[var(--card)]">
      <ul className="max-h-[70vh] divide-y divide-[var(--border)] overflow-y-auto">
        {items.map((it) => {
          const actif = it.tenantId === selectionId;
          return (
            <li key={it.tenantId}>
              <button
                onClick={() => onSelect(it.tenantId)}
                className={`flex w-full items-center justify-between gap-3 px-4 py-3 text-left transition-colors focus:outline-none focus-visible:ring-1 focus-visible:ring-inset focus-visible:ring-accent/40 ${
                  actif ? "bg-accent/5" : "hover:bg-[var(--muted)]"
                }`}
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    {it.bloque && <span className="h-2 w-2 shrink-0 rounded-full bg-danger" title="Bloqué" />}
                    <span className="truncate text-sm font-medium text-[var(--foreground)]">
                      {it.nomEtablissement}
                    </span>
                  </div>
                  <div className="mt-0.5 truncate text-xs text-[var(--color-lr-text-tertiary)]">
                    #{it.tenantId}
                    {it.numero ? ` · ${formatPhoneInput(it.numero)}` : ""}
                  </div>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  <StatutPill statut={it.statut} />
                  <span className="text-[10px] tabular-nums text-[var(--color-lr-text-tertiary)]">
                    {it.etape}/{it.etapesTotal}
                  </span>
                </div>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function StatutPill({ statut }: { statut: string | null }) {
  if (!statut) return <span className="text-xs text-[var(--color-lr-text-tertiary)]">—</span>;
  return (
    <span title={statut}>
      <StatutBadge statut={STATUT_LABELS[statut] ?? statut} tone={STATUT_CONFIG_TONE[statut] ?? "neutral"} />
    </span>
  );
}

// ── Panneau de détail ───────────────────────────────────────────────────────
function PanneauDetail({
  activation,
  onRefresh,
  onError,
}: {
  activation: Activation;
  onRefresh: () => Promise<void>;
  onError: (msg: string) => void;
}) {
  const apiFetch = useApiClient();
  const [actionEnCours, setActionEnCours] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ tone: "success" | "danger"; msg: string } | null>(null);
  const [modale, setModale] = useState<"forcer-actif" | "desactiver" | null>(null);
  const [justification, setJustification] = useState("");
  const [copieTout, setCopieTout] = useState(false);
  // Modale de création de sender : nom affiché pré-rempli, éditable (override SAV).
  const [modaleSender, setModaleSender] = useState(false);
  const [nomSender, setNomSender] = useState("");

  // Reset du feedback quand on change d'établissement.
  useEffect(() => {
    setFeedback(null);
    setModale(null);
    setJustification("");
    setModaleSender(false);
  }, [activation.tenantId]);

  const lancer = useCallback(
    async (cle: string, url: string, succes: string, body?: Record<string, unknown>) => {
      setActionEnCours(cle);
      setFeedback(null);
      try {
        const res = await apiFetch(url, {
          method: "POST",
          ...(body
            ? { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }
            : {}),
        });
        if (!res.ok) {
          // Le backend renvoie { code, message } sur 4xx : on remonte le message
          // métier (ex: "Numéro WhatsApp absent", refus Twilio) plutôt qu'un code HTTP.
          const detail = await res.json().catch(() => null);
          throw new Error(detail?.message || `Erreur ${res.status}`);
        }
        setFeedback({ tone: "success", msg: succes });
        await onRefresh();
      } catch (e) {
        setFeedback({ tone: "danger", msg: formatErreur(e) });
      } finally {
        setActionEnCours(null);
      }
    },
    [apiFetch, onRefresh]
  );

  const lancerActionJustifiee = useCallback(async () => {
    if (!modale || justification.trim().length < 20) return;
    setActionEnCours(modale);
    setFeedback(null);
    try {
      const res = await apiFetch(
        `${API_BASE}/api/admin/whatsapp/configs/${activation.tenantId}/${modale}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ justification }),
        }
      );
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setFeedback({
        tone: "success",
        msg: modale === "forcer-actif" ? "Statut forcé à ACTIF." : "Configuration désactivée.",
      });
      setModale(null);
      setJustification("");
      await onRefresh();
    } catch (e) {
      onError(formatErreur(e));
    } finally {
      setActionEnCours(null);
    }
  }, [apiFetch, modale, justification, activation.tenantId, onRefresh, onError]);

  // Copie tous les identifiants techniques en un bloc, prêt à coller dans un ticket.
  async function copierBloc() {
    const lignes = [
      `Établissement : ${activation.nomEtablissement} (#${activation.tenantId})`,
      `Statut : ${activation.statut ?? "—"}`,
      `Étape : ${activation.etape}/${activation.etapesTotal} (${activation.etapeLibelle})`,
      activation.bloque ? `Blocage : ${activation.raisonBlocage ?? "—"}` : null,
      `Numéro : ${activation.numero ?? "—"}`,
      `Nom affiché : ${activation.displayName ?? "—"}`,
      `Examen du nom : ${activation.nameStatus ? (NAME_STATUS_LABELS[activation.nameStatus] ?? activation.nameStatus) : "—"}`,
      `Sous-compte Twilio (SID) : ${activation.twilioSubAccountSid ?? "—"}`,
      `Sender Twilio (SID) : ${activation.twilioSenderSid ?? "—"}`,
      `WABA ID (Meta) : ${activation.wabaId ?? "—"}`,
      `Meta Business ID : ${activation.metaBusinessId ?? "—"}`,
      `Statut Meta : ${activation.metaStatus ?? "—"}`,
      `Qualité du numéro : ${activation.qualityRating ?? "—"}`,
      `Plafond de messagerie : ${activation.messagingLimit ?? "—"}`,
      `Raison Meta : ${activation.metaRejectReason ?? "—"}`,
      `Messages en erreur (7j) : ${activation.nbEchecsRecents}`,
      `Dernier échec : ${activation.dernierEchecLe ?? "—"}`,
    ]
      .filter(Boolean)
      .join("\n");
    try {
      await navigator.clipboard.writeText(lignes);
      setCopieTout(true);
      setTimeout(() => setCopieTout(false), 1500);
    } catch {
      // Copie indisponible (navigateur/permissions) : les champs restent lisibles à l'écran.
    }
  }

  return (
    <div className="space-y-6 rounded-xl border border-[var(--border)] bg-[var(--card)] p-6">
      {/* En-tête */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-lg font-semibold text-[var(--foreground)]">
            {activation.nomEtablissement}
          </h2>
          <p className="mt-0.5 text-xs text-[var(--color-lr-text-tertiary)]">
            #{activation.tenantId}
            {activation.numero ? ` · ${formatPhoneInput(activation.numero)}` : ""}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <StatutPill statut={activation.statut} />
          <span className="text-xs tabular-nums text-[var(--color-lr-text-secondary)]">
            Étape {activation.etape}/{activation.etapesTotal}
          </span>
        </div>
      </div>

      {activation.bloque && activation.raisonBlocage && (
        <Alert tone="danger">{activation.raisonBlocage}</Alert>
      )}
      {feedback && <Alert tone={feedback.tone}>{feedback.msg}</Alert>}

      {/* Timeline */}
      <Timeline etape={activation.etape} bloque={activation.bloque} />

      {/* Modèles */}
      <section>
        <h3 className="mb-2 text-sm font-semibold text-[var(--foreground)]">
          Modèles de messages
        </h3>
        {activation.modeles.length === 0 ? (
          <p className="text-xs text-[var(--color-lr-text-secondary)]">
            Aucun modèle initialisé pour cet établissement.
          </p>
        ) : (
          <div className="overflow-hidden rounded-lg border border-[var(--border)]">
            <table className="w-full text-sm">
              <thead className="bg-[var(--muted)] text-xs uppercase text-[var(--color-lr-text-secondary)]">
                <tr>
                  <th className="px-3 py-2 text-left">Modèle</th>
                  <th className="px-3 py-2 text-left">Statut</th>
                  <th className="px-3 py-2 text-left">Tentatives</th>
                  <th className="px-3 py-2 text-left">Dernière synchro</th>
                  <th className="px-3 py-2 text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {activation.modeles.map((m) => (
                  <tr key={m.nom} className="border-t border-[var(--border)] align-top">
                    <td className="px-3 py-2">
                      <div className="font-medium text-[var(--foreground)]">{labelModele(m.nom)}</div>
                      <div className="font-mono text-[10px] text-[var(--color-lr-text-tertiary)]">{m.nom}</div>
                      {m.metaRejectReason && (
                        <div className="mt-1 text-[11px] text-danger">{m.metaRejectReason}</div>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      <span title={m.statut}>
                        <StatutBadge
                          statut={STATUT_MODELE_LABELS[m.statut] ?? m.statut}
                          tone={STATUT_MODELE_TONE[m.statut] ?? "neutral"}
                        />
                      </span>
                    </td>
                    <td className="px-3 py-2 tabular-nums text-[var(--color-lr-text-secondary)]">
                      {m.nbTentatives}
                    </td>
                    <td className="px-3 py-2 text-xs text-[var(--color-lr-text-secondary)]">
                      {formatDate(m.dateDerniereSynchro)}
                    </td>
                    <td className="px-3 py-2 text-right">
                      {/* Re-soumission ciblee de CE modele : relance un modele en
                          erreur/refuse sans toucher aux modeles deja approuves. */}
                      <button
                        type="button"
                        disabled={actionEnCours !== null}
                        title={actionEnCours !== null ? "Une action est déjà en cours" : "Re-soumettre uniquement ce modèle"}
                        onClick={() =>
                          lancer(
                            `resoumettre-${m.nom}`,
                            `${API_BASE}/api/admin/whatsapp/configs/${activation.tenantId}/resoumettre-modele`,
                            `Modèle « ${labelModele(m.nom)} » re-soumis.`,
                            { templateName: m.nom }
                          )
                        }
                        className="rounded-lg px-2 py-1 text-xs font-medium text-accent transition-colors hover:bg-accent/10 focus:outline-none focus-visible:ring-1 focus-visible:ring-accent/40 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {actionEnCours === `resoumettre-${m.nom}` ? "Envoi…" : "Re-soumettre"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Détails techniques */}
      <section>
        <div className="mb-2 flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-[var(--foreground)]">Détails techniques</h3>
          <button
            type="button"
            onClick={() => void copierBloc()}
            className="rounded-lg px-2 py-1 text-xs text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)] focus:outline-none focus-visible:ring-1 focus-visible:ring-accent/40"
          >
            {copieTout ? "Copié" : "Copier le bloc"}
          </button>
        </div>
        <div className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-2">
          <ChampCopiable label="Tenant ID" valeur={String(activation.tenantId)} />
          <ChampCopiable label="Numéro" valeur={activation.numero} affichage={activation.numero ? formatPhoneInput(activation.numero) : null} />
          <ChampCopiable label="Nom affiché" valeur={activation.displayName} copiable={false} />
          <div className="flex items-center justify-between gap-2 border-b border-[var(--border)] py-1.5">
            <span className="text-xs text-[var(--color-lr-text-secondary)]">Examen du nom</span>
            {activation.nameStatus ? (
              <span title={activation.nameStatus}>
                <StatutBadge
                  statut={NAME_STATUS_LABELS[activation.nameStatus] ?? activation.nameStatus}
                  tone={NAME_STATUS_TONE[activation.nameStatus] ?? "neutral"}
                />
              </span>
            ) : (
              <span className="text-xs text-[var(--color-lr-text-tertiary)]">—</span>
            )}
          </div>
          <ChampCopiable label="Sous-compte Twilio (SID)" valeur={activation.twilioSubAccountSid} />
          <ChampCopiable label="Sender Twilio (SID)" valeur={activation.twilioSenderSid} />
          <ChampCopiable label="WABA ID (Meta)" valeur={activation.wabaId} />
          <ChampCopiable label="Meta Business ID" valeur={activation.metaBusinessId} />
          <ChampCopiable label="Statut Meta" valeur={activation.metaStatus} copiable={false} />
          <div className="flex items-center justify-between gap-2 border-b border-[var(--border)] py-1.5">
            <span className="text-xs text-[var(--color-lr-text-secondary)]">Qualité du numéro</span>
            {activation.qualityRating ? (
              <span title={activation.qualityRating}>
                <StatutBadge
                  statut={QUALITY_LABELS[activation.qualityRating] ?? activation.qualityRating}
                  tone={QUALITY_TONE[activation.qualityRating] ?? "neutral"}
                />
              </span>
            ) : (
              <span className="text-xs text-[var(--color-lr-text-tertiary)]">—</span>
            )}
          </div>
          <ChampCopiable label="Plafond de messagerie" valeur={activation.messagingLimit} copiable={false} />
          <ChampCopiable label="Dernière synchro Meta" valeur={formatDate(activation.dateDerniereSynchroMeta)} copiable={false} />
          <ChampCopiable label="Dernière modification" valeur={formatDate(activation.modifieLe)} copiable={false} />
          <ChampCopiable
            label="Messages en erreur (7j)"
            valeur={String(activation.nbEchecsRecents)}
            affichage={
              activation.nbEchecsRecents > 0
                ? `${activation.nbEchecsRecents} · dernier ${formatDate(activation.dernierEchecLe)}`
                : "0"
            }
            copiable={false}
          />
          {activation.motifsEchec.length > 0 && (
            <div className="mt-1 flex flex-col gap-1 rounded-lg border border-danger/20 bg-danger/5 p-3">
              <span className="font-mono text-[10px] font-semibold uppercase tracking-wider text-danger">
                Causes des échecs (7j)
              </span>
              {activation.numeroEnEchec && (
                <span className="text-xs font-semibold text-danger">
                  ⚠ Numéro indisponible : les envois sont cassés, intervention requise (recréer / vérifier le sender).
                </span>
              )}
              <ul className="flex flex-col gap-1">
                {activation.motifsEchec.map((m, i) => (
                  <li key={i} className="flex items-start justify-between gap-3 text-xs">
                    <span className="text-[var(--color-lr-text-secondary)]">{m.motif}</span>
                    <span className="shrink-0 font-mono tabular-nums text-[var(--foreground)]">{m.nombre}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
        {activation.metaRejectReason && (
          <div className="mt-2 rounded-lg bg-danger/5 px-3 py-2 text-xs text-danger">
            Raison Meta : {activation.metaRejectReason}
          </div>
        )}
      </section>

      {/* Actions SAV : routine, puis zone de contournement (actions de bypass). */}
      <section className="space-y-3 border-t border-[var(--border)] pt-4">
        <div className="flex flex-wrap items-center gap-2">
          <span className="mr-1 text-xs font-medium text-[var(--color-lr-text-secondary)]">Actions</span>
          <Button
            variant="secondary"
            size="sm"
            disabled={actionEnCours !== null}
            title={actionEnCours !== null ? "Une action est déjà en cours" : undefined}
            onClick={() =>
              lancer(
                "resync",
                `${API_BASE}/api/admin/concessions/${activation.tenantId}/synchroniser-statut-twilio`,
                "Statut re-synchronisé depuis Twilio."
              )
            }
          >
            {actionEnCours === "resync" ? "Vérification…" : "Re-vérifier chez Twilio"}
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={actionEnCours !== null}
            title={actionEnCours !== null ? "Une action est déjà en cours" : undefined}
            onClick={() =>
              lancer(
                "resoumettre",
                `${API_BASE}/api/admin/whatsapp/configs/${activation.tenantId}/resoumettre-modeles`,
                "Modèles re-soumis pour validation."
              )
            }
          >
            {actionEnCours === "resoumettre" ? "Envoi…" : "Re-soumettre les modèles"}
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={actionEnCours !== null}
            title={actionEnCours !== null ? "Une action est déjà en cours" : "Crée ou réenregistre le sender chez Twilio (débloque un sender absent ou orphelin)"}
            onClick={() => {
              setNomSender(activation.displayName ?? "");
              setModaleSender(true);
            }}
          >
            {actionEnCours === "creer-sender" ? "Création…" : "Créer le sender"}
          </Button>
          <Button
            variant="danger"
            size="sm"
            disabled={actionEnCours !== null}
            title={actionEnCours !== null ? "Une action est déjà en cours" : "Supprime le sender chez Twilio (pour le recréer ensuite). Désenregistre le numéro."}
            onClick={() =>
              lancer(
                "supprimer-sender",
                `${API_BASE}/api/admin/whatsapp/configs/${activation.tenantId}/supprimer-sender`,
                "Sender supprimé. Vous pouvez le recréer."
              )
            }
          >
            {actionEnCours === "supprimer-sender" ? "Suppression…" : "Supprimer le sender"}
          </Button>
          {actionEnCours !== null && (
            <span className="flex items-center gap-1.5 text-xs text-[var(--color-lr-text-tertiary)]">
              <span className="h-3 w-3 animate-spin rounded-full border-2 border-[var(--border)] border-t-accent motion-reduce:animate-none" />
              Action en cours…
            </span>
          )}
        </div>

        {/* Zone de contournement : actions de bypass, distinguées visuellement. */}
        <div className="rounded-lg border border-warning/30 bg-warning/5 p-3">
          <p className="mb-2 text-xs font-medium text-warning">Zone de contournement</p>
          <div className="flex flex-wrap gap-2">
            {activation.statut !== "ACTIF" && (
              <button
                type="button"
                disabled={actionEnCours !== null}
                title={actionEnCours !== null ? "Une action est déjà en cours" : "Forcer l'activation sans validation"}
                onClick={() => {
                  setModale("forcer-actif");
                  setJustification("");
                }}
                className="rounded-lg border border-warning/40 bg-[var(--card)] px-3 py-1.5 text-xs font-medium text-warning transition-colors hover:bg-warning/10 focus:outline-none focus-visible:ring-1 focus-visible:ring-warning/50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Forcer actif
              </button>
            )}
            {activation.statut !== "DESACTIVE" && (
              <Button
                variant="danger"
                size="sm"
                disabled={actionEnCours !== null}
                title={actionEnCours !== null ? "Une action est déjà en cours" : undefined}
                onClick={() => {
                  setModale("desactiver");
                  setJustification("");
                }}
              >
                Désactiver
              </Button>
            )}
          </div>
        </div>
      </section>

      {/* Modale action justifiée */}
      <Modal
        open={modale !== null}
        onClose={() => setModale(null)}
        title={modale === "forcer-actif" ? "Forcer le statut ACTIF" : "Désactiver la configuration"}
        description={
          modale === "forcer-actif"
            ? "Contourne la validation. À réserver à une restauration manuelle après incident."
            : "Coupe l'envoi de messages pour cet établissement."
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setModale(null)}>
              Annuler
            </Button>
            <Button
              variant={modale === "desactiver" ? "danger" : "primary"}
              size="sm"
              disabled={justification.trim().length < 20 || actionEnCours !== null}
              onClick={() => void lancerActionJustifiee()}
            >
              Confirmer
            </Button>
          </div>
        }
      >
        <label className="block text-sm text-[var(--color-lr-text-secondary)]">
          Justification (min. 20 caractères, tracée pour audit)
          <textarea
            value={justification}
            onChange={(e) => setJustification(e.target.value)}
            rows={3}
            className="mt-1 w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-sm text-[var(--foreground)] outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
            placeholder="Raison de l'action…"
          />
        </label>
        <p className="mt-1 text-xs text-[var(--color-lr-text-tertiary)]">
          {justification.trim().length}/20
        </p>
      </Modal>

      {/* Modale création de sender : nom affiché pré-rempli, éditable */}
      <Modal
        open={modaleSender}
        onClose={() => setModaleSender(false)}
        title="Créer le sender WhatsApp"
        description="Le nom affiché doit correspondre au nom validé par WhatsApp (visible par le prospect). Pré-rempli avec le nom connu, modifiable."
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setModaleSender(false)}>
              Annuler
            </Button>
            <Button
              variant="primary"
              size="sm"
              disabled={nomSender.trim().length === 0 || actionEnCours !== null}
              onClick={() => {
                setModaleSender(false);
                void lancer(
                  "creer-sender",
                  `${API_BASE}/api/admin/whatsapp/configs/${activation.tenantId}/creer-sender`,
                  "Sender Twilio créé. Validation en cours.",
                  { displayName: nomSender.trim() }
                );
              }}
            >
              Créer le sender
            </Button>
          </div>
        }
      >
        <label className="block text-sm text-[var(--color-lr-text-secondary)]">
          Nom affiché (validé par WhatsApp Business)
          <input
            type="text"
            value={nomSender}
            onChange={(e) => setNomSender(e.target.value)}
            className="mt-1 w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-sm text-[var(--foreground)] outline-none focus:border-accent focus:ring-1 focus:ring-accent/30"
            placeholder="Ex : Garage Pinson"
          />
        </label>
        <p className="mt-1 text-xs text-[var(--color-lr-text-tertiary)]">
          À récupérer dans WhatsApp Manager si vous n'êtes pas sûr du nom validé.
        </p>
      </Modal>
    </div>
  );
}

// ── Timeline des 6 étapes ───────────────────────────────────────────────────
function Timeline({ etape, bloque }: { etape: number; bloque: boolean }) {
  return (
    <ol className="space-y-2">
      {ETAPES_LABELS.map((label, idx) => {
        const numero = idx + 1;
        const faite = numero <= etape;
        const courante = numero === etape + 1;
        const erreur = courante && bloque;
        return (
          <li key={label} className="flex items-center gap-3">
            <span
              className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[10px] font-semibold ${
                faite
                  ? "bg-success/15 text-success"
                  : erreur
                    ? "bg-danger/15 text-danger"
                    : courante
                      ? "bg-accent/15 text-accent"
                      : "bg-[var(--muted)] text-[var(--color-lr-text-tertiary)]"
              }`}
            >
              {faite ? "✓" : numero}
            </span>
            <span
              className={`text-sm ${
                faite
                  ? "text-[var(--foreground)]"
                  : erreur
                    ? "font-medium text-danger"
                    : courante
                      ? "font-medium text-[var(--foreground)]"
                      : "text-[var(--color-lr-text-tertiary)]"
              }`}
            >
              {label}
            </span>
            {courante && !erreur && (
              <span className="text-[10px] text-accent">en cours</span>
            )}
          </li>
        );
      })}
    </ol>
  );
}

// ── Champ copiable ──────────────────────────────────────────────────────────
function ChampCopiable({
  label,
  valeur,
  affichage,
  copiable = true,
}: {
  label: string;
  valeur: string | null;
  affichage?: string | null;
  copiable?: boolean;
}) {
  const [copie, setCopie] = useState(false);
  const aValeur = valeur != null && valeur !== "" && valeur !== "—";

  async function copier() {
    if (!valeur) return;
    try {
      await navigator.clipboard.writeText(valeur);
      setCopie(true);
      setTimeout(() => setCopie(false), 1500);
    } catch {
      // Copie indisponible (navigateur/permissions) : on ignore silencieusement,
      // la valeur reste lisible et sélectionnable à l'écran.
    }
  }

  return (
    <div className="flex items-center justify-between gap-2 border-b border-[var(--border)] py-1.5">
      <span className="shrink-0 text-xs text-[var(--color-lr-text-secondary)]">{label}</span>
      <span className="flex min-w-0 items-center gap-1.5">
        <span className="truncate font-mono text-xs text-[var(--foreground)]" title={aValeur ? valeur ?? undefined : undefined}>
          {aValeur ? affichage ?? valeur : "—"}
        </span>
        {copiable && aValeur && (
          <button
            onClick={() => void copier()}
            className="shrink-0 rounded p-1 text-[var(--color-lr-text-tertiary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)] focus:outline-none focus-visible:ring-1 focus-visible:ring-accent/40"
            title="Copier"
            aria-label={`Copier ${label}`}
          >
            {copie ? (
              <span className="text-[10px] text-success">Copié</span>
            ) : (
              <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            )}
          </button>
        )}
      </span>
    </div>
  );
}
