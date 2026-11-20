"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useAuth, useClerk } from "@clerk/nextjs";
import { useCurrentRole, deconnexionComplete } from "@/lib/auth";
import LeadriveLogo from "@/components/common/LeadriveLogo";

const ENV = process.env.NEXT_PUBLIC_ENV || "local";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { isLoaded: authLoaded, isSignedIn } = useAuth();
  const { signOut } = useClerk();
  const { role, isLoaded: roleLoaded } = useCurrentRole();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [darkMode, setDarkMode] = useState(false);

  useEffect(() => {
    if (typeof window !== "undefined") {
      // Format unifie avec Sidebar.tsx : String(boolean) = "true"/"false".
      // L'ancien format "1"/"0" creait une desync : dark mode active dans l'app,
      // puis arrive sur /admin -> le toggle affichait "mode clair" alors que la
      // classe `dark` etait restee sur documentElement (UI sombre, libelle clair).
      const saved = localStorage.getItem("leadrive_dark");
      const isDark = saved === "true";
      setDarkMode(isDark);
      document.documentElement.classList.toggle("dark", isDark);
    }
  }, []);

  const toggleDark = useCallback(() => {
    const next = !darkMode;
    setDarkMode(next);
    document.documentElement.classList.toggle("dark", next);
    localStorage.setItem("leadrive_dark", String(next));
  }, [darkMode]);

  useEffect(() => {
    if (!authLoaded) return;
    if (!isSignedIn) {
      router.replace("/login");
      return;
    }
    if (!roleLoaded) return;
    // /admin/config-ia uses AppLayout (handled by that page)
    if (role !== "SUPER_ADMIN" && !pathname.startsWith("/admin/config-ia")) {
      router.replace("/dashboard");
    }
  }, [authLoaded, isSignedIn, role, roleLoaded, pathname, router]);

  // Ferme le menu mobile au changement de route
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [pathname]);

  // For /admin/config-ia, let the page handle its own layout (AppLayout)
  if (pathname.startsWith("/admin/config-ia")) {
    return <>{children}</>;
  }

  if (!authLoaded || !roleLoaded || !isSignedIn || role !== "SUPER_ADMIN") return null;

  // Bandeau d'environnement : danger (prod) / warning (staging) / success (local)
  const envColor = ENV === "production" ? "bg-danger"
    : ENV === "staging" ? "bg-warning"
    : "bg-success";

  return (
    <div className="flex h-screen flex-col">
      {/* Env banner */}
      <div className={`h-8 ${envColor} flex items-center justify-center text-xs text-white font-mono`}>
        Back-office Leadrive · Env {ENV}
      </div>

      {/* Mobile top bar */}
      <div className="flex h-12 items-center gap-3 border-b border-[var(--color-admin-border)] bg-[var(--color-admin-bg)] px-4 lg:hidden">
        <button
          onClick={() => setMobileMenuOpen(true)}
          className="rounded-lg p-1.5 text-[var(--color-admin-text-secondary)] hover:bg-[var(--color-admin-bg-hover)]"
          aria-label="Ouvrir le menu"
        >
          <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <div className="flex items-center gap-2">
          <LeadriveLogo variant="dark" height={20} priority className="w-auto" />
          <span className="text-[10px] uppercase bg-[var(--color-admin-border)] text-[var(--color-admin-text-secondary)] px-1.5 py-0.5 rounded">Admin</span>
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar admin desktop */}
        <aside className="hidden w-60 bg-[var(--color-admin-bg)] text-[var(--color-admin-text-secondary)] lg:flex lg:flex-col">
          <SidebarContent pathname={pathname} onSignOut={() => { void deconnexionComplete(() => signOut()); }} darkMode={darkMode} onToggleDark={toggleDark} />
        </aside>

        {/* Sidebar admin mobile (overlay) */}
        {mobileMenuOpen && (
          <>
            <div
              className="fixed inset-0 z-40 bg-black/50 lg:hidden"
              onClick={() => setMobileMenuOpen(false)}
            />
            <aside className="fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-[var(--color-admin-bg)] text-[var(--color-admin-text-secondary)] shadow-xl lg:hidden">
              <div className="absolute right-3 top-3">
                <button
                  onClick={() => setMobileMenuOpen(false)}
                  className="rounded-lg p-1.5 text-[var(--color-admin-text-muted)] hover:bg-[var(--color-admin-bg-hover)] hover:text-[var(--color-admin-text)]"
                  aria-label="Fermer le menu"
                >
                  <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <SidebarContent pathname={pathname} onSignOut={() => { void deconnexionComplete(() => signOut()); }} darkMode={darkMode} onToggleDark={toggleDark} />
            </aside>
          </>
        )}

        {/* Main */}
        <main className="flex-1 overflow-y-auto bg-[var(--background)] min-w-0">
          {children}
        </main>
      </div>
    </div>
  );
}

function SidebarContent({ pathname, onSignOut, darkMode, onToggleDark }: { pathname: string; onSignOut: () => void; darkMode: boolean; onToggleDark: () => void }) {
  return (
    <>
      <div className="px-5 py-5 border-b border-[var(--color-admin-border)]">
        <div className="flex items-center gap-2">
          <LeadriveLogo variant="dark" height={20} priority className="w-auto" />
          <span className="text-[10px] uppercase bg-[var(--color-admin-border)] text-[var(--color-admin-text-secondary)] px-1.5 py-0.5 rounded">Admin</span>
        </div>
      </div>
      <nav className="flex-1 p-3 space-y-0.5 overflow-y-auto">
        <AdminNavLink href="/admin" label="Vue d&apos;ensemble" active={pathname === "/admin"} />
        <AdminNavLink href="/admin/concessions" label="Établissements" active={pathname.startsWith("/admin/concessions")} />
        <AdminNavLink href="/admin/onboardings-bloques" label="Onboardings bloqués" active={pathname === "/admin/onboardings-bloques"} />
        <AdminNavLink href="/admin/whatsapp" label="WhatsApp" active={pathname.startsWith("/admin/whatsapp")} />
        <AdminNavLink href="/admin/demandes-demo" label="Demandes de démo" active={pathname === "/admin/demandes-demo"} />
      </nav>
      <div className="p-3 border-t border-[var(--color-admin-border)] space-y-2">
        <button
          onClick={onToggleDark}
          className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-[var(--color-admin-text-muted)] transition hover:bg-[var(--color-admin-bg-hover)] hover:text-[var(--color-admin-text)]"
        >
          {darkMode ? (
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
          ) : (
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
            </svg>
          )}
          {darkMode ? "Mode clair" : "Mode sombre"}
        </button>
        <button onClick={onSignOut}
          className="w-full text-left text-sm text-[var(--color-admin-text-muted)] hover:text-[var(--color-admin-text)] transition px-3 py-2">
          Déconnexion
        </button>
      </div>
    </>
  );
}

function AdminNavLink({ href, label, active }: { href: string; label: string; active: boolean }) {
  return (
    <Link
      href={href}
      className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${
        active
          ? "bg-[var(--color-accent)]/15 font-medium text-[var(--color-accent-light)]"
          : "text-[var(--color-admin-text-muted)] hover:bg-[var(--color-admin-bg-hover)] hover:text-[var(--color-admin-text)]"
      }`}
    >
      {label}
    </Link>
  );
}
