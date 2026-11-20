// ============================================================================
// DetailCard — section card éditoriale avec header + body.
// Standardise le rythme rounded-2xl + border + bg-card + overflow-hidden +
// padding body (px-6 py-6 / p-5 / none). Remplace les 6 inlines de
// /leads/[id] (Brief IA, Conversation, Pilotage, Véhicule, RDV, Détails) et
// ceux de dashboards/stats. Le header est typiquement <SectionHeader bordered>.
// ============================================================================

import { ReactNode } from "react";

type Padding = "md" | "lg" | "none";
type Variant = "default" | "signature";

interface DetailCardProps {
  /** Header (typiquement <SectionHeader bordered title="..." actions={...} />). */
  header?: ReactNode;
  /** Contenu de la card. */
  children: ReactNode;
  /** Padding du body. md=p-5 (sidebar lead, KPI), lg=px-6 py-6 (sections principales), none=enfant gère. */
  padding?: Padding;
  /** signature = .card-leadrive (barre gradient ember top-left). default = bordure tan simple. */
  variant?: Variant;
  /** Identifiant DOM (utile pour ancres #conversation-whatsapp). */
  id?: string;
  className?: string;
}

const BODY_PADDING: Record<Padding, string> = {
  none: "",
  md: "p-5",
  lg: "px-6 py-6",
};

export default function DetailCard({
  header,
  children,
  padding = "lg",
  variant = "default",
  id,
  className = "",
}: DetailCardProps) {
  const baseChrome =
    variant === "signature"
      ? "card-leadrive overflow-hidden"
      : "overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--card)]";

  return (
    <section id={id} className={`${baseChrome} ${className}`}>
      {header}
      <div className={BODY_PADDING[padding]}>{children}</div>
    </section>
  );
}
