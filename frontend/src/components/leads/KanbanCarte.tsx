"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useDraggable } from "@dnd-kit/core";
import { useRouter } from "next/navigation";
import Tooltip from "@/components/common/Tooltip";
import Tachograph from "@/components/common/Tachograph";
import type { KanbanCarteData, VendeurOption } from "./KanbanBoard";

interface Props {
  carte: KanbanCarteData;
  isOverlay?: boolean;
  vendeurs?: VendeurOption[];
  onAssignVendeur?: (leadId: number, vendeurId: string) => void;
}

const prioriteDot: Record<string, string> = {
  HAUTE: "bg-danger animate-pulse",
  MOYENNE: "bg-warning",
  BASSE: "bg-[var(--color-lr-text-tertiary)]",
};

const tagStyle: Record<string, string> = {
  Chaud: "bg-danger/10 text-danger",
};

export default function KanbanCarte({ carte, isOverlay, vendeurs, onAssignVendeur }: Props) {
  const router = useRouter();
  const [showPicker, setShowPicker] = useState(false);
  // Position calculee du menu (portal fixed) : evite d'etre clippe par le
  // overflow-y-auto de la colonne / overflow-hidden du board, et le stacking
  // des cartes suivantes. Le menu est rendu dans document.body.
  const [menuPos, setMenuPos] = useState<{ top: number; left: number } | null>(null);
  const boutonRef = useRef<HTMLButtonElement>(null);
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: carte.id,
  });

  // Ferme le picker sur scroll / resize : le menu est en position fixed et ne
  // suivrait pas la carte si la colonne defile.
  useEffect(() => {
    if (!showPicker) return;
    const fermer = () => setShowPicker(false);
    window.addEventListener("scroll", fermer, true);
    window.addEventListener("resize", fermer);
    return () => {
      window.removeEventListener("scroll", fermer, true);
      window.removeEventListener("resize", fermer);
    };
  }, [showPicker]);

  // Ouvre le picker en calculant sa position a partir du bouton (aligne a droite,
  // s'ouvre vers le haut si pas la place en bas).
  function togglePicker() {
    if (showPicker) {
      setShowPicker(false);
      return;
    }
    const rect = boutonRef.current?.getBoundingClientRect();
    if (!rect) return;
    const LARGEUR = 176; // w-44
    const hauteurEstimee = Math.min(288, (vendeurs!.length + 1) * 32 + 8);
    const ouvrirVersHaut = rect.bottom + hauteurEstimee + 8 > window.innerHeight;
    const top = ouvrirVersHaut ? Math.max(8, rect.top - hauteurEstimee - 4) : rect.bottom + 4;
    const left = Math.min(Math.max(8, rect.right - LARGEUR), window.innerWidth - LARGEUR - 8);
    setMenuPos({ top, left });
    setShowPicker(true);
  }

  const style = transform
    ? { transform: `translate(${transform.x}px, ${transform.y}px)` }
    : undefined;

  const hasScore = carte.scoreIA != null && carte.scoreIA > 0;
  const hasMeta = carte.kilometrage != null || carte.energie;

  return (
    <div
      ref={!isOverlay ? setNodeRef : undefined}
      {...(!isOverlay ? { ...listeners, ...attributes } : {})}
      style={style}
      onClick={() => { if (!isDragging) router.push(`/leads/${carte.id}`); }}
      className={`group card-hover-lift cursor-grab rounded-xl border border-[var(--border)] bg-[var(--card)] p-3 hover:border-[var(--color-accent)]/30 hover:shadow-card active:cursor-grabbing ${
        isDragging ? "opacity-50" : ""
      } ${isOverlay ? "rotate-1 shadow-xl ring-2 ring-[var(--color-accent)]/30" : ""}`}
    >
      {/* HEAD : Nom + priorite dot + Tachograph score signature */}
      <div className="flex items-start gap-2.5">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5">
            <Tooltip content={`Priorité ${carte.priorite === "HAUTE" ? "haute, à traiter en premier" : carte.priorite === "MOYENNE" ? "moyenne" : "basse"}`}>
              <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${prioriteDot[carte.priorite] || "bg-[var(--color-lr-text-tertiary)]"}`} />
            </Tooltip>
            <span className="truncate text-[13.5px] font-semibold text-[var(--foreground)]">
              {carte.nom}
            </span>
          </div>
          {carte.vehiculeInteret && (
            <p className="mt-0.5 truncate text-[11.5px] text-[var(--color-lr-text-secondary)]">
              {carte.vehiculeInteret}
            </p>
          )}
        </div>
        {hasScore && (
          <Tooltip
            content={`Score IA ${carte.scoreIA}/100, ${
              carte.scoreIA! >= 70 ? "chaud, à traiter en priorité" : carte.scoreIA! >= 40 ? "tiède, à relancer" : "froid, à qualifier"
            }`}
          >
            <span>
              <Tachograph value={carte.scoreIA!} max={100} size="sm" variant="auto" />
            </span>
          </Tooltip>
        )}
      </div>

      {/* META : telephone + chips vehicule en row dense */}
      {(carte.telephone || hasMeta) && (
        <div className="mt-2.5 flex flex-wrap items-center gap-x-3 gap-y-1">
          {carte.telephone && (
            <span className="font-mono text-[10.5px] text-[var(--color-lr-text-tertiary)]">
              {carte.telephone}
            </span>
          )}
          {carte.kilometrage != null && (
            <span className="font-mono text-[10px] uppercase tracking-wider text-[var(--color-lr-text-tertiary)]">
              {carte.kilometrage.toLocaleString("fr-FR")} km
            </span>
          )}
          {carte.energie && (
            <span className={`font-mono text-[10px] uppercase tracking-wider ${
              carte.energie.includes("lectrique") ? "text-[var(--color-accent)]"
              : carte.energie.includes("ybride") ? "text-success"
              : "text-[var(--color-lr-text-tertiary)]"
            }`}>
              {carte.energie}
            </span>
          )}
        </div>
      )}

      {/* TAGS, uniquement si tags presents */}
      {carte.tags.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {carte.tags.map(tag => (
            <span key={tag} className={`rounded-full px-2 py-0.5 font-mono text-[9px] font-semibold uppercase tracking-wider ${
              tagStyle[tag] || "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]"
            }`}>
              {tag}
            </span>
          ))}
        </div>
      )}

      {/* FOOTER : separator hairline + temps + vendeur avatar.
          divide-y inside the card replaces extra row wrapping. */}
      <div className="mt-3 flex items-center justify-between border-t border-[var(--border)] pt-2">
        {carte.tempsDepuisDernierContact ? (
          <Tooltip content="Temps écoulé depuis le dernier contact ou changement de statut">
            <p className="font-mono text-[10px] uppercase tracking-wider text-[var(--color-lr-text-tertiary)]">
              {carte.tempsDepuisDernierContact}
            </p>
          </Tooltip>
        ) : <span />}
        {vendeurs && vendeurs.length > 0 && onAssignVendeur && (
          <div className="relative">
            <Tooltip content={carte.vendeurNom ? `Assigné à ${carte.vendeurNom}, cliquez pour changer` : "Cliquez pour assigner un vendeur"}>
              <button
                ref={boutonRef}
                onPointerDown={(e) => e.stopPropagation()}
                onClick={(e) => { e.stopPropagation(); togglePicker(); }}
                className={`flex h-6 min-w-[24px] items-center justify-center rounded-full text-[10px] font-bold transition-colors ${
                  carte.vendeurNom
                    ? "bg-[var(--color-accent)]/10 text-[var(--color-accent)] px-1.5"
                    : "bg-[var(--muted)] text-[var(--color-lr-text-tertiary)] hover:bg-[var(--color-accent)]/10 hover:text-[var(--color-accent)]"
                }`}
                aria-label={carte.vendeurNom ? `Assigné à ${carte.vendeurNom}` : "Assigner un vendeur"}
              >
                {carte.vendeurNom
                  ? carte.vendeurNom.split(" ").map(w => w[0]).join("").slice(0, 2).toUpperCase()
                  : "+"}
              </button>
            </Tooltip>
            {showPicker && menuPos && createPortal(
              <>
                {/* Backdrop transparent : clic hors menu = fermeture. */}
                <div
                  className="fixed inset-0 z-[100]"
                  onPointerDown={(e) => { e.stopPropagation(); setShowPicker(false); }}
                  onClick={(e) => e.stopPropagation()}
                />
                <div
                  style={{ position: "fixed", top: menuPos.top, left: menuPos.left, width: 176 }}
                  className="z-[101] max-h-72 overflow-y-auto rounded-lg border border-[var(--border)] bg-[var(--card)] py-1 shadow-card"
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={(e) => e.stopPropagation()}
                >
                  <button
                    onClick={() => { onAssignVendeur(carte.id, ""); setShowPicker(false); }}
                    className="w-full px-3 py-1.5 text-left text-xs text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)]"
                  >
                    Non assigné
                  </button>
                  {vendeurs.map((v) => (
                    <button
                      key={v.id}
                      onClick={() => { onAssignVendeur(carte.id, String(v.id)); setShowPicker(false); }}
                      className="w-full px-3 py-1.5 text-left text-xs text-[var(--foreground)] transition-colors hover:bg-[var(--muted)]"
                    >
                      {v.prenom} {v.nom}
                    </button>
                  ))}
                </div>
              </>,
              document.body
            )}
          </div>
        )}
      </div>
    </div>
  );
}
