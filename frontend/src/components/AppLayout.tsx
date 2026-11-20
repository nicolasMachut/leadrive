"use client";

import { useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useAuth } from "@clerk/nextjs";
import { useCurrentRole, useMe } from "@/lib/auth";
import Sidebar from "./Sidebar";
import HelpBubble from "./help/HelpBubble";
import ToastContainer from "./realtime/ToastContainer";
import WhatsAppSelfSignupBanner from "./whatsapp/WhatsAppSelfSignupBanner";
import LeadriveLogo from "./common/LeadriveLogo";
import GlobalAlertesRappelBanner from "./inbox/GlobalAlertesRappelBanner";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { isLoaded, isSignedIn } = useAuth();
  const { role } = useCurrentRole();
  const me = useMe();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    if (isLoaded && !isSignedIn) {
      router.replace("/login");
    }
  }, [isLoaded, isSignedIn, router]);

  // Rediriger les managers non-onboardes vers le wizard (uniquement depuis le dashboard).
  // Filet de securite : le login fait deja le redirect direct, mais ce check couvre
  // les cas d'acces direct (refresh sur /dashboard, click "Tableau de bord" dans la
  // sidebar avant onboarding fini, etc.).
  const doitFaireOnboarding = me && role === "MANAGER" && !me.onboardingTermine && pathname === "/dashboard";
  useEffect(() => {
    if (doitFaireOnboarding) {
      router.replace("/onboarding");
    }
  }, [doitFaireOnboarding, router]);

  if (!isLoaded || !isSignedIn) return null;

  // Bloque le rendu du dashboard pendant que le redirect onboarding s'effectue,
  // sinon flash visuel du dashboard avant la redirection.
  if (doitFaireOnboarding) return null;

  return (
    <div className="flex h-screen overflow-hidden bg-[var(--background)]">
      <Sidebar mobileOpen={mobileMenuOpen} onClose={() => setMobileMenuOpen(false)} />
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Mobile top bar */}
        <div className="flex h-14 items-center gap-3 border-b border-[var(--border)] bg-[var(--card)] px-4 lg:hidden">
          <button
            onClick={() => setMobileMenuOpen(true)}
            className="rounded-lg p-1.5 text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)]"
          >
            <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <LeadriveLogo height={24} priority className="w-auto" />
        </div>

        {/* Bandeau d'alertes "rappel immediat" GLOBAL : visible sur toutes les
            pages app (pas seulement /inbox). Souscrit a la conversation-stream
            une fois pour toute la session authentifiee. */}
        <GlobalAlertesRappelBanner />
        <main key={pathname} className="flex-1 overflow-y-auto animate-page-enter">
          {/* Bannière cross-page WhatsApp Self Sign-up (epic 25). Cachee sur
              la page d'activation pour eviter le doublon. */}
          <WhatsAppSelfSignupBanner />
          {children}
        </main>
      </div>
      <HelpBubble />
      <ToastContainer />
    </div>
  );
}
