"use client";

// ============================================================================
// FICHE DE PREPARATION IA, Epic 10 design refonte
// Layout 4 zones : header lead / 2 colonnes / analyse IA / footer
// ============================================================================

interface Lead {
  idLead: number;
  nom: string;
  email: string;
  telephone: string;
  marque: string;
  modele: string;
  territoire: string;
  statut: string;
  scoring: number;
  source: string;
  etape: string;
  devis: string;
  reprise: boolean;
  typeOpportunite: string;
  creeLe: string;
}

interface FichePreparationCardProps {
  ficheRaw: string;
  lead: Lead;
  vendeurNom?: string;
}

// ── Parsing (inchange) ──────────────────────────────────────────────────────

interface SubBlock { title: string; text: string; }

interface FicheSectionData {
  kind: "accroche" | "reformuler" | "decouvrir" | "analyse" | "strategie" | "generic";
  title: string;
  subBlocks: SubBlock[];
  items: string[];
  text: string;
}

// Mots-cles indiquant qu'un h3 est en realite un sous-bloc de "REFORMULER"
// (et pas une section top-level). Si l'IA omet le wrapper "## BLOC 1", on les regroupe.
const REFORMULER_SUBBLOCK_KEYWORDS = /PROJET|D[EÉ]LAI|REPRISE|BUDGET|FINANCEMENT|APPORT/i;

function classifyTitle(title: string):
  | "accroche" | "reformuler" | "decouvrir" | "strategie" | "analyse"
  | "reformuler-sub" | "generic" {
  const upper = title.toUpperCase();
  if (upper.includes("ACCROCHE")) return "accroche";
  if (upper.includes("REFORMULER") || upper.includes("BLOC 1")) return "reformuler";
  if (upper.includes("DECOUVRIR") || upper.includes("DÉCOUVRIR") || upper.includes("BLOC 2")) return "decouvrir";
  if (upper.includes("STRATEG") || upper.includes("STRATÉG")) return "strategie";
  if (upper.includes("ANALYSE")) return "analyse";
  if (REFORMULER_SUBBLOCK_KEYWORDS.test(upper)) return "reformuler-sub";
  return "generic";
}

function parseFiche(raw: string): FicheSectionData[] {
  type Block = { title: string; body: string; level: 2 | 3 | 0 };
  const blocks: Block[] = [];
  const lines = raw.split("\n");
  let currentTitle = "";
  let currentLevel: 2 | 3 | 0 = 0;
  let currentBody: string[] = [];

  const flush = () => {
    if (currentTitle) blocks.push({ title: currentTitle, body: currentBody.join("\n").trim(), level: currentLevel });
  };

  // On accepte h2, h3 et lignes en **bold-only** comme separateurs de section.
  // Le niveau (2 vs 3) est conserve pour pouvoir distinguer top-level vs sous-bloc.
  for (const line of lines) {
    const h2 = line.match(/^##\s+(.+?)\s*$/);
    const h3 = line.match(/^###\s+(.+?)\s*$/);
    const bold = line.match(/^\*\*(.+?)\*\*\s*$/);
    if (h2 || h3 || bold) {
      flush();
      currentTitle = (h2 ? h2[1] : h3 ? h3[1] : bold![1]).trim();
      currentLevel = h2 ? 2 : h3 ? 3 : 0;
      currentBody = [];
    } else {
      currentBody.push(line);
    }
  }
  flush();

  if (blocks.length === 0) {
    return [{ kind: "generic", title: "Fiche", subBlocks: [], items: [], text: raw }];
  }

  // Cas 1 : l'IA a genere "## BLOC 1 - REFORMULER" (h2) puis les "### Projet", "### Reprise" (h3)
  // comme blocs separes. Le BLOC 1 a un body vide, et les h3 sub sont parses comme blocs
  // independants. On absorbe les reformuler-sub qui suivent un reformuler-parent dans son body.
  const reformulerIndex = blocks.findIndex((b) => classifyTitle(b.title) === "reformuler");
  if (reformulerIndex !== -1) {
    const subToAbsorb: number[] = [];
    for (let i = reformulerIndex + 1; i < blocks.length; i += 1) {
      const k = classifyTitle(blocks[i].title);
      if (k === "reformuler-sub") subToAbsorb.push(i);
      else break; // on s'arrete des qu'on rencontre un autre top-level
    }
    if (subToAbsorb.length > 0) {
      const absorbed = subToAbsorb
        .map((i) => `### ${blocks[i].title}\n${blocks[i].body}`)
        .join("\n");
      const existingBody = blocks[reformulerIndex].body;
      blocks[reformulerIndex].body = existingBody ? `${existingBody}\n${absorbed}` : absorbed;
      // Retirer les sous-blocs absorbes (en ordre inverse)
      for (let i = subToAbsorb.length - 1; i >= 0; i -= 1) blocks.splice(subToAbsorb[i], 1);
    }
  } else {
    // Cas 2 : pas de wrapper REFORMULER du tout. On regroupe les sous-blocs orphelins
    // (Projet, Reprise, Budget, Financement) dans une section reformuler virtuelle.
    const subIndices: number[] = [];
    blocks.forEach((b, i) => {
      if (b.level === 3 && classifyTitle(b.title) === "reformuler-sub") subIndices.push(i);
    });
    if (subIndices.length > 0) {
      const virtualBody = subIndices
        .map((i) => `### ${blocks[i].title}\n${blocks[i].body}`)
        .join("\n");
      const insertAt = subIndices[0];
      for (let i = subIndices.length - 1; i >= 0; i -= 1) blocks.splice(subIndices[i], 1);
      blocks.splice(insertAt, 0, { title: "REFORMULER", body: virtualBody, level: 2 });
    }
  }

  return blocks.map((b) => {
    const kind = classifyTitle(b.title);
    if (kind === "accroche") {
      return { kind: "accroche" as const, title: "Accroche d'appel", subBlocks: [], items: [], text: b.body };
    }
    if (kind === "reformuler") {
      return { kind: "reformuler" as const, title: "Ce que tu vas reformuler", subBlocks: extractSubBlocks(b.body), items: [], text: "" };
    }
    if (kind === "decouvrir") {
      return { kind: "decouvrir" as const, title: "A decouvrir pendant l'appel", subBlocks: [], items: extractListItems(b.body), text: "" };
    }
    if (kind === "strategie") {
      return { kind: "strategie" as const, title: "Stratégie", subBlocks: [], items: [], text: b.body };
    }
    if (kind === "analyse") {
      return { kind: "analyse" as const, title: "Analyse IA", subBlocks: [], items: [], text: b.body };
    }
    return { kind: "generic" as const, title: b.title, subBlocks: [], items: [], text: b.body };
  });
}

function extractSubBlocks(body: string): SubBlock[] {
  const subs: SubBlock[] = [];
  const lines = body.split("\n");
  let title = "";
  let buf: string[] = [];
  const flush = () => {
    const text = buf.join(" ").replace(/\s+/g, " ").trim();
    if (title || text) subs.push({ title: title.trim(), text });
  };
  for (const ln of lines) {
    const h3 = ln.match(/^###\s+(.+?)\s*$/);
    if (h3) { flush(); title = h3[1]; buf = []; }
    else if (ln.trim()) { buf.push(ln.trim()); }
  }
  flush();
  if (subs.length === 0 && body.trim()) return [{ title: "", text: body.trim() }];
  return subs;
}

function extractListItems(body: string): string[] {
  const items: string[] = [];
  const lines = body.split("\n");
  let buffer = "";
  for (const ln of lines) {
    const stripped = ln.trim();
    const m = stripped.match(/^[-*\u2022]\s+(.+)$/) || stripped.match(/^\d+[.)]\s+(.+)$/);
    if (m) { if (buffer) items.push(buffer.trim()); buffer = m[1]; }
    else if (stripped && buffer) { buffer += " " + stripped; }
    else if (stripped) { buffer = stripped; }
  }
  if (buffer.trim()) items.push(buffer.trim());
  if (items.length === 0 && body.trim()) return body.split(/(?<=[.!?])\s+/).map((s) => s.trim()).filter(Boolean);
  return items;
}

interface StrategieItem {
  type: "commencer" | "levier" | "attention" | "other";
  label: string;
  text: string;
}

function parseStrategieBlocks(body: string): StrategieItem[] {
  // L'IA peut generer le texte sur une seule ligne : "*COMMENCER PAR : ... *LEVIER : ..."
  // ou sur plusieurs lignes avec - ou ** comme prefixe. On normalise d'abord en splitant
  // sur les marqueurs COMMENCER PAR / LEVIER / ATTENTION meme s'ils sont en milieu de ligne.
  const normalized = body
    .replace(/\*\*/g, "")
    .replace(/\n/g, " ")
    .replace(/\s+/g, " ")
    // Inserer un separateur avant chaque marqueur (meme en milieu de ligne)
    .replace(/[*\-•]\s*COMMENCER PAR/gi, "\n COMMENCER PAR")
    .replace(/[*\-•]\s*LEVIER/gi, "\n LEVIER")
    .replace(/[*\-•]\s*ATTENTION/gi, "\n ATTENTION")
    // Aussi sans prefixe * ou -
    .replace(/(?:^|\.\s+)COMMENCER PAR/gi, "\n COMMENCER PAR")
    .replace(/(?:^|\.\s+)LEVIER/gi, "\n LEVIER")
    .replace(/(?:^|\.\s+)ATTENTION/gi, "\n ATTENTION");

  const items: StrategieItem[] = [];
  const lines = normalized.split("\n").map(l => l.trim()).filter(Boolean);

  for (const line of lines) {
    const clean = line.replace(/^[-*•]\s*/, "");
    const upper = clean.toUpperCase();
    if (upper.startsWith("COMMENCER PAR")) {
      items.push({ type: "commencer", label: "Commencer par", text: clean.replace(/^COMMENCER PAR\s*:?\s*/i, "").trim() });
    } else if (upper.startsWith("LEVIER")) {
      items.push({ type: "levier", label: "Levier", text: clean.replace(/^LEVIER\s*:?\s*/i, "").trim() });
    } else if (upper.startsWith("ATTENTION")) {
      items.push({ type: "attention", label: "Attention", text: clean.replace(/^ATTENTION\s*:?\s*/i, "").trim() });
    } else if (clean) {
      items.push({ type: "other", label: "", text: clean });
    }
  }

  // Si le parser n'a rien trouve de structure, fallback sur le texte brut
  if (items.length === 0 && body.trim()) {
    items.push({ type: "other", label: "Stratégie", text: body.replace(/\*\*/g, "").trim() });
  }

  return items;
}

function hasBlockingMarker(text: string): boolean {
  const t = text.toLowerCase();
  return t.includes("point_bloquant") || t.includes("point bloquant") || t.includes("conditionne l'achat") || t.includes("a traiter en priorite");
}

function cleanText(text: string): string {
  return text.replace(/\bPOINT_BLOQUANT\b\.?/gi, "").replace(/\*\*/g, "").replace(/\s+/g, " ").trim();
}

// ── Couleurs label par type de sous-bloc (eyebrow color, plus de side-stripe) ─

function getSubBlockTone(title: string): string {
  const t = title.toUpperCase();
  if (t.includes("PROJET") || t.includes("DELAI") || t.includes("DÉLAI") || t.includes("FINANCEMENT")) {
    return "text-[var(--color-accent)]";
  }
  if (t.includes("REPRISE")) return "text-warning";
  if (t.includes("BUDGET")) return "text-success";
  return "text-[var(--color-lr-text-tertiary)]";
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function formatDateRelative(dateStr: string | null): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return "";
  const now = new Date();
  const diffDays = Math.floor((now.getTime() - d.getTime()) / 86400000);
  if (diffDays === 0) return `aujourd'hui a ${d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" })}`;
  if (diffDays === 1) return "hier";
  if (diffDays < 7) return `il y a ${diffDays} jours`;
  return d.toLocaleDateString("fr-FR", { day: "2-digit", month: "short" });
}

// ── Composant principal ─────────────────────────────────────────────────────

export default function FichePreparationCard({ ficheRaw, lead, vendeurNom }: FichePreparationCardProps) {
  const sections = parseFiche(ficheRaw);
  const accroche = sections.find((s) => s.kind === "accroche");
  const reformuler = sections.find((s) => s.kind === "reformuler");
  const decouvrir = sections.find((s) => s.kind === "decouvrir");
  const strategie = sections.find((s) => s.kind === "strategie");
  const analyse = sections.find((s) => s.kind === "analyse");
  const generics = sections.filter((s) => s.kind === "generic");
  const vehicule = [lead.marque, lead.modele].filter(Boolean).join(" ");

  return (
    <article className="overflow-hidden rounded-xl border border-[var(--border)] bg-[var(--card)] animate-fade-in">

      {/* ═════ ZONE 1 : Header lead — DÉDUPLIQUÉ vs identity header de la page.
            Avant : nom + initiales + véhicule chip + score badge réapparaissaient
            ici, alors qu'ils sont déjà 200px au-dessus dans la fiche prospect.
            Audit impeccable P1 : "le user vient de lire ces infos juste au-dessus".
            On garde uniquement source + date relative (info qui n'est PAS dans
            le hero) + chip "Reprise" critique (signalement métier). ═════════ */}
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--border)] bg-[var(--muted)]/40 px-6 py-3">
        <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
          Lead {lead.source || "direct"} · Reçu {formatDateRelative(lead.creeLe)}
        </p>
        {lead.reprise && (
          <span className="font-mono inline-flex items-center rounded-full bg-warning/12 px-2.5 py-1 text-[10.5px] font-semibold uppercase tracking-[0.14em] text-warning">
            Reprise
          </span>
        )}
      </header>

      {/* ═════ ACCROCHE D'APPEL, citation editoriale compacte ════════════════ */}
      {accroche && accroche.text && (
        <section className="relative border-b border-[var(--border)] bg-[var(--color-accent-subtle)] px-6 py-5">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
            Accroche d&apos;appel
          </p>
          <blockquote className="mt-2 font-display text-[16px] font-medium italic leading-snug tracking-[-0.01em] text-[var(--foreground)] sm:text-[17px]">
            «&nbsp;{cleanText(accroche.text)}&nbsp;»
          </blockquote>
        </section>
      )}

      {/* ═════ ZONE 2 : 2 colonnes, Reformuler / À découvrir ════════════════ */}
      <div className="grid grid-cols-1 gap-0 lg:grid-cols-2">

        {/* Reformuler : sous-blocs identifies par eyebrow tonalite (plus de side-stripe) */}
        <section className="p-6">
          <p className="mb-4 font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-success">
            Ce que vous allez reformuler
          </p>
          {reformuler ? (
            <ol className="stagger-children space-y-4">
              {reformuler.subBlocks.map((sb, i) => {
                const cleaned = cleanText(sb.text);
                const blocking = hasBlockingMarker(sb.text);
                const toneClass = getSubBlockTone(sb.title);
                return (
                  <li key={i} className="relative pl-8">
                    <span className="absolute left-0 top-0.5 inline-flex h-6 w-6 items-center justify-center rounded-full bg-[var(--muted)] font-mono text-[10px] font-semibold text-[var(--color-lr-text-secondary)]">
                      {String(i + 1).padStart(2, "0")}
                    </span>
                    {sb.title && (
                      <p className={`font-mono text-[10px] font-semibold uppercase tracking-[0.18em] ${toneClass}`}>
                        {sb.title}
                      </p>
                    )}
                    <p className="mt-1 text-[13.5px] leading-relaxed text-[var(--foreground)]">
                      {cleaned}
                    </p>
                    {blocking && (
                      <p className="mt-1.5 inline-flex items-center gap-1.5 rounded-full bg-danger/10 px-2 py-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-danger">
                        <span className="h-1.5 w-1.5 rounded-full bg-danger" /> Point bloquant
                      </p>
                    )}
                  </li>
                );
              })}
            </ol>
          ) : (
            <p className="text-[13.5px] italic text-[var(--color-lr-text-tertiary)]">Aucune information à reformuler pour ce prospect.</p>
          )}
        </section>

        {/* À découvrir : checklist numerotee */}
        <section className="border-t border-[var(--border)] p-6 lg:border-l lg:border-t-0">
          <p className="mb-4 font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-warning">
            Ce qu&apos;il reste à découvrir
          </p>
          {decouvrir && decouvrir.items.length > 0 ? (
            <ol className="stagger-children space-y-4">
              {decouvrir.items.map((item, i) => {
                const cleaned = cleanText(item);
                const parts = cleaned.split(/:\s+/);
                const head = parts.length > 1 ? parts[0].replace(/\*\*/g, "") : cleaned;
                const tail = parts.length > 1 ? parts.slice(1).join(": ") : "";
                return (
                  <li key={i} className="relative pl-8">
                    <span className="absolute left-0 top-0.5 inline-flex h-6 w-6 items-center justify-center rounded-full bg-warning/15 font-mono text-[10px] font-semibold text-warning">
                      {String(i + 1).padStart(2, "0")}
                    </span>
                    <p className="text-[13.5px] font-semibold text-[var(--foreground)]">{head}</p>
                    {tail && (
                      <p className="mt-1 text-[12.5px] leading-relaxed text-[var(--color-lr-text-secondary)]">{tail}</p>
                    )}
                  </li>
                );
              })}
            </ol>
          ) : (
            <p className="text-[13.5px] italic text-[var(--color-lr-text-tertiary)]">Tous les points sont déjà connus.</p>
          )}
        </section>
      </div>

      {/* ═════ ZONE 3 : Stratégie (ou Analyse IA legacy) ═════════════════════ */}
      {strategie && strategie.text && (() => {
        const blocks = parseStrategieBlocks(strategie.text);
        const STYLE_MAP: Record<string, { icon: string; bg: string; iconBg: string; textColor: string; eyebrow: string }> = {
          commencer: { icon: "01", bg: "bg-[var(--color-accent-subtle)]", iconBg: "bg-[var(--color-accent)]/15 text-[var(--color-accent)]", textColor: "text-[var(--color-accent)]", eyebrow: "Commencer par" },
          levier: { icon: "02", bg: "bg-success/8", iconBg: "bg-success/15 text-success", textColor: "text-success", eyebrow: "Levier" },
          attention: { icon: "!", bg: "bg-danger/8", iconBg: "bg-danger/15 text-danger", textColor: "text-danger", eyebrow: "Attention" },
          other: { icon: "•", bg: "bg-[var(--muted)]/50", iconBg: "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]", textColor: "text-[var(--color-lr-text-secondary)]", eyebrow: "Note" },
        };
        return (
          <section className="border-t border-[var(--border)] px-6 py-5">
            <p className="mb-4 font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
              Stratégie d&apos;appel
            </p>
            <div className="space-y-2.5">
              {blocks.map((block, i) => {
                const style = STYLE_MAP[block.type] || STYLE_MAP.other;
                return (
                  <div key={i} className={`flex items-start gap-3 rounded-lg ${style.bg} px-3.5 py-2.5`}>
                    <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full font-mono text-[10.5px] font-semibold ${style.iconBg}`}>
                      {style.icon}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className={`font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] ${style.textColor}`}>
                        {block.label || style.eyebrow}
                      </p>
                      <p className="mt-0.5 text-[13.5px] leading-relaxed text-[var(--foreground)]">{cleanText(block.text)}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        );
      })()}
      {analyse && analyse.text && !strategie && (
        <section className="border-t border-[var(--border)] bg-warning/5 px-6 py-5">
          <p className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-warning">Analyse IA</p>
          <p className="text-[13.5px] leading-relaxed text-[var(--foreground)]">{cleanText(analyse.text)}</p>
        </section>
      )}

      {/* Fallback generics */}
      {generics.length > 0 && (
        <section className="border-t border-[var(--border)] px-6 py-5">
          {generics.map((g, i) => (
            <div key={i} className="mb-4 last:mb-0">
              <p className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                {g.title}
              </p>
              <p className="whitespace-pre-wrap text-[13.5px] leading-relaxed text-[var(--foreground)]">{g.text}</p>
            </div>
          ))}
        </section>
      )}

      {/* ═════ ZONE 4 : Footer, vendeur qui prépare/passe l'appel ═══════════════
          Source et véhicule sont déjà dans l'en-tête de la fiche prospect : on ne
          les redouble pas ici. La "durée estimée" statique (15-20 min) n'apportait
          aucune information. On ne garde que le vendeur, masqué s'il n'y en a pas.
          ════════════════════════════════════════════════════════════════════ */}
      {vendeurNom && (
        <footer className="flex items-center gap-2 border-t border-[var(--border)] bg-[var(--background)] px-6 py-3">
          <span className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
            Vendeur
          </span>
          <span className="text-[12.5px] font-semibold text-[var(--foreground)]">
            {vendeurNom}
          </span>
        </footer>
      )}
    </article>
  );
}
