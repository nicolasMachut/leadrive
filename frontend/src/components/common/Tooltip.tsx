"use client";

import { ReactNode, useState, useRef, useEffect } from "react";
import { createPortal } from "react-dom";

/**
 * Tooltip leger sans dependance externe.
 * Affiche `content` au survol/focus de `children`.
 * Utilise un Portal pour eviter les problemes d'overflow/z-index.
 */

interface TooltipProps {
  content: ReactNode;
  children: ReactNode;
  /** Position preferee. La tooltip s'adapte si elle deborde du viewport. */
  position?: "top" | "bottom" | "left" | "right";
  /** Largeur max en px (defaut 240) */
  maxWidth?: number;
  /** Delay avant affichage en ms (defaut 200) */
  delay?: number;
}

export default function Tooltip({
  content,
  children,
  position = "top",
  maxWidth = 240,
  delay = 200,
}: TooltipProps) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(null);
  const triggerRef = useRef<HTMLSpanElement>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [mounted, setMounted] = useState(false);

  useEffect(() => setMounted(true), []);

  function compute() {
    const trigger = triggerRef.current;
    const tip = tooltipRef.current;
    if (!trigger || !tip) return;

    const tr = trigger.getBoundingClientRect();
    const tw = tip.offsetWidth;
    const th = tip.offsetHeight;
    const margin = 8;

    let top = 0;
    let left = 0;

    switch (position) {
      case "top":
        top = tr.top - th - margin;
        left = tr.left + tr.width / 2 - tw / 2;
        break;
      case "bottom":
        top = tr.bottom + margin;
        left = tr.left + tr.width / 2 - tw / 2;
        break;
      case "left":
        top = tr.top + tr.height / 2 - th / 2;
        left = tr.left - tw - margin;
        break;
      case "right":
        top = tr.top + tr.height / 2 - th / 2;
        left = tr.right + margin;
        break;
    }

    // Clamp dans le viewport
    const padding = 4;
    if (left < padding) left = padding;
    if (left + tw > window.innerWidth - padding) left = window.innerWidth - tw - padding;
    if (top < padding) top = tr.bottom + margin; // bascule en bas si pas de place en haut
    if (top + th > window.innerHeight - padding) top = tr.top - th - margin;

    setCoords({ top, left });
  }

  function show() {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      setOpen(true);
      // Laisser le temps au DOM de rendre la tooltip pour lire ses dimensions
      requestAnimationFrame(compute);
    }, delay);
  }

  function hide() {
    if (timerRef.current) clearTimeout(timerRef.current);
    setOpen(false);
    setCoords(null);
  }

  return (
    <>
      <span
        ref={triggerRef}
        onMouseEnter={show}
        onMouseLeave={hide}
        onFocus={show}
        onBlur={hide}
        className="inline-flex"
      >
        {children}
      </span>
      {open && mounted && createPortal(
        <div
          ref={tooltipRef}
          role="tooltip"
          className="fixed z-[200] rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-xs leading-relaxed text-[var(--foreground)] shadow-lg pointer-events-none animate-fade-in"
          style={{
            top: coords?.top ?? -9999,
            left: coords?.left ?? -9999,
            maxWidth,
            visibility: coords ? "visible" : "hidden",
          }}
        >
          {content}
        </div>,
        document.body
      )}
    </>
  );
}
