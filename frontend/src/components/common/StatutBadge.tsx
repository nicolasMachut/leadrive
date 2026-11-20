type Tone = "neutral" | "success" | "warning" | "danger" | "info" | "accent";

const TONES: Record<Tone, string> = {
  neutral: "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]",
  success: "bg-success/10 text-success",
  warning: "bg-warning/10 text-warning",
  danger: "bg-danger/10 text-danger",
  info: "bg-accent/10 text-accent",
  accent: "bg-accent/10 text-accent",
};

// Mapping centralise des statuts metier vers les tons. Etend si besoin.
const STATUT_TONE: Record<string, Tone> = {
  ACTIF: "success",
  ACTIVE: "success",
  RDV_OBTENU: "success",
  ENVOYE: "success",
  RECU: "success",

  INVITE: "warning",
  EN_COURS: "info",
  EN_ATTENTE: "warning",
  NOUVEAU: "info",
  RAPPELE: "accent",
  CONTACTE: "info",

  INACTIF: "neutral",
  EXPIREE: "neutral",
  ARCHIVE: "neutral",

  ECHEC: "danger",
  ERREUR: "danger",
  STOP_DEMANDE: "danger",
  SUSPENDU: "danger",
  TERMINE: "neutral",
};

interface StatutBadgeProps {
  statut: string;
  tone?: Tone;
  className?: string;
}

export default function StatutBadge({ statut, tone, className = "" }: StatutBadgeProps) {
  const resolvedTone = tone ?? STATUT_TONE[statut?.toUpperCase()] ?? "neutral";
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${TONES[resolvedTone]} ${className}`}
    >
      {statut}
    </span>
  );
}
