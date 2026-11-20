"use client";

import { useEffect, useState } from "react";
import type { Step, EventData, TooltipRenderProps } from "react-joyride";
import { useJoyride, EVENTS } from "react-joyride";
import { useCurrentRole } from "@/lib/auth";
import { useUpdateOnboarding } from "@/lib/onboarding";

// Step etendu : ajoute un champ "managerOnly" pour filtrer selon le role. Les
// elements cibles doivent exister dans le DOM au moment du step (sinon joyride
// bloque ou saute silencieusement). Les entrees Acquisition + Reglages
// n'apparaissent dans la sidebar QUE pour le manager (cf Sidebar.tsx).
type TourStep = Step & { managerOnly?: boolean };

// Ordre des steps aligne avec la sidebar (dashboard -> nav-inbox -> nav-leads
// -> ... -> Acquisition (manager) -> Reglages (manager) -> aide -> bulle).
const ALL_STEPS: TourStep[] = [
  {
    target: "[data-tour='dashboard']",
    content: "Voici votre tableau de bord. Chaque matin, retrouvez ici vos priorites du jour.",
    placement: "center",
    skipBeacon: true,
  },
  {
    target: "[data-tour='action-center']",
    content: "Vos actions prioritaires : prospects a rappeler, conversations a reprendre, ventes a confirmer.",
    placement: "bottom",
    skipBeacon: true,
  },
  {
    target: "[data-tour='briefing']",
    content: "L'IA analyse vos leads et vous prepare un briefing quotidien personnalise.",
    placement: "top",
    skipBeacon: true,
  },
  {
    target: "[data-tour='nav-inbox']",
    content: "Toutes vos conversations WhatsApp avec vos prospects sont centralisees ici.",
    placement: "right",
    skipBeacon: true,
  },
  {
    target: "[data-tour='nav-leads']",
    content: "Retrouvez tous vos prospects avec leur scoring IA et leur historique.",
    placement: "right",
    skipBeacon: true,
  },
  {
    target: "[data-tour='nav-planning']",
    content: "Votre planning de la journee : appels, relances, RDV.",
    placement: "right",
    skipBeacon: true,
  },
  {
    target: "[data-tour='nav-stats']",
    content: "Suivez vos performances : taux de conversion, pipeline, evolution dans le temps.",
    placement: "right",
    skipBeacon: true,
  },
  {
    target: "[data-tour='nav-import']",
    content: "Importez vos prospects depuis un fichier CSV, Excel ou par copier-coller.",
    placement: "right",
    skipBeacon: true,
    managerOnly: true,
  },
  {
    target: "[data-tour='nav-email-import']",
    content: "Recevez vos prospects automatiquement par email, Le Bon Coin, La Centrale et autres plateformes. Une adresse dediee est genere pour votre etablissement.",
    placement: "right",
    skipBeacon: true,
    managerOnly: true,
  },
  {
    target: "[data-tour='nav-equipe']",
    content: "Gerez votre equipe : invitez des vendeurs, suivez leur activite.",
    placement: "right",
    skipBeacon: true,
    managerOnly: true,
  },
  {
    target: "[data-tour='nav-config-ia']",
    content: "Configurez votre etablissement : horaires, adresse, telephone, et activez votre WhatsApp Business.",
    placement: "right",
    skipBeacon: true,
    managerOnly: true,
  },
  {
    target: "[data-tour='nav-aide']",
    content: "Documentation complete et visite guidee relancable a tout moment.",
    placement: "right",
    skipBeacon: true,
  },
  {
    target: "[data-tour='help-bubble']",
    content: "Besoin d'aide ? Cliquez ici pour discuter avec Nora, notre assistante IA. Elle repond a toutes vos questions sur l'outil. Vous pouvez aussi basculer en mode \"Simuler un prospect\" pour tester comment l'IA repond a un prospect WhatsApp, ideal pour vous familiariser avec l'IA avant de lancer vos premieres conversations.",
    placement: "top-end",
    skipBeacon: true,
  },
];

function TourTooltip({ backProps, closeProps, index, isLastStep, primaryProps, skipProps, step, size, tooltipProps }: TooltipRenderProps) {
  return (
    <div
      {...tooltipProps}
      className="rounded-xl border border-[var(--border)] bg-[var(--card)] shadow-xl"
      style={{ maxWidth: 360, minWidth: 280 }}
    >
      {/* Header avec progress et bouton fermer */}
      <div className="flex items-center justify-between px-4 pt-3 pb-1">
        <span className="text-xs font-medium text-[var(--color-lr-text-tertiary)]">
          {index + 1} / {size}
        </span>
        <button
          {...closeProps}
          className="rounded-md p-1 text-[var(--color-lr-text-tertiary)] hover:text-[var(--foreground)] hover:bg-[var(--muted)] transition-colors"
          aria-label="Fermer la visite guidée"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Contenu */}
      <div className="px-4 py-2 text-sm text-[var(--foreground)] leading-relaxed">
        {step.content}
      </div>

      {/* Footer */}
      <div className="flex items-center justify-between px-4 pt-1 pb-3">
        <button
          {...skipProps}
          className="text-xs text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)] transition-colors"
        >
          Passer la visite
        </button>
        <div className="flex items-center gap-2">
          {index > 0 && (
            <button
              {...backProps}
              className="rounded-lg border border-[var(--border)] px-3 py-1.5 text-xs font-medium text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] transition-colors"
            >
              Precedent
            </button>
          )}
          <button
            {...primaryProps}
            className="rounded-lg btn-primary px-3 py-1.5 text-xs font-medium text-white transition-colors"
          >
            {isLastStep ? "Terminer" : "Suivant"}
          </button>
        </div>
      </div>
    </div>
  );
}

interface GuidedTourProps {
  run: boolean;
  onFinish: () => void;
}

export default function GuidedTour({ run, onFinish }: GuidedTourProps) {
  const { role } = useCurrentRole();
  const updateOnboarding = useUpdateOnboarding();
  const [steps, setSteps] = useState<Step[]>([]);

  useEffect(() => {
    // Tant que role n'est pas resolu (Clerk en cours de chargement), on garde
    // steps vide. Joyride ne demarre que quand steps.length > 0 (cf prop run
    // ci-dessous), ca evite un bug de timing ou run passe a true avant que
    // les steps soient prets, et joyride ne demarrait jamais ensuite.
    if (!role) {
      setSteps([]);
      return;
    }
    // Filtre les steps managerOnly pour les vendeurs (les entrees sidebar
    // correspondantes n'existent pas pour eux).
    const tourSteps = ALL_STEPS
      .filter((s) => role === "MANAGER" || !s.managerOnly)
      .map(({ managerOnly: _, ...step }) => step as Step);
    setSteps(tourSteps);
  }, [role]);

  function handleEvent(data: EventData) {
    if (data.type === EVENTS.TOUR_END) {
      updateOnboarding({ onboardingTermine: true }).catch(() => {});
      onFinish();
    }
  }

  // run effectif uniquement quand les steps sont prets : sinon joyride
  // s'initialise avec steps=[] et ignore les changements ulterieurs de steps,
  // ce qui empechait le tour de demarrer apres l'onboarding (timing entre
  // setRunTour(true) cote Dashboard et la resolution async de role).
  const runEffectif = run && steps.length > 0;
  const { Tour } = useJoyride({
    steps,
    continuous: true,
    run: runEffectif,
    onEvent: handleEvent,
    tooltipComponent: TourTooltip,
    options: {
      primaryColor: "#6366F1",
      zIndex: 10000,
      spotlightRadius: 12,
      overlayColor: "rgba(0, 0, 0, 0.5)",
    },
  });

  if (!runEffectif) return null;

  return Tour;
}
