/**
 * Affichage visuel du scoring d'un lead sous forme de jauge circulaire.
 * Score : 0 (froid) -> 1 (chaud).
 * Variantes :
 * - "lg" : grosse jauge avec label dessous (pour la fiche prospect)
 * - "md" : jauge moyenne en ligne (pour le header)
 * - "sm" : pastille compacte (pour les listes)
 *
 * Tooltip integre par defaut, expliquant ce qu'est le scoring et comment il est calcule.
 * Desactivable via `withTooltip={false}`.
 */

import Tooltip from "./Tooltip";

interface ScoringGaugeProps {
  /** Score d'engagement calcule par l'IA (0-1). */
  score: number | null | undefined;
  /** Alias pour compatibilite, prioritaire sur score si fourni. */
  hotnessScore?: number | null;
  size?: "sm" | "md" | "lg";
  label?: string;
  withTooltip?: boolean;
}

const SIZE_CONFIG = {
  sm: { box: 32, stroke: 4, font: "text-[10px]" },
  md: { box: 48, stroke: 5, font: "text-xs" },
  lg: { box: 80, stroke: 7, font: "text-base" },
};

function getColor(score: number): { stroke: string; text: string; label: string } {
  if (score >= 0.7) return { stroke: "#22c55e", text: "text-success", label: "Chaud" };
  if (score >= 0.4) return { stroke: "#D9603A", text: "text-accent", label: "Tiède" }; // ember (No-Blue Rule), cohérent avec text-accent
  return { stroke: "#94a3b8", text: "text-[var(--color-lr-text-secondary)]", label: "Froid" };
}

export default function ScoringGauge({ score, hotnessScore, size = "md", label, withTooltip = true }: ScoringGaugeProps) {
  const config = SIZE_CONFIG[size];

  const effectiveScore = (hotnessScore != null && !isNaN(Number(hotnessScore))) ? hotnessScore : score;

  if (effectiveScore == null || isNaN(Number(effectiveScore))) {
    return (
      <div
        className="inline-flex items-center justify-center rounded-full border border-dashed border-[var(--border)]"
        style={{ width: config.box, height: config.box }}
        title="Pas encore de scoring"
      >
        <span className={`${config.font} text-[var(--color-lr-text-tertiary)]`}>—</span>
      </div>
    );
  }

  const num = Math.max(0, Math.min(1, Number(effectiveScore)));
  const radius = (config.box - config.stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - num);
  const color = getColor(num);
  const display = Math.round(num * 100);

  const gauge = (
    <div className="inline-flex flex-col items-center gap-1 cursor-help relative">
      <div className="relative inline-flex items-center justify-center" style={{ width: config.box, height: config.box }}>
        <svg className="-rotate-90" width={config.box} height={config.box}>
          <circle
            cx={config.box / 2}
            cy={config.box / 2}
            r={radius}
            stroke="var(--border)"
            strokeWidth={config.stroke}
            fill="none"
          />
          <circle
            cx={config.box / 2}
            cy={config.box / 2}
            r={radius}
            stroke={color.stroke}
            strokeWidth={config.stroke}
            fill="none"
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            strokeLinecap="round"
            className="transition-all duration-700"
          />
        </svg>
        <span className={`absolute font-bold font-mono ${config.font} ${color.text}`}>{display}</span>
      </div>
      {label !== undefined && (
        <span className={`${config.font} font-medium ${color.text}`}>{label || color.label}</span>
      )}
    </div>
  );

  if (!withTooltip) return gauge;

  const tooltipContent = (
    <div className="space-y-1.5">
      <p className="font-semibold">Score d&apos;engagement : {display}/100 ({color.label})</p>
      <p className="text-[var(--color-lr-text-secondary)]">
        Calcule par l&apos;IA apres chaque echange WhatsApp en fonction des signaux d&apos;achat (intention RDV, budget, urgence) et des objections detectees.
      </p>
      <div className="pt-1 border-t border-[var(--border)] space-y-0.5 text-[10px] text-[var(--color-lr-text-tertiary)]">
        <p><span className="text-success">●</span> 70-100 : Chaud (priorite haute)</p>
        <p><span className="text-accent">●</span> 40-69 : Tiede (a relancer)</p>
        <p><span className="text-[var(--color-lr-text-secondary)]">●</span> 0-39 : Froid (a qualifier)</p>
      </div>
    </div>
  );

  return <Tooltip content={tooltipContent}>{gauge}</Tooltip>;
}
