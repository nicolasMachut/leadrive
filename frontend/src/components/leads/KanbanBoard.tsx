"use client";

import { useCallback, useEffect, useState } from "react";
import { DndContext, DragEndEvent, DragOverlay, DragStartEvent, PointerSensor, useSensor, useSensors } from "@dnd-kit/core";
import { useApiClient, formatErreur } from "@/lib/auth";
import SpeedLines from "@/components/common/SpeedLines";
import ErrorState from "@/components/common/ErrorState";
import KanbanColonne from "./KanbanColonne";
import KanbanCarte from "./KanbanCarte";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface KanbanCarteData {
  id: number;
  nom: string;
  telephone: string | null;
  email: string | null;
  vehiculeInteret: string | null;
  scoreIA: number | null;
  dateCreation: string;
  dernierContact: string | null;
  vendeurId: number | null;
  vendeurNom: string | null;
  sourceOrigine: string | null;
  statut: string;
  tempsDepuisDernierContact: string | null;
  priorite: string;
  tags: string[];
  kilometrage: number | null;
  energie: string | null;
}

export interface KanbanColonneData {
  statut: string;
  label: string;
  couleur: string;
  leads: KanbanCarteData[];
  count: number;
}

interface KanbanData {
  colonnes: KanbanColonneData[];
  totalLeads: number;
}

export interface VendeurOption {
  id: number;
  nom: string;
  prenom: string;
}

interface Props {
  vendeurId?: number | null;
  search?: string;
  statut?: string;
  source?: string;
  marque?: string;
  vendeurs?: VendeurOption[];
  onAssignVendeur?: (leadId: number, vendeurId: string) => void;
  refreshKey?: number;
}

export default function KanbanBoard({ vendeurId, search, statut, source, marque, vendeurs, onAssignVendeur, refreshKey }: Props) {
  const apiFetch = useApiClient();
  const [data, setData] = useState<KanbanData | null>(null);
  const [loading, setLoading] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [activeCarte, setActiveCarte] = useState<KanbanCarteData | null>(null);
  const [errorToast, setErrorToast] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
  );

  const fetchKanban = useCallback(async () => {
    setLoading(true);
    setErreur(null);
    try {
      const params = new URLSearchParams();
      if (vendeurId != null) params.set("vendeurId", String(vendeurId));
      if (search) params.set("search", search);
      if (statut) params.set("statut", statut);
      if (source) params.set("source", source);
      if (marque) params.set("marque", marque);
      const url = `${API_BASE}/api/leads/kanban${params.toString() ? "?" + params.toString() : ""}`;
      const res = await apiFetch(url);
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setData(await res.json());
    } catch (err) {
      console.warn("Kanban fetch failed", err);
      setErreur(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [apiFetch, vendeurId, search, statut, source, marque, refreshKey]);

  useEffect(() => { fetchKanban(); }, [fetchKanban]);

  // Auto-dismiss du toast d'erreur apres 5s (sinon l'utilisateur doit cliquer la croix
  // a chaque fois et ca pollue le coin de la fenetre).
  useEffect(() => {
    if (!errorToast) return;
    const t = setTimeout(() => setErrorToast(null), 5000);
    return () => clearTimeout(t);
  }, [errorToast]);

  function handleDragStart(event: DragStartEvent) {
    const carte = findCarte(event.active.id as number);
    setActiveCarte(carte || null);
  }

  function handleDragEnd(event: DragEndEvent) {
    setActiveCarte(null);
    const { active, over } = event;
    if (!over || !data) return;

    const leadId = active.id as number;
    const newStatut = over.id as string;
    const carte = findCarte(leadId);
    if (!carte || carte.statut === newStatut) return;

    deplacerOptimistic(leadId, carte.statut, newStatut);
    callDeplacer(leadId, newStatut, null).catch((err: Error) => {
      // Rollback visuel + feedback utilisateur (sinon : la carte revient sans explication
      // et le manager pense que le drag a fonctionne, ou que l'app est cassee).
      deplacerOptimistic(leadId, newStatut, carte.statut);
      setErrorToast(err.message || "Impossible de déplacer le prospect.");
    });
  }

  async function callDeplacer(leadId: number, statut: string, commentaire: string | null) {
    const res = await apiFetch(`${API_BASE}/api/leads/${leadId}/statut`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", "X-Source": "kanban" },
      body: JSON.stringify({ statut, commentaire }),
    });
    if (!res.ok) {
      // Recupere le message d'erreur du backend si possible (peut etre du JSON ou du texte).
      let message = "Le déplacement a échoué.";
      try {
        const ct = res.headers.get("content-type") || "";
        if (ct.includes("application/json")) {
          const data = await res.json();
          if (typeof data?.message === "string") message = data.message;
        } else {
          const text = await res.text();
          if (text) message = text;
        }
      } catch { /* ignore parse */ }
      throw new Error(message);
    }
  }

  function deplacerOptimistic(leadId: number, fromStatut: string, toStatut: string) {
    setData(prev => {
      if (!prev) return prev;
      const colonnes = prev.colonnes.map(col => {
        if (col.statut === fromStatut) {
          return { ...col, leads: col.leads.filter(c => c.id !== leadId), count: col.count - 1 };
        }
        if (col.statut === toStatut) {
          const carte = prev.colonnes.find(c => c.statut === fromStatut)?.leads.find(c => c.id === leadId);
          if (carte) {
            return { ...col, leads: [{ ...carte, statut: toStatut }, ...col.leads], count: col.count + 1 };
          }
        }
        return col;
      });
      return { ...prev, colonnes };
    });
  }

  function findCarte(id: number): KanbanCarteData | undefined {
    if (!data) return undefined;
    for (const col of data.colonnes) {
      const carte = col.leads.find(c => c.id === id);
      if (carte) return carte;
    }
    return undefined;
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <SpeedLines label="Chargement du pipeline" />
      </div>
    );
  }

  if (erreur) {
    return <ErrorState message={erreur} onRetry={fetchKanban} />;
  }

  if (!data) return null;

  return (
    <>
      <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
        {/* h-full + pb-4 : prend toute la hauteur fournie par le wrapper parent
            (flex-1 min-h-0). Chaque colonne gere son propre overflow vertical. */}
        <div className="grid grid-cols-5 gap-3 h-full pb-4">
          {data.colonnes.map(col => (
            <KanbanColonne key={col.statut} colonne={col} vendeurs={vendeurs} onAssignVendeur={onAssignVendeur} />
          ))}
        </div>
        <DragOverlay>
          {activeCarte && <KanbanCarte carte={activeCarte} isOverlay />}
        </DragOverlay>
      </DndContext>

      {errorToast && (
        <div className="fixed bottom-6 right-6 z-50 max-w-sm rounded-lg border border-danger/30 bg-danger/10 px-4 py-3 shadow-lg animate-fade-in">
          <div className="flex items-start gap-3">
            <svg className="h-4 w-4 flex-shrink-0 text-danger mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M4.93 19h14.14a2 2 0 001.74-3l-7.07-12a2 2 0 00-3.48 0l-7.07 12a2 2 0 001.74 3z" />
            </svg>
            <p className="flex-1 text-xs text-danger">{errorToast}</p>
            <button onClick={() => setErrorToast(null)} className="text-danger hover:opacity-70" aria-label="Fermer">
              <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      )}
    </>
  );
}
