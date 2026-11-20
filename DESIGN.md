---
name: Leadrive
description: Atelier automobile français — SaaS B2B d'activation commerciale en palette sand/ink/ember
colors:
  ember: "#D9603A"
  ember-deep: "#A8401F"
  ember-light: "#E07B5A"
  ember-subtle: "#FBEEE6"
  ignition: "#13141A"
  sand: "#F4EFE5"
  paper: "#FBF8F1"
  clay: "#EAE3D2"
  tan: "#D8CFB8"
  ink: "#1A1B23"
  ink-secondary: "#4B4D58"
  ink-tertiary: "#8B8676"
  success: "#0E9F6E"
  success-subtle: "#E6F7F1"
  warning: "#D97706"
  warning-subtle: "#FEF3E2"
  danger: "#E5484D"
  danger-subtle: "#FCEBEC"
  admin-bg: "#0F1014"
  admin-bg-hover: "#1A1B22"
  admin-border: "#2E2E37"
  admin-text: "#FAFAF7"
typography:
  display:
    fontFamily: "Bricolage Grotesque, -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "clamp(28px, 4vw, 34px)"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.035em"
    fontVariation: "opsz 48"
  headline:
    fontFamily: "Bricolage Grotesque, sans-serif"
    fontSize: "18px"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.035em"
  title:
    fontFamily: "Geist, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "-0.01em"
  body:
    fontFamily: "Geist, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  label:
    fontFamily: "JetBrains Mono, ui-monospace, monospace"
    fontSize: "11px"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "0.22em"
  numeric:
    fontFamily: "JetBrains Mono, ui-monospace, monospace"
    fontSize: "44px"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "-0.04em"
    fontFeature: "tnum, ss01"
rounded:
  sm: "6px"
  md: "8px"
  lg: "12px"
  xl: "16px"
  pill: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  xxl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.ember}"
    textColor: "#FFFFFF"
    rounded: "{rounded.md}"
    padding: "8px 16px"
    typography: "{typography.title}"
  button-primary-hover:
    backgroundColor: "{colors.ember-deep}"
  button-secondary:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
  button-ghost:
    textColor: "{colors.ink-secondary}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
  button-danger:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.danger}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
  card:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.lg}"
    padding: "20px"
  card-leadrive:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.lg}"
    padding: "20px"
  input:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "8px 12px"
  modal:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.xl}"
    padding: "24px"
  chip-metadata:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink-secondary}"
    rounded: "{rounded.pill}"
    padding: "4px 10px"
    typography: "{typography.label}"
  statut-badge:
    rounded: "{rounded.pill}"
    padding: "2px 10px"
    typography: "{typography.label}"
  page-header-eyebrow:
    textColor: "{colors.ember}"
    typography: "{typography.label}"
---

# Design System: Leadrive

## 1. Overview

**Creative North Star: "L'Atelier Automobile Français"**

Leadrive est un *atelier*, pas une *plateforme*. La direction artistique s'inspire des intérieurs de garages premium français (Citroën DS, Alpine, ateliers de carrosserie haut de gamme) et de la mise en page d'une revue d'auto-écrite-pour-amateurs-éclairés, *L'Automobile Magazine* époque belle, pas Auto Plus. La palette est monochrome warm, sand cream sur ink anthracite avec un seul accent ember (sienne brûlée, feu de brake light). Aucun bleu. Aucun jaune. Aucun glassmorphism froid.

Le système combine trois familles typographiques avec des rôles distincts qui n'empiètent jamais : **Bricolage Grotesque** (display, variable, axes opsz/wght/wdth) pour les titres et eyebrows uppercase wide-tracked, **Geist Sans** (Vercel) pour tout le body — choisi explicitement contre Inter qui est devenu l'empreinte digitale du SaaS template AI-généré, **JetBrains Mono** pour les chiffres KPI et les labels techniques. Lecture immédiate : "instrument de mesure", pas "dashboard générique".

Ce que ce système rejette explicitement (issu de PRODUCT.md anti-references) :
- **Blue tech SaaS générique** (HubSpot, Salesforce, Intercom) — palette navy + Inter + cards identiques
- **AI-generated template** (v0/Lovable first-pass) — gradient violet, hero "Big number + small label + supporting metrics", copy "AI-powered intelligent smart"
- **Logiciel métier années 2010** (DMS/CRM concession) — dropdowns gris, dialogs empilées, captures Salesforce 2014
- **Marketing automobile cliché** (Auto Plus, Caradisiac, sites Stellantis) — bleu métallisé + rouge Ferrari + photo voiture haute-saturation sur noir

**Key Characteristics:**
- Palette warm monochrome (sand / paper / clay / tan / ink) + un seul accent ember
- Trois fontes à rôles non-recouvrants : Bricolage display, Geist body, JetBrains Mono numeric/label
- Eyebrows uppercase wide-tracked (0.22em) signent toutes les pages
- KPI en mono tabular nums (effet "instrument de bord")
- Cards et boutons "fabriqués" : signature `card-leadrive::before` (barre gradient courte top-left), `btn-primary` (gradient 3-stops + sheen + double ombre)
- Easings cubic-bezier exponentiels (cubic-bezier(0.16, 1, 0.3, 1)) — pas de linear/ease-in-out, jamais de bounce
- Sidebar admin en dark constant (back-office différencié de l'app)
- `prefers-reduced-motion` honoré, focus ring ember visible

## 2. Colors: La Palette Atelier

Trois familles, monochrome warm + un accent unique. Aucune couleur bleue, aucun jaune. Tous les neutres sont tintés vers l'ember (chroma 0.005-0.01 OKLCH) — la chaleur est non-négociable.

### Primary
- **Ember** (`#D9603A` — sienne brûlée / feu de brake light / terracotta) : accent unique du produit. CTA primaire, focus ring, signatures (`bar-gradient`, `editorial-rule`, `section-label::before`), eyebrows, hover sur dark sections. Une seule couleur d'attention dans toute l'app.
- **Ember Deep** (`#A8401F`) : hover sur boutons ember sur fond clair, ombres tintées, emphase texte ember (passe AA pour body 14px sur sand).
- **Ember Light** (`#E07B5A`) : highlight sur dark sections, hover sur ember-deep, premier stop du gradient brand.
- **Ember Subtle** (`#FBEEE6`) : backgrounds très pâles pour zones tintées (banners douces, hover de chips ember).

### Neutral (la palette Atelier proprement dite)
- **Sand** (`#F4EFE5` — cream chaud) : fond de page principal. Lecture "papier mat artisanal".
- **Paper** (`#FBF8F1`) : cards, surfaces élevées, modales. Plus chaud que white, garde la cohésion sand.
- **Clay** (`#EAE3D2`) : sections muted pour le rythme (alterner sand/clay donne le pouls de la page).
- **Tan** (`#D8CFB8`) : hairlines, dividers, bordures cards.
- **Ink** (`#1A1B23` — anthracite presque noir) : texte principal. Plus chaud que black pur (refuse `#000`).
- **Ink Secondary** (`#4B4D58`) : sous-titres, labels secondaires.
- **Ink Tertiary** (`#8B8676`) : meta, captions, placeholders.
- **Ignition** (`#13141A`) : dark sections bookends (landing hero, demo, footer). Encore plus chaud que zinc, garde de la matière.

### Status Tones
- **Success** (`#0E9F6E` sur `#E6F7F1` subtle) : confirmations, statuts ACTIF/RDV_OBTENU/ENVOYE.
- **Warning** (`#D97706` sur `#FEF3E2` subtle) : alertes non-bloquantes, statuts INVITE/EN_ATTENTE.
- **Danger** (`#E5484D` sur `#FCEBEC` subtle) : erreurs, statuts ECHEC/STOP_DEMANDE/SUSPENDU.

### Admin Dark (back-office constant, indépendant du dark mode app)
- **Admin Bg** (`#0F1014`) : sidebar admin et zones back-office. Toujours dark.
- **Admin Bg Hover** (`#1A1B22`), **Admin Border** (`#2E2E37`), **Admin Text** (`#FAFAF7`)

### Named Rules

**The One Attention Rule.** Ember est la *seule* couleur qui demande à l'œil de regarder. Les statuts success/warning/danger restent en arrière-plan, contenus dans leur `*-subtle` bg avec leur teinte de statut en texte. Si deux couleurs d'attention se disputent une page, supprimer la moins importante.

**The No-Blue Rule.** Aucun bleu, aucun gris bleuté, aucun cyan, aucun navy — dans aucune circonstance. Pas même pour les liens. Les liens prennent ember-deep. C'est ce qui sort Leadrive de l'aquarium SaaS instantanément.

**The Warm Tinting Rule.** Tout neutre doit être tinté vers l'ember (chroma OKLCH 0.005-0.01 suffit). `#000` et `#fff` sont prohibés : utiliser ink (`#1A1B23`) et paper (`#FBF8F1`). Même les ombres sont teintées ember (`rgba(217, 96, 58, 0.07)`).

**The Subtle BG Rule.** Pour signaler un statut sans crier, fond `*-subtle` (10% opacity du statut) + texte du statut. Pas de fond plein statut. Pas de bordure colorée épaisse.

## 3. Typography

**Display Font:** Bricolage Grotesque (variable, axes opsz/wght/wdth) — fallback `-apple-system, BlinkMacSystemFont, sans-serif`
**Body Font:** Geist Sans (Vercel) — fallback `system-ui, sans-serif`
**Mono/Label Font:** JetBrains Mono — fallback `ui-monospace, monospace`

**Character:** Bricolage donne aux titres une personnalité légèrement industrielle/automotive sans céder au cliché racing. Geist en body apporte la fabrication premium (proportions Inter mais terminaisons plus fines, ouvertures plus subtiles) sans tomber dans le défaut "AI-generated template Inter". JetBrains Mono sur les chiffres et les labels uppercase produit l'effet "instrument de bord" recherché. Les trois fontes ont des rôles non-recouvrants : on ne met jamais Bricolage en body, jamais Geist en eyebrow, jamais JetBrains sur du texte courant.

### Hierarchy

- **Display** (Bricolage 700, 28px → 34px responsive, line-height 1.05, letter-spacing -0.035em, opsz 48) : H1 de page (PageHeader), titres hero. Variable font axe opsz s'ajuste à la taille.
- **Headline** (Bricolage 700, 18px, line-height 1.2, letter-spacing -0.035em) : titres de modales, titres de section secondaire.
- **Title** (Geist 600, 15px, line-height 1.4, letter-spacing -0.01em) : titres de cards, labels forts.
- **Body** (Geist 400, 14px, line-height 1.5) : tout le texte courant. **Max line-length cappée à 65-75ch** pour les paragraphes longs (page /aide, descriptions).
- **Label / Eyebrow** (JetBrains Mono 600, 11px, letter-spacing 0.22em, uppercase, couleur ember) : eyebrows de pages (PageHeader.eyebrow), chapter numbers, section labels.
- **Numeric Hero** (JetBrains Mono 600, 44px, line-height 1, letter-spacing -0.04em, `font-feature-settings: "tnum", "ss01"`) : KPIs heros, valeur principale des KpiCard.
- **Mono inline** (JetBrains Mono 500, hérite size, `font-variant-numeric: tabular-nums`) : valeurs numériques inline, chips metadata, codes techniques.

### Named Rules

**The Three Roles Rule.** Une seule fonte par rôle. Bricolage = display uniquement. Geist = body uniquement. JetBrains Mono = chiffres et labels uniquement. Mélanger les rôles (titre en Geist, body en Bricolage) est interdit.

**The Tabular Numbers Rule.** Tout chiffre dans une colonne ou un alignement vertical (KPIs, tableaux, stats) doit utiliser `font-variant-numeric: tabular-nums`. Les chiffres proportionnels qui dansent dans un tableau sont un défaut de fabrication.

**The No Inter Rule.** Inter est l'empreinte digitale n°1 du SaaS template AI-généré 2024-2026. Geist Sans est notre fallback de référence. Ne réintroduire Inter sous aucun prétexte (même via Google Fonts de secours).

**The Eyebrow Signature Rule.** Toute page produit a un eyebrow JetBrains Mono uppercase wide-tracked en haut, couleur ember. C'est la signature qui ancre l'identité Atelier à travers toute l'app. PageHeader.eyebrow est obligatoire sur les pages principales.

## 4. Elevation

Le système est **flat-tinted** : surfaces plates par défaut, avec une ombre signature unique tintée ember qui apparaît sur les cards interactives et les surfaces élevées. Pas de stack d'ombres Material à 5 niveaux. La profondeur est portée par : (a) la teinte des fonds (sand → paper → ink), (b) les hairlines tan 1px, (c) l'ombre `shadow-card` (très discrète), (d) le hover-lift sur les cards interactives (translateY -2px + ombre légèrement plus dense).

### Shadow Vocabulary

- **shadow-card** (`box-shadow: 0 2px 10px rgba(217, 96, 58, 0.07), 0 1px 3px rgba(26, 27, 35, 0.05)`) : ombre par défaut pour cards, modales, tooltips. Tintée ember + ink, à peine perceptible.
- **shadow-ember-glow** (`box-shadow: 0 0 32px rgba(217, 96, 58, 0.35)`) : halo doux autour des CTA primaires et des éléments en focus visuel fort. Réservé aux moments d'emphase.
- **btn-primary triple shadow** (`inset 0 1px 0 rgba(255,255,255,0.30), 0 1px 2px rgba(120,50,25,0.32), 0 4px 12px rgba(217,96,58,0.25)`) : signature du CTA principal — inner highlight (sheen specular) + ombre proche + ombre diffuse. Donne l'aspect "touche physique d'instrument".
- **card hover lift** (`transform: translateY(-2px); box-shadow: 0 12px 28px rgba(79,107,255,0.10)`) : micro-physics au hover sur cards interactives. Combiné à `transition cubic-bezier(0.16, 1, 0.3, 1)`.

### Named Rules

**The Flat-By-Default Rule.** Cards et surfaces sont plates au repos (bordure 1px tan + fond paper). L'ombre apparaît au hover, pas par défaut. Une page produit pleine d'ombres permanentes lit "Material 2017".

**The One Glow Rule.** Le `shadow-ember-glow` est réservé aux CTA primaires et aux moments d'emphase explicites (modale ouverte, élément en focus actif). Pas de glow décoratif sur les cards génériques.

## 5. Components

### Buttons (`frontend/src/components/common/Button.tsx`)

Tactiles, "fabriqués". Quatre variantes : primary, secondary, danger, ghost. Deux tailles : sm (12px text), md (14px text, défaut).

- **Shape:** Coins arrondis (`rounded-lg`, 12px).
- **Primary (`btn-primary`):** Gradient vertical 3-stops (`#E07B5A → #D9603A → #B84A28`), texte blanc, border `rgba(255,255,255,0.16)`, triple ombre (inner sheen + proche + diffuse). Hover : gradient plus profond + translateY(-1px) + ombre intensifiée. Active : `translateY(0)` + inset shadow plus dense (effet "enfoncé"). Transition 200ms ease-cinema.
- **Secondary:** Background paper, bordure tan, texte ink. Hover : bg clay.
- **Danger:** Background paper, bordure danger/30, texte danger. Hover : bg danger/10.
- **Ghost:** Pas de fond, texte ink-secondary. Hover : bg clay + texte ink.
- **Magnetic Variant (`btn-magnetic`):** Optionnel sur CTAs landing. Icône en pill 28px qui se translate (3px, -1px) + scale 1.06 au hover. Easing 700ms cubic-bezier(0.16, 1, 0.3, 1). Active : `scale(0.97)`.
- **Disabled:** `opacity-50 cursor-not-allowed`.

### Cards (`frontend/src/components/common/Card.tsx`)

- **Corner Style:** `rounded-xl` (16px) — plus généreux que les boutons (12px) pour signaler la surface.
- **Background:** Paper (`#FBF8F1`) par défaut.
- **Border:** 1px tan (`#D8CFB8`).
- **Shadow Strategy:** Plat au repos. Si interactif, ajouter `card-hover-lift` qui translateY(-2px) au hover.
- **Internal Padding:** `p-5` (20px) si `padded` prop, sinon le consommateur gère.

### Signature Card: `.card-leadrive`

Card distinctive avec barre gradient ember courte (28px × 3px) qui dépasse en top-left au-dessus de la bordure (`top: 0; left: 24px`). Signature visuelle unique qui différencie nos cards d'une card Tailwind générique. Utilisée sur les cards les plus visibles (KPIs heros, sections importantes).

### KpiCard (`frontend/src/components/common/Card.tsx` — KpiCard)

Card spécialisée pour les indicateurs. Label (Geist 600, ink-secondary) + tooltip InfoIcon optionnel en haut. Icône optionnelle (40×40px, fond ember/10) à droite. Valeur principale en bottom (Geist 700 24px ou JetBrains Mono via prop `mono`). Hint sous la valeur (12px ink-secondary).

### Inputs / Fields (`frontend/src/components/common/Input.tsx`)

Wrappers stables sur input/select/textarea avec FieldLabel partagé.

- **Style:** Background paper, bordure tan, `rounded-lg` (12px), padding `px-3 py-2`, text 14px.
- **Label:** Geist 500, 14px, ink. Hint sous le label : 12px ink-secondary.
- **Placeholder:** ink-tertiary (`#8B8676`).
- **Focus:** Bordure ember + ring `accent/30` 1px. Pas de outline native.
- **Disabled:** `opacity-60 cursor-not-allowed`.

### Modal (`frontend/src/components/common/Modal.tsx`)

Portal dans `<body>` pour échapper aux containing blocks (transform parents). Backdrop `bg-black/40` + animation `fade-in`. Modale `rounded-2xl` (16px), bordure tan, shadow-card, animation `slide-up` 220ms.

- **Header:** padding `px-6 py-4`, border-bottom tan, titre en Bricolage 18px font-bold + close icon top-right.
- **Body:** padding `px-6 py-5`.
- **Footer (optionnel):** background sand, border-top tan, padding `px-6 py-4`, actions alignées à droite avec `gap-3`.
- **Sizes:** sm (max-w-sm), md (max-w-md, défaut), lg (max-w-2xl).
- **A11y:** `role="dialog" aria-modal="true"`, ESC pour fermer, scroll body bloqué.

### Tooltip (`frontend/src/components/common/Tooltip.tsx`)

Portal-based, sans dépendance externe. Délai d'apparition 200ms, position auto-corrigée si débord viewport.

- **Style:** `rounded-lg` (12px), bordure tan, fond paper, `shadow-lg`, texte 12px leading-relaxed.
- **Max-width:** 240px par défaut, paramétrable.
- **A11y:** `role="tooltip"`, trigger sur focus + hover.

### Alert (`frontend/src/components/common/Alert.tsx`)

Quatre tons : danger, success, warning, info (ember). Fond `tone/10` + bordure `tone/30` + texte du ton. `rounded-lg`, padding `px-4 py-3`, text 14px.

### StatutBadge (`frontend/src/components/common/StatutBadge.tsx`)

Badge pill (rounded-full) `px-2.5 py-0.5`, texte 10px JetBrains Mono uppercase tracking-wide (signature instrument). Mapping centralisé des statuts métier vers les tons (ACTIF → success, INVITE → warning, ECHEC → danger, etc.).

### PageHeader (`frontend/src/components/common/PageHeader.tsx`)

Pattern éditorial signature, présent sur toutes les pages produit principales. Eyebrow JetBrains Mono 11px uppercase 0.22em tracking ember + titre Bricolage display 28→34px font-bold tracking-display-tight + sous-titre Geist 15px leading-relaxed ink-secondary. Actions à droite. Border-bottom tan 1px en separation. C'est la signature qui ancre l'identité Atelier à travers toute l'app.

### Chip Metadata (`.chip-metadata`)

Pill "fiche technique" automotive : padding `0.25rem 0.625rem`, bordure tan 1px, fond paper, JetBrains Mono 10.5px, letter-spacing 0.04em, texte ink-secondary. Pour méta-données techniques (versions, IDs lisibles, tags techniques).

### Navigation (AppLayout / Sidebar)

Sidebar gauche fixe sur desktop, drawer sur mobile. Light dans l'app principale (sand bg), **dark constant** dans l'admin (`/admin/*` utilise `--color-admin-bg #0F1014`). Le contraste back-office vs app est volontaire et permanent (indépendant du dark mode utilisateur).

### Signature Component: `bar-gradient`

Barre 3px de gradient ember (`linear-gradient(90deg, #A8401F 0%, #D9603A 50%, #E07B5A 100%)`) fixée en `top: 0` sur toutes les pages (z-index 200). C'est le fil rouge brand qui ancre l'identité Atelier sur chaque écran, hors page d'erreur full-screen.

## 6. Do's and Don'ts

### Do:

- **Do** utiliser les CSS custom properties (`var(--color-accent)`, `var(--background)`, `var(--color-lr-text-secondary)`, etc.) plutôt que les classes Tailwind hardcodées. Le mapping est dans `frontend/src/app/globals.css`.
- **Do** mettre un `PageHeader` avec `eyebrow` sur chaque page produit principale (équipe, leads, conversations, stats, planning, etc.). L'eyebrow ember uppercase est la signature Atelier.
- **Do** utiliser `.font-mono` ou `.tabular-nums` sur tout chiffre dans un tableau ou un KPI. Les chiffres proportionnels qui dansent sont un défaut de fabrication.
- **Do** utiliser `.font-display` pour les H1/H2 hero et les titres de modales. Le reste du texte (95%) reste en Geist Sans body.
- **Do** réutiliser les composants de `frontend/src/components/common/` (Button, Card, Modal, Input, Alert, Tooltip, StatutBadge, PageHeader) plutôt que de recoder un badge/bouton inline.
- **Do** utiliser `ease-cinema` (cubic-bezier(0.16, 1, 0.3, 1)) sur les transitions premium. C'est l'easing exponentiel par défaut pour les state changes de qualité.
- **Do** spacing standardisé : `p-3` (sm), `p-4` (md), `p-6` (lg) sur les cards et conteneurs. Ne pas inventer `p-5` ou `p-7` sauf cas exceptionnel.
- **Do** respecter `prefers-reduced-motion` : les animations infinies (`shimmer`, `pulse-stripe`, `speed-streak`) sont déjà coupées dans le media query. Tout nouvel `@keyframes infinite` doit être ajouté à la liste du media query.
- **Do** formater tous les numéros de téléphone affichés à l'utilisateur via `formatPhoneInput()` (espaces). Jamais d'E.164 brut "+33700000001" dans l'UI.
- **Do** utiliser l'eyebrow ember + uppercase wide-tracked comme signature de section, pas un titre coloré générique.

### Don't:

- **Don't** introduire de bleu, cyan, navy, indigo, ou gris bleuté dans l'UI, sous aucun prétexte. C'est la *première* signature de l'aquarium SaaS générique (HubSpot, Salesforce, Intercom). Pareil pour les liens : utiliser ember-deep, pas bleu.
- **Don't** utiliser Inter ni proposer de fallback Inter. C'est l'empreinte digitale n°1 du SaaS template AI-généré. Geist Sans est notre fontes body.
- **Don't** utiliser `#000` ou `#fff` purs. Ink (`#1A1B23`) et Paper (`#FBF8F1`) sont les bornes warm de la palette.
- **Don't** mettre du gradient sur du texte (`background-clip: text` + gradient). Banni par les shared design laws — décoratif sans signification. Emphase via weight ou size, ou solid color ember.
- **Don't** utiliser de side-stripe border (border-left épais coloré) sur les alerts, callouts, cards. Banni par les shared design laws — c'est un cliché. Bordure complète, ou fond tinté `*-subtle`, ou icône + numéro en tête.
- **Don't** empiler des cards à 3 colonnes identiques (icon + heading + text répétée). Anti-pattern AI-generated template. Si vraiment besoin, varier le contenu, varier les tailles, ajouter un focus point.
- **Don't** ouvrir une modale pour tout. Le vendeur n'a pas le temps. Préférer un inline edit, un drawer latéral, ou un panel inline. Modale uniquement quand l'action est critique et irréversible (suppression, paiement).
- **Don't** utiliser d'em-dash (`—`) dans les textes user-facing (UI, prompts IA, page /aide). C'est une signature classique d'IA en français. Virgule, point, deux-points, parenthèses, point-virgule.
- **Don't** exposer de jargon technique à l'utilisateur dans l'UI : "Meta", "OTP", "webhook", "sender SID", "API", "tenant", "concession" (exclusif Stellantis). Vocabulaire qui parle au concessionnaire / garagiste / multi-marques : "WhatsApp Business", "code de vérification", "notification", "numéro", "connexion", "établissement".
- **Don't** dupliquer un badge / bouton / alerte inline. Si un composant existe dans `common/`, l'utiliser.
- **Don't** animer linear ou ease-in-out. Utiliser `ease-cinema` (cubic-bezier exponentiel) ou `ease-out-quart/quint/expo`. Pas de bounce, pas d'élastique.
- **Don't** mettre `transform` sur le `<main>` wrapper d'une page : un containing block transform casse les `position: fixed` des enfants (cas vécu : la save bar de `/admin/config-ia`). Animation page-enter limitée à `opacity`.
- **Don't** importer des couleurs Tailwind hardcodées : `bg-emerald-500`, `text-amber-400`, `bg-red-600`, `bg-zinc-200`, `text-indigo-500`. Mapping : emerald → success, amber → warning, red → danger, blue/indigo/cyan → ember, zinc/gray → `--color-lr-*` ou `--muted`/`--border`.