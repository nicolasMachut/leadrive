"use client";

import Image from "next/image";
import { useState } from "react";
import Tachograph from "@/components/common/Tachograph";
import {
  WhatsappLogo,
  ClipboardText,
  FileText,
  Upload,
  Robot,
  Handshake,
  Target,
  Brain,
  ChartLineUp,
  ArrowRight,
  Check,
  PaperPlaneTilt,
} from "@phosphor-icons/react";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

const JSON_LD_ORGANIZATION = {
  "@context": "https://schema.org",
  "@type": "Organization",
  name: "Leadrive",
  url: "https://leadrive.fr",
  logo: "https://leadrive.fr/leadrive_logo_light.svg",
  description: "Leadrive est un assistant IA qui relance automatiquement vos leads automobile par WhatsApp pour les concessions, garages et revendeurs multimarques francais.",
  contactPoint: { "@type": "ContactPoint", email: "contact@leadrive.fr", contactType: "sales" },
};

const JSON_LD_SOFTWARE = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "Leadrive",
  applicationCategory: "BusinessApplication",
  operatingSystem: "Web",
  description: "Assistant IA de performance commerciale pour les professionnels de l'automobile : concessions, garages, revendeurs multimarques. Import intelligent, relance WhatsApp automatique, pipeline Kanban, statistiques.",
  offers: [
    { "@type": "Offer", name: "Offre unique", price: "99", priceCurrency: "EUR", url: "https://leadrive.fr/#tarifs", description: "Par point de vente / mois, 15 RDV inclus, 8€ par RDV supplémentaire" },
  ],
};

const JSON_LD_FAQ = {
  "@context": "https://schema.org",
  "@type": "FAQPage",
  mainEntity: [
    { "@type": "Question", name: "Comment fonctionne Leadrive ?", acceptedAnswer: { "@type": "Answer", text: "Leadrive importe vos leads depuis un CSV ou Excel, contacte automatiquement les prospects via WhatsApp avec une IA conversationnelle, et détecte les signaux d'achat pour qualifier les opportunités." } },
    { "@type": "Question", name: "Combien de temps pour être opérationnel ?", acceptedAnswer: { "@type": "Answer", text: "Quelques minutes. Import de vos leads, configuration de l'assistant IA, et premier message WhatsApp envoyé dans la foulée." } },
    { "@type": "Question", name: "Faut-il changer de DMS ?", acceptedAnswer: { "@type": "Answer", text: "Non. Leadrive se connecte par-dessus votre DMS existant via un simple export CSV ou Excel. Aucune intégration technique nécessaire." } },
    { "@type": "Question", name: "Les données sont-elles sécurisées ?", acceptedAnswer: { "@type": "Answer", text: "Oui. Hébergement en Europe, chiffrement des données, isolation multi-tenant, et conformité RGPD. Leadrive est sous-traitant de données, votre établissement reste responsable de traitement." } },
  ],
};

export default function LandingPage() {
  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD_ORGANIZATION) }} />
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD_SOFTWARE) }} />
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD_FAQ) }} />
      <HeroSection />
      <ProblemSection />
      <SolutionSection />
      <HowItWorksSection />
      <StatsSection />
      <AiLearningSection />
      <PricingSection />
      <FaqSection />
      <DemoFormSection />
    </>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Eyebrow editorial, chapitre numerote + label. Pattern repete tout le long
// pour rythmer la lecture comme une revue automobile.
// ─────────────────────────────────────────────────────────────────────────────

function Eyebrow({ chapter, label, dark = false }: { chapter: string; label: string; dark?: boolean }) {
  const colorClass = dark
    ? "text-[var(--color-accent)] text-opacity-90"
    : "text-[var(--color-accent)]";
  const dividerClass = dark ? "bg-white/15" : "bg-[var(--color-accent)]/25";
  return (
    <div className="flex items-center gap-3">
      {/* chapter-scroll : letter-spacing + opacity animées au scroll d'entrée
          (CSS view-timeline natif). Effet "le numéro de chapitre s'allume"
          quand la section devient visible. Fallback statique sans support. */}
      <span className={`font-mono text-[11px] font-semibold uppercase tracking-[0.22em] chapter-scroll ${colorClass}`}>
        <span className={dark ? "text-white/35" : "text-[var(--color-lr-text-tertiary)]"}>
          {chapter}
        </span>
        <span className="mx-2">·</span>
        {label}
      </span>
      <span className={`hidden h-px flex-1 max-w-16 ${dividerClass} sm:block`} />
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 1 : Hero
// Layout centered editorial. Photo showroom traitee en ambiance (40% opacity).
// CTA cluster : btn-phare avec island arrow + outline. Proof rail 4 chips.
// Highlight "de vos leads." en solid var(--color-accent) (gradient text banni).
// ─────────────────────────────────────────────────────────────────────────────

function HeroSection() {
  return (
    <section className="relative flex min-h-[100dvh] items-center justify-center overflow-hidden bg-[var(--color-ignition)] px-6">
      <div className="absolute inset-0 overflow-hidden">
        {/* hero-parallax : zoom-out lent (1.08 → 1.0) + translation -40px tied
            to scroll. Donne au hero une sensation cinéma "le showroom recule"
            quand on commence à descendre. CSS scroll-timeline natif, fallback
            statique sur Firefox. */}
        <Image
          src="/hero-showroom.jpg"
          alt=""
          fill
          className="object-cover opacity-50 hero-parallax"
          priority
          quality={80}
          sizes="100vw"
        />
        {/* Dégradé asymétrique : laisse la voiture visible en haut (40% opacity),
            assombrit progressivement vers le bas pour la lisibilité du texte
            et des CTAs. La photo devient lisible sans être en plein jour. */}
        <div className="absolute inset-0 bg-gradient-to-b from-[var(--color-ignition)]/30 via-[var(--color-ignition)]/70 to-[var(--color-ignition)]/95" />
        <div className="absolute inset-0 bg-stripes-pattern opacity-[0.02] pointer-events-none" />
      </div>

      {/* Glow Ember bas-droite, chaleur ponctuelle qui signe la palette Atelier. */}
      <div
        className="pointer-events-none absolute -right-40 bottom-0 h-[520px] w-[520px] rounded-full opacity-30 blur-3xl"
        style={{ background: "radial-gradient(circle, rgba(217,96,58,0.6) 0%, transparent 70%)" }}
        aria-hidden="true"
      />

      <div className="relative z-10 mx-auto max-w-4xl pt-24 pb-12 text-center">
        {/* Eyebrow editorial, chapitre 00 (ouverture) */}
        <div className="mb-8 inline-flex items-center gap-3">
          <span className="h-px w-10 bg-white/20" />
          <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-white/60">
            <span className="text-white/35">00 / 06</span>
            <span className="mx-2">·</span>
            Leadrive
          </span>
          <span className="h-px w-10 bg-white/20" />
        </div>

        <h1 className="font-display text-[44px] font-bold leading-[1.02] tracking-display-tight text-white sm:text-[64px] lg:text-[84px]">
          Exploitez le potentiel
          <br />
          <span className="text-[var(--color-accent-light)]">de vos leads.</span>
        </h1>

        <p className="mx-auto mt-8 max-w-xl text-base leading-relaxed text-white/65 sm:text-lg">
          L&apos;IA qui contacte vos prospects en moins de 5&nbsp;minutes, génère des RDV et prépare vos vendeurs avant chaque appel.
        </p>

        {/* CTA cluster, Phare primaire avec island arrow + outline secondaire.
            Pattern "magnetic button" : hover translate l'icone, scale 0.97 actif. */}
        <div className="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row sm:gap-4">
          <a
            href="#demo"
            className="btn-phare btn-magnetic group inline-flex items-center rounded-xl px-7 py-3.5 text-base font-semibold"
          >
            Demander une démo
            <span className="btn-island-icon">
              <ArrowRight size={14} weight="bold" />
            </span>
          </a>
          <a
            href="#probleme"
            className="inline-flex items-center gap-2 rounded-xl border border-white/15 px-7 py-3.5 text-base font-medium text-white/85 transition-colors hover:border-white/30 hover:bg-white/5 hover:text-white"
          >
            Voir comment ça marche
          </a>
        </div>

        {/* Proof rail, 4 chips horizontales discrètes */}
        <div className="mt-14 flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-[12px] text-white/45">
          {["Prêt en 10 minutes", "Sans engagement", "1er mois offert", "RGPD · Hébergement Europe"].map((proof) => (
            <span key={proof} className="flex items-center gap-1.5">
              <span className="h-1 w-1 rounded-full bg-[var(--color-accent)]" />
              {proof}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 2 : Le constat (Problem)
// Layout EDITORIAL 2-col, ZERO card. Sticky title left, drop-cap + stats inline
// + pull-quote right. Surface cream.
// ─────────────────────────────────────────────────────────────────────────────

function ProblemSection() {
  return (
    <section id="probleme" className="relative bg-[var(--background)] px-6 py-32 lg:py-40">
      <div className="mx-auto max-w-6xl">
        <div className="grid grid-cols-1 gap-12 lg:grid-cols-12 lg:gap-16">
          {/* Col gauche, sticky title */}
          <div className="lg:col-span-5">
            <div className="lg:sticky lg:top-32">
              <Eyebrow chapter="01 / 06" label="Le constat" />
              <h2 className="mt-6 font-display text-[40px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)] sm:text-[52px] lg:text-[60px]">
                Chaque mois, des dizaines de ventes s&apos;évaporent en silence.
              </h2>
            </div>
          </div>

          {/* Col droite, corps editorial — scroll-reveal lie au view-timeline */}
          <div className="lg:col-span-7 scroll-reveal">
            {/* 3 cartes problème, inspirées du format "pain card" avec stat + explication + source */}
            <div className="space-y-5">
              {/* Carte 1, Délai de rappel */}
              <div className="rounded-2xl border border-[var(--border)] bg-[var(--card)] p-6">
                <p className="font-numeric text-[36px] font-bold leading-none text-[var(--color-accent)] sm:text-[44px]">
                  1 chance sur 4
                </p>
                <p className="mt-2 text-[14px] font-semibold text-[var(--foreground)]">
                  de conclure si rappelé dans l&apos;heure
                </p>
                <p className="mt-3 text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                  Passé ce délai, votre lead est chez le concurrent. La durée de vie d&apos;un lead automobile est de 12&nbsp;heures.
                </p>
                <p className="mt-3 text-[11px] italic text-[var(--color-accent)]">
                  Source : IMAWEB / Ubiflow
                </p>
              </div>

              {/* Carte 2, Numéros inconnus */}
              <div className="rounded-2xl border border-[var(--border)] bg-[var(--card)] p-6">
                <p className="font-numeric text-[36px] font-bold leading-none text-[var(--color-accent)] sm:text-[44px]">
                  65<span className="text-[24px] text-[var(--color-lr-text-tertiary)]">%</span>
                </p>
                <p className="mt-2 text-[14px] font-semibold text-[var(--foreground)]">
                  des Français ignorent les numéros inconnus
                </p>
                <p className="mt-3 text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                  Chez les moins de 35&nbsp;ans, ce chiffre monte à 82&nbsp;%. Votre vendeur appelle, le prospect ne décroche pas, car il ne peut pas préparer ses réponses.
                </p>
                <p className="mt-3 text-[11px] italic text-[var(--color-accent)]">
                  Source : ARCEP 2024
                </p>
              </div>

              {/* Carte 3, Raccourcis */}
              <div className="rounded-2xl border border-[var(--border)] bg-[var(--card)] p-6">
                <p className="font-display text-[32px] font-bold leading-none text-[var(--color-accent)] sm:text-[40px]">
                  Raccourcis
                </p>
                <p className="mt-2 text-[14px] font-semibold text-[var(--foreground)]">
                  pris à chaque appel, même par les meilleurs
                </p>
                <p className="mt-3 text-[13px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                  Reprise, financement, projet de vie… Les étapes oubliées réduisent vos chances de conclure.
                </p>
                <p className="mt-3 text-[11px] italic text-[var(--color-lr-text-tertiary)]">
                  Constat terrain
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 3 : Le contre-pied (Solution)
// Layout BENTO ASYMETRIQUE 3 tuiles de tailles differentes (col-span-7 / 5 / 12).
// Tile 1 : grosse, avec mock WhatsApp realiste. Tile 2 : medium, checklist.
// Tile 3 : pleine largeur, fiche de prep horizontale.
// Surface muted (rythme).
// ─────────────────────────────────────────────────────────────────────────────

function SolutionSection() {
  return (
    <section id="fonctionnalités" className="relative bg-[var(--muted)] px-6 py-32 lg:py-40">
      <div className="mx-auto max-w-6xl">
        <div className="mb-14 max-w-2xl">
          <Eyebrow chapter="02 / 06" label="Le contre-pied" />
          <h2 className="mt-6 font-display text-[40px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)] sm:text-[52px] lg:text-[60px]">
            Votre meilleur vendeur. En version IA.
          </h2>
          <p className="mt-5 text-[17px] leading-relaxed text-[var(--color-lr-text-secondary)]">
            Leadrive contacte, qualifie, prépare le rendez-vous. Vos vendeurs n&apos;ont plus qu&apos;à conclure.
          </p>
        </div>

        <div className="grid grid-cols-12 gap-5">
          {/* TILE 1, grosse, mock WhatsApp */}
          <article className="relative col-span-12 overflow-hidden rounded-3xl border border-[var(--border)] bg-[var(--card)] p-8 lg:col-span-7 lg:p-10 scroll-reveal">
            <div className="flex h-full flex-col">
              <div className="flex items-center gap-3">
                <div className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#25D366]/12">
                  <WhatsappLogo size={20} weight="fill" className="text-[#25D366]" />
                </div>
                <h3 className="font-display text-2xl font-semibold tracking-display-tight text-[var(--foreground)]">
                  Contact et relance automatiques.
                </h3>
              </div>
              <p className="mt-3 max-w-md text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                L&apos;IA envoie un WhatsApp personnalisé à chaque prospect, relance ceux qui ne répondent pas, décroche un créneau de rappel.
              </p>

              {/* Mock conversation distillee : 2 messages seulement (1 IA + 1 prospect).
                  Suffit a montrer le ton et le format, sans tomber dans le "AI demo
                  screenshot" verbeux qui fait pattern SaaS chatbot. Le reste
                  des exemples vit dans /aide (section "Exemples de conversations IA"). */}
              <div className="mt-8 rounded-2xl border border-[var(--border)] bg-[var(--background)] p-5">
                <div className="mb-3 flex items-center justify-between">
                  <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                    WhatsApp · il y a 4 min
                  </p>
                  <span className="inline-flex items-center gap-1.5 font-mono text-[10px] uppercase tracking-[0.16em] text-[var(--color-accent)]">
                    <span className="h-1.5 w-1.5 rounded-full bg-[var(--color-accent)] animate-pulse" />
                    En direct
                  </span>
                </div>
                <div className="space-y-2.5">
                  <div className="ml-auto max-w-[82%] rounded-2xl rounded-br-md bg-[#25D366]/95 px-3.5 py-2 text-[13.5px] leading-snug text-white">
                    Bonjour Sophie, je suis l&apos;assistante de Garage Central. Pour votre projet de Peugeot 3008, à quel moment un conseiller peut-il vous appeler&nbsp;?
                  </div>
                  <div className="max-w-[82%] rounded-2xl rounded-bl-md bg-[var(--card)] px-3.5 py-2 text-[13.5px] leading-snug text-[var(--foreground)] shadow-card">
                    Demain 14h ça irait
                  </div>
                </div>
                <p className="mt-3 font-mono text-[10px] uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)]">
                  → RDV pris, fiche prête pour le conseiller.
                </p>
              </div>
            </div>
          </article>

          {/* TILE 2, medium, checklist qualification */}
          <article className="relative col-span-12 overflow-hidden rounded-3xl border border-[var(--border)] bg-[var(--card)] p-8 lg:col-span-5 lg:p-10 scroll-reveal">
            <div className="flex items-center gap-3">
              <div className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[var(--color-accent-subtle)]">
                <ClipboardText size={20} weight="duotone" className="text-[var(--color-accent)]" />
              </div>
              <h3 className="font-display text-2xl font-semibold tracking-display-tight text-[var(--foreground)]">
                Qualification intelligente.
              </h3>
            </div>
            <p className="mt-3 text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              L&apos;IA pose les bonnes questions. Votre vendeur sait tout avant de décrocher.
            </p>
            <ul className="mt-7 space-y-3.5">
              {[
                "Budget et financement",
                "Délai d'acquisition",
                "Reprise du véhicule actuel",
                "Critères techniques (motorisation, options)",
              ].map((it) => (
                <li key={it} className="flex items-start gap-3 text-[14.5px]">
                  <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[var(--color-accent)]/12">
                    <Check size={11} weight="bold" className="text-[var(--color-accent)]" />
                  </span>
                  <span className="text-[var(--foreground)]">{it}</span>
                </li>
              ))}
            </ul>
          </article>

          {/* TILE 3, pleine largeur, layout horizontal texte / list */}
          <article className="relative col-span-12 overflow-hidden rounded-3xl border border-[var(--border)] bg-[var(--card)] p-8 lg:p-12 scroll-reveal">
            <div className="grid grid-cols-1 gap-8 lg:grid-cols-12 lg:items-center">
              <div className="lg:col-span-6">
                <div className="flex items-center gap-3">
                  <div className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[var(--color-accent-subtle)]">
                    <FileText size={20} weight="duotone" className="text-[var(--color-accent)]" />
                  </div>
                  <h3 className="font-display text-2xl font-semibold tracking-display-tight text-[var(--foreground)]">
                    Fiche de préparation prête en 5 secondes.
                  </h3>
                </div>
                <p className="mt-3 max-w-md text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                  Un briefing IA résume tout&nbsp;: projet, budget, objections, points à approfondir. Le vendeur prend le relais préparé.
                </p>
              </div>
              <div className="lg:col-span-6">
                {/* Mini fiche de prep, style "data sheet" en mono labels */}
                <dl className="grid grid-cols-1 gap-x-6 gap-y-3 rounded-2xl border border-[var(--border)] bg-[var(--background)] p-5 sm:grid-cols-2">
                  {[
                    { k: "Projet", v: "Mégane SW Estate" },
                    { k: "Budget", v: "20 – 24 k€" },
                    { k: "Délai", v: "Sous 3 mois" },
                    { k: "Reprise", v: "Clio IV 2018" },
                    { k: "Financement", v: "LOA possible" },
                    { k: "Objection clé", v: "Délais de livraison" },
                  ].map((row) => (
                    <div key={row.k} className="flex items-baseline justify-between gap-3 border-b border-[var(--border)] pb-2 last:border-0 last:pb-0">
                      <dt className="font-mono text-[10px] uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)]">
                        {row.k}
                      </dt>
                      <dd className="text-right text-[13px] font-medium text-[var(--foreground)]">
                        {row.v}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 4 : Sur la piste (How it works)
// Layout : 3 etapes horizontales sur lg+, decalees verticalement pour creer
// un rythme (effect "checkpoint sur une piste"). Cream surface. Pas de
// "3 cards identiques" : chaque etape a son numero MASSIF en mono + visuel.
// ─────────────────────────────────────────────────────────────────────────────

function HowItWorksSection() {
  const steps = [
    {
      n: "01",
      icon: Upload,
      title: "Vos leads arrivent.",
      text: "Import depuis vos sources (La Centrale, LeBonCoin, DMS) ou par fichier. Aucune intégration technique.",
    },
    {
      n: "02",
      icon: Robot,
      title: "L'IA engage la conversation.",
      text: "Leadrive envoie un WhatsApp personnalisé, mène la conversation, qualifie le besoin et propose un créneau.",
    },
    {
      n: "03",
      icon: Handshake,
      title: "Votre vendeur conclut.",
      text: "Le vendeur reçoit une fiche de préparation complète et prend le relais. Le prospect est chaud, informé, prêt.",
    },
  ];

  return (
    <section className="relative bg-[var(--background)] px-6 py-32 lg:py-40">
      <div className="mx-auto max-w-6xl">
        <div className="mb-16 max-w-2xl">
          <Eyebrow chapter="03 / 06" label="Sur la piste" />
          <h2 className="mt-6 font-display text-[40px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)] sm:text-[52px] lg:text-[60px]">
            De l&apos;import à la conclusion, en trois temps.
          </h2>
        </div>

        {/* Rail decoratif horizontal, fil rouge subtil entre les etapes */}
        <div className="relative">
          <div
            className="pointer-events-none absolute left-0 right-0 top-[44px] hidden h-px lg:block"
            style={{ background: "linear-gradient(90deg, transparent, var(--border), transparent)" }}
            aria-hidden="true"
          />
          <div className="grid grid-cols-1 gap-12 lg:grid-cols-3 lg:gap-10">
            {steps.map((s, i) => (
              <div
                key={s.n}
                className="relative scroll-reveal"
              >
                {/* Numero MASSIF en mono, presque "panneau routier" */}
                <div className="relative mb-6 flex h-[88px] items-end">
                  <span className="font-numeric text-[88px] font-semibold leading-[0.85] tracking-[-0.04em] text-[var(--color-accent)] opacity-90">
                    {s.n}
                  </span>
                  <span className="ml-3 flex h-9 w-9 items-center justify-center rounded-full bg-[var(--background)] ring-1 ring-[var(--border)]">
                    <s.icon size={18} weight="duotone" className="text-[var(--foreground)]" />
                  </span>
                </div>
                <h3 className="font-display text-[22px] font-semibold tracking-display-tight text-[var(--foreground)]">
                  {s.title}
                </h3>
                <p className="mt-3 text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                  {s.text}
                </p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 5 : Les chiffres (Stats)
// REWORK total : on QUITTE le pattern "hero metric template" (gros chiffre +
// supporting stats + accent gradient = banni par impeccable).
// Nouveau layout : 2-col editorial. Gauche = paragraphe qui weave les chiffres
// dans la narration. Droite = Tachograph 98 isole + fiche technique mini.
// Surface CREAM (le dark surface du milieu de page est supprime).
// ─────────────────────────────────────────────────────────────────────────────

function StatsSection() {
  return (
    <section className="relative bg-[var(--background)] px-6 py-32 lg:py-40">
      <div className="mx-auto max-w-6xl">
        <div className="grid grid-cols-1 gap-12 lg:grid-cols-12 lg:gap-16">
          {/* Col gauche, narration editoriale */}
          <div className="lg:col-span-7">
            <Eyebrow chapter="04 / 06" label="Les chiffres" />
            <h2 className="mt-6 font-display text-[40px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)] sm:text-[52px] lg:text-[60px]">
              Ce que change un lead bien traité.
            </h2>
            <p className="mt-6 text-[17px] leading-[1.7] text-[var(--color-lr-text-secondary)]">
              À <span className="font-semibold text-[var(--foreground)]">98&nbsp;% d&apos;ouverture</span>, WhatsApp surclasse l&apos;email (20&nbsp;%). Vos messages sont vus dans les minutes qui suivent l&apos;envoi, jamais demain matin.
            </p>
            <p className="mt-4 text-[17px] leading-[1.7] text-[var(--color-lr-text-secondary)]">
              En <span className="font-semibold text-[var(--foreground)]">moins de 5 minutes</span> entre l&apos;import et le premier message envoyé, vos prospects sont recontactés au bon moment, là où ils convertissent le plus. L&apos;IA répond <span className="font-semibold text-[var(--foreground)]">le soir, le week-end, 24/7</span>. Et <span className="font-semibold text-[var(--foreground)]">zéro intégration technique</span> pour démarrer&nbsp;: un export depuis votre DMS suffit.
            </p>
          </div>

          {/* Col droite, visuel Tachograph isole + fiche technique mini */}
          <div className="lg:col-span-5 scroll-reveal">
            <div className="rounded-3xl border border-[var(--border)] bg-[var(--card)] p-8">
              <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                Taux d&apos;ouverture WhatsApp
              </p>
              <div className="mt-5 flex justify-center">
                <Tachograph
                  value={98}
                  max={100}
                  size="lg"
                  variant="phare"
                  label="ouverture"
                />
              </div>
              <p className="mt-5 text-center text-[13.5px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                Contre 20&nbsp;% en email moyen.
              </p>
            </div>

            {/* Fiche technique mini, style data sheet automobile */}
            <dl className="mt-5 overflow-hidden rounded-3xl border border-[var(--border)] bg-[var(--card)]">
              {[
                { k: "Délai de réponse", v: "< 5 min" },
                { k: "Disponibilité", v: "24 / 7" },
                { k: "Intégration requise", v: "Aucune (CSV)" },
              ].map((row, i, arr) => (
                <div key={row.k} className={`flex items-baseline justify-between gap-3 px-6 py-4 ${i < arr.length - 1 ? "border-b border-[var(--border)]" : ""}`}>
                  <dt className="font-mono text-[10px] uppercase tracking-[0.16em] text-[var(--color-lr-text-tertiary)]">
                    {row.k}
                  </dt>
                  <dd className="font-numeric text-[15px] font-semibold text-[var(--foreground)]">
                    {row.v}
                  </dd>
                </div>
              ))}
            </dl>
          </div>
        </div>
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 6 : L'intelligence (AI Learning)
// Layout EDITORIAL 2-col, sticky titre gauche, contenu droit avec drop cap +
// 3 process steps numerotes (PAS DE CARDS, juste numerotation + paragraphes).
// Surface muted.
// ─────────────────────────────────────────────────────────────────────────────

function AiLearningSection() {
  const steps = [
    {
      n: "01",
      icon: Target,
      title: "Vos vendeurs notent en 2 clics.",
      text: "Après chaque RDV, un tap pour la qualification, un tap pour la suite prévue. Aucun formulaire, aucune perte de temps.",
    },
    {
      n: "02",
      icon: Brain,
      title: "L'IA analyse ce qui marche.",
      text: "Quels messages déclenchent un RDV ? Quels créneaux ont le meilleur taux de présence ? L'IA repère les schémas que personne ne voit.",
    },
    {
      n: "03",
      icon: ChartLineUp,
      title: "Vos résultats s'améliorent.",
      text: "Plus de prospects bien qualifiés, moins de no-show, des vendeurs mieux préparés. Votre établissement a une IA qui travaille pour lui.",
    },
  ];

  return (
    <section className="relative bg-[var(--muted)] px-6 py-32 lg:py-40">
      <div className="mx-auto max-w-6xl">
        <div className="grid grid-cols-1 gap-12 lg:grid-cols-12 lg:gap-16">
          {/* Col gauche, sticky title */}
          <div className="lg:col-span-5">
            <div className="lg:sticky lg:top-32">
              <Eyebrow chapter="05 / 06" label="L'intelligence" />
              <h2 className="mt-6 font-display text-[40px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)] sm:text-[52px] lg:text-[60px]">
                Une IA qui s&apos;améliore à chaque échange.
              </h2>
              <p className="mt-6 text-[17px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                Plus vos vendeurs l&apos;utilisent, plus elle comprend votre établissement. Elle apprend de chaque conversation et de chaque retour pour qualifier vos prochains prospects avec plus de précision.
              </p>
            </div>
          </div>

          {/* Col droite, 3 process steps verticaux (PAS DE CARDS) */}
          <div className="lg:col-span-7">
            <ol className="space-y-12">
              {steps.map((s, i) => (
                <li
                  key={s.n}
                  className="relative pl-16 scroll-reveal"
                >
                  {/* Numero en mono sur la gauche, comme une numerotation de chapitre */}
                  <span className="absolute left-0 top-1 font-numeric text-[40px] font-semibold leading-none tracking-tight text-[var(--color-accent)]">
                    {s.n}
                  </span>
                  <h3 className="font-display text-[22px] font-semibold tracking-display-tight text-[var(--foreground)]">
                    {s.title}
                  </h3>
                  <p className="mt-3 max-w-xl text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                    {s.text}
                  </p>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 7 : Tarifs, Comparaison tabulaire data-sheet
// Plus de "3 cards". Table de comparaison dense : 1 col label + 3 cols plans.
// Pro = colonne tintee ember subtle + edge top ember + badge "Recommande".
// Mobile : stack 3 plans en blocs verticaux (la table ne marche pas en mobile).
// ─────────────────────────────────────────────────────────────────────────────

const MULTI_SITE_TIERS = [
  { range: "1 – 2 sites", price: "99€", discount: null, example: "198€ (2 sites)" },
  { range: "3 – 5 sites", price: "89€", discount: "-10%", example: "267€ (3 sites)" },
  { range: "6 – 9 sites", price: "84€", discount: "-15%", example: "504€ (6 sites)" },
  { range: "10+ sites", price: "79€", discount: "-20%", example: "790€ (10 sites)" },
];

function PricingSection() {
  return (
    <section id="tarifs" className="bg-[var(--background)] px-6 py-32 lg:py-40">
      <div className="mx-auto max-w-6xl">
        <div className="mb-14 max-w-2xl">
          <Eyebrow chapter="06 / 06" label="Tarifs" />
          <h2 className="mt-6 font-display text-[40px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)] sm:text-[52px] lg:text-[60px]">
            Simple, transparent,<br className="hidden sm:inline" /> sans engagement.
          </h2>
          <p className="mt-5 text-[17px] leading-relaxed text-[var(--color-lr-text-secondary)]">
            Une seule offre, tout inclus. Pas de plans, pas de features cachées. Vous ne payez que pour les résultats.
          </p>
        </div>

        {/* ═══ CARTE OFFRE UNIQUE ═══ */}
        <div className="mx-auto max-w-2xl">
          <div className="relative overflow-hidden rounded-3xl border border-[var(--color-accent)]/40 bg-[var(--card)]">
            {/* Gradient stripe top */}
            <div
              className="absolute inset-x-0 top-0 h-1"
              style={{ background: "linear-gradient(90deg, #A8401F, #D9603A, #E07B5A)" }}
            />
            <span className="absolute -top-0 left-1/2 -translate-x-1/2 translate-y-3 inline-flex items-center rounded-full bg-[var(--color-accent)] px-4 py-1 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-white shadow-card">
              Offre unique
            </span>

            <div className="px-8 pb-8 pt-12 text-center sm:px-12">
              <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
                par point de vente / mois
              </p>
              <div className="mt-4 flex items-baseline justify-center gap-2">
                <span className="font-numeric text-[72px] font-bold leading-none text-[var(--foreground)]">
                  99
                </span>
                <span className="text-2xl font-semibold text-[var(--color-lr-text-tertiary)]">€</span>
              </div>
              <p className="mt-2 text-sm text-[var(--color-lr-text-secondary)]">HT / mois / point de vente</p>

              {/* Inclus, grille 2×2 */}
              <div className="mt-8 grid grid-cols-2 gap-3 text-left">
                <div className="rounded-xl bg-[var(--background)] p-4">
                  <p className="font-numeric text-xl font-semibold text-[var(--foreground)]">15</p>
                  <p className="mt-0.5 text-[12px] text-[var(--color-lr-text-tertiary)]">RDV confirmés inclus / mois</p>
                </div>
                <div className="rounded-xl bg-[var(--color-accent-subtle)] p-4">
                  <p className="font-numeric text-xl font-semibold text-[var(--color-accent-deep)]">8€</p>
                  <p className="mt-0.5 text-[12px] text-[var(--color-lr-text-tertiary)]">par RDV supplémentaire</p>
                </div>
                <div className="rounded-xl bg-[var(--background)] p-4">
                  <p className="font-numeric text-xl font-semibold text-[var(--foreground)]">Illimité</p>
                  <p className="mt-0.5 text-[12px] text-[var(--color-lr-text-tertiary)]">Nombre de vendeurs</p>
                </div>
                <div className="rounded-xl bg-[var(--background)] p-4">
                  <p className="font-numeric text-xl font-semibold text-[var(--foreground)]">Toutes</p>
                  <p className="mt-0.5 text-[12px] text-[var(--color-lr-text-tertiary)]">Fonctionnalités incluses</p>
                </div>
              </div>

              {/* Garanties */}
              <div className="mt-6 flex flex-wrap items-center justify-center gap-x-6 gap-y-2">
                {["1er mois offert", "Sans engagement", "Résiliable à tout moment"].map((g) => (
                  <span key={g} className="flex items-center gap-1.5 text-[13px] text-[var(--color-lr-text-secondary)]">
                    <span className="text-[var(--color-success)] font-bold">✓</span> {g}
                  </span>
                ))}
              </div>

              <a
                href="#demo"
                className="btn-phare mt-8 inline-block rounded-xl px-10 py-3.5 text-[15px] font-semibold"
              >
                Demander une démo
              </a>
            </div>
          </div>
        </div>

        {/* ═══ TABLEAU MULTI-SITES ═══ */}
        <div className="mx-auto mt-16 max-w-2xl">
          <div className="mb-6">
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-accent)]">
              Tarif multi-sites
            </p>
            <h3 className="mt-2 font-display text-xl font-bold text-[var(--foreground)]">
              Plus de sites, moins cher.
            </h3>
            <p className="mt-2 text-sm text-[var(--color-lr-text-secondary)]">
              Vous gérez plusieurs points de vente ? Le tarif baisse automatiquement.
            </p>
          </div>

          <div className="overflow-hidden rounded-2xl border border-[var(--border)]">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[var(--color-ignition)] text-white">
                  <th className="px-5 py-3 text-left font-mono text-[11px] font-semibold uppercase tracking-[0.08em]">Points de vente</th>
                  <th className="px-5 py-3 text-left font-mono text-[11px] font-semibold uppercase tracking-[0.08em]">Prix / site / mois</th>
                  <th className="hidden px-5 py-3 text-left font-mono text-[11px] font-semibold uppercase tracking-[0.08em] sm:table-cell">Réduction</th>
                  <th className="hidden px-5 py-3 text-right font-mono text-[11px] font-semibold uppercase tracking-[0.08em] sm:table-cell">Exemple</th>
                </tr>
              </thead>
              <tbody>
                {MULTI_SITE_TIERS.map((t, i) => (
                  <tr
                    key={t.range}
                    className={`border-t border-[var(--border)] ${i % 2 === 0 ? "bg-[var(--card)]" : "bg-[var(--background)]"}`}
                  >
                    <td className="px-5 py-3 font-medium text-[var(--foreground)]">{t.range}</td>
                    <td className={`px-5 py-3 font-numeric font-semibold ${t.discount ? "text-[var(--color-accent)]" : "text-[var(--foreground)]"}`}>
                      {t.price}
                    </td>
                    <td className={`hidden px-5 py-3 font-numeric font-semibold sm:table-cell ${t.discount ? "text-[var(--color-accent)]" : "text-[var(--color-lr-text-tertiary)]"}`}>
                      {t.discount ?? "—"}
                    </td>
                    <td className="hidden px-5 py-3 text-right text-[var(--color-lr-text-secondary)] sm:table-cell">
                      {t.example}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <p className="mt-3 text-[12px] text-[var(--color-lr-text-tertiary)]">
            Le prix du RDV supplémentaire (8€) reste identique quel que soit le nombre de sites.
            Les 15 RDV sont comptabilisés par site.
          </p>
        </div>
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 8 : FAQ
// Layout side-by-side 2-col flat list (PAS D'ACCORDION : impeccable et
// redesign-existing-projects flaggent l'accordion comme cliche SaaS).
// Toutes les Q&A visibles d'un coup, parcours rapide. Surface cream.
// ─────────────────────────────────────────────────────────────────────────────

function FaqSection() {
  const faqs = [
    { q: "Est-ce que les prospects savent que c'est une IA ?", a: "Oui, l'IA se présente clairement comme un assistant intelligent. C'est transparent et conforme à la réglementation IA Act. Le prospect sait qu'il parle à un assistant, mais les messages sont naturels et personnalisés." },
    { q: "Comment l'IA sait quoi écrire à chaque prospect ?", a: "L'IA analyse les données du lead (véhicule recherché, source, historique) et votre configuration (marques, atouts, consignes). Chaque message est unique et adapté au contexte du prospect." },
    { q: "Que se passe-t-il quand l'IA ne sait plus répondre ?", a: "L'IA détecte automatiquement les situations complexes (demande de prix, ton négatif, question hors scope) et passe la main au vendeur avec un message explicatif. Le vendeur reçoit une notification et reprend la conversation en un clic." },
    { q: "Est-ce que ça marche avec mon DMS ou CRM actuel ?", a: "Pour démarrer, un simple export CSV suffit. Pas besoin de connecteur ni d'informaticien. Des intégrations directes avec les DMS du marché arrivent ensuite." },
    { q: "Combien de temps pour être opérationnel ?", a: "Quelques minutes. Vous importez vos leads, on configure l'IA pour votre établissement (nom, marques, consignes), les premiers messages partent immédiatement." },
    { q: "Est-ce que l'IA s'améliore avec le temps ?", a: "Oui. Après chaque RDV, vos vendeurs donnent leur avis en 2 clics. L'IA utilise ces retours pour mieux qualifier vos prochains prospects. En formules Pro et Business, elle s'adapte de plus en plus à votre établissement." },
    { q: "Mes données sont-elles protégées ?", a: "Oui. Chiffrement, isolation complète entre établissements (multi-tenant), conforme RGPD. Vous restez propriétaire de vos données. L'IA ne mentionne jamais de prix, ne fait aucune promesse commerciale, et un filtre de sortie bloque automatiquement les contenus à risque." },
    { q: "Est-ce que je suis engagé ?", a: "Non. Pas d'engagement de durée. Vous pouvez changer de forfait ou arrêter à tout moment. La facturation est mensuelle." },
  ];

  return (
    <section id="faq" className="bg-[var(--background)] px-6 py-32 lg:py-40">
      <div className="mx-auto max-w-6xl">
        <div className="mb-14 max-w-2xl">
          <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
            Réponses
          </span>
          <h2 className="mt-3 font-display text-[40px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)] sm:text-[52px] lg:text-[60px]">
            Questions fréquentes.
          </h2>
        </div>

        {/* Grille 2-col, toutes les Q&A visibles. Pas d'accordion. */}
        <div className="grid grid-cols-1 gap-x-12 gap-y-10 lg:grid-cols-2">
          {faqs.map((f) => (
            <div key={f.q} className="scroll-reveal-fast">
              <h3 className="font-display text-[18px] font-semibold leading-snug tracking-display-tight text-[var(--foreground)]">
                {f.q}
              </h3>
              <p className="mt-3 text-[14.5px] leading-relaxed text-[var(--color-lr-text-secondary)]">
                {f.a}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 9 : Demo form
// Surface dark ignition (bookend final avec hero / footer).
// Form epure, CTA btn-phare avec island arrow.
// ─────────────────────────────────────────────────────────────────────────────

// Regex email standard (au moins un point dans le domaine, pas d'espaces).
// Aligne sur le pattern utilise cote backend pour rejeter les emails malformes.
const DEMO_EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// Telephone : minimum 8 chiffres apres normalisation (FR national, mobile, +33).
const DEMO_TEL_NORMALIZED_MIN = 8;

function DemoFormSection() {
  // Form state : la cle est `concession` cote code (variable interne) mais le
  // backend recoit { ..., concession: ... } — backend non modifie ici. Cote UI
  // le label est neutre "Nom de l'entreprise" pour inclure garages + multimarques.
  const [form, setForm] = useState({ nom: "", concession: "", email: "", telephone: "", volumeLeads: "" });
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  // Honeypot anti-bot : champ caché que seuls les robots remplissent. (Défense
  // complémentaire au rate-limit backend, qui reste la vraie protection.)
  const [honeypot, setHoneypot] = useState("");
  // Touch state : on n'affiche les erreurs inline que sur les champs deja
  // touches (blur ou submit attempt) pour eviter le mur d'erreurs au mount.
  const [touched, setTouched] = useState<Record<string, boolean>>({});

  const emailInvalid = touched.email && form.email !== "" && !DEMO_EMAIL_REGEX.test(form.email);
  const telDigits = form.telephone.replace(/\D/g, "");
  const telInvalid = touched.telephone && form.telephone !== "" && telDigits.length < DEMO_TEL_NORMALIZED_MIN;

  const formValid = form.nom.trim().length >= 2
    && form.concession.trim().length >= 2
    && DEMO_EMAIL_REGEX.test(form.email)
    && form.volumeLeads !== "";

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // Honeypot : si rempli, c'est un bot. On simule un succès sans rien envoyer
    // (ne pas signaler au bot qu'il a été détecté).
    if (honeypot) {
      setSent(true);
      return;
    }
    // Au submit, on touch tous les champs pour activer l'affichage des erreurs
    setTouched({ nom: true, concession: true, email: true, telephone: true, volumeLeads: true });
    if (!formValid) {
      setError("Vérifiez les champs avant d'envoyer.");
      return;
    }
    setSending(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/api/public/demande-demo`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setSent(true);
    } catch {
      setError("Une erreur est survenue. Réessayez ou contactez-nous directement.");
    } finally {
      setSending(false);
    }
  }

  const inputClass = "w-full rounded-xl border border-white/12 bg-white/[0.04] px-4 py-3 text-[15px] text-white placeholder-white/40 transition-colors focus:border-[var(--color-accent)] focus:outline-none focus:ring-1 focus:ring-[var(--color-accent)]/30";
  const inputClassInvalid = "w-full rounded-xl border border-[var(--color-danger)]/50 bg-[var(--color-danger)]/[0.04] px-4 py-3 text-[15px] text-white placeholder-white/40 transition-colors focus:border-[var(--color-danger)] focus:outline-none focus:ring-1 focus:ring-[var(--color-danger)]/30";

  return (
    <section id="demo" className="relative overflow-hidden bg-[var(--color-ignition)] px-6 py-32 lg:py-40">
      <div className="absolute inset-0 bg-stripes-pattern opacity-[0.025] pointer-events-none" />
      {/* Glow Ember side gauche, chaleur ponctuelle Atelier */}
      <div
        className="pointer-events-none absolute -left-32 top-1/2 h-[420px] w-[420px] -translate-y-1/2 rounded-full opacity-25 blur-3xl"
        style={{ background: "radial-gradient(circle, rgba(217,96,58,0.55) 0%, transparent 70%)" }}
        aria-hidden="true"
      />

      <div className="relative mx-auto max-w-lg text-center">
        <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent-light)]">
          Démo gratuite · 30 minutes
        </span>
        <h2 className="mt-4 mb-4 font-display text-[36px] font-bold leading-[1.05] tracking-display-tight text-white sm:text-[44px]">
          On vous le montre sur <span className="text-[var(--color-accent-light)]">vos leads</span>.
        </h2>
        <p className="mb-10 text-[15px] leading-relaxed text-white/65">
          Vous envoyez un export, on configure l&apos;IA, on vous montre les premiers messages partis et les premiers RDV décrochés. Aucune carte bancaire.
        </p>

        {sent ? (
          <div className="rounded-3xl border border-[var(--color-success)]/25 bg-[var(--color-success)]/10 p-8">
            <PaperPlaneTilt size={28} weight="duotone" className="mx-auto mb-3 text-[var(--color-success)]" />
            <p className="font-display text-lg font-semibold text-white">Demande envoyée.</p>
            <p className="mt-2 text-sm text-white/65">Nous vous recontactons dans les 24 heures pour planifier votre démo.</p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3.5 text-left" noValidate>
            {/* Honeypot anti-bot : invisible et hors tabulation pour les humains. */}
            <input
              type="text"
              name="website"
              value={honeypot}
              onChange={(e) => setHoneypot(e.target.value)}
              className="hidden"
              tabIndex={-1}
              autoComplete="off"
              aria-hidden="true"
            />
            <input
              type="text"
              placeholder="Votre nom"
              value={form.nom}
              onChange={(e) => setForm({ ...form, nom: e.target.value })}
              onBlur={() => setTouched((t) => ({ ...t, nom: true }))}
              required
              className={inputClass}
              aria-label="Votre nom"
            />
            <input
              type="text"
              placeholder="Nom de l'entreprise"
              value={form.concession}
              onChange={(e) => setForm({ ...form, concession: e.target.value })}
              onBlur={() => setTouched((t) => ({ ...t, concession: true }))}
              required
              className={inputClass}
              aria-label="Nom de l'entreprise"
            />
            <div>
              <input
                type="email"
                placeholder="Email professionnel"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                onBlur={() => setTouched((t) => ({ ...t, email: true }))}
                required
                className={emailInvalid ? inputClassInvalid : inputClass}
                aria-label="Email professionnel"
                aria-invalid={emailInvalid || undefined}
              />
              {emailInvalid && (
                <p className="mt-1.5 text-[12px] text-[var(--color-danger)]">Format d&apos;email invalide.</p>
              )}
            </div>
            <div>
              <input
                type="tel"
                placeholder="Téléphone (optionnel)"
                value={form.telephone}
                onChange={(e) => setForm({ ...form, telephone: e.target.value })}
                onBlur={() => setTouched((t) => ({ ...t, telephone: true }))}
                className={telInvalid ? inputClassInvalid : inputClass}
                aria-label="Téléphone"
                aria-invalid={telInvalid || undefined}
              />
              {telInvalid && (
                <p className="mt-1.5 text-[12px] text-[var(--color-danger)]">Numéro trop court, vérifiez le format.</p>
              )}
            </div>
            <select
              value={form.volumeLeads}
              onChange={(e) => setForm({ ...form, volumeLeads: e.target.value })}
              onBlur={() => setTouched((t) => ({ ...t, volumeLeads: true }))}
              required
              className={inputClass}
              aria-label="Combien de leads traitez-vous par mois"
            >
              <option value="" className="bg-[var(--color-ignition)]">Combien de leads traitez-vous par mois&nbsp;?</option>
              <option value="< 100" className="bg-[var(--color-ignition)]">Moins de 100</option>
              <option value="100-300" className="bg-[var(--color-ignition)]">100 à 300</option>
              <option value="300-500" className="bg-[var(--color-ignition)]">300 à 500</option>
              <option value="500+" className="bg-[var(--color-ignition)]">Plus de 500</option>
            </select>

            {error && <p className="text-sm text-[var(--color-danger)]">{error}</p>}

            <button
              type="submit"
              disabled={sending || !formValid}
              className="btn-phare btn-magnetic btn-magnetic--dark group inline-flex w-full items-center justify-center rounded-xl py-3.5 text-[15px] font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {sending ? "Envoi en cours..." : (
                <>
                  Demander ma démo gratuite
                  <span className="btn-island-icon">
                    <ArrowRight size={14} weight="bold" />
                  </span>
                </>
              )}
            </button>

            <p className="text-[12px] text-white/45">
              En soumettant ce formulaire, vous acceptez d&apos;être contacté par l&apos;équipe Leadrive. Vos données ne seront jamais partagées.
            </p>
          </form>
        )}
      </div>
    </section>
  );
}
