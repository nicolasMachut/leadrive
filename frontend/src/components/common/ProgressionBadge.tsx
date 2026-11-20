/**
 * Badge visuel de progression du lead dans le funnel commercial.
 * Affiche le statut + une mini barre de progression colorée pour montrer l'avancement.
 *
 * Pipeline : NOUVEAU → CONTACTE → EN_COURS → RDV_PRIS → TERMINE
 *
 * Anime un mini-pop (scale 1 → 1.08 → 1) sur changement de statut pour
 * que l'oeil capte instantanement la transition.
 */

"use client";
import { useEffect, useRef, useState } from "react";
import Tooltip from "./Tooltip";

interface ProgressionBadgeProps {
  statut: string;
}

interface StatutMeta {
  label: string;
  step: number; // 1-4 pour la progression active
  color: string;
  bg: string;
  border: string;
  isFinal: boolean; // true si etat terminal (TERMINE)
  isLost: boolean;
  description: string;
}

const STATUTS: Record<string, StatutMeta> = {
  NOUVEAU: {
    label: "Nouveau",
    step: 1,
    color: "text-accent",
    bg: "bg-accent/10",
    border: "bg-accent",
    isFinal: false,
    isLost: false,
    description: "Lead venant d'être importé, jamais contacté.",
  },
  CONTACTE: {
    label: "Contacté",
    step: 2,
    color: "text-warning",
    bg: "bg-warning/10",
    border: "bg-warning",
    isFinal: false,
    isLost: false,
    description: "Premier message envoyé au prospect (WhatsApp), en attente de réponse.",
  },
  EN_COURS: {
    label: "Conversation en cours",
    step: 3,
    color: "text-success",
    bg: "bg-success/10",
    border: "bg-success",
    isFinal: false,
    isLost: false,
    description: "Conversation engagée. Le prospect a répondu mais n'a pas encore confirmé de rendez-vous.",
  },
  RDV_PRIS: {
    label: "RDV pris",
    step: 4,
    color: "text-accent",
    bg: "bg-accent/10",
    border: "bg-accent",
    isFinal: false,
    isLost: false,
    description: "Le prospect a proposé ou accepté un créneau de rendez-vous.",
  },
  TERMINE: {
    label: "Terminé",
    step: 4,
    color: "text-[var(--color-lr-text-secondary)]",
    bg: "bg-[var(--muted)]",
    border: "bg-[var(--color-lr-text-tertiary)]",
    isFinal: true,
    isLost: false,
    description: "Le traitement de ce lead est terminé.",
  },
};

const TOTAL_STEPS = 4;

export default function ProgressionBadge({ statut }: ProgressionBadgeProps) {
  const meta = STATUTS[statut?.toUpperCase()] ?? null;

  // Pop animation sur changement de statut (skip 1er render)
  const [pop, setPop] = useState(false);
  const isFirstRender = useRef(true);
  useEffect(() => {
    if (isFirstRender.current) { isFirstRender.current = false; return; }
    setPop(true);
    const t = setTimeout(() => setPop(false), 450);
    return () => clearTimeout(t);
  }, [statut]);

  if (!meta) {
    return (
      <span className="inline-flex items-center rounded-full bg-[var(--muted)] px-2.5 py-1 text-xs font-medium text-[var(--color-lr-text-secondary)]">
        {statut || "-"}
      </span>
    );
  }

  const tooltipContent = (
    <div className="space-y-1.5">
      <p className="font-semibold">
        {meta.label}
        {!meta.isFinal && ` (étape ${meta.step}/${TOTAL_STEPS})`}
      </p>
      <p className="text-[var(--color-lr-text-secondary)]">{meta.description}</p>
      <div className="pt-1 border-t border-[var(--border)]">
        <p className="text-[10px] text-[var(--color-lr-text-tertiary)]">
          Pipeline : Nouveau → Contacte → En cours → RDV pris → Termine
        </p>
        <p className="text-[10px] italic text-[var(--color-lr-text-tertiary)] mt-0.5">
          Mis à jour automatiquement par l&apos;IA selon les réponses du prospect.
        </p>
      </div>
    </div>
  );

  return (
    <Tooltip content={tooltipContent}>
      <div
        className="inline-flex flex-col gap-1 cursor-help"
        style={{
          transform: pop ? "scale(1.08)" : "scale(1)",
          transition: "transform 450ms cubic-bezier(0.34, 1.56, 0.64, 1)",
        }}
      >
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${meta.bg} ${meta.color}`}>
          {meta.isLost && (
            <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M6 18L18 6M6 6l12 12" />
            </svg>
          )}
          {meta.isFinal && !meta.isLost && (
            <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
            </svg>
          )}
          {meta.label}
        </span>
        {/* Mini progression : 4 segments colores selon l'avancement */}
        <div className="flex gap-0.5 px-1">
          {Array.from({ length: TOTAL_STEPS }).map((_, i) => {
            const isActive = i < meta.step;
            return (
              <div
                key={i}
                className={`h-0.5 flex-1 rounded-full transition-colors ${
                  isActive ? meta.border : "bg-[var(--border)]"
                }`}
              />
            );
          })}
        </div>
      </div>
    </Tooltip>
  );
}
