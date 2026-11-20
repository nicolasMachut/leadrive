"use client";

import { useCallback } from "react";
import { setMeCache, useApiClient } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function updateOnboarding(
  apiFetch: (url: string, init?: RequestInit) => Promise<Response>,
  data: { onboardingTermine?: boolean; onboardingEtape?: number }
): Promise<void> {
  const res = await apiFetch(`${API_BASE}/api/me/onboarding`, {
    method: "PATCH",
    body: JSON.stringify(data),
  });
  if (res.ok) {
    // Le back renvoie le RepMeDto a jour. On met le cache a jour pour eviter
    // qu'AppLayout ne re-redirige vers /onboarding sur un snapshot stale.
    try { setMeCache(await res.json()); } catch { /* silent */ }
  }
}

export function useUpdateOnboarding() {
  const apiFetch = useApiClient();
  return useCallback(
    (data: { onboardingTermine?: boolean; onboardingEtape?: number }) =>
      updateOnboarding(apiFetch, data),
    [apiFetch]
  );
}
