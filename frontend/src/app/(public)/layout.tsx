"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import Link from "next/link";
import ForceLightMode from "@/components/common/ForceLightMode";
import LeadriveLogo from "@/components/common/LeadriveLogo";

export default function PublicLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isLanding = pathname === "/";
  const [scrolled, setScrolled] = useState(!isLanding);

  useEffect(() => {
    if (!isLanding) { setScrolled(true); return; }
    // Reset immédiat au retour sur la landing (navigation client-side) :
    // sans ça, scrolled reste true (fixé par la page précédente) et le
    // header garde le fond crème au lieu d'être transparent sur le hero.
    setScrolled(window.scrollY > 20);
    const onScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, [isLanding]);

  return (
    <div className="min-h-screen">
      {/* Forcer le theme clair sur la landing publique :
          la marque doit toujours apparaitre en sand/ember, pas en dark. */}
      <ForceLightMode />
      {/* Reading progress : barre fine 2px sous la signature gradient bar du
          layout root. Scale-X 0 → 1 lié au scroll du document via CSS scroll-timeline
          natif. Fallback (Firefox) : barre invisible. Honore prefers-reduced-motion. */}
      <div
        aria-hidden="true"
        className="reading-progress fixed top-[3px] left-0 right-0 z-[199] h-[2px] bg-[var(--color-accent)]/85"
        style={{ transformOrigin: "0 50%" }}
      />
      {/* Header */}
      <header
        className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
          scrolled ? "bg-[var(--background)]/92 backdrop-blur-md border-b border-[var(--border)]/60" : "bg-transparent"
        }`}
      >
        <nav className="mx-auto flex max-w-6xl items-center px-6 py-4">
          <div className="flex flex-1 items-center">
            <Link href="/" className="transition hover:opacity-80">
              <div className={`${scrolled ? "block" : "hidden"}`}>
                <LeadriveLogo variant="light" height={36} priority className="w-auto" />
              </div>
              <div className={`${scrolled ? "hidden" : "block"}`}>
                <LeadriveLogo variant="dark" height={36} priority className="w-auto" />
              </div>
            </Link>
          </div>

          <div className="hidden items-center gap-8 md:flex">
            <a href="/#fonctionnalités" className={`text-sm font-medium transition ${scrolled ? "text-[var(--color-lr-text-secondary)] hover:text-[var(--color-phare)]" : "text-white/70 hover:text-[var(--color-phare)]"}`}>
              Fonctionnalités
            </a>
            <a href="/#tarifs" className={`text-sm font-medium transition ${scrolled ? "text-[var(--color-lr-text-secondary)] hover:text-[var(--color-phare)]" : "text-white/70 hover:text-[var(--color-phare)]"}`}>
              Tarifs
            </a>
            <a href="/#faq" className={`text-sm font-medium transition ${scrolled ? "text-[var(--color-lr-text-secondary)] hover:text-[var(--color-phare)]" : "text-white/70 hover:text-[var(--color-phare)]"}`}>
              FAQ
            </a>
          </div>

          <div className="flex flex-1 items-center justify-end gap-3">
            <Link href="/login" className={`hidden text-sm font-medium transition md:inline ${scrolled ? "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]" : "text-white/70 hover:text-white"}`}>
              Connexion
            </Link>
            <a href="/#demo" className="btn-phare rounded-xl px-5 py-2.5 text-sm font-semibold">
              Demander une démo
            </a>
          </div>
        </nav>
      </header>

      {/* Content */}
      {children}

      {/* Footer */}
      <footer className="relative bg-[var(--color-ignition)] px-6 py-16">
        <div className="absolute top-0 left-0 right-0 bar-gradient" />
        <div className="mx-auto grid max-w-6xl grid-cols-1 gap-12 md:grid-cols-3">
          <div>
            <LeadriveLogo variant="dark" height={24} className="w-auto" />
            <p className="mt-3 text-sm text-white/50">Exploitez le potentiel de vos leads.</p>
          </div>
          <div className="space-y-2">
            <Link href="/mentions-legales" className="block text-sm text-white/60 transition hover:text-[var(--color-phare)]">Mentions légales</Link>
            <Link href="/politique-confidentialite" className="block text-sm text-white/60 transition hover:text-[var(--color-phare)]">Politique de confidentialité</Link>
            <Link href="/login" className="block text-sm text-white/60 transition hover:text-[var(--color-phare)]">Connexion</Link>
          </div>
          <div className="space-y-2">
            <p className="text-sm text-white/60">contact@leadrive.fr</p>
          </div>
        </div>
        <div className="mx-auto mt-12 max-w-6xl border-t border-white/10 pt-6">
          <p className="text-xs text-white/40">&copy; 2026 Leadrive, Tous droits réservés</p>
        </div>
      </footer>
    </div>
  );
}
