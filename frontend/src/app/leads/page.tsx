"use client";

import { Suspense, useEffect, useState, useCallback } from "react";
import Link from "next/link";
import dynamic from "next/dynamic";
import { useRouter, useSearchParams } from "next/navigation";
import { useApiClient, useIsManager, formatErreur } from "@/lib/auth";
import { useRealtime, useToast } from "@/lib/realtime";
import AppLayout from "@/components/AppLayout";
import ProgressionBadge from "@/components/common/ProgressionBadge";
import ScoringGauge from "@/components/common/ScoringGauge";
import EmptyState from "@/components/common/EmptyState";
import ErrorState from "@/components/common/ErrorState";
import PageHeader from "@/components/common/PageHeader";
import InfoIcon from "@/components/common/InfoIcon";
import ArchiveIcon from "@/components/common/ArchiveIcon";
import Tooltip from "@/components/common/Tooltip";
import { STATUTS_PIPELINE, STATUTS_ACTIFS_DEFAUT, STATUT_LABELS } from "@/lib/leads/statuts";
import { formatEnvoiAutoLabel } from "@/lib/envoiAuto";
import { toastUndoDesarchiveLead } from "@/lib/rdvUndo";

const KanbanBoard = dynamic(() => import("@/components/leads/KanbanBoard"), { ssr: false });

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// Constantes partagées : labels user-facing dans /lib/leads/statuts.ts
const TOUS_STATUTS = [...STATUTS_PIPELINE];
const STATUTS_DEFAUT = [...STATUTS_ACTIFS_DEFAUT];

interface Lead {
  idLead: number;
  hotnessScore: number | null;
  nom: string;
  email: string;
  telephone: string;
  marque: string;
  modele: string;
  territoire: string;
  source: string;
  statut: string;
  scoring: number | null;
  vendeurId: number | null;
  creeLe: string;
  premierMessageAutoPrevuLe: string | null;
  premierMessageAutoEffectifLe: string | null;
  premierMessageAutoHorsPlage: boolean;
}

interface PageResponse {
  content: Lead[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}

function SkeletonRow() {
  return (
    <tr className="border-b border-[var(--border)]">
      {Array.from({ length: 6 }).map((_, i) => (
        <td key={i} className="px-4 py-3">
          <div className="h-4 w-20 rounded animate-shimmer" />
        </td>
      ))}
    </tr>
  );
}

export default function LeadsPage() {
  return <Suspense><LeadsContent /></Suspense>;
}

function LeadsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const apiFetch = useApiClient();
  const isManager = useIsManager();
  const toast = useToast();
  const [leads, setLeads] = useState<Lead[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<number>(() => {
    const fromUrl = searchParams.get("size");
    const n = fromUrl ? parseInt(fromUrl, 10) : 20;
    return [20, 50, 100].includes(n) ? n : 20;
  });
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  // Input "Aller à la page X" — controlé localement pour permettre la saisie
  // sans déclencher un fetch à chaque keystroke. Le user submit en blur ou Enter.
  const [pageJumpInput, setPageJumpInput] = useState("");

  const [statuts, setStatuts] = useState<string[]>(() => {
    const fromUrl = searchParams.get("statut");
    if (fromUrl) return fromUrl.split(",");
    return [...STATUTS_DEFAUT];
  });
  const [statutDropdownOpen, setStatutDropdownOpen] = useState(false);
  // Toggle d'ouverture du groupe de filtres sur mobile (desktop : toujours
  // visible inline dans la barre). Sur mobile, les selects Statut/Marque/Source/
  // Vendeur sont caches derriere un bouton "Filtres" pour eviter le scroll
  // dégueulasse de flex-wrap sur ecran etroit.
  const [mobileFiltresOpen, setMobileFiltresOpen] = useState(false);
  const [recherche, setRecherche] = useState(searchParams.get("recherche") || "");
  const [marque, setMarque] = useState(searchParams.get("marque") || "");
  const [source, setSource] = useState(searchParams.get("source") || "");
  const [vendeur, setVendeur] = useState(searchParams.get("vendeur") || "");

  const [marques, setMarques] = useState<string[]>([]);
  const [sources, setSources] = useState<string[]>([]);
  const [vendeurs, setVendeurs] = useState<{ id: number; nom: string; prenom: string }[]>([]);
  // Bascule "Actifs | Archives". En mode Archives, on affiche les prospects
  // archivés (archive=true) et on force la vue liste (le Kanban reste sur les actifs).
  const [archives, setArchives] = useState(searchParams.get("archives") === "1");
  const [vueKanban, setVueKanban] = useState(
    searchParams.get("archives") === "1" ? false : searchParams.get("vue") === "liste" ? false : true
  );
  const [kanbanRefreshKey, setKanbanRefreshKey] = useState(0);
  const [sortField, setSortField] = useState("creeLe");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");

  // Charger la liste complete des marques, sources et vendeurs au mount.
  // On utilise un endpoint dedie /api/leads/filtres au lieu de fetcher 1000 leads (perf + memoire).
  useEffect(() => {
    apiFetch(`${API_BASE}/api/leads/filtres`)
      .then((res) => res.ok ? res.json() : null)
      .then((data: { marques: string[]; sources: string[] } | null) => {
        if (!data) return;
        setMarques(data.marques ?? []);
        setSources(data.sources ?? []);
      })
      .catch((err) => console.warn("Leads filters/vendeurs fetch failed", err));
    apiFetch(`${API_BASE}/api/equipe?actifsUniquement=true`)
      .then((res) => res.ok ? res.json() : [])
      .then((data: { id: number; nom: string; prenom: string; role: string }[]) =>
        setVendeurs(data.filter((v) => v.role === "VENDEUR")))
      .catch((err) => console.warn("Leads filters/vendeurs fetch failed", err));
  }, [apiFetch]);

  // Debounce sur la recherche texte
  const [rechercheDebounced, setRechercheDebounced] = useState(recherche);
  useEffect(() => {
    const timer = setTimeout(() => setRechercheDebounced(recherche), 350);
    return () => clearTimeout(timer);
  }, [recherche]);

  // Reset page a 0 quand la recherche change : sinon l'utilisateur peut etre sur
  // la page 3 d'un filtre, tape une recherche qui retourne 1 page, et tombe sur
  // une liste vide sans comprendre pourquoi.
  useEffect(() => {
    setPage(0);
  }, [rechercheDebounced]);

  function toggleVue(mode: "liste" | "kanban") {
    setVueKanban(mode === "kanban");
    if (mode === "liste") fetchLeads();
    if (mode === "kanban") setKanbanRefreshKey((k) => k + 1);
  }

  /** Bascule Actifs / Archives. En mode Archives on force la vue liste (le Kanban
   *  reste réservé aux prospects actifs) et on repart page 0. */
  function toggleArchives(mode: "actifs" | "archives") {
    const versArchives = mode === "archives";
    setArchives(versArchives);
    if (versArchives) setVueKanban(false);
    setPage(0);
  }

  /** Désarchive un prospect depuis la liste (mode Archives). Action REVERSIBLE :
   *  toast succès + "Annuler" qui ré-archive. On retire la ligne de la liste courante
   *  (elle n'est plus un archivé) puis on refetch pour rester cohérent. */
  async function handleDesarchiver(leadId: number) {
    try {
      const res = await apiFetch(`${API_BASE}/api/leads/${leadId}/restaurer`, { method: "POST" });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setLeads((prev) => prev.filter((l) => l.idLead !== leadId));
      toastUndoDesarchiveLead({
        showToast: toast.showToast,
        apiFetch,
        leadId,
        onReverted: () => fetchLeads(),
      });
    } catch (err) {
      toast.showToast({ titre: "Désarchivage échoué", message: formatErreur(err), tone: "danger" });
    }
  }

  function handleSort(field: string) {
    if (sortField === field) {
      setSortDir(sortDir === "asc" ? "desc" : "asc");
    } else {
      setSortField(field);
      setSortDir("desc");
    }
    setPage(0);
  }

  function SortHeader({ field, label }: { field: string; label: string }) {
    const active = sortField === field;
    const ariaSort: "ascending" | "descending" | "none" = active
      ? (sortDir === "asc" ? "ascending" : "descending")
      : "none";
    return (
      <th
        scope="col"
        aria-sort={ariaSort}
        className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] sticky top-0 bg-[var(--muted)]"
      >
        <button
          type="button"
          onClick={() => handleSort(field)}
          className="flex items-center gap-1 select-none hover:text-[var(--foreground)] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 rounded"
        >
          {label}
          {active && <span className="text-accent" aria-hidden="true">{sortDir === "asc" ? "↑" : "↓"}</span>}
        </button>
      </th>
    );
  }

  async function handleAssignVendeur(leadId: number, newVendeurId: string) {
    // Rollback explicite si !ok (l'API peut renvoyer 403 sur droits insuffisants).
    // Avant : seul le throw catch toastait, un 4xx restait silencieux.
    const previousLead = leads.find((l) => l.idLead === leadId);
    try {
      const body = newVendeurId ? { vendeurId: Number(newVendeurId) } : { vendeurId: null };
      const res = await apiFetch(`${API_BASE}/api/leads/${leadId}`, {
        method: "PUT",
        body: JSON.stringify(body),
      });
      if (res.ok) {
        setLeads((prev) => prev.map((l) => l.idLead === leadId ? { ...l, vendeurId: newVendeurId ? Number(newVendeurId) : null } : l));
        setKanbanRefreshKey((k) => k + 1);
      } else {
        // Rollback : remettre le state au précédent + signaler à l'utilisateur.
        if (previousLead) {
          setLeads((prev) => prev.map((l) => l.idLead === leadId ? previousLead : l));
        }
        throw new Error(`Erreur ${res.status}`);
      }
    } catch (err) {
      console.warn("Assign vendeur failed", err);
      toast.showToast({ titre: "Réassignation échouée", message: formatErreur(err), tone: "danger" });
    }
  }

  const fetchLeads = useCallback(async () => {
    setLoading(true);
    setLoadError("");

    try {
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("size", String(pageSize));
      params.set("sort", `${sortField},${sortDir}`);
      // En mode Archives : on N'applique PAS le filtre de statut par défaut restrictif
      // (STATUTS_DEFAUT masque TERMINE et pourrait donner une liste d'archives vide et
      // trompeuse). On montre tous les statuts archivés ; la recherche/tri restent actifs.
      if (!archives && statuts.length > 0 && statuts.length < TOUS_STATUTS.length) {
        params.set("statut", statuts.join(","));
      }
      if (rechercheDebounced) params.set("recherche", rechercheDebounced);
      if (marque) params.set("marque", marque);
      if (source) params.set("source", source);
      if (vendeur) params.set("vendeurId", vendeur);
      if (archives) params.set("archive", "true");

      const res = await apiFetch(`${API_BASE}/api/leads?${params.toString()}`);

      if (!res.ok) throw new Error(`Erreur ${res.status}`);

      const data: PageResponse = await res.json();
      setLeads(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      setLoadError(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statuts, rechercheDebounced, marque, source, vendeur, sortField, sortDir, archives, apiFetch]);

  useEffect(() => {
    fetchLeads();
  }, [fetchLeads]);

  // Reset le badge "nouveaux leads" au mount de la page + auto-refresh sur event SSE.
  // Important : on rafraichit la vue ACTIVE (liste OU kanban, le kanban a son propre
  // fetch via refreshKey, fetchLeads ne le concerne pas).
  const { resetNouveauxLeads, lastEvent } = useRealtime();
  useEffect(() => {
    resetNouveauxLeads();
  }, [resetNouveauxLeads]);
  useEffect(() => {
    if (lastEvent?.type === "lead.created" || lastEvent?.type === "lead.batch.imported" || lastEvent?.type === "lead.updated") {
      if (vueKanban) {
        setKanbanRefreshKey((k) => k + 1);
      } else {
        fetchLeads();
      }
      resetNouveauxLeads();
    }
  }, [lastEvent, vueKanban, fetchLeads, resetNouveauxLeads]);

  // Synchronise les filtres dans l'URL
  useEffect(() => {
    const params = new URLSearchParams();
    if (archives) params.set("archives", "1");
    // "vue=liste" est implicite en mode Archives (Kanban désactivé), on ne le duplique pas.
    if (!vueKanban && !archives) params.set("vue", "liste");
    // Le filtre de statut n'a pas d'effet en mode Archives (tous statuts affichés) : on l'omet de l'URL.
    if (!archives && statuts.length > 0 && statuts.length < TOUS_STATUTS.length) params.set("statut", statuts.join(","));
    if (recherche) params.set("recherche", recherche);
    if (marque) params.set("marque", marque);
    if (source) params.set("source", source);
    if (vendeur) params.set("vendeur", vendeur);
    if (page > 0) params.set("page", String(page));
    if (pageSize !== 20) params.set("size", String(pageSize));
    router.replace(`/leads${params.toString() ? "?" + params.toString() : ""}`, { scroll: false });
  }, [statuts, recherche, marque, source, vendeur, page, pageSize, vueKanban, archives, router]);


  /* py-2.5 = 40px de hauteur effective ; aligne tous les filtres + recherche
     sur un même rythme tactile (WCAG 2.5.5 AAA recommande 44, AA accepte 40
     sur les composants compacts contigus). */
  const selectClass =
    "rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2.5 text-sm text-[var(--foreground)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-colors";
  const inputClass =
    "rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2.5 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-secondary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-colors";

  // Compteur de filtres actifs (≠ préréglage par défaut). Hoisted hors du JSX
  // pour piloter à la fois le badge mobile, le bouton "Effacer" desktop et
  // l'aria-pressed des segments.
  const isStatutsDefault =
    statuts.length === STATUTS_DEFAUT.length &&
    STATUTS_DEFAUT.every((s) => statuts.includes(s));
  const activeFiltresCount =
    // Le filtre de statut n'est pas appliqué en mode Archives : il ne compte pas.
    (!archives && !isStatutsDefault ? 1 : 0) +
    (marque ? 1 : 0) +
    (source ? 1 : 0) +
    (vendeur ? 1 : 0) +
    (recherche ? 1 : 0);

  function effacerFiltres() {
    setStatuts([...STATUTS_DEFAUT]);
    setMarque("");
    setSource("");
    setVendeur("");
    setRecherche("");
    setPage(0);
  }

  return (
    <AppLayout>
      <div className={`px-6 py-4 ${vueKanban ? "h-full flex flex-col overflow-hidden" : ""}`}>
        {/* Header signature : PageHeader compact restaure la signature Atelier
            (eyebrow ember + display Bricolage + count mono). Filtres séparés
            en barre dédiée en-dessous pour respirer. */}
        <PageHeader
          variant="compact"
          eyebrow="Pipeline"
          title="Prospects"
          count={!loading ? totalElements : undefined}
        />

        {/* Barre filtres séparée. Recherche flex-1 + filtres groupés + segmented
            control liste/Kanban labellé. */}
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[200px]">
            <svg className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-lr-text-secondary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              value={recherche}
              onChange={(e) => setRecherche(e.target.value)}
              placeholder="Rechercher par nom, email..."
              className={`${inputClass} pl-9 w-full`}
            />
          </div>
          {/* Bouton "Filtres" mobile uniquement : ouvre le groupe selects en dessous. */}
          {(
              <button
                type="button"
                onClick={() => setMobileFiltresOpen((v) => !v)}
                className={`${selectClass} md:hidden flex items-center gap-1.5`}
                aria-expanded={mobileFiltresOpen}
                aria-label="Afficher les filtres"
              >
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2a1 1 0 01-.293.707L14 13.414V19a1 1 0 01-.553.894l-4 2A1 1 0 018 21v-7.586L3.293 6.707A1 1 0 013 6V4z" />
                </svg>
                Filtres
                {activeFiltresCount > 0 && (
                  <span className="rounded-full bg-accent px-1.5 text-[10px] font-semibold text-white">{activeFiltresCount}</span>
                )}
              </button>
            )}
          {/* Wrapper qui regroupe les selects, visible inline en desktop,
              cache derriere le toggle mobileFiltresOpen sur mobile. */}
          <div className={`${mobileFiltresOpen ? "flex" : "hidden"} md:flex w-full md:w-auto flex-col md:flex-row md:items-center md:flex-wrap gap-2 md:gap-3`}>
          {!vueKanban && !archives && <div className="relative">
            <button onClick={() => setStatutDropdownOpen(!statutDropdownOpen)}
              className={`${selectClass} w-full md:w-auto flex items-center gap-1`}>
              Statut {statuts.length < TOUS_STATUTS.length && <span className="rounded-full bg-accent px-1.5 text-[10px] text-white">{statuts.length}</span>}
              <svg className="h-3 w-3 ml-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" /></svg>
            </button>
            {statutDropdownOpen && (
              <div className="absolute top-full left-0 z-50 mt-1 min-w-[160px] rounded-lg border border-[var(--border)] bg-[var(--card)] py-1 shadow-lg">
                {TOUS_STATUTS.map((s) => (
                  <label key={s} className="flex items-center gap-2 px-3 py-1.5 text-xs text-[var(--foreground)] hover:bg-[var(--muted)] cursor-pointer">
                    <input type="checkbox" checked={statuts.includes(s)}
                      onChange={(e) => {
                        const next = e.target.checked ? [...statuts, s] : statuts.filter((x) => x !== s);
                        setStatuts(next);
                        setPage(0);
                      }}
                      className="rounded border-[var(--border)] text-accent focus:ring-accent/30"
                    />
                    {STATUT_LABELS[s] ?? s}
                  </label>
                ))}
                <div className="border-t border-[var(--border)] mt-1 pt-1 px-3 pb-1 flex gap-2">
                  <button onClick={() => { setStatuts([...TOUS_STATUTS]); setPage(0); }}
                    className={`text-[10px] font-medium ${statuts.length === TOUS_STATUTS.length ? "text-accent" : "text-[var(--color-lr-text-tertiary)] hover:text-[var(--foreground)]"}`}>Tous</button>
                  <button onClick={() => { setStatuts([...STATUTS_DEFAUT]); setPage(0); }}
                    className={`text-[10px] font-medium ${statuts.length === STATUTS_DEFAUT.length && !statuts.includes("TERMINE") ? "text-accent" : "text-[var(--color-lr-text-tertiary)] hover:text-[var(--foreground)]"}`}>Actifs</button>
                </div>
              </div>
            )}
          </div>}
          {/* Marque : visible seulement si la concession a >= 2 marques distinctes
              (filtre inutile pour les concessions mono-marque). */}
          {marques.length >= 2 && (
            <select
              value={marque}
              onChange={(e) => { setMarque(e.target.value); setPage(0); }}
              className={`${selectClass} w-full md:w-auto`}
            >
              <option value="">Toutes les marques</option>
              {marques.map((m) => (
                <option key={m} value={m}>{m}</option>
              ))}
            </select>
          )}
          {/* Source : pertinent depuis l'import par email (LeBonCoin, La Centrale, etc.)
              Visible des qu'il y a au moins 1 source detectee. */}
          {sources.length > 0 && (
            <select
              value={source}
              onChange={(e) => { setSource(e.target.value); setPage(0); }}
              className={`${selectClass} w-full md:w-auto`}
            >
              <option value="">Toutes les sources</option>
              {sources.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          )}
          {isManager && vendeurs.length > 0 && (
            <select
              value={vendeur}
              onChange={(e) => { setVendeur(e.target.value); setPage(0); }}
              className={`${selectClass} w-full md:w-auto`}
            >
              <option value="">Tous les vendeurs</option>
              <option value="0">Non assignés</option>
              {vendeurs.map((v) => (
                <option key={v.id} value={String(v.id)}>{v.prenom} {v.nom}</option>
              ))}
            </select>
          )}
          </div>
          {/* Effacer filtres : visible uniquement si au moins 1 filtre actif. */}
          {activeFiltresCount > 0 && (
            <button
              type="button"
              onClick={effacerFiltres}
              className="inline-flex items-center gap-1 rounded-lg px-3 py-2 text-xs font-medium text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
              title="Effacer tous les filtres"
            >
              <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
              Effacer
            </button>
          )}
          {/* Segmented control Actifs | Archives. En mode Archives, la vue Kanban
              est masquée (le Kanban reste sur les actifs) et le filtre statut est ignoré. */}
          <div className="flex rounded-lg border border-[var(--border)] bg-[var(--card)] p-0.5" role="group" aria-label="Prospects actifs ou archivés">
            <button onClick={() => toggleArchives("actifs")}
              type="button"
              aria-pressed={!archives}
              className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors ${!archives ? "bg-accent text-white" : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"}`}>
              Actifs
            </button>
            <button onClick={() => toggleArchives("archives")}
              type="button"
              aria-pressed={archives}
              className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors ${archives ? "bg-accent text-white" : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"}`}>
              Archives
            </button>
          </div>

          {/* Segmented control liste/Kanban — icône + label (texte visible md:),
              hauteur alignée sur les filtres (36px). TOUJOURS visible : en mode
              Archives, seule l'option Kanban est désactivée (le Kanban reste
              réservé aux prospects actifs) plutôt que de masquer tout le contrôle. */}
          {/* aria-pressed pattern (simpler than full tablist/tabpanel which would
              require a tabpanel sibling — overkill ici car le contenu reste dans
              le même container). */}
          <div className="flex rounded-lg border border-[var(--border)] bg-[var(--card)] p-0.5" role="group" aria-label="Vue prospects">
            <button onClick={() => toggleVue("liste")}
              type="button"
              aria-pressed={!vueKanban}
              aria-label="Vue liste"
              className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors ${!vueKanban ? "bg-accent text-white" : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"}`}>
              <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 10h16M4 14h16M4 18h16" /></svg>
              <span className="hidden sm:inline">Liste</span>
            </button>
            {archives ? (
              <Tooltip content="Le Kanban ne concerne que les prospects actifs">
                <button
                  type="button"
                  disabled
                  aria-pressed={false}
                  aria-label="Vue Kanban (indisponible sur les archivés)"
                  className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium text-[var(--color-lr-text-secondary)] opacity-40 cursor-not-allowed">
                  <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 17V7m0 10a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h2a2 2 0 012 2m0 10a2 2 0 002 2h2a2 2 0 002-2M9 7a2 2 0 012-2h2a2 2 0 012 2m0 10V7m0 10a2 2 0 002 2h2a2 2 0 002-2V7a2 2 0 00-2-2h-2a2 2 0 00-2 2" /></svg>
                  <span className="hidden sm:inline">Kanban</span>
                </button>
              </Tooltip>
            ) : (
              <button onClick={() => toggleVue("kanban")}
                type="button"
                aria-pressed={vueKanban}
                aria-label="Vue Kanban"
                className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors ${vueKanban ? "bg-accent text-white" : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"}`}>
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 17V7m0 10a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h2a2 2 0 012 2m0 10a2 2 0 002 2h2a2 2 0 002-2M9 7a2 2 0 012-2h2a2 2 0 012 2m0 10V7m0 10a2 2 0 002 2h2a2 2 0 002-2V7a2 2 0 00-2-2h-2a2 2 0 00-2 2" /></svg>
                <span className="hidden sm:inline">Kanban</span>
              </button>
            )}
          </div>
        </div>

        {/* Vue Kanban */}
        {vueKanban && (
          // Wrapper flex-1 + min-h-0 : permet au KanbanBoard de prendre toute la
          // hauteur restante (apres le header/filtres au-dessus) sans deborder.
          // min-h-0 est obligatoire dans un flex parent sinon le contenu force
          // l'expansion et casse l'overflow.
          <div className="flex-1 min-h-0">
            <KanbanBoard search={rechercheDebounced} source={source} marque={marque} vendeurId={vendeur ? Number(vendeur) : undefined} vendeurs={isManager ? vendeurs : undefined} onAssignVendeur={isManager ? handleAssignVendeur : undefined} refreshKey={kanbanRefreshKey} />
          </div>
        )}

        {/* Vue Liste — erreur de chargement : ErrorState remplace le tableau entier */}
        {!vueKanban && loadError && (
          <ErrorState message={loadError} onRetry={fetchLeads} />
        )}

        {!vueKanban && !loadError && (
        <div className="rounded-xl bg-[var(--card)] border border-[var(--border)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[var(--border)] bg-[var(--muted)]">
                  <SortHeader field="nom" label="Nom" />
                  <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] sticky top-0 bg-[var(--muted)]">Véhicule</th>
                  {/* Statut non triable : trier l'enum donnerait l'ordre alphabétique
                      (pas le pipeline). La vue Kanban est la vue ordonnée par étape. */}
                  <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] sticky top-0 bg-[var(--muted)]">Statut</th>
                  <th
                    scope="col"
                    aria-sort={sortField === "hotnessScore" ? (sortDir === "asc" ? "ascending" : "descending") : "none"}
                    className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] sticky top-0 bg-[var(--muted)]"
                  >
                    <span className="inline-flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() => handleSort("hotnessScore")}
                        className="flex items-center gap-1 select-none hover:text-[var(--foreground)] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 rounded"
                      >
                        Score
                        {sortField === "hotnessScore" && <span className="text-accent" aria-hidden="true">{sortDir === "asc" ? "↑" : "↓"}</span>}
                      </button>
                      <InfoIcon>
                        Score d&apos;engagement (0-100) calculé par l&apos;IA après chaque échange WhatsApp. 70+ chaud (prio haute), 40-69 tiède, 0-39 froid. Un lead sans conversation n&apos;a pas encore de score.
                      </InfoIcon>
                    </span>
                  </th>
                  {isManager && <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] sticky top-0 bg-[var(--muted)]">Vendeur</th>}
                  <SortHeader field="creeLe" label="Date" />
                  {archives && <th className="px-4 py-3 font-medium text-[var(--color-lr-text-secondary)] sticky top-0 bg-[var(--muted)]"><span className="sr-only">Actions</span></th>}
                </tr>
              </thead>
              <tbody>
                {/* Loading skeleton */}
                {loading && Array.from({ length: 8 }).map((_, i) => <SkeletonRow key={i} />)}

                {/* Empty state — 2 variantes :
                    (1) aucun prospect dans la base (tenant fresh) → CTA Import
                    (2) filtres trop restrictifs → invite à élargir */}
                {!loading && leads.length === 0 && !loadError && (() => {
                  const isStatutsDefaultEmpty =
                    statuts.length === STATUTS_DEFAUT.length &&
                    STATUTS_DEFAUT.every((s) => statuts.includes(s));
                  // En mode Archives, le filtre de statut par défaut n'est pas appliqué :
                  // seules la recherche + marque/source/vendeur comptent comme "filtres actifs".
                  const hasActiveFilters = Boolean(
                    rechercheDebounced || marque || source || vendeur || (!archives && !isStatutsDefaultEmpty)
                  );
                  const baseCols = isManager ? 6 : 5;
                  return (
                    <tr>
                      <td colSpan={archives ? baseCols + 1 : baseCols} className="px-4 py-10">
                        {archives && !hasActiveFilters ? (
                          <EmptyState
                            eyebrow="Aucune archive"
                            eyebrowTone="muted"
                            title="Aucun prospect archivé."
                            description="Les prospects que vous archivez depuis leur fiche apparaissent ici."
                            size="md"
                            align="center"
                          />
                        ) : hasActiveFilters ? (
                          <EmptyState
                            eyebrow="Filtre vide"
                            eyebrowTone="muted"
                            title="Aucun prospect ne colle à ces filtres."
                            description="Élargissez la recherche ou revenez au pipeline complet."
                            size="sm"
                            align="center"
                          />
                        ) : (
                          <EmptyState
                            eyebrow="Pipeline vide"
                            eyebrowTone="accent"
                            title="Aucun prospect pour l'instant."
                            description="Importez votre première liste pour démarrer. CSV, Excel, copier-coller, ou laissez Leadrive ingérer vos emails de leads automatiquement."
                            action={
                              <Link
                                href="/import"
                                className="inline-flex items-center gap-1.5 rounded-lg btn-primary px-4 py-2 text-sm font-medium text-white transition-colors"
                              >
                                Importer des prospects
                                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
                                </svg>
                              </Link>
                            }
                            size="md"
                            align="center"
                            divider
                          />
                        )}
                      </td>
                    </tr>
                  );
                })()}

                {/* Data rows — la navigation se fait via <Link> sur la première
                    cellule (nom), pas onClick row. Permet (a) sélection de texte
                    dans les autres cellules sans déclencher de nav, (b) tabIndex
                    + Enter clavier, (c) sémantique de lien correcte pour SR.
                    Alternance striping continue cross-page via (page * size + i). */}
                {!loading &&
                  leads.map((lead, i) => {
                    const absIdx = page * pageSize + i;
                    return (
                      <tr
                        key={lead.idLead || i}
                        className={`border-b border-[var(--border)] text-[var(--foreground)] transition-colors hover:bg-accent/5 ${
                          absIdx % 2 === 1 ? "bg-[var(--muted)]/30" : ""
                        }`}
                      >
                        <td className="px-4 py-3 font-medium">
                          <Link
                            href={`/leads/${lead.idLead}`}
                            className="block text-[var(--foreground)] hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 rounded"
                          >
                            {lead.nom || "—"}
                          </Link>
                          {lead.premierMessageAutoPrevuLe && (
                            <span
                              className="mt-1 inline-flex items-center gap-1 rounded-full bg-accent/10 px-2 py-0.5 text-[10.5px] font-medium text-accent"
                              title={
                                lead.premierMessageAutoHorsPlage
                                  ? "Envoi automatique du premier message WhatsApp reporté hors plage d'envoi"
                                  : "Envoi automatique du premier message WhatsApp programmé"
                              }
                            >
                              <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                              </svg>
                              auto {formatEnvoiAutoLabel(lead.premierMessageAutoEffectifLe, lead.premierMessageAutoPrevuLe)}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-[var(--color-lr-text-secondary)]">
                          {[lead.marque, lead.modele].filter(Boolean).join(" ") || "—"}
                        </td>
                        <td className="px-4 py-3">
                          <ProgressionBadge statut={lead.statut} />
                        </td>
                        <td className="px-4 py-3">
                          <ScoringGauge score={lead.scoring} hotnessScore={lead.hotnessScore} size="sm" />
                        </td>
                        {isManager && (
                          <td className="px-4 py-3">
                            {vendeurs.length > 0 ? (
                              <select
                                value={lead.vendeurId != null ? String(lead.vendeurId) : ""}
                                onChange={(e) => handleAssignVendeur(lead.idLead, e.target.value)}
                                className="rounded border border-[var(--border)] bg-transparent px-1.5 py-0.5 text-xs text-[var(--foreground)] focus:border-accent focus:outline-none"
                              >
                                <option value="">Non assigné</option>
                                {vendeurs.map((v) => (
                                  <option key={v.id} value={String(v.id)}>{v.prenom} {v.nom}</option>
                                ))}
                              </select>
                            ) : (
                              <span className="text-[var(--color-lr-text-tertiary)]">—</span>
                            )}
                          </td>
                        )}
                        <td className="px-4 py-3 text-xs text-[var(--color-lr-text-secondary)]">
                          {lead.creeLe ? new Date(lead.creeLe).toLocaleDateString("fr-FR", { day: "2-digit", month: "2-digit", year: "2-digit" }) : "—"}
                        </td>
                        {archives && (
                          <td className="px-4 py-3 text-right">
                            <button
                              type="button"
                              onClick={() => handleDesarchiver(lead.idLead)}
                              className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-[var(--color-accent)] transition-colors hover:bg-accent/10"
                              aria-label={`Désarchiver ${lead.nom || "ce prospect"}`}
                            >
                              <ArchiveIcon className="h-3.5 w-3.5" />
                              Désarchiver
                            </button>
                          </td>
                        )}
                      </tr>
                    );
                  })}
              </tbody>
            </table>
          </div>

          {/* Pagination — affichage range + jump-to-page + page-size selector.
              Visible dès qu'il y a au moins 1 page (même avec 1 seul résultat,
              le select de taille reste utile pour anticiper). */}
          {totalElements > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--border)] px-4 py-3">
              {/* Range affichage : "Affichage 41-60 sur 980" */}
              <p className="font-mono text-[11px] text-[var(--color-lr-text-secondary)]">
                <span className="font-semibold text-[var(--foreground)]">
                  {page * pageSize + 1}
                  <span className="mx-0.5 text-[var(--color-lr-text-tertiary)]">–</span>
                  {Math.min((page + 1) * pageSize, totalElements)}
                </span>
                <span className="ml-1 text-[var(--color-lr-text-tertiary)]">sur</span>{" "}
                <span className="font-semibold text-[var(--foreground)]">{totalElements}</span>
              </p>

              <div className="flex flex-wrap items-center gap-2">
                {/* Nav précédent/suivant */}
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-1.5 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--muted)] disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Précédent
                </button>

                {/* Pagination numbered : pages adjacentes cliquables (-2/+2) +
                    jump-to-page input pour les sauts longs. Pattern standard
                    "1 ... 6 7 8 ... 12" facilite la navigation power user. */}
                {totalPages > 1 && (() => {
                  // Calcule la fenêtre des pages adjacentes (5 max) autour de la courante.
                  const window = 2;
                  const start = Math.max(0, page - window);
                  const end = Math.min(totalPages - 1, page + window);
                  const pages: number[] = [];
                  for (let i = start; i <= end; i++) pages.push(i);
                  const showFirstEllipsis = start > 1;
                  const showLastEllipsis = end < totalPages - 2;
                  return (
                    <div className="flex items-center gap-0.5 font-mono text-[12px] text-[var(--color-lr-text-secondary)]">
                      {/* Première page */}
                      {start > 0 && (
                        <button
                          type="button"
                          onClick={() => setPage(0)}
                          className="rounded px-2 py-1 transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                        >
                          1
                        </button>
                      )}
                      {showFirstEllipsis && (
                        <span className="px-1 text-[var(--color-lr-text-tertiary)]">…</span>
                      )}
                      {/* Pages adjacentes */}
                      {pages.map((p) => (
                        <button
                          key={p}
                          type="button"
                          onClick={() => setPage(p)}
                          aria-current={p === page ? "page" : undefined}
                          className={`rounded px-2 py-1 transition-colors ${
                            p === page
                              ? "bg-accent font-semibold text-white"
                              : "hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                          }`}
                        >
                          {p + 1}
                        </button>
                      ))}
                      {showLastEllipsis && (
                        <span className="px-1 text-[var(--color-lr-text-tertiary)]">…</span>
                      )}
                      {/* Dernière page */}
                      {end < totalPages - 1 && (
                        <button
                          type="button"
                          onClick={() => setPage(totalPages - 1)}
                          className="rounded px-2 py-1 transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                        >
                          {totalPages}
                        </button>
                      )}
                      {/* Jump pour les très gros pipelines */}
                      {totalPages > 10 && (
                        <form
                          onSubmit={(e) => {
                            e.preventDefault();
                            const n = parseInt(pageJumpInput, 10);
                            if (!isNaN(n) && n >= 1 && n <= totalPages) {
                              setPage(n - 1);
                            }
                            setPageJumpInput("");
                          }}
                          className="ml-2 flex items-center gap-1"
                        >
                          <input
                            type="text"
                            inputMode="numeric"
                            pattern="[0-9]*"
                            value={pageJumpInput}
                            onChange={(e) => setPageJumpInput(e.target.value.replace(/\D/g, ""))}
                            placeholder="→ p"
                            aria-label="Aller à la page"
                            className="w-12 rounded border border-[var(--border)] bg-[var(--card)] px-2 py-1 text-center text-[12px] text-[var(--foreground)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30"
                          />
                        </form>
                      )}
                    </div>
                  );
                })()}

                <button
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-1.5 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--muted)] disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Suivant
                </button>

                {/* Page-size selector — 20 par défaut, 50 et 100 pour les gros pipelines. */}
                <select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value));
                    setPage(0);
                  }}
                  aria-label="Nombre de lignes par page"
                  className="rounded-lg border border-[var(--border)] bg-[var(--card)] px-2 py-1.5 text-[12px] text-[var(--color-lr-text-secondary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30"
                >
                  <option value={20}>20 / page</option>
                  <option value={50}>50 / page</option>
                  <option value={100}>100 / page</option>
                </select>
              </div>
            </div>
          )}
        </div>
        )}
      </div>
    </AppLayout>
  );
}
