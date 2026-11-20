"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import AppLayout from "@/components/AppLayout";
import DashboardVendeur from "@/components/dashboard/DashboardVendeur";
import DashboardManager from "@/components/dashboard/DashboardManager";
import GuidedTour from "@/components/onboarding/GuidedTour";
import { useCurrentRole, useMe } from "@/lib/auth";

function DashboardContent() {
  const router = useRouter();
  const { role, isLoaded } = useCurrentRole();
  const searchParams = useSearchParams();
  const me = useMe();
  const [runTour, setRunTour] = useState(false);

  useEffect(() => {
    if (isLoaded && role === "SUPER_ADMIN") {
      router.replace("/admin");
    }
  }, [isLoaded, role, router]);

  useEffect(() => {
    if (!me) return;
    const tourParam = searchParams.get("tour");
    if (tourParam === "1") {
      setRunTour(true);
      router.replace("/dashboard", { scroll: false });
    } else if (!me.onboardingTermine && role === "VENDEUR") {
      setRunTour(true);
    }
  }, [me, searchParams, role, router]);

  if (!isLoaded) return null;
  if (role === "SUPER_ADMIN") return null;

  return (
    <AppLayout>
      <div data-tour="dashboard">
        {role === "VENDEUR" ? <DashboardVendeur /> : <DashboardManager />}
      </div>
      <GuidedTour run={runTour} onFinish={() => setRunTour(false)} />
    </AppLayout>
  );
}

export default function DashboardPage() {
  return (
    <Suspense>
      <DashboardContent />
    </Suspense>
  );
}
