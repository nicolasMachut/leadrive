# Product

## Register

product

> Note : Leadrive est majoritairement un produit (app SaaS B2B utilisée au quotidien par managers et vendeurs). Les surfaces brand (landing `/`, plaquette `/plaquette`, page d'aide `/aide`, mentions, politique) suivent les mêmes règles d'identité mais peuvent localement basculer en register `brand` pour les pages marketing long-form. La majorité du travail d'amélioration porte sur les pages produit.

## Users

**Cible primaire : concessions automobiles et garages indépendants en France.**

Deux profils utilisateurs cohabitent dans l'app, avec des contextes radicalement différents :

- **Manager / chef des ventes (rôle `MANAGER`)** : décideur. Suit la performance commerciale globale, configure les séquences IA, valide les imports, gère l'équipe vendeurs. Contexte : bureau, écran 24-27", session longue (30-60 min), souvent multitâche. Veut une vue d'ensemble immédiate (KPIs, pipeline, alertes), capacité de drill-down rapide.
- **Vendeur (rôle `VENDEUR`)** : opérationnel. Reçoit des alertes de rappel, lit les fiches IA, gère ses conversations WhatsApp, rapporte des opportunités. Contexte : showroom, mobile/tablette parfois, sessions courtes et fragmentées (5-15 min entre deux clients), bruit ambiant.

**Cible inclusive** : pas exclusivement concessionnaires monomarque. Inclut garages indépendants, revendeurs multi-marques, MRA (mécaniciens-réparateurs automobiles). Le vocabulaire UI dit "établissement", pas "concession".

**Job to be done** :
- Manager : "Ne plus perdre les leads dormants. Savoir qui rappeler, savoir qui a fait quoi, savoir combien ça rapporte. Sans changer notre DMS."
- Vendeur : "Vendre, pas faire de l'administratif. L'outil doit me dire quoi faire, je le fais en 2 clics, retour au client."

## Product Purpose

Leadrive est un **outil d'activation commerciale** qui se branche par-dessus le DMS/CRM existant pour exploiter les données dormantes et déclencher des actions automatiques pilotées par IA. Quatre modules :

1. **Vitesse temps réel** — alertes vendeur si pas de rappel en 15 min, relance auto si vendeur muet
2. **Réactivation des leads dormants** — séquences IA personnalisées par WhatsApp
3. **Réactivation base historique** — détection des cycles de renouvellement 2-4 ans
4. **Coaching transversal** — tableau de bord manager + recommandations IA hebdo

**Pourquoi ça existe** : 50 à 70% des leads en concession dorment sans relance après 48h. Les vendeurs n'ont pas le temps, le DMS ne pousse rien, l'opportunité se perd. Leadrive automatise la première relance qualifiée et alerte le vendeur uniquement quand un humain doit reprendre la main.

**Définition du succès** :
- Le manager voit chaque matin "X opportunités ré-engagées hier" sans avoir cliqué nulle part
- Le vendeur ouvre Leadrive 3-5 fois par jour pour 30 secondes, sans frustration
- L'IA produit des messages qu'un humain ne pourrait pas distinguer d'un envoi vendeur soigné
- Pas une seule fois en 3 mois un message inopportun n'a été envoyé (24h-window respecté, opt-out RGPD garanti, hallucination IA bloquée)

## Brand Personality

**Trois mots : artisanal, précis, chaleureux.**

- **Artisanal** — Leadrive est *fabriqué*, pas généré. Chaque page a une intention. Le code, la copy, les détails (le grain noise, les easings cubic-bezier, les chip-metadata) montrent un soin de main, pas un template. C'est l'opposé de "AI workflow tool sorti cette semaine sur Product Hunt".
- **Précis** — Métaphore "instrument de mesure" assumée : tachographe, jauges de scoring, KPIs en JetBrains Mono tabular, eyebrows uppercase wide-tracked. Les nombres sont *des nombres*, pas des décorations. Une mesure floue ferait perdre confiance au manager.
- **Chaleureux** — Palette Atelier (sand cream, ink anthracite, ember sienne) tournée résolument vers le warm. Aucun bleu, aucun glassmorphism froid. Le produit doit donner l'impression d'un atelier français bien tenu, pas d'un dashboard Bloomberg.

**Voice & tone** :
- Français naturel, tutoiement implicite mais formel (vous). Aucun terme tech jeté brut à l'utilisateur ("Meta", "OTP", "webhook", "API" sont interdits dans l'UI — cf. CLAUDE.md).
- Direct, pas sermonneur. "12 leads à rappeler aujourd'hui." pas "Vous avez 12 leads qui nécessitent votre attention immédiate."
- Honnête sur les limites. Si l'IA a un doute, on le dit. Si une feature n'est pas configurée, on guide pas on bloque.
- **Aucun em-dash (—) dans les textes user-facing.** Virgule, point, deux-points, parenthèses, point-virgule.

**Émotions à provoquer** :
- Manager : confiance instrumentée ("je vois ce qui se passe"), sérénité ("c'est sous contrôle"), légère fierté esthétique ("ce truc est beau").
- Vendeur : clarté immédiate ("je sais quoi faire"), absence de friction ("c'est rapide"), respect ("ça parle ma langue, pas du jargon").

## Anti-references

Quatre lanes explicites à éviter, identifiées par le contraste avec la cible :

### 1. Blue tech SaaS générique (HubSpot, Salesforce, Intercom, Pipedrive)

Le piège n°1. Palette navy + blanc + accent bleu, fonte Inter, cards identiques en grilles de 3, hero "Big number + small label + supporting metrics", gradient bleu sur le CTA. Toute la catégorie CRM mid-market tombe dans ce moule. La palette Atelier sand/ink/ember est précisément la fuite hors de cet aquarium.

> **Test rapide** : si quelqu'un peut deviner "outil de gestion de leads B2B" à partir des couleurs seules, on est dans le blue tech aquarium. Échec.

### 2. Outils AI-generated template (v0, Lovable, Stitch first-pass)

L'esthétique "généré par IA cette semaine" : hero gradient violet/bleu, copy "AI-powered intelligent smart automated", cards icon+title+description répétées à l'infini, modals pour tout, side-stripe borders sur les alertes, gradient text decoratif sur les chiffres clés. Tous les bans des shared design laws viennent de cette lane.

### 3. Logiciel métier années 2010 (DMS/CRM concession)

Le contre-modèle exact que Leadrive remplace. Windows 7 vibe, dropdowns gris, tableaux Excel embarqués, captures de Salesforce 2014, dialogs modales empilées, dates en `JJ/MM/AAAA` à la place de "il y a 2 jours". Les utilisateurs *viennent* de cet univers : si Leadrive ressemble même de loin à ça, on a perdu.

### 4. Marketing automobile cliché (Auto Plus, Caradisiac, sites concessions Stellantis)

Bleu métallisé Stellantis + rouge Ferrari + photo voiture haute-saturation sur fond noir, gradient diagonal "high-tech speed", typo "racing" oblique. Anti-référence pour la landing et la plaquette. Leadrive est sobre, atelier, *pas* showroom Salon de l'Auto.

## Design Principles

Cinq principes stratégiques qui guident chaque choix de design :

### 1. Instrument plutôt qu'app

Leadrive est un *instrument de mesure* pour le manager et un *outil de bord* pour le vendeur, pas une "app" générique. KPIs en mono tabular, jauges physiques, eyebrows uppercase, chip-metadata façon fiche technique. Quand le doute existe entre une présentation "app" et une présentation "instrument", choisir instrument.

### 2. Chaud, jamais froid

Aucun bleu, aucun gris bleuté, aucun glass froid. Tous les neutres sont *tintés vers l'ember* (chroma 0.005-0.01 sur OKLCH suffit). Le warm est non-négociable : c'est la signature qui sort Leadrive de la catégorie SaaS. Même les ombres sont teintées ember.

### 3. Une seule couleur d'attention

Ember (#D9603A) est la *seule* couleur qui demande à l'œil de regarder. Tout le reste (statuts success/warning/danger) reste en arrière-plan, contenus dans des `*-subtle` bg avec texte du statut. Si une page a deux couleurs d'attention en compétition, supprimer l'une des deux.

### 4. Le vendeur n'a pas 30 secondes

Chaque interaction du vendeur doit être *terminable en moins de 10 secondes en showroom, sur mobile, avec un client à côté*. Pas de wizard 5 étapes, pas de confirmation modale superflue, pas de "êtes-vous sûr ?" sur une action réversible. Les modales sont presque toujours le mauvais choix (cf. ban impeccable).

### 5. La langue du métier, pas la langue de l'outil

Vocabulaire qui parle au concessionnaire : "lead", "relance", "rappel", "rendez-vous", "essai", "renouvellement". Jamais "Meta", "OTP", "webhook", "API", "tenant", "concession" (exclusif), "sender SID". Si un terme technique fuit dans l'UI, c'est un bug à corriger. La règle est codifiée dans CLAUDE.md, on l'applique partout.

## Accessibility & Inclusion

**Cible : WCAG 2.1 niveau AA.**

Standard B2B européen, équivalent RGAA fonctionnel. Le `globals.css` honore déjà `prefers-reduced-motion` (animations coupées, transitions à 0.01ms), focus ring Atelier visible (`box-shadow 0 0 0 3px rgba(217, 96, 58, 0.20)`), tabular nums pour les chiffres.

**Points d'attention spécifiques** :

- **Contrastes** : 4.5:1 minimum sur tout le texte. L'ember `#D9603A` sur blanc passe en AA pour texte ≥18px ou ≥14px bold uniquement. Pour body text, utiliser `--color-accent-deep` (#A8401F) ou Ink (#1A1B23).
- **Navigation clavier** : toutes les actions accessibles au clavier. Tab order logique. Pas de piège focus dans les modales.
- **Lecteurs d'écran** : labels explicites sur tous les inputs, `aria-label` sur les boutons icon-only, `aria-live` sur les toasts et alertes système.
- **Motion** : `prefers-reduced-motion` désactive les animations infinies (`pulse`, `shimmer`, `ping`, `speed-streak`). One-shot animations courtes (fade-in 200ms) restent.
- **Couleur** : aucune information ne dépend uniquement de la couleur. Les statuts ont toujours un libellé ou une icône en plus de la teinte.
- **Numéros de téléphone** : toujours formatés avec espaces dans l'UI (`+33 7 00 00 00 01`), jamais en E.164 brut. Règle codifiée dans CLAUDE.md.
- **PII dans logs** : aucun email/téléphone/contenu de message en clair. Toujours via `PiiMasker`.

**Considération vendeur en showroom** : la lecture se fait souvent dans des conditions de bruit visuel (showroom éclairé, écran tactile, doigt qui passe). Hit-targets ≥44px, contraste haut sur les CTAs, pas de hover-only (toujours un fallback touch).

**Considération manager en bureau** : sessions longues, lecture dense (tableaux, KPIs). Line-length cappée à 65-75ch sur le texte long, hiérarchie typo via Bricolage display + Geist body. Pas de fatigue visuelle.