export const METIER = {
  prospect: { singular: "prospect", plural: "prospects" },
  conversation: { singular: "conversation", plural: "conversations" },
  vente: { singular: "vente", plural: "ventes" },
  rdv: { singular: "rendez-vous", plural: "rendez-vous" },
  vendeur: { singular: "vendeur", plural: "vendeurs" },
} as const;

export const LABELS = {
  // Sidebar
  pilotage: "Pilotage",
  mesProspects: "Mes prospects",
  tousLesProspects: "Tous les prospects",
  conversations: "Conversations",
  reglagesAssistant: "Reglages assistant",
  rapports: "Rapports",
  monEquipe: "Mon equipe",
  mesNotifications: "Mes notifications",
  mesImports: "Mes imports",
  ajouterProspects: "Ajouter des prospects",

  // Actions
  preparerAppel: "Preparer l'appel",
  relancerProspect: "Relancer le prospect",
  marquerVendu: "Marquer comme vendu",
  importerCsv: "Ajouter des prospects",
} as const;
