"use client";

import { RealtimeProvider } from "@/lib/realtime";

/**
 * Wrapper client haut-niveau qui monte le {@link RealtimeProvider} au-dessus
 * de toute l'application. Place dans {@code app/layout.tsx} (Server Component
 * qui ne peut pas utiliser de Provider directement).
 *
 * <p>Le RealtimeProvider expose aussi useToast() (cf. {@code @/lib/realtime}),
 * réutilisé partout dans l'app pour signaler les erreurs API à l'utilisateur
 * (remplace les silent catches). Le ToastContainer est rendu par AppLayout.</p>
 *
 * <p>Pourquoi pas dans AppLayout : les pages comme /email-import, /stats,
 * /leads montent {@code <AppLayout>} DANS leur composant, du coup
 * {@code useRealtime()} appele dans le body de la page (avant AppLayout) tombe
 * en dehors du Provider et recoit le fallback no-op. En montant le Provider a
 * la racine, n'importe quel composant client de l'app a acces au context.</p>
 */
export default function AppProviders({ children }: { children: React.ReactNode }) {
  return <RealtimeProvider>{children}</RealtimeProvider>;
}
