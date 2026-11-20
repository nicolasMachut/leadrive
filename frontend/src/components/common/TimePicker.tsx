"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

interface Props {
  /** Valeur au format HH:mm (24h). */
  value: string;
  onChange: (value: string) => void;
  /** Pas en minutes entre 2 creneaux (defaut 15). */
  stepMinutes?: number;
  /** Heure de debut (defaut 8). */
  heureDebut?: number;
  /** Heure de fin exclusive (defaut 20). */
  heureFin?: number;
  className?: string;
}

/**
 * Time picker custom avec popover liste de creneaux (08:00 → 19:45 par
 * defaut, pas de 15 min). Aligne avec DatePicker pour un look coherent.
 *
 * <p>L'utilisateur peut aussi taper directement dans le champ (format HH:mm)
 * pour les cas hors creneaux (ex: 06:30).</p>
 */
export default function TimePicker({
  value,
  onChange,
  stepMinutes = 15,
  heureDebut = 8,
  heureFin = 20,
  className = "",
}: Props) {
  const [open, setOpen] = useState(false);
  const [popoverPos, setPopoverPos] = useState<{ top: number; left: number; width: number }>({ top: 0, left: 0, width: 0 });
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      const target = e.target as Node;
      if (
        containerRef.current && !containerRef.current.contains(target) &&
        listRef.current && !listRef.current.contains(target)
      ) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
      return () => document.removeEventListener("mousedown", handleClickOutside);
    }
  }, [open]);

  // Calcule la position du popover (Portal = body)
  useEffect(() => {
    if (!open || !containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    setPopoverPos({
      top: rect.bottom + window.scrollY + 8,
      left: rect.left + window.scrollX,
      width: rect.width,
    });
  }, [open]);

  // Scroll auto vers le creneau actif a l'ouverture
  useEffect(() => {
    if (open && listRef.current && value) {
      const activeButton = listRef.current.querySelector(`[data-time="${value}"]`);
      if (activeButton instanceof HTMLElement) {
        activeButton.scrollIntoView({ block: "center" });
      }
    }
  }, [open, value]);

  const creneaux: string[] = [];
  for (let h = heureDebut; h < heureFin; h++) {
    for (let m = 0; m < 60; m += stepMinutes) {
      creneaux.push(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);
    }
  }

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className={`flex w-full items-center justify-between rounded-lg border border-[var(--border)] bg-[var(--background)] px-4 py-2.5 text-left text-sm transition-colors hover:border-accent/40 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent ${
          value ? "text-[var(--foreground)]" : "text-[var(--color-lr-text-tertiary)]"
        }`}
      >
        <span>{value || "Heure"}</span>
        <svg className="h-4 w-4 shrink-0 text-[var(--color-lr-text-secondary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </button>

      {open && typeof document !== "undefined" && createPortal(
        <div
          ref={listRef}
          style={{ position: "absolute", top: popoverPos.top, left: popoverPos.left, width: popoverPos.width }}
          className="z-[200] max-h-60 overflow-y-auto rounded-xl border border-[var(--border)] bg-[var(--card)] py-1 shadow-lg"
        >
          {creneaux.map((c) => (
            <button
              key={c}
              type="button"
              data-time={c}
              onClick={() => {
                onChange(c);
                setOpen(false);
              }}
              className={`block w-full px-4 py-1.5 text-left text-sm transition-colors hover:bg-[var(--muted)] ${
                c === value ? "bg-accent/10 text-accent font-medium" : "text-[var(--foreground)]"
              }`}
            >
              {c}
            </button>
          ))}
        </div>,
        document.body,
      )}
    </div>
  );
}
