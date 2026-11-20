import type { Metadata } from "next";
import { Geist, JetBrains_Mono, Bricolage_Grotesque } from "next/font/google";
import { ClerkProvider } from "@clerk/nextjs";
import { frFR } from "@clerk/localizations";
import AppProviders from "@/components/AppProviders";
import "./globals.css";

// Geist Sans (Vercel), remplace Inter. Inter est l'empreinte n°1 du SaaS template
// "AI-generated". Geist garde les memes proportions geometriques mais avec une
// fabrication plus refinee (ouvertures plus subtiles, terminaisons plus fines).
// C'est le pairing premium de reference pour les sites cotes 50k€+ en 2026.
const geist = Geist({
  variable: "--font-sans-body",
  subsets: ["latin"],
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-jetbrains-mono",
  subsets: ["latin"],
  display: "swap",
});

// Police d'affichage signature, Bricolage Grotesque (variable font, axes opsz/wght/wdth).
// Choix delibere : se distingue de tous les SaaS clones avec une personnalite plus
// industrielle/automotive sans verser dans le cliche garagiste. Reservee aux titres,
// KPI heros et eyebrows. Le body est en Geist Sans.
const bricolage = Bricolage_Grotesque({
  variable: "--font-bricolage",
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Leadrive, Assistant IA pour établissements automobiles",
    template: "%s | Leadrive",
  },
  description: "Leadrive relance automatiquement vos leads automobile par WhatsApp grace a l'IA. Import CSV, mapping intelligent, conversations automatiques, pipeline Kanban. Essai gratuit.",
  metadataBase: new URL("https://leadrive.fr"),
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    locale: "fr_FR",
    siteName: "Leadrive",
    title: "Leadrive, Assistant IA pour établissements automobiles",
    description: "Leadrive relance automatiquement vos leads automobile par WhatsApp grace a l'IA. Import CSV, mapping intelligent, conversations automatiques, pipeline Kanban.",
    url: "https://leadrive.fr",
    images: [{ url: "/og-image.jpg", width: 1200, height: 630, alt: "Leadrive, Performance commerciale automobile" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "Leadrive, Assistant IA pour établissements automobiles",
    description: "Leadrive relance automatiquement vos leads automobile par WhatsApp grace a l'IA.",
    images: ["/og-image.jpg"],
  },
  robots: { index: true, follow: true },
  icons: {
    icon: [
      { url: "/favicon.ico", sizes: "32x32" },
      { url: "/leadrive_icon.svg", type: "image/svg+xml" },
    ],
    apple: "/leadrive_icon_192.png",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // afterSignOutUrl="/login" : sans ça Clerk redirige par défaut vers "/" (landing)
  // à la déconnexion, puis notre code force "/login" → double navigation = flash
  // landing → login. On pointe directement vers /login pour une sortie propre.
  return (
    <ClerkProvider localization={frFR} afterSignOutUrl="/login">
      <html
        lang="fr"
        className={`${geist.variable} ${jetbrainsMono.variable} ${bricolage.variable} h-full antialiased`}
        suppressHydrationWarning
      >
        <head>
          {/*
            Applique le dark mode AVANT l'hydratation React pour eviter le flash
            light->dark et garantir que la classe `dark` est presente sur toutes
            les pages (login inclus), pas seulement celles qui montent la Sidebar.
          */}
          <script
            dangerouslySetInnerHTML={{
              __html:
                "try{if(localStorage.getItem('leadrive_dark')==='true')document.documentElement.classList.add('dark')}catch(e){}",
            }}
          />
        </head>
        <body className="min-h-full flex flex-col font-sans">
          {/* Signature gradient bar, fil rouge brand sur TOUTES les pages.
              Shimmer très lent (9s) "live" pour signaler que le produit est vivant
              sans clignoter. prefers-reduced-motion coupe automatiquement. */}
          <div className="bar-gradient bar-gradient-live fixed top-0 left-0 right-0 z-[200]" />
          <AppProviders>{children}</AppProviders>
        </body>
      </html>
    </ClerkProvider>
  );
}
