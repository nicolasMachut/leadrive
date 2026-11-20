"use client";

import { ReactNode } from "react";

export interface TabItem<T extends string> {
  value: T;
  label: string;
  icon?: ReactNode;
  /** Badge optionnel (ex: nombre de notifications non lues). */
  badge?: number;
}

interface Props<T extends string> {
  items: TabItem<T>[];
  value: T;
  onChange: (value: T) => void;
  /** Taille : "sm" (compact, default) ou "md" (plus large pour les pages avec
   *  beaucoup de contenu en-dessous). */
  size?: "sm" | "md";
  /** Mode plein-largeur (chaque onglet prend la meme part) ou auto (taille
   *  selon le texte). Defaut "auto". */
  fullWidth?: boolean;
  className?: string;
}

/**
 * Composant Tabs unifie - pattern "segment buttons" coherent dans toute
 * l'app (Planning, Import, fiche lead, etc.). L'onglet actif a un fond
 * accent solide, les inactifs sont transparents avec hover.
 *
 * <p>Variante deja en place : les boutons de periode sur /stats (7j/30j/...).
 * Meme look, meme spacing.</p>
 */
export default function Tabs<T extends string>({
  items, value, onChange, size = "sm", fullWidth = false, className = "",
}: Props<T>) {
  const padding = size === "sm" ? "px-3 py-1.5 text-xs" : "px-4 py-2 text-sm";

  return (
    <div className={`inline-flex rounded-lg border border-[var(--border)] bg-[var(--card)] p-0.5 ${fullWidth ? "w-full" : ""} ${className}`}>
      {items.map((item) => {
        const active = item.value === value;
        return (
          <button
            key={item.value}
            type="button"
            onClick={() => onChange(item.value)}
            className={`inline-flex items-center gap-1.5 rounded-md font-medium transition-colors ${padding} ${
              fullWidth ? "flex-1 justify-center" : ""
            } ${
              active
                ? "bg-accent text-white"
                : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"
            }`}
          >
            {item.icon && <span className={size === "sm" ? "h-3.5 w-3.5" : "h-4 w-4"}>{item.icon}</span>}
            {item.label}
            {typeof item.badge === "number" && item.badge > 0 && (
              <span className={`ml-1 rounded-full px-1.5 text-[10px] font-bold ${
                active ? "bg-white/20 text-white" : "bg-accent/10 text-accent"
              }`}>
                {item.badge > 99 ? "99+" : item.badge}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
