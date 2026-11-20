"use client";

import { useEffect, useState } from "react";

/**
 * Lien vers la racine du site qui s'adapte automatiquement à l'environnement.
 * Affiche le hostname (ex: localhost:3000, dev.leadrive.fr, app.leadrive.fr)
 * et pointe vers l'origin courant.
 */
export function SiteLink({ className }: { className?: string }) {
  const [origin, setOrigin] = useState<string | null>(null);

  useEffect(() => {
    setOrigin(window.location.origin);
  }, []);

  return (
    <a href={origin ?? "https://app.leadrive.fr"} className={className}>
      app.leadrive.fr
    </a>
  );
}
