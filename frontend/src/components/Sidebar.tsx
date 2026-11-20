"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { useClerk } from "@clerk/nextjs";
import { useApiClient, useConcessionName, useCurrentRole, useUserDisplayName, deconnexionComplete } from "@/lib/auth";
import { useRealtime } from "@/lib/realtime";
import { useSelfSignupStatus } from "@/components/whatsapp/useSelfSignupStatus";
import TenantLogoImage from "@/components/whatsapp/TenantLogoImage";
import { LeadriveIcon } from "@/components/common/LeadriveLogo";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

const icons: Record<string, string> = {
  dashboard: "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6",
  inbox: "M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-2.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4",
  users: "M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z",
  team: "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z",
  upload: "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12",
  stats: "M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z",
  calendar: "M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z",
  calendarCheck: "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4",
  sparkle: "M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z",
  help: "M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
  chat: "M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z",
  logout: "M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1",
};

function NavContent({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const router = useRouter();
  const { signOut } = useClerk();
  const apiFetch = useApiClient();
  const { role: currentRole } = useCurrentRole();
  const userName = useUserDisplayName();
  const concessionName = useConcessionName();
  const role = currentRole ?? "";
  const [inboxUnread, setInboxUnread] = useState(0);
  const [darkMode, setDarkMode] = useState(false);
  const { nouveauxLeads } = useRealtime();
  // Track previous values pour declencher un pulse visuel quand le compteur monte
  const [pulsedKeys, setPulsedKeys] = useState<Set<string>>(new Set());
  const prevInboxUnread = useRef(0);
  const prevNouveauxLeads = useRef(0);

  useEffect(() => {
    if (inboxUnread > prevInboxUnread.current) {
      setPulsedKeys((p) => { const n = new Set(p); n.add("inbox"); return n; });
      const t = setTimeout(() => {
        setPulsedKeys((p) => { const n = new Set(p); n.delete("inbox"); return n; });
      }, 1600);
      prevInboxUnread.current = inboxUnread;
      return () => clearTimeout(t);
    }
    prevInboxUnread.current = inboxUnread;
  }, [inboxUnread]);

  useEffect(() => {
    if (nouveauxLeads > prevNouveauxLeads.current) {
      setPulsedKeys((p) => { const n = new Set(p); n.add("leads"); return n; });
      const t = setTimeout(() => {
        setPulsedKeys((p) => { const n = new Set(p); n.delete("leads"); return n; });
      }, 1600);
      prevNouveauxLeads.current = nouveauxLeads;
      return () => clearTimeout(t);
    }
    prevNouveauxLeads.current = nouveauxLeads;
  }, [nouveauxLeads]);

  // Hydrate dark mode from localStorage
  useEffect(() => {
    const saved = typeof window !== "undefined" && localStorage.getItem("leadrive_dark");
    if (saved === "true") {
      setDarkMode(true);
      document.documentElement.classList.add("dark");
    }
  }, []);

  const toggleDark = useCallback(() => {
    const next = !darkMode;
    setDarkMode(next);
    document.documentElement.classList.toggle("dark", next);
    localStorage.setItem("leadrive_dark", String(next));
  }, [darkMode]);

  useEffect(() => {
    // SUPER_ADMIN : redirige vers /admin si l'utilisateur arrive sur une page
    // non-admin (manager/vendeur view). Mais reste sur place si on navigue
    // deja dans la zone /admin/* (sinon impossible d'ouvrir /admin/concessions/123
    // /admin/onboardings-bloques, etc.).
    if (currentRole === "SUPER_ADMIN") {
      if (!pathname?.startsWith("/admin")) {
        router.replace("/admin");
      }
      return;
    }
    if (!currentRole) return;
    // Fetch au montage + a chaque changement de page (pour mettre a jour apres lecture)
    apiFetch(`${API_BASE}/api/conversations/compteurs`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (data && typeof data.nonLues === "number") setInboxUnread(data.nonLues);
      })
      .catch(() => {});
  }, [currentRole, apiFetch, router, pathname]);

  function handleLogout() {
    void deconnexionComplete(() => signOut());
  }

  function isActive(href: string) {
    return pathname === href || pathname.startsWith(href + "/");
  }

  const isManagerRole = role === "MANAGER";

  // WhatsApp activation status — affiche un item sidebar tant que l'activation
  // n'est pas terminee. Beneficie du cache module-level rempli par le banner.
  const { status: whatsappStatus } = useSelfSignupStatus();
  const whatsappNeedsActivation = !!whatsappStatus && !whatsappStatus.tousTemplatesApprouves;
  // Point d'alerte : activation a finir OU compte restreint (envoi suspendu).
  const whatsappAlerte = whatsappNeedsActivation || whatsappStatus?.etatCompte === "RESTREINT";

  // Menu organise en groupes hierarchiques (Proposition A) :
  // 1. Au quotidien : les pages consultees tous les jours (visible everyone)
  // 2. Acquisition : import de prospects (manager only, vendeur n'a pas le droit
  //    d'importer cote backend de toute facon)
  // 3. Reglages : config compte (manager only)
  type NavItem = { name: string; href: string; icon: string; badge?: number; badgeKey?: string; tourId?: string; dot?: boolean };
  type NavGroup = { label?: string; items: NavItem[] };

  const groups: NavGroup[] = [
    {
      items: [
        { name: "Tableau de bord", href: "/dashboard", icon: "dashboard" },
        { name: "Conversations", href: "/inbox", icon: "inbox", badge: inboxUnread, badgeKey: "inbox", tourId: "nav-inbox" },
        { name: "Prospects", href: "/leads", icon: "users", badge: nouveauxLeads, badgeKey: "leads", tourId: "nav-leads" },
        { name: "Planning", href: "/planning", icon: "calendarCheck", tourId: "nav-planning" },
        { name: "Statistiques", href: "/stats", icon: "stats", tourId: "nav-stats" },
      ],
    },
    ...(isManagerRole
      ? [
          {
            label: "Acquisition",
            items: [
              { name: "Importer un fichier", href: "/import", icon: "upload", tourId: "nav-import" },
              { name: "Import par email", href: "/email-import", icon: "inbox", tourId: "nav-email-import" },
            ],
          },
          {
            label: "Réglages",
            items: [
              { name: "Mon équipe", href: "/equipe", icon: "team", tourId: "nav-equipe" },
              { name: "Mon établissement", href: "/admin/config-ia", icon: "sparkle", tourId: "nav-config-ia" },
              // Toujours visible pour un manager : c'est une page de reglages
              // (statut, templates, deconnexion). Le point de notification n'apparait
              // que tant que l'activation n'est pas terminee (nudge). Sans ca, l'onglet
              // disparaissait une fois tous les templates approuves -> page orpheline,
              // plus aucun acces pour gerer ou deconnecter WhatsApp.
              { name: "WhatsApp", href: "/parametres/whatsapp/activation", icon: "chat", ...(whatsappAlerte ? { dot: true } : {}) },
            ],
          },
        ]
      : []),
  ];

  const roleLabel: Record<string, string> = {
    SUPER_ADMIN: "Super Admin",
    MANAGER: "Manager",
    VENDEUR: "Vendeur",
  };

  const roleBadgeColor: Record<string, string> = {
    SUPER_ADMIN: "bg-[var(--foreground)] text-[var(--background)]",
    MANAGER: "bg-accent/10 text-accent",
    VENDEUR: "bg-success/10 text-success",
  };

  const initials = userName
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);

  return (
    <>
      {/* Header concession : co-branding inverse - logo + nom de la concession
          dominent (l'utilisateur se sent "chez lui"). Si pas de logo uploade,
          fallback sur le logo Leadrive (incite a uploader le logo pour
          personnaliser). Pattern road-stripes conserve en arriere-plan pour
          garder la signature visuelle Leadrive subtile. */}
      <div className="relative flex items-center gap-3 px-3 py-4 border-b border-[var(--border)] overflow-hidden">
        <div className="absolute inset-0 bg-stripes-pattern-faint pointer-events-none" />
        <div className="relative h-10 w-10 shrink-0 rounded-lg overflow-hidden flex items-center justify-center">
          <TenantLogoImage
            alt={concessionName ?? "Leadrive"}
            className="h-full w-full object-cover"
            fallback={<LeadriveIcon size={40} priority />}
          />
        </div>
        <div className="relative min-w-0 flex-1">
          <div
            className="text-sm font-semibold text-[var(--foreground)] truncate"
            title={concessionName ?? undefined}
          >
            {concessionName || "—"}
          </div>
        </div>
      </div>

      <nav className="flex-1 space-y-0.5 px-3 py-3 overflow-y-auto">
        {groups.map((group, groupIdx) => (
          <div key={group.label ?? "main"} className={groupIdx > 0 ? "mt-4" : ""}>
            {group.label && (
              <>
                <div className="mb-2 border-t border-[var(--border)]" />
                <p className="px-3 pb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                  {group.label}
                </p>
              </>
            )}
            {group.items.map((item) => {
              const active = isActive(item.href);
              return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onNavigate}
                {...(item.tourId ? { "data-tour": item.tourId } : {})}
                className={`relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-200 ease-out ${
                  active
                    ? "bg-accent/12 text-accent pl-4 font-semibold"
                    : "text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] hover:text-[var(--foreground)] hover:pl-3.5"
                }`}
              >
                {/* Tab indicator ember vertical (3px) sur item actif :
                    signal "tu es ici" très visible, cohérent avec l'inbox conv active. */}
                {active && (
                  <span
                    aria-hidden="true"
                    className="pointer-events-none absolute inset-y-1.5 left-0 w-[3px] rounded-r-full bg-[var(--color-accent)]"
                    style={{ boxShadow: "0 0 10px rgba(217, 96, 58, 0.45)" }}
                  />
                )}
                <svg className="h-4 w-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={active ? 2 : 1.5} d={icons[item.icon]} />
                </svg>
                {item.name}
                {typeof item.badge === "number" && item.badge > 0 && (
                  <span className={`ml-auto flex h-5 min-w-5 items-center justify-center rounded-full bg-[var(--color-accent)] px-1.5 font-mono text-[10px] font-bold text-white ${
                    item.badgeKey && pulsedKeys.has(item.badgeKey) ? "animate-pulse-attention" : ""
                  }`}>
                    {item.badge > 99 ? "99+" : item.badge}
                  </span>
                )}
                {item.dot && (
                  <span className="ml-auto flex h-2 w-2 rounded-full bg-warning" title="Action requise" />
                )}
              </Link>
              );
            })}
          </div>
        ))}
      </nav>

      {/* Aide (toujours visible) */}
      <div className="border-t border-[var(--border)] px-3 py-2">
        <Link
          href="/aide"
          onClick={onNavigate}
          data-tour="nav-aide"
          className={`relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-200 ease-out ${
            isActive("/aide")
              ? "bg-accent/12 text-accent pl-4 font-semibold"
              : "text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] hover:text-[var(--foreground)] hover:pl-3.5"
          }`}
        >
          {isActive("/aide") && (
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-y-1.5 left-0 w-[3px] rounded-r-full bg-[var(--color-accent)]"
              style={{ boxShadow: "0 0 10px rgba(217, 96, 58, 0.45)" }}
            />
          )}
          <svg className="h-4 w-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={isActive("/aide") ? 2 : 1.5} d={icons.help} />
          </svg>
          Aide
        </Link>
      </div>

      {/* Bottom section, compacted : user info row with inline dark toggle */}
      <div className="border-t border-[var(--border)] p-3 space-y-1">
        {/* User info + dark toggle inline */}
        {userName && (
          <div className="flex items-center gap-2 rounded-lg px-2 py-1.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-accent/10 text-[11px] font-bold text-accent">
              {initials}
            </div>
            <div className="flex-1 min-w-0">
              <p className="truncate text-[13px] font-medium text-[var(--foreground)]">{userName}</p>
              {role && (
                <span className={`mt-0.5 inline-block rounded-full px-1.5 py-0.5 font-mono text-[9px] font-semibold uppercase tracking-[0.12em] ${roleBadgeColor[role] || "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]"}`}>
                  {roleLabel[role] || role}
                </span>
              )}
            </div>
            {/* Dark toggle compacte, icon-only */}
            <button
              onClick={toggleDark}
              className="rounded-lg p-1.5 text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
              title={darkMode ? "Passer en mode clair" : "Passer en mode sombre"}
              aria-label={darkMode ? "Passer en mode clair" : "Passer en mode sombre"}
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
            </button>
          </div>
        )}

        {/* Logout */}
        <button
          onClick={handleLogout}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-[13px] font-medium text-[var(--color-lr-text-secondary)] transition-colors duration-150 hover:bg-danger/10 hover:text-danger"
        >
          <svg className="h-4 w-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d={icons.logout} />
          </svg>
          Déconnexion
        </button>
      </div>
    </>
  );
}

export default function Sidebar({ mobileOpen, onClose }: { mobileOpen?: boolean; onClose?: () => void }) {
  return (
    <>
      {/* Desktop sidebar */}
      <aside className="hidden h-full w-56 flex-col border-r border-[var(--border)] bg-[var(--card)] lg:flex">
        <NavContent />
      </aside>

      {/* Mobile overlay */}
      {mobileOpen && (
        <>
          <div className="fixed inset-0 z-40 bg-black/50 lg:hidden" onClick={onClose} />
          <aside className="fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-[var(--card)] shadow-xl lg:hidden">
            <div className="absolute right-3 top-3 z-10">
              <button
                onClick={onClose}
                aria-label="Fermer le menu"
                className="rounded-lg p-1.5 text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)]"
              >
                <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <NavContent onNavigate={onClose} />
          </aside>
        </>
      )}
    </>
  );
}
