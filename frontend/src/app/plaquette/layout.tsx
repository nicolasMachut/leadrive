import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Plaquette commerciale | Leadrive",
  description:
    "Découvrez l'offre Leadrive : l'IA qui contacte vos prospects automobile par WhatsApp en moins de 5 minutes et génère des rendez-vous qualifiés.",
  robots: { index: false, follow: false },
};

/**
 * Layout minimal pour la plaquette commerciale.
 * Pas de header/footer du site, la brochure a son propre design.
 * On masque aussi la gradient bar du root layout.
 */
export default function PlaquetteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <>
      {/* Masquer la barre gradient du root layout sur cette page */}
      <style>{`
        .bar-gradient { display: none !important; }
        body { background: #F4EFE5; }
        @media print {
          body, body * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; color-adjust: exact !important; }
          body { background: transparent !important; }
        }
      `}</style>
      {children}
    </>
  );
}
