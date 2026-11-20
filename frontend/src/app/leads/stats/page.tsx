// Page dépréciée : /leads/stats fusionnée dans /stats (sections Top territoires,
// Top marques, Répartition statut migrées). On garde l'URL stable (redirect
// permanent serveur) pour ne pas casser les anciens bookmarks / liens emails.

import { redirect } from "next/navigation";

export default function LeadStatsRedirect() {
  redirect("/stats");
}
