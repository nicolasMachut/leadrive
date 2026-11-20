"use client";

import { useEffect, useState, useRef } from "react";

/**
 * useCountUp, anime un nombre de 0 vers une valeur cible en X ms.
 *
 * Usage :
 *   const display = useCountUp(rdvSemaine, 900);
 *   <span>{display}</span>
 *
 * Respecte prefers-reduced-motion : retourne directement la valeur cible
 * sans animation si l'utilisateur a coupe les animations.
 *
 * Easing : ease-out-quart (rapide au depart, ralentit a la fin) pour effet
 * "instrument qui s'allume puis se cale" coherent avec la signature Atelier.
 */
export function useCountUp(target: number, durationMs = 900): number {
  const [value, setValue] = useState(0);
  const rafRef = useRef<number | null>(null);
  const startRef = useRef<number | null>(null);
  const fromRef = useRef<number>(0);

  useEffect(() => {
    // Respect du prefers-reduced-motion : pas d'animation
    if (typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setValue(target);
      return;
    }

    // Pas de target ou target = 0 : juste set la valeur
    if (target === 0) {
      setValue(0);
      return;
    }

    fromRef.current = value;
    startRef.current = null;

    const tick = (timestamp: number) => {
      if (startRef.current === null) startRef.current = timestamp;
      const elapsed = timestamp - startRef.current;
      const progress = Math.min(elapsed / durationMs, 1);
      // ease-out-quart : 1 - (1 - x)^4
      const eased = 1 - Math.pow(1 - progress, 4);
      const current = fromRef.current + (target - fromRef.current) * eased;
      setValue(Math.round(current));

      if (progress < 1) {
        rafRef.current = requestAnimationFrame(tick);
      }
    };

    rafRef.current = requestAnimationFrame(tick);

    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target, durationMs]);

  return value;
}
