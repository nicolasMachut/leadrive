"use client";

import { useDroppable } from "@dnd-kit/core";
import Link from "next/link";
import KanbanCarte from "./KanbanCarte";
import type { KanbanColonneData, VendeurOption } from "./KanbanBoard";

interface Props {
  colonne: KanbanColonneData;
  vendeurs?: VendeurOption[];
  onAssignVendeur?: (leadId: number, vendeurId: string) => void;
}

export default function KanbanColonne({ colonne, vendeurs, onAssignVendeur }: Props) {
  const { setNodeRef, isOver } = useDroppable({ id: colonne.statut });

  const isFinal = colonne.statut === "TERMINE";

  return (
    <div
      ref={setNodeRef}
      className={`flex min-w-0 flex-col rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden ${
        isFinal ? "opacity-80" : ""
      } ${isOver ? "ring-2 ring-accent/40" : ""}`}
    >
      {/* Header, 2px top accent + label sentence-case + count en JetBrains Mono.
          Count badge tinted avec la couleur du statut + tabular-nums pour alignement. */}
      <div className="rounded-t-xl px-3 py-3.5" style={{ borderTop: `3px solid ${colonne.couleur}` }}>
        <div className="flex items-center justify-between gap-2">
          <span className="text-[12.5px] font-semibold tracking-[-0.005em] text-[var(--foreground)] truncate">
            {colonne.label}
          </span>
          <span
            className="flex h-[20px] min-w-[28px] items-center justify-center rounded-full px-2 font-mono text-[11px] font-bold tabular-nums shrink-0"
            style={{ backgroundColor: colonne.couleur + "1A", color: colonne.couleur }}
          >
            {colonne.count}
          </span>
        </div>
      </div>

      {/* Cartes, stagger-children = entree cascadee au mount (40ms entre cards) */}
      <div className="flex-1 space-y-2 overflow-y-auto px-2 py-2 stagger-children">
        {colonne.leads.length === 0 && (
          <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-[var(--border)] px-3 py-12 text-center">
            {/* Mini road-stripes "en attente", la voie est libre */}
            <div className="flex items-end gap-[3px] opacity-50" aria-hidden="true">
              <div className="h-4 w-[3px] rounded-sm bg-[var(--color-lr-text-tertiary)] animate-pulse-stripe-1" />
              <div className="h-5 w-[3px] rounded-sm bg-[var(--color-lr-text-tertiary)] animate-pulse-stripe-2" />
              <div className="h-4 w-[3px] rounded-sm bg-[var(--color-lr-text-tertiary)] animate-pulse-stripe-1" />
            </div>
            <div>
              <p className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                Voie libre
              </p>
              <p className="mt-1 text-[11px] text-[var(--color-lr-text-tertiary)]">
                Aucun prospect ici.
              </p>
            </div>
          </div>
        )}
        {colonne.leads.map(carte => (
          <KanbanCarte key={carte.id} carte={carte} vendeurs={vendeurs} onAssignVendeur={onAssignVendeur} />
        ))}
        {colonne.count > colonne.leads.length && (
          // Kanban affiche un nombre limite de cartes par colonne (50). Sans ce lien,
          // les leads non affiches sont invisibles pour le vendeur, il pense avoir tout
          // traite alors qu'il en reste des dizaines.
          <Link
            href={`/leads?vue=liste&statut=${encodeURIComponent(colonne.statut)}`}
            className="block py-2 px-2 text-center text-[10px] font-medium text-accent hover:bg-accent/5 rounded-md transition-colors"
          >
            + {colonne.count - colonne.leads.length} non affichés, voir la liste complète →
          </Link>
        )}
      </div>
    </div>
  );
}
