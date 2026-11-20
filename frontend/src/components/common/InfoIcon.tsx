"use client";

import { ReactNode } from "react";
import Tooltip from "./Tooltip";

/**
 * Petite icone "i" cliquable/survolable qui affiche une explication via Tooltip.
 * Usage : `<InfoIcon>Le scoring represente l'interet detecte du prospect.</InfoIcon>`
 */
interface InfoIconProps {
  children: ReactNode;
  className?: string;
  size?: "sm" | "md";
}

export default function InfoIcon({ children, className = "", size = "sm" }: InfoIconProps) {
  const dim = size === "sm" ? "h-3.5 w-3.5" : "h-4 w-4";
  return (
    <Tooltip content={children} maxWidth={280}>
      <button
        type="button"
        tabIndex={0}
        aria-label="Plus d'informations"
        className={`inline-flex cursor-help items-center justify-center text-[var(--color-lr-text-tertiary)] hover:text-[var(--color-lr-text-secondary)] transition-colors ${className}`}
      >
        <svg className={dim} fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </button>
    </Tooltip>
  );
}
