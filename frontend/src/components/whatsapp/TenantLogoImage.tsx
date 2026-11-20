"use client";

import { ReactNode, useEffect, useState } from "react";
import { useApiClient } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// Cache module-level partage entre tous les composants TenantLogoImage de la session.
// Empeche le "flash" de la sidebar a chaque navigation (la sidebar se remonte a chaque
// route Next.js sur ce projet et le useState repartait a null + fetch a chaque fois).
// Le blob URL reste valide tant que la page n'est pas dechargee.
let cachedLogoUrl: string | null = null;
let cachedFetchStatus: "idle" | "loading" | "done" | "absent" = "idle";

// Pub/sub minimaliste pour notifier les TenantLogoImage actifs quand le cache
// est invalide (par exemple apres un upload). Sans ca, le state local de chaque
// instance garde l'ancien blob URL et il faut un reload navigateur pour voir le
// nouveau logo. Avec ca, l'appel a invaliderCacheLogoTenant() declenche un
// refetch dans toutes les instances montees.
const listeners = new Set<() => void>();

/**
 * Image du logo de la concession courante, fetchee une seule fois par session
 * et mise en cache module-level. Si pas de logo uploade : retourne {@code fallback}
 * (ex: avatar a base d'initiales) ou null si fallback absent.
 */
export default function TenantLogoImage({
  alt,
  className,
  fallback,
}: {
  alt?: string;
  className?: string;
  fallback?: ReactNode;
}) {
  const apiFetch = useApiClient();
  const [logoUrl, setLogoUrl] = useState<string | null>(cachedLogoUrl);
  const [resolved, setResolved] = useState(cachedFetchStatus === "done" || cachedFetchStatus === "absent");

  useEffect(() => {
    function chargerSiNecessaire() {
      if (cachedFetchStatus === "done") {
        setLogoUrl(cachedLogoUrl);
        setResolved(true);
        return;
      }
      if (cachedFetchStatus === "absent") {
        setLogoUrl(null);
        setResolved(true);
        return;
      }
      if (cachedFetchStatus === "loading") return;
      cachedFetchStatus = "loading";
      apiFetch(`${API_BASE}/api/parametres/whatsapp/profil/logo`)
        // 200 = logo present (corps PNG). 204 = pas de logo (etat normal, corps
        // vide) : on retombe sur le fallback sans erreur. Tout autre statut -> null.
        .then((res) => (res.status === 200 ? res.blob() : null))
        .then((blob) => {
          if (!blob) {
            cachedFetchStatus = "absent";
            setLogoUrl(null);
            setResolved(true);
            return;
          }
          cachedLogoUrl = URL.createObjectURL(blob);
          cachedFetchStatus = "done";
          setLogoUrl(cachedLogoUrl);
          setResolved(true);
          // Notifier les autres instances montees (qui ont leur propre state).
          listeners.forEach((l) => {
            if (l !== chargerSiNecessaire) l();
          });
        })
        .catch(() => {
          cachedFetchStatus = "absent";
          setLogoUrl(null);
          setResolved(true);
        });
    }
    chargerSiNecessaire();
    listeners.add(chargerSiNecessaire);
    return () => {
      listeners.delete(chargerSiNecessaire);
    };
  }, [apiFetch]);

  // blob URL : next/image ne sait pas optimiser un blob du navigateur, on garde img natif.
  // eslint-disable-next-line @next/next/no-img-element
  if (logoUrl) return <img src={logoUrl} alt={alt ?? "Logo établissement"} className={className} />;
  // Pendant le loading initial (avant que le fetch ait abouti), on n'affiche PAS
  // le fallback : sinon le manager voit un autre logo flasher 200-500ms avant
  // que SON logo apparaisse. On retourne un placeholder neutre qui reserve
  // l'espace (heritage du className type rounded-full / h-10) sans contenu visible.
  if (!resolved) return <span className={className} aria-hidden="true" />;
  return <>{fallback}</>;
}

/**
 * Permet de forcer le rechargement du logo apres upload (vide le cache + notifie
 * toutes les instances montees pour qu'elles refetchent).
 * A appeler depuis l'ecran d'upload pour qu'apres "Enregistrer", la sidebar et
 * les apercus voient le nouveau logo sans hard reload.
 */
export function invaliderCacheLogoTenant(): void {
  if (cachedLogoUrl) URL.revokeObjectURL(cachedLogoUrl);
  cachedLogoUrl = null;
  cachedFetchStatus = "idle";
  listeners.forEach((l) => l());
}

