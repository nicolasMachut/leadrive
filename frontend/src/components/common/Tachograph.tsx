"use client";

import { useEffect, useRef, useState } from "react";

type Size = "sm" | "md" | "lg";
type Variant = "auto" | "accent" | "phare" | "success" | "danger" | "warning";

interface TachographProps {
  /** Valeur actuelle (0, max) */
  value: number;
  /** Valeur maximale (default 100) */
  max?: number;
  /** Label affiche sous le chiffre (uniquement en size lg) */
  label?: string;
  /** sm 48px, md 72px, lg 120px */
  size?: Size;
  /** auto = couleur selon le seuil. Sinon force la couleur. */
  variant?: Variant;
  /** Force la couleur du chiffre central. Utile sur fond sombre (ex : "white").
      Default : var(--foreground), adapte au mode light/dark courant. */
  textColor?: string;
  /** Force la couleur du track (l'arc de fond non rempli). Utile sur fond
      sombre ou colore. Default : var(--border). */
  trackColor?: string;
  /** Force la couleur du label sous le chiffre. */
  labelColor?: string;
  className?: string;
}

const PX: Record<Size, number> = { sm: 48, md: 72, lg: 120 };
const STROKE: Record<Size, number> = { sm: 4, md: 6, lg: 10 };
const FONT_RATIO: Record<Size, number> = { sm: 0.36, md: 0.34, lg: 0.30 };

function couleurAuto(ratio: number): string {
  if (ratio >= 0.7) return "var(--color-success)";
  if (ratio >= 0.4) return "var(--color-accent)";
  if (ratio >= 0.2) return "var(--color-warning)";
  return "var(--color-lr-text-tertiary)";
}

const VARIANT_COLOR: Record<Exclude<Variant, "auto">, string> = {
  accent: "var(--color-accent)",
  phare: "var(--color-phare)",
  success: "var(--color-success)",
  danger: "var(--color-danger)",
  warning: "var(--color-warning)",
};

/**
 * Tachograph, jauge signature Leadrive. Arc 270° (ouvert en bas) + chiffre
 * central en JetBrains Mono, animation de remplissage au mount. Inspire des
 * compteurs auto. À utiliser pour les KPI principaux (scoring lead, taux de
 * conversion, progression objectif) à la place des barres + chiffre plates.
 */
export default function Tachograph({
  value,
  max = 100,
  label,
  size = "md",
  variant = "auto",
  textColor = "var(--foreground)",
  trackColor = "var(--border)",
  labelColor = "var(--color-lr-text-tertiary)",
  className = "",
}: TachographProps) {
  const px = PX[size];
  const stroke = STROKE[size];
  const radius = (px - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const arcLength = circumference * 0.75; // 270° arc
  const ratio = Math.min(1, Math.max(0, value / max));
  const color = variant === "auto" ? couleurAuto(ratio) : VARIANT_COLOR[variant];

  // Mount anim : on demarre a 0 et on anime jusqu'a la valeur cible.
  // strokeDashoffset = arcLength * (1 - ratio) -> visible = arcLength * ratio.
  const [animatedRatio, setAnimatedRatio] = useState(0);
  // Scale-pop sur changement de valeur (apres mount), emphase visuelle du delta
  const [pop, setPop] = useState(false);
  const isFirstRender = useRef(true);
  useEffect(() => {
    const id = requestAnimationFrame(() => setAnimatedRatio(ratio));
    return () => cancelAnimationFrame(id);
  }, [ratio]);
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    setPop(true);
    const t = setTimeout(() => setPop(false), 400);
    return () => clearTimeout(t);
  }, [value]);

  const offset = arcLength * (1 - animatedRatio);

  return (
    <div
      className={`relative inline-flex shrink-0 items-center justify-center ${className}`}
      style={{
        width: px,
        height: px,
        transform: pop ? "scale(1.05)" : "scale(1)",
        transition: "transform 400ms cubic-bezier(0.34, 1.56, 0.64, 1)",
      }}
    >
      <svg
        width={px}
        height={px}
        viewBox={`0 0 ${px} ${px}`}
        style={{ transform: "rotate(135deg)" }}
        aria-hidden="true"
      >
        {/* Track, l'arc complet en couleur muted */}
        <circle
          cx={px / 2}
          cy={px / 2}
          r={radius}
          fill="none"
          stroke={trackColor}
          strokeWidth={stroke}
          strokeDasharray={`${arcLength} ${circumference}`}
          strokeLinecap="round"
        />
        {/* Fill, l'arc colore qui represente la valeur */}
        <circle
          cx={px / 2}
          cy={px / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeDasharray={`${arcLength} ${circumference}`}
          strokeDashoffset={offset}
          strokeLinecap="round"
          style={{
            transition: "stroke-dashoffset 0.9s cubic-bezier(0.22, 1, 0.36, 1)",
          }}
        />
      </svg>
      {/* Chiffre central + label */}
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
        <span
          className="font-numeric leading-none"
          style={{ fontSize: px * FONT_RATIO[size], color: textColor }}
          aria-label={label ? `${value} sur ${max} ${label}` : `${value} sur ${max}`}
        >
          {Math.round(value)}
        </span>
        {label && size === "lg" && (
          <span
            className="mt-1 text-[10px] font-medium uppercase tracking-wider"
            style={{ color: labelColor }}
          >
            {label}
          </span>
        )}
      </div>
    </div>
  );
}
