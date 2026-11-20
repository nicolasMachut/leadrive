"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { DayPicker } from "react-day-picker";
import { fr } from "date-fns/locale";
import "react-day-picker/style.css";

interface Props {
  /** Valeur au format YYYY-MM-DD. */
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
}

/**
 * Date picker custom avec popover calendrier (react-day-picker).
 * Cohérent cross-browser, look aligné sur le design system Leadrive.
 *
 * <p>L'input affiche la date au format français (ex: "14 mai 2026") mais
 * la valeur interne reste en ISO (YYYY-MM-DD) pour compatibilité avec les
 * APIs backend qui attendent ce format.</p>
 */
export default function DatePicker({ value, onChange, placeholder = "Choisir une date", className = "" }: Props) {
  const [open, setOpen] = useState(false);
  const [popoverPos, setPopoverPos] = useState<{ top: number; left: number; width: number }>({ top: 0, left: 0, width: 0 });
  const containerRef = useRef<HTMLDivElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);

  // Ferme la popover au clic exterieur (input OU popover en portail)
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      const target = e.target as Node;
      if (
        containerRef.current && !containerRef.current.contains(target) &&
        popoverRef.current && !popoverRef.current.contains(target)
      ) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
      return () => document.removeEventListener("mousedown", handleClickOutside);
    }
  }, [open]);

  // Calcule la position de la popover relative a la viewport (Portal = body).
  // useLayoutEffect + mesure de la hauteur reelle pour "flip" vers le haut quand
  // il n'y a pas assez de place en bas (sinon le calendrier deborde sous l'ecran,
  // ex. ReporterModal ouverte en bas de viewport).
  useLayoutEffect(() => {
    if (!open || !containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const marge = 8;
    const hauteurPopover = popoverRef.current?.offsetHeight ?? 340;
    const placeEnBas = window.innerHeight - rect.bottom;
    const placeEnHaut = rect.top;
    // Ouvre vers le haut si pas assez de place en bas ET plus de place en haut.
    const versLeHaut = placeEnBas < hauteurPopover + marge && placeEnHaut > placeEnBas;
    const top = versLeHaut
      ? Math.max(window.scrollY + marge, rect.top + window.scrollY - hauteurPopover - marge)
      : rect.bottom + window.scrollY + marge;
    setPopoverPos({ top, left: rect.left + window.scrollX, width: rect.width });
  }, [open]);

  const selectedDate = value ? new Date(value + "T00:00") : undefined;
  const displayLabel = selectedDate
    ? selectedDate.toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" })
    : placeholder;

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className={`flex w-full items-center justify-between rounded-lg border border-[var(--border)] bg-[var(--background)] px-4 py-2.5 text-left text-sm transition-colors hover:border-accent/40 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent ${
          selectedDate ? "text-[var(--foreground)]" : "text-[var(--color-lr-text-tertiary)]"
        }`}
      >
        <span>{displayLabel}</span>
        <svg className="h-4 w-4 shrink-0 text-[var(--color-lr-text-secondary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      </button>

      {/* Popover en Portal : echappe au containing block du parent (modal, overflow-auto,
          transform, etc.) qui sinon provoque une scrollbar quand la popover deborde. */}
      {open && typeof document !== "undefined" && createPortal(
        <div
          ref={popoverRef}
          style={{ position: "absolute", top: popoverPos.top, left: popoverPos.left }}
          className="z-[200] rounded-xl border border-[var(--border)] bg-[var(--card)] p-2 shadow-lg"
        >
          <DayPicker
            mode="single"
            locale={fr}
            selected={selectedDate}
            onSelect={(date) => {
              if (date) {
                const yyyy = date.getFullYear();
                const mm = String(date.getMonth() + 1).padStart(2, "0");
                const dd = String(date.getDate()).padStart(2, "0");
                onChange(`${yyyy}-${mm}-${dd}`);
                setOpen(false);
              }
            }}
            weekStartsOn={1}
            classNames={{
              today: "text-accent font-semibold",
              selected: "!bg-accent !text-white !rounded-md",
            }}
          />
        </div>,
        document.body,
      )}
    </div>
  );
}
