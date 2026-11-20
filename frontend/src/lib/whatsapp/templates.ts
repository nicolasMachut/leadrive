// Labels user-facing des templates Meta-approved WhatsApp.
// Centralisés ici pour rester en sync entre /parametres/whatsapp/activation,
// /admin/whatsapp/* et toute page d'admin qui les liste.
//
// Les noms techniques (clés) restent ceux du backend Twilio :
// "leadrive_premier_contact" — exposé uniquement aux SUPER_ADMIN.

export const TEMPLATE_LABELS: Record<string, string> = {
  leadrive_premier_contact: "Premier contact",
  leadrive_relance_j1: "Relance J+1",
  leadrive_relance_j3: "Relance J+3",
  leadrive_rappel_rdv: "Rappel RDV",
};

export function templateLabel(name: string): string {
  return TEMPLATE_LABELS[name] ?? name;
}
