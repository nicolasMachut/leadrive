"use client";

import { useEffect, useRef, useState } from "react";
import Button from "@/components/common/Button";

interface Props {
  /** Adresse email a afficher (ex: garage-x7k9@leads.leadrive.fr). */
  adresse: string;
  /** Variant du bouton copier (defaut: primary pour mise en avant). */
  variant?: "primary" | "secondary";
}

/**
 * Bloc d'affichage d'une adresse email d'import avec bouton "Copier".
 * Reutilise dans /email-import (page dediee manager) et dans l'onboarding
 * (hero d'import auto), un seul comportement clipboard, un seul rendu.
 */
export default function EmailAddressCopyBlock({ adresse, variant = "primary" }: Props) {
  const [copyState, setCopyState] = useState<"idle" | "copied" | "error">("idle");
  const codeRef = useRef<HTMLElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  async function copier() {
    try {
      await navigator.clipboard.writeText(adresse);
      setCopyState("copied");
    } catch {
      // navigator.clipboard indisponible (http sans https, navigateur restrictif).
      // On selectionne l'adresse pour que l'utilisateur n'ait plus qu'a faire Ctrl/Cmd+C,
      // et on l'en informe explicitement (pas d'echec silencieux).
      const node = codeRef.current;
      if (node) {
        const range = document.createRange();
        range.selectNodeContents(node);
        const sel = window.getSelection();
        sel?.removeAllRanges();
        sel?.addRange(range);
      }
      setCopyState("error");
    }
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setCopyState("idle"), 2500);
  }

  return (
    <div>
      <div className="flex items-center gap-2 rounded-lg border border-[var(--border)] bg-[var(--muted)] px-3 py-2.5">
        <code ref={codeRef} className="flex-1 font-mono text-sm text-[var(--foreground)] truncate">
          {adresse}
        </code>
        <Button onClick={copier} variant={variant} size="sm">
          {copyState === "copied" ? "Copié !" : copyState === "error" ? "Réessayer" : "Copier"}
        </Button>
      </div>
      {copyState === "error" && (
        <p role="status" className="mt-1.5 text-xs text-warning">
          Copie automatique indisponible. L&apos;adresse est sélectionnée : faites Ctrl/Cmd+C pour la copier.
        </p>
      )}
    </div>
  );
}
