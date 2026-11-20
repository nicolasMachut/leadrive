"use client";

import { ReactNode, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

const FOCUSABLE = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: ReactNode;
  size?: "sm" | "md" | "lg";
  children: ReactNode;
  footer?: ReactNode;
}

const SIZES = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-2xl",
};

export default function Modal({ open, onClose, title, description, size = "md", children, footer }: ModalProps) {
  const [mounted, setMounted] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);

  // Ref stable vers onClose : les appelants passent un onClose inline (nouvelle identite a
  // chaque render). Sans cette ref, l'effet ci-dessous aurait `onClose` en dependance et se
  // relancerait a CHAQUE frappe dans un champ (re-render parent) -> son setTimeout de focus
  // initial volait le focus du champ ("des que j'ecris une lettre, ca sort du focus du form").
  // On garde donc l'effet dependant du SEUL `open`, et on lit toujours le dernier onClose ici.
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  // Mount flag pour eviter les erreurs d'hydratation avec createPortal
  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!open) return;
    // Mémorise l'élément déclencheur pour restaurer le focus à la fermeture (a11y).
    const previouslyFocused = document.activeElement as HTMLElement | null;

    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") { onCloseRef.current(); return; }
      // Focus trap : Tab/Shift+Tab cyclent à l'intérieur de la modale (sinon le focus
      // s'échappe vers la page derrière, inutilisable au clavier / lecteur d'écran).
      if (e.key === "Tab" && dialogRef.current) {
        const focusables = dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLE);
        if (focusables.length === 0) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    window.addEventListener("keydown", onKey);
    // Empecher le scroll de la page quand la modal est ouverte
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    // Focus initial sur le conteneur du dialog (tabIndex=-1) : le lecteur d'écran
    // annonce la modale, et on évite de déclencher une action par accident.
    const t = setTimeout(() => dialogRef.current?.focus(), 0);
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = originalOverflow;
      clearTimeout(t);
      previouslyFocused?.focus?.();
    };
    // Depend UNIQUEMENT de `open` : l'effet (focus initial + trap + lock scroll) ne doit
    // s'executer qu'a l'ouverture/fermeture, jamais a chaque re-render parent (frappe clavier).
  }, [open]);

  if (!open || !mounted) return null;

  // Portal dans <body> : sort de tout containing block (transform parent, overflow, etc.)
  // pour garantir que la modal couvre tout le viewport.
  return createPortal(
    <>
      <div className="fixed inset-0 z-[100] bg-black/40 animate-fade-in" onClick={onClose} />
      <div className="fixed inset-0 z-[101] flex items-center justify-center p-4 pointer-events-none overflow-y-auto">
        <div
          ref={dialogRef}
          tabIndex={-1}
          className={`pointer-events-auto w-full ${SIZES[size]} my-auto overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--card)] shadow-card animate-slide-up focus:outline-none`}
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-title"
        >
          {/* Header editorial, display title + close icon */}
          <div className="flex items-start justify-between gap-4 border-b border-[var(--border)] px-6 py-4">
            <div className="min-w-0">
              <h2 id="modal-title" className="font-display text-[18px] font-bold tracking-display-tight text-[var(--foreground)]">
                {title}
              </h2>
              {description && (
                <div className="mt-1 text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                  {description}
                </div>
              )}
            </div>
            <button
              type="button"
              onClick={onClose}
              className="shrink-0 rounded-md p-1 text-[var(--color-lr-text-tertiary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
              aria-label="Fermer"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          {/* Body */}
          <div className="px-6 py-5">{children}</div>
          {/* Footer (optional) */}
          {footer && (
            <div className="flex justify-end gap-3 border-t border-[var(--border)] bg-[var(--background)] px-6 py-4">
              {footer}
            </div>
          )}
        </div>
      </div>
    </>,
    document.body
  );
}
