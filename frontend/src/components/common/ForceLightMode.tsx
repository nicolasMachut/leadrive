"use client";

import { useEffect } from "react";

/**
 * ForceLightMode, verrouille la page en theme clair, quelles que soient les
 * preferences localStorage du user. À monter en haut des pages publiques
 * (landing, login, mentions, politique) qui doivent rester en light mode
 * pour une presence brand coherente.
 *
 * Retire la classe `dark` du <html> au mount, la restaure au unmount.
 */
export default function ForceLightMode() {
  useEffect(() => {
    const root = document.documentElement;
    const wasDark = root.classList.contains("dark");
    if (wasDark) {
      root.classList.remove("dark");
    }
    return () => {
      // Restaure le mode user au unmount, base sur localStorage (source de verite).
      try {
        const saved = localStorage.getItem("leadrive_dark");
        if (saved === "true") {
          root.classList.add("dark");
        }
      } catch {
        /* localStorage indispo (SSR / private mode), on ignore */
      }
    };
  }, []);

  return null;
}
