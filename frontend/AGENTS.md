<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

# Conventions Leadrive frontend

## Navigation interne : TOUJOURS utiliser `<Link>` de `next/link`

**Ne JAMAIS utiliser `<a href="/route">`** pour des routes internes. Cela force un rechargement complet de la page (perte du state, re-fetch de tout le bundle, flash blanc).

```tsx
// ❌ MAUVAIS — full page reload
<a href="/leads">Voir les leads</a>
<a href={`/leads/${id}`}>Detail</a>

// ✅ BON — navigation client-side instantanee
import Link from "next/link";
<Link href="/leads">Voir les leads</Link>
<Link href={`/leads/${id}`}>Detail</Link>
```

**Quand garder `<a href>` legitimement** :
- Liens externes (`http://...`, `mailto:`, `tel:`)
- Ancres dans la page courante (`href="#section"`)
- Telechargements (`download` attribute)

## Composants UI partages

Avant de creer du HTML inline, verifier les composants reutilisables dans `src/components/common/` :
- `Button`, `Alert`, `Card`, `KpiCard`, `Modal`, `Input`, `Skeleton`
- `ProgressionBadge` : statut lead avec barre 4 segments + tooltip
- `ScoringGauge` : jauge circulaire scoring/hotnessScore + tooltip
- `Tooltip`, `InfoIcon` : tooltips et helpers d'aide contextuelle
- `StatutBadge` : badges generiques de statut

## Tooltips d'aide

Pour clarifier des concepts metier (statuts, scoring, KPIs, modes), ajouter `<InfoIcon>...</InfoIcon>` ou `<Tooltip content={...}>` plutot que d'allonger les libelles. Plusieurs tooltips valent mieux qu'aucun.

## Modales

Toujours utiliser `<Modal>` de `common/Modal.tsx`. Il rend dans un Portal pour eviter les soucis de containing block (transforms, overflow). Ne pas re-implementer une modal a la main.

## CSS : coherence stricte avec le design system

Objectif : UI de qualite professionnelle, zero duplication, design system unique.

### Variables CSS du theme (utiliser AVANT toute couleur)

Definies dans `frontend/src/app/globals.css` via `@theme inline` + light/dark mode :

**Couleurs semantiques** (utilisables comme classes Tailwind avec opacite `/10`, `/20`, etc.) :
- `accent` (bleu Leadrive) : `bg-accent`, `bg-accent/10`, `text-accent`, `border-accent/30`
- `success` (vert) : `bg-success`, `bg-success/10`, `text-success`
- `warning` (orange) : `bg-warning`, `bg-warning/10`, `text-warning`
- `danger` (rouge) : `bg-danger`, `bg-danger/10`, `text-danger`

**Couleurs neutres** (uniquement via `var(--...)`) :
- `bg-[var(--background)]`, `bg-[var(--card)]`, `bg-[var(--muted)]`
- `border-[var(--border)]`
- `text-[var(--foreground)]`, `text-[var(--color-lr-text-secondary)]`, `text-[var(--color-lr-text-tertiary)]`

### INTERDIT : couleurs Tailwind hardcodees

```tsx
// ❌ MAUVAIS
<span className="bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30">RDV</span>
<button className="bg-red-600 hover:bg-red-700">Supprimer</button>
<div className="text-zinc-600 dark:text-zinc-400">...</div>

// ✅ BON
<span className="bg-success/10 text-success">RDV</span>
<button className="bg-danger hover:opacity-90">Supprimer</button>
<div className="text-[var(--color-lr-text-secondary)]">...</div>
```

**Mapping de migration** : emerald/green → success, amber/yellow → warning, red/rose → danger, blue/indigo/cyan/sky → accent, zinc/gray/slate → `--color-lr-text-*` ou `--muted`/`--border`.

### Pas de duplication

Avant de coder un composant visuel inline, **verifier dans `src/components/common/`** :
- `Button`, `Alert`, `Card`, `KpiCard`, `Modal`, `Input`, `Skeleton`
- `ProgressionBadge`, `ScoringGauge`, `StatutBadge`, `Tooltip`, `InfoIcon`, `PageHeader`

Si tu repetes 2+ fois un meme pattern (badge custom, alerte inline, bouton specifique avec styles repetes), **factorise dans `common/`**.

### Spacing standardise

- `p-3` : cards sm (Kanban, items compacts)
- `p-4` : cards md (default)
- `p-6` : cards lg (sections principales)
- `gap-1.5` (entre badges/icones), `gap-2` (items inline), `gap-3` (sections)

Ne pas inventer `p-5`, `p-7`, `gap-2.5` sauf justification explicite.

### Typo standardisee

- Titres section : `text-base font-semibold text-[var(--foreground)]`
- Sous-titres : `text-sm text-[var(--color-lr-text-secondary)]`
- Meta/timestamps : `text-xs text-[var(--color-lr-text-tertiary)]`
- Labels formulaires : `text-sm font-medium text-[var(--foreground)]`
- Bannieres : `text-sm font-semibold` (titre) + `font-normal` (sous-texte)

### Exception : bannieres full-width

Style `AlerteRappelBanner.tsx` / `WhatsAppBanner.tsx` / `HandoffAlert.tsx` (full-width, `border-b`, sans `rounded`) peuvent utiliser la palette Tailwind explicite (`bg-amber-50`, `bg-red-50`) pour leur impact visuel, **mais doivent rester coherents entre eux** (meme structure `bg-X-50 / border-X-200 / text-X-800` light, `dark:bg-X-950/30 / dark:border-X-900/50 / dark:text-X-300`).

### Exception : palette categorielle des statuts leads

`src/lib/statut-colors.ts` definit 5 couleurs categorielles distinctes pour les 5 statuts du kanban (NOUVEAU=indigo, CONTACTE=amber, EN_COURS=emerald, RDV_PRIS=cyan, TERMINE=zinc). Les 4 couleurs semantiques (`accent/success/warning/danger`) sont insuffisantes pour distinguer 5+ categories cote a cote dans le kanban — la palette categorielle est conservee pour la lisibilite. **Ne JAMAIS dupliquer ces classes Tailwind ailleurs** : pour afficher un statut, toujours importer `getStatutBadgeStyle()` ou utiliser le composant `<StatutBadge>`. La page `/aide` documente ces couleurs et reste alignee avec `statut-colors.ts`.

## Pas d'em-dashes (—) dans les textes français

L'em-dash `—` est une **signature classique d'IA en français**. En France, il ne s'utilise quasiment jamais à l'écrit en prose. Le voir partout dans une app crie "rédigé par ChatGPT/Claude".

```tsx
// ❌ MAUVAIS — signature IA visible
<p>Contactez-nous — on répond sous 24h.</p>
<p>Le manager — qui gère l'équipe — peut configurer l'IA.</p>

// ✅ BON — ponctuation française naturelle
<p>Contactez-nous. Réponse sous 24h.</p>
<p>Le manager (qui gère l'équipe) peut configurer l'IA.</p>
<p>Le manager, qui gère l'équipe, peut configurer l'IA.</p>
```

**Règle** : zéro em-dash (`—`) dans les textes UI ou les prompts IA. Remplacer par :
- **virgule** (`,`) — pour les incises courtes
- **point** (`.`) — pour les phrases successives
- **deux-points** (`:`) — pour une explication / précision
- **parenthèses** (`( )`) — pour une apposition discrète
- **point-virgule** (`;`) — pour une transition entre deux idées proches

**Exception légitime** : `—` comme placeholder de cellule vide dans un tableau (équivalent à "n/a"). Là c'est un usage typographique standard, pas de la prose.

## JSX : espaces entre balises inline et texte

JSX/React **peut absorber les espaces** entre une balise inline (`<strong>`, `<a>`, `<em>`, `<code>`) et le texte qui suit, particulierement quand le texte est sur la ligne suivante (le whitespace en fin de ligne est trim). Symptome visible : `d'aideen bas`, `compte Metaà`, `cliquerici`.

```tsx
// ❌ MAUVAIS — espace pas garanti (vu en production : "d'aideen bas")
<p>
  Cliquez sur le <strong>chatbot d&apos;aide</strong>
  en bas a droite pour discuter.
</p>

// ❌ MAUVAIS aussi sur meme ligne dans certaines compilations
<p>
  Le <strong>chatbot d&apos;aide</strong> en bas a droite est dispo.
</p>

// ✅ BON — {" "} explicite garantit l'espace
<p>
  Cliquez sur le <strong>chatbot d&apos;aide</strong>
  {" "}en bas a droite pour discuter.
</p>

// ✅ BON aussi
<p>
  Le <strong>chatbot d&apos;aide</strong>{" "}en bas a droite est dispo.
</p>
```

**Regle** : des qu'on a une balise inline (`<strong>`, `<a>`, `<em>`, `<code>`) suivie d'un mot, ajouter `{" "}` explicite si le contenu se prolonge sur la ligne suivante. En cas de doute, ajouter `{" "}` — c'est inoffensif et garantit le rendu.

## Maintenir l'aide a jour (TRES IMPORTANT)

L'app embarque un assistant IA d'aide (bouton `?` flottant cf `components/help/HelpBubble.tsx` + page `/aide` cf `app/aide/page.tsx`). **Cette aide doit toujours refleter l'etat reel de l'app**.

A chaque evolution UI/fonctionnelle (nouvelle page, nouveau statut, renommage de bouton, suppression de feature) :

1. **Met a jour `frontend/src/app/aide/page.tsx`** : section concernee dans `SECTIONS`, FAQ si necessaire.
2. **Met a jour `backend/src/main/resources/prompts/aide-app.txt`** : c'est le system prompt du chat IA d'aide. Ajoute / modifie / retire la section concernee. Verifie que les routes et noms de boutons cites correspondent au code.
3. **Met a jour `docs/FONCTIONNALITES_MVP.md`** : liste maitre des fonctionnalites livrees.

Sans ces 3 mises a jour, l'aide deviendra fausse et donnera de mauvaises reponses aux utilisateurs. C'est une partie integrante du Definition of Done.
