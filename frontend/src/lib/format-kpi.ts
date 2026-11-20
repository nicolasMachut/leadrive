export function formatRoi(caGenere: number, coutTotal: number): string {
  if (coutTotal === 0 || coutTotal == null) return "En attente de premiere vente";
  if (caGenere === 0 || caGenere == null) return "En attente de votre premiere vente";
  const ratio = Math.round(caGenere / coutTotal);
  if (ratio > 100) return `Pour 1\u20AC investi, vous gagnez ${ratio.toLocaleString("fr-FR")}\u20AC`;
  return `x${ratio}`;
}

export function formatTauxConversion(rdv: number, conversations: number): string {
  if (conversations === 0 || conversations == null) return "Lancez vos premieres relances";
  if (rdv === 0) return "Pas encore de RDV pris";
  const ratio = Math.round(conversations / rdv);
  return `1 conversation sur ${ratio} aboutit a un RDV`;
}

export function formatReactivite(minutes: number): string {
  // eslint-disable-next-line no-restricted-syntax -- "\u2014" placeholder de cellule vide (n/a), exception legitime DESIGN.md
  if (minutes == null || minutes < 0) return "\u2014";
  if (minutes < 60) return `${Math.round(minutes)} min`;
  const heures = Math.floor(minutes / 60);
  const mins = Math.round(minutes % 60);
  if (minutes < 1440) return `${heures}h${mins > 0 ? String(mins).padStart(2, "0") : ""}`;
  const jours = Math.floor(minutes / 1440);
  return `${jours} jours`;
}

export function formatMontant(n: number | null | undefined): string {
  // eslint-disable-next-line no-restricted-syntax -- "\u2014" placeholder de cellule vide (n/a), exception legitime DESIGN.md
  if (n == null || isNaN(Number(n))) return "\u2014";
  const num = Number(n);
  if (num >= 1000000) return `${(num / 1000000).toFixed(1).replace(".", ",")} M`;
  return Math.round(num).toLocaleString("fr-FR");
}

export function formatTendance(current: number, previous: number): { text: string; positive: boolean } {
  if (previous == null || previous === 0 || isNaN(Number(previous))) return { text: "", positive: true };
  if (current == null || isNaN(Number(current))) return { text: "", positive: true };
  const diff = ((current - previous) / previous) * 100;
  const rounded = Math.round(diff);
  if (rounded === 0) return { text: "Stable", positive: true };
  return { text: `${rounded > 0 ? "+" : ""}${rounded}%`, positive: rounded >= 0 };
}

/** Capitalize chaque mot d'un nom (DUPONT JEAN → Dupont Jean) */
export function capitalizeName(name: string | null | undefined): string {
  if (!name) return "";
  return name.toLowerCase().replace(/(^|\s|-)(\S)/g, (_, sep, char) => sep + char.toUpperCase());
}
