export interface ConversationInbox {
  conversationId: string;
  leadId: number;
  nomProspect: string;
  marque: string;
  modele: string;
  territoire: string;
  scoring: number;
  hotnessScore: number | null;
  statut: string;
  /** Statut du lead dans le funnel commercial (NOUVEAU/CONTACTE/EN_COURS/RDV_PRIS/TERMINE) */
  leadStatut: string | null;
  nombreMessages: number;
  dernierMessageLe: string;
  dernierMessageContenu: string;
  dernierMessageDirection: string;
  messageNonLu: boolean;
  userId: number;
  nomVendeur: string;
  modePilotage: string;
  /** Lead sensible : prospect mécontent ayant déclenché un handoff. Affiche un badge rouge "Client irrité". */
  clientIrrite: boolean;
}

/** Alerte de rappel immédiat déclenchée par l'IA quand un prospect demande à être rappelé tout de suite. */
export interface AlerteRappelImmediat {
  conversationId: string;
  leadId: number;
  nomProspect: string;
  marque: string;
  modele: string;
  timestamp: string;
}

export interface ConversationCompteurs {
  enCours: number;
  rdvObtenus: number;
  nonLues: number;
  modeHumain: number;
}

export interface ConversationDetail {
  id: number;
  conversationId: string;
  statut: string;
  numeroProspect: string;
  nombreMessages: number;
  dernierMessageLe: string;
  creeLe: string;
  messages: ConversationMessage[];
}

export interface ConversationMessage {
  id?: number;
  direction: string;
  /** IA / VENDEUR / SYSTEME — qui a émis le message sortant (rendu de la bulle). */
  expediteur?: string;
  contenu: string;
  statut: string;
  /** Motif d'échec lisible (FR, sans jargon) quand statut === "ECHEC", sinon absent. */
  motifEchec?: string;
  ordre: number;
  creeLe: string;
}

export function formatTempsRelatif(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return "maintenant";
  if (diffMin < 60) return `${diffMin}m`;
  const diffH = Math.floor(diffMin / 60);
  if (diffH < 24) return `${diffH}h`;
  const diffD = Math.floor(diffH / 24);
  if (diffD === 1) return "hier";
  return date.toLocaleDateString("fr-FR", { day: "2-digit", month: "2-digit" });
}

export function formatDateSeparator(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const msgDay = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const diffDays = Math.floor((today.getTime() - msgDay.getTime()) / 86400000);
  if (diffDays === 0) return "Aujourd'hui";
  if (diffDays === 1) return "Hier";
  return date.toLocaleDateString("fr-FR", { day: "2-digit", month: "long", year: "numeric" });
}
