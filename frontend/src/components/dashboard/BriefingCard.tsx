"use client";


// ============================================================================
// BRIEFING QUOTIDIEN, Epic 10.1 design refonte
// 4 zones : header / resume / 3 cartes (hier, priorites, insight) / footer
// ============================================================================

interface BriefingData {
  id: number | null;
  dateBriefing: string;
  contenu: string | null;
  tokensPrompt: number;
  tokensReponse: number;
  coutEstime: number;
  creeLe: string;
  /** Si non-null : generation IA en echec, afficher le message au lieu du contenu. */
  erreurGeneration?: string | null;
}

interface BriefingCardProps {
  briefing: BriefingData | null;
  loading: boolean;
  onRegenerate: () => void;
  isAdmin: boolean;
  prenomVendeur?: string;
  hideHeader?: boolean;
}

// ── Parsing structure [SECTION] ─────────────────────────────────────────────

interface BriefingParsed {
  resume: string;
  hier: string;
  priorites: { intro: string; prospects: { nom: string; vehicule: string }[] };
  insight: { chiffreCle: string; analyse: string };
  feedbackInsights: string;
}

function parseBriefing(contenu: string): BriefingParsed {
  const result: BriefingParsed = {
    resume: "",
    hier: "",
    priorites: { intro: "", prospects: [] },
    insight: { chiffreCle: "", analyse: "" },
    feedbackInsights: "",
  };

  const sectionPattern = /\[(RESUME|HIER|PRIORITES|INSIGHT|FEEDBACK_INSIGHTS)\]/gi;
  const parts: { key: string; body: string }[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let lastKey = "";

  while ((match = sectionPattern.exec(contenu)) !== null) {
    if (lastKey) {
      parts.push({ key: lastKey, body: contenu.slice(lastIndex, match.index).trim() });
    }
    lastKey = match[1].toUpperCase();
    lastIndex = match.index + match[0].length;
  }
  if (lastKey) {
    parts.push({ key: lastKey, body: contenu.slice(lastIndex).trim() });
  }

  // Fallback : pas de marqueurs → tout dans resume (ancien format)
  if (parts.length === 0) {
    result.resume = contenu.replace(/\*\*/g, "").replace(/^#{1,3}\s+(.+)$/gm, "$1").trim();
    return result;
  }

  for (const p of parts) {
    const clean = p.body.replace(/\*\*/g, "").trim();
    switch (p.key) {
      case "RESUME":
        result.resume = clean;
        break;
      case "HIER":
        result.hier = clean;
        break;
      case "PRIORITES": {
        const lines = clean.split("\n").filter(Boolean);
        result.priorites.intro = lines[0] || "";
        for (let i = 1; i < lines.length; i++) {
          const parts2 = lines[i].split("|").map((s) => s.trim());
          if (parts2.length >= 2) {
            result.priorites.prospects.push({ nom: parts2[0], vehicule: parts2[1] });
          } else if (parts2[0]) {
            result.priorites.prospects.push({ nom: parts2[0], vehicule: "" });
          }
        }
        break;
      }
      case "INSIGHT": {
        const lines = clean.split("\n").filter(Boolean);
        result.insight.chiffreCle = lines[0] || "";
        result.insight.analyse = lines.slice(1).join(" ").trim();
        break;
      }
      case "FEEDBACK_INSIGHTS":
        result.feedbackInsights = clean;
        break;
    }
  }

  return result;
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function formatDateBriefing(dateStr: string): string {
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
}

function formatTime(dateStr: string): string {
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return "";
  return d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function renderBold(text: string): string {
  return escapeHtml(text).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
}

// ── Composant ───────────────────────────────────────────────────────────────

export default function BriefingCard({ briefing, loading, onRegenerate, isAdmin, prenomVendeur, hideHeader }: BriefingCardProps) {
  if (loading) {
    return (
      <div className="overflow-hidden rounded-xl border border-[var(--border)] bg-[var(--card)] animate-fade-in">
        <div className="px-6 py-4 border-b border-[var(--border)]">
          <div className="h-5 w-64 rounded bg-[var(--muted)] animate-shimmer" />
        </div>
        <div className="px-6 py-5 space-y-3">
          <div className="h-4 w-full rounded bg-[var(--muted)] animate-shimmer" />
          <div className="h-4 w-11/12 rounded bg-[var(--muted)] animate-shimmer" />
          <div className="h-4 w-9/12 rounded bg-[var(--muted)] animate-shimmer" />
        </div>
      </div>
    );
  }

  if (!briefing) {
    return (
      <div className="overflow-hidden animate-fade-in rounded-xl border border-[var(--border)] bg-[var(--card)]">
        <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
            Briefing
          </p>
          <p className="mt-2 font-display text-[16px] font-semibold tracking-display-tight text-[var(--foreground)]">
            Pas encore de briefing pour aujourd&apos;hui.
          </p>
          <p className="mt-1.5 max-w-sm text-[13px] text-[var(--color-lr-text-secondary)]">
            Votre premier briefing du jour sera généré automatiquement à l&apos;ouverture du dashboard.
          </p>
          <button
            onClick={onRegenerate}
            className="btn-primary mt-5 rounded-lg px-5 py-2 text-[13px] font-semibold"
          >
            Générer maintenant
          </button>
        </div>
      </div>
    );
  }

  // Fallback gracieux : la generation IA a echoue (Anthropic down, circuit open).
  // Plutot que de masquer ou de planter le dashboard, on affiche un encart clair
  // avec un bouton "Reessayer" qui declenche onRegenerate.
  if (briefing.erreurGeneration) {
    return (
      <div className={`overflow-hidden animate-fade-in ${hideHeader ? "" : "rounded-xl border border-[var(--border)] bg-[var(--card)]"}`}>
        <div className="flex flex-col items-center justify-center px-6 py-10 text-center">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-warning">
            Briefing indisponible
          </p>
          <p className="mt-2 font-display text-[15px] font-semibold tracking-display-tight text-[var(--foreground)]">
            La génération IA a échoué.
          </p>
          <p className="mt-1.5 max-w-md text-[13px] text-[var(--color-lr-text-secondary)]">
            {briefing.erreurGeneration}
          </p>
          <button
            onClick={onRegenerate}
            className="btn-primary mt-5 rounded-lg px-5 py-2 text-[13px] font-semibold"
          >
            Réessayer
          </button>
        </div>
      </div>
    );
  }

  const parsed = parseBriefing(briefing.contenu ?? "");
  const hasStructured = parsed.hier || parsed.priorites.prospects.length > 0 || parsed.insight.chiffreCle;

  return (
    <div className={`overflow-hidden animate-fade-in ${hideHeader ? "" : "card-leadrive"}`}>

      {/* Zone 1, Header (caché si le parent gère). Editorial : eyebrow + display. */}
      {!hideHeader && (
        <div className="flex items-center justify-between border-b border-[var(--border)] px-6 py-4">
          <div>
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
              Briefing{prenomVendeur && <> · {prenomVendeur}</>}
            </p>
            <h2 className="mt-0.5 font-display text-[15px] font-bold tracking-display-tight text-[var(--foreground)]">
              {formatDateBriefing(briefing.dateBriefing)}
            </h2>
          </div>
          {isAdmin && (
            <button
              onClick={onRegenerate}
              className="rounded-lg p-1.5 text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
              title="Régénérer le briefing"
              aria-label="Régénérer le briefing"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          )}
        </div>
      )}

      {/* Zone 2, Resume (uniquement si format structure). Border full warning,
          pas de side-stripe (banni). */}
      {parsed.resume && hasStructured && (
        <div className="border-b border-warning/30 bg-warning/5 px-6 py-4">
          <p className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-warning">Résumé du jour</p>
          <p
            className="text-sm leading-relaxed text-[var(--foreground)]"
            dangerouslySetInnerHTML={{ __html: renderBold(parsed.resume) }}
          />
        </div>
      )}

      {/* Zone 3, Asymétrie 7/5 + bandeau Insight full-width.
          Refonte critique : on tue les 3 cards icônes colorées identiques
          (DESIGN.md "Don't" §6 + One Attention Rule : ember = seule couleur d'attention).
          Priorités porte l'attention (ember-subtle bg + eyebrow accent),
          Hier = contexte passé en bloc large, Insight = analyse muted en bandeau. */}
      {hasStructured && (
        <div className="grid grid-cols-1 gap-4 px-6 py-5 lg:grid-cols-12">

          {/* Hier — bloc large gauche (7/12) */}
          <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-5 lg:col-span-7">
            <p className="eyebrow-muted">Faits marquants d&apos;hier</p>
            <p className="mt-2 text-[14px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              {parsed.hier || "Rien de notable hier, c'est l'occasion de repartir fort aujourd'hui."}
            </p>
          </div>

          {/* Priorités — focus ember (5/12) */}
          <div className="rounded-xl border border-[var(--color-accent)]/30 bg-[var(--color-accent-subtle)] p-5 lg:col-span-5">
            <p className="eyebrow">Priorités aujourd&apos;hui</p>
            {parsed.priorites.intro && (
              <p className="mt-2 text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)]">{parsed.priorites.intro}</p>
            )}
            <div className="mt-3 flex flex-wrap gap-1.5">
              {parsed.priorites.prospects.length > 0 ? (
                parsed.priorites.prospects.map((p, i) => (
                  <span key={i} className="inline-flex items-center gap-1.5 rounded-full border border-[var(--border)] bg-[var(--card)] px-2.5 py-1 text-[12px]">
                    <span className="font-medium text-[var(--foreground)]">{p.nom}</span>
                    {p.vehicule && <span className="text-[var(--color-lr-text-tertiary)]">{p.vehicule}</span>}
                  </span>
                ))
              ) : (
                <p className="text-[13px] italic text-[var(--color-lr-text-tertiary)]">Pas de priorité brûlante aujourd&apos;hui, pipeline calme.</p>
              )}
            </div>
          </div>

          {/* Insight — bandeau full-width sous les deux (12/12) */}
          <div className="rounded-xl border border-[var(--border)] bg-[var(--muted)]/40 p-5 lg:col-span-12">
            <p className="eyebrow-muted">Insight de la semaine</p>
            {parsed.insight.chiffreCle && (
              <p className="mt-2 font-display text-[18px] font-bold leading-snug tracking-display-tight text-[var(--color-accent-deep)]">
                {parsed.insight.chiffreCle}
              </p>
            )}
            <p className={`text-[14px] leading-relaxed text-[var(--color-lr-text-secondary)] ${parsed.insight.chiffreCle ? "mt-1" : "mt-2"}`}>
              {parsed.insight.analyse || "Les données de la semaine seront analysées prochainement."}
            </p>
          </div>
        </div>
      )}

      {/* Fallback ancien format (pas de cartes) */}
      {!hasStructured && parsed.resume && !parsed.hier && (
        <div className="px-6 py-5">
          <p
            className="text-sm leading-relaxed text-[var(--foreground)] whitespace-pre-line"
            dangerouslySetInnerHTML={{ __html: renderBold(parsed.resume) }}
          />
        </div>
      )}

      {/* Section bonus, Insights des retours vendeurs (n'apparait que si feedbacks) */}
      {parsed.feedbackInsights && (
        <div className="border-t border-[var(--color-accent)]/30 bg-[var(--color-accent-subtle)] px-6 py-4">
          <p className="mb-2 flex items-center gap-2 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-accent)]">
            <svg className="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
            </svg>
            Insights de vos retours
          </p>
          <p
            className="text-sm leading-relaxed text-[var(--foreground)]"
            dangerouslySetInnerHTML={{ __html: renderBold(parsed.feedbackInsights) }}
          />
        </div>
      )}

      {/* Zone 4, Footer */}
      <div className="flex items-center justify-between border-t border-[var(--border)] bg-[var(--muted)] px-6 py-3">
        <p className="text-xs text-[var(--color-lr-text-tertiary)]">
          Généré à {formatTime(briefing.creeLe)}
        </p>
        <p className="text-xs text-[var(--color-lr-text-tertiary)]">A vous de jouer</p>
      </div>
    </div>
  );
}

export type { BriefingData };
