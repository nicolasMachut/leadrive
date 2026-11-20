"use client";

import Link from "next/link";
import { ReactNode } from "react";

/**
 * Mini-rendu markdown sans dependance, conçu pour les reponses du chat d'aide.
 * Gere :
 * - **gras** -> <strong>
 * - *italique* -> <em>
 * - `code inline` -> <code>
 * - [texte](url) -> <a> (Link Next.js si url interne)
 * - lignes "- texte" -> bullets
 * - lignes "# texte" -> bold (au cas ou l'IA ne respecte pas le prompt)
 * - sauts de ligne -> <br />
 * - paragraphes (double saut) -> <p>
 *
 * Pas de support pour : tableaux, blocs de code multiligne, images, listes numerotees.
 * Suffit largement pour le besoin.
 */

interface MarkdownLiteProps {
  text: string;
}

// Parse les patterns inline (gras, italique, code, liens) et retourne un tableau de ReactNode
function renderInline(text: string): ReactNode[] {
  // Regex combinee : capture **bold**, *italic*, `code`, [text](url)
  // Ordre important : on traite **double** avant *simple*
  const tokens: ReactNode[] = [];
  // Pattern : [text](url) | **bold** | `code` | *italic*
  const regex = /\[([^\]]+)\]\(([^)]+)\)|\*\*([^*]+)\*\*|`([^`]+)`|\*([^*]+)\*/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let key = 0;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      tokens.push(text.slice(lastIndex, match.index));
    }
    if (match[1] !== undefined && match[2] !== undefined) {
      // Lien [text](url)
      const linkText = match[1];
      const url = match[2];
      const isInternal = url.startsWith("/") && !url.startsWith("//");
      if (isInternal) {
        tokens.push(
          <Link key={key++} href={url} className="text-accent hover:underline">
            {linkText}
          </Link>
        );
      } else {
        tokens.push(
          <a key={key++} href={url} target="_blank" rel="noopener noreferrer" className="text-accent hover:underline">
            {linkText}
          </a>
        );
      }
    } else if (match[3] !== undefined) {
      // Gras
      tokens.push(<strong key={key++} className="font-semibold">{match[3]}</strong>);
    } else if (match[4] !== undefined) {
      // Code inline
      tokens.push(
        <code key={key++} className="rounded bg-[var(--muted)] px-1 py-0.5 text-[11px] font-mono">
          {match[4]}
        </code>
      );
    } else if (match[5] !== undefined) {
      // Italique
      tokens.push(<em key={key++}>{match[5]}</em>);
    }
    lastIndex = regex.lastIndex;
  }
  if (lastIndex < text.length) {
    tokens.push(text.slice(lastIndex));
  }
  return tokens;
}

export default function MarkdownLite({ text }: MarkdownLiteProps) {
  if (!text) return null;

  // Nettoyage prealable : si l'IA a mis des # malgre les instructions, on les transforme en gras
  // Exemple : "## Titre" -> "**Titre**"
  const cleaned = text
    .split("\n")
    .map((line) => {
      const trimmed = line.replace(/^#{1,6}\s+/, "");
      if (trimmed !== line) return `**${trimmed}**`;
      return line;
    })
    .join("\n");

  // Parsing par bloc :
  // - Suite de lignes "- " consecutives -> <ul>
  // - Lignes vides -> separateurs de paragraphes
  // - Sinon -> ligne dans un <p>
  const lines = cleaned.split("\n");
  const blocks: ReactNode[] = [];
  let currentList: string[] = [];
  let currentParagraph: string[] = [];
  let key = 0;

  function flushList() {
    if (currentList.length > 0) {
      blocks.push(
        <ul key={key++} className="list-disc list-inside space-y-0.5 my-1">
          {currentList.map((item, i) => (
            <li key={i}>{renderInline(item)}</li>
          ))}
        </ul>
      );
      currentList = [];
    }
  }

  function flushParagraph() {
    if (currentParagraph.length > 0) {
      const text = currentParagraph.join(" ").trim();
      if (text) {
        blocks.push(
          <p key={key++} className="my-1 first:mt-0 last:mb-0">
            {renderInline(text)}
          </p>
        );
      }
      currentParagraph = [];
    }
  }

  for (const line of lines) {
    const trimmed = line.trim();
    const bulletMatch = trimmed.match(/^[-*]\s+(.+)$/);

    if (bulletMatch) {
      flushParagraph();
      currentList.push(bulletMatch[1]);
    } else if (trimmed === "") {
      flushList();
      flushParagraph();
    } else {
      flushList();
      currentParagraph.push(trimmed);
    }
  }
  flushList();
  flushParagraph();

  return <>{blocks}</>;
}
