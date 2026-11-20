import * as Sentry from "@sentry/nextjs";

const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;

// CNIL : le tracing de performance est un traceur NON strictement nécessaire ; il ne doit
// pas se déclencher sans consentement sur les pages publiques. On le désactive donc sur la
// landing et les pages publiques (tracesSampleRate=0), tout en gardant la capture
// d'exceptions partout (défendable au titre de l'intérêt légitime sécurité/stabilité).
const PUBLIC_PREFIXES = ["/mentions-legales", "/politique-confidentialite", "/plaquette", "/login"];
const path = typeof window !== "undefined" ? window.location.pathname : "/";
const isPublicPage = path === "/" || PUBLIC_PREFIXES.some((p) => path.startsWith(p));

if (dsn) {
  Sentry.init({
    dsn,
    environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT ?? process.env.NODE_ENV,
    // Tracing perf : 0 sur les pages publiques (CNIL), sinon 10% prod / 100% dev.
    tracesSampleRate: isPublicPage ? 0 : (process.env.NODE_ENV === "production" ? 0.1 : 1.0),
    // Replay des sessions : desactive par defaut (cout + RGPD). A activer plus tard si besoin.
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: 0,
    // Ne pas envoyer d'evenements personnels (PII) automatiquement
    sendDefaultPii: false,
  });
}

export const onRouterTransitionStart = Sentry.captureRouterTransitionStart;
