import { ReactNode } from "react";
import InfoIcon from "./InfoIcon";

interface CardProps {
  children: ReactNode;
  className?: string;
  padded?: boolean;
}

export default function Card({ children, className = "", padded = false }: CardProps) {
  return (
    <div className={`rounded-xl border border-[var(--border)] bg-[var(--card)] ${padded ? "p-5" : ""} ${className}`}>
      {children}
    </div>
  );
}

interface KpiCardProps {
  label: string;
  value: ReactNode;
  mono?: boolean;
  hint?: ReactNode;
  icon?: ReactNode;
  iconBg?: string;
  /** Texte d'aide optionnel, declenche un petit "i" a cote du label */
  tooltip?: ReactNode;
  /**
   * Si true, anime la valeur au mount avec `animate-kpi-ignite` (fade + overshoot
   * + blur clear). Effet "instrument qui s'allume". A utiliser sur les KPIs
   * stratégiques (heros), pas sur tous les KpiCard d'une grille.
   */
  ignite?: boolean;
  /**
   * Couleur du value. Si non précisé, hérite de `text-[var(--foreground)]`.
   * Permet de teinter sur des KpiCard à enjeu (taux, conversion).
   */
  valueColor?: string;
  /**
   * Texte d'info inline en complément du tooltip (kept for backward compat avec
   * les usages /stats/page.tsx qui passent `info=` au lieu de `tooltip=`).
   */
  info?: ReactNode;
  /** Indicateur d'évolution (ex: "+12%") affiché en chip à côté de la valeur. */
  evolution?: number | null;
}

export function KpiCard({
  label,
  value,
  mono,
  hint,
  icon,
  iconBg = "bg-accent/10 text-accent",
  tooltip,
  ignite = false,
  valueColor,
  info,
  evolution,
}: KpiCardProps) {
  const tooltipContent = tooltip ?? info;
  return (
    <Card padded>
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex items-center gap-1.5 min-w-0">
          <p className="text-sm font-medium text-[var(--color-lr-text-secondary)]">{label}</p>
          {tooltipContent && <InfoIcon>{tooltipContent}</InfoIcon>}
        </div>
        {icon && (
          <div className={`flex h-9 w-9 items-center justify-center rounded-lg ${iconBg}`}>
            {icon}
          </div>
        )}
      </div>
      <div className="flex items-baseline gap-2">
        <p
          className={`text-2xl font-bold ${valueColor ?? "text-[var(--foreground)]"} ${mono ? "font-mono" : ""} ${ignite ? "animate-kpi-ignite" : ""}`}
        >
          {value}
        </p>
        {evolution != null && Number.isFinite(evolution) && evolution !== 0 && (
          <span
            className={`font-mono text-[10px] font-semibold tabular-nums ${
              evolution > 0 ? "text-success" : "text-[var(--color-lr-text-tertiary)]"
            }`}
          >
            {evolution > 0 ? "+" : ""}
            {Math.round(evolution)}%
          </span>
        )}
      </div>
      {hint && <div className="mt-1 text-xs text-[var(--color-lr-text-secondary)]">{hint}</div>}
    </Card>
  );
}
