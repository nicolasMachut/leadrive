"use client";

import { Suspense, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import AppLayout from "@/components/AppLayout";
import Tabs from "@/components/common/Tabs";
import dynamic from "next/dynamic";

const MaJourneeView = dynamic(() => import("@/components/planning/MaJourneeView"), { ssr: false });
const CalendrierView = dynamic(() => import("@/components/planning/CalendrierView"), { ssr: false });

export default function PlanningPage() {
  return <Suspense><PlanningContent /></Suspense>;
}

function PlanningContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [vue, setVue] = useState<"journee" | "calendrier">(
    searchParams.get("vue") === "calendrier" ? "calendrier" : "journee"
  );

  function toggleVue(mode: "journee" | "calendrier") {
    setVue(mode);
    const params = new URLSearchParams(searchParams.toString());
    if (mode === "calendrier") params.set("vue", "calendrier");
    else params.delete("vue");
    router.replace(`/planning${params.toString() ? "?" + params.toString() : ""}`, { scroll: false });
  }

  return (
    <AppLayout>
      <div className="mx-auto max-w-6xl px-6 py-6">
        {/* Tab bar minimaliste — pas de PageHeader/titre ici, chaque vue (Ma
            Journée, Calendrier) possède son propre header éditorial avec le
            contexte dynamique (jour ou mois). Évite le double H1. */}
        <div className="mb-6 flex justify-end">
          <Tabs
            value={vue}
            onChange={toggleVue}
            items={[
              {
                value: "journee",
                label: "Ma journée",
                icon: (
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                      d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
                  </svg>
                ),
              },
              {
                value: "calendrier",
                label: "Calendrier",
                icon: (
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                      d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                ),
              },
            ]}
          />
        </div>

        {/* key={vue} : remount du sous-arbre quand on change d'onglet, declenche
            l'animation tab-content-enter. */}
        <div key={vue} className="animate-tab-content">
          {vue === "journee" && <MaJourneeView />}
          {vue === "calendrier" && <CalendrierView />}
        </div>
      </div>
    </AppLayout>
  );
}
