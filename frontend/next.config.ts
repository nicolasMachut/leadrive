import type { NextConfig } from "next";
import { withSentryConfig } from "@sentry/nextjs";

const nextConfig: NextConfig = {
  output: "standalone",
  // Autorise le HMR cross-origin depuis le tunnel ngrok (Next.js 16+).
  allowedDevOrigins: ["violation-skirmish-squabble.ngrok-free.dev"],
  // View Transitions API : active la transition cinematique cross-route.
  // Le navigateur (Chrome/Edge/Safari 116+) anime les elements partages entre
  // routes en se basant sur leur `view-transition-name`. Firefox tombe en fallback
  // instant (pas d'anim mais pas de regression). Cf. globals.css pour les CSS rules.
  experimental: {
    viewTransition: true,
  },
  // Proxy local : tous les appels /api/* et /actuator/* sont relayes vers le backend
  // Spring (defaut http://localhost:8080). Active uniquement en dev — en prod le
  // frontend appelle le backend public directement via NEXT_PUBLIC_API_URL.
  //
  // Permet de servir le frontend derriere un tunnel HTTPS (ngrok, cloudflared) :
  // - Pages frontend et appels API restent same-origin (pas de mixed content, pas de CORS).
  // - Les webhooks entrants (Twilio, Clerk, SendGrid) recus sur /api/webhook/* via le
  //   tunnel sont automatiquement relayes au backend local. Un seul tunnel suffit.
  async rewrites() {
    if (process.env.NODE_ENV !== "development") return [];
    const backend = process.env.BACKEND_PROXY_URL || "http://localhost:8080";

    // Clerk proxy : en dev derriere ngrok, les scripts et appels API Clerk
    // charges depuis adapted-aardvark-48.clerk.accounts.dev sont bloques
    // (cross-origin, third-party cookies). Le proxy route tout via le domaine
    // de l'app (ngrok) pour eliminer les problemes cross-origin.
    // Active uniquement si NEXT_PUBLIC_CLERK_PROXY_URL est defini.
    const clerkProxy = process.env.NEXT_PUBLIC_CLERK_PROXY_URL;
    const clerkDomain = process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY
      ? `https://${Buffer.from(process.env.NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY.replace("pk_test_", "").replace("pk_live_", ""), "base64").toString().replace("$", "")}`
      : "";

    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/actuator/:path*", destination: `${backend}/actuator/:path*` },
      // Clerk proxy — rewrites /__clerk/* vers le Frontend API Clerk
      ...(clerkProxy && clerkDomain
        ? [{ source: `${clerkProxy}/:path*`, destination: `${clerkDomain}/:path*` }]
        : []),
    ];
  },
  async headers() {
    const isProd = process.env.NODE_ENV === "production";
    // CSP en mode Report-Only par defaut : observe les violations sans bloquer,
    // pour ajustement progressif. Quand stable, on bascule en Content-Security-Policy
    // strict en passant CSP_ENFORCE=1.
    // Domaines whitelist : self + Clerk (auth) + Sentry (errors) + Vercel/Twilio (media WhatsApp).
    const cspParts = [
      "default-src 'self'",
      // connect.facebook.net : SDK Facebook charge par le bouton Embedded Signup WhatsApp.
      "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://*.clerk.accounts.dev https://*.clerk.com https://challenges.cloudflare.com https://connect.facebook.net",
      // graph.facebook.com : XHR du SDK Facebook pendant le flow Self Sign-up.
      "connect-src 'self' https://*.clerk.accounts.dev https://*.clerk.com https://*.sentry.io https://*.ingest.sentry.io https://graph.facebook.com",
      "img-src 'self' data: blob: https://*.clerk.com https://*.twilio.com https://img.clerk.com",
      // *.facebook.com : popup OAuth Embedded Signup (login + permissions WABA).
      "frame-src 'self' https://*.clerk.com https://challenges.cloudflare.com https://*.facebook.com",
      "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
      "font-src 'self' data: https://fonts.gstatic.com",
      // Clerk cree ses workers depuis des blobs (worker-src tombe sur script-src par defaut, et bloque blob:).
      "worker-src 'self' blob:",
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'",
    ].join("; ");
    const cspHeader = process.env.CSP_ENFORCE === "1"
      ? "Content-Security-Policy"
      : "Content-Security-Policy-Report-Only";

    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
          // HSTS uniquement en prod (en dev sur localhost http, ca casserait les outils).
          // max-age 1 an + preload pour HSTS preload list eligible.
          ...(isProd
            ? [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains; preload" }]
            : []),
          // CSP en mode Report-Only par defaut (cf. commentaire au-dessus). En prod,
          // basculer CSP_ENFORCE=1 dans l'env Railway apres avoir verifie les rapports.
          { key: cspHeader, value: cspParts },
        ],
      },
    ];
  },
};

// Sentry est opt-in : si SENTRY_DSN n'est pas defini, on n'active pas le wrapper
// (evite que le build local echoue sans config Sentry).
const sentryEnabled = Boolean(process.env.SENTRY_DSN);

export default sentryEnabled
  ? withSentryConfig(nextConfig, {
      // Org / project Sentry — necessaires pour l'upload des source maps en build
      org: process.env.SENTRY_ORG,
      project: process.env.SENTRY_PROJECT,
      // Token requis uniquement pour l'upload des source maps (CI)
      authToken: process.env.SENTRY_AUTH_TOKEN,
      // Coupe les logs verbeux du plugin Sentry pendant le build
      silent: !process.env.CI,
      // Tunnel desactive : la route /monitoring ne se generait pas correctement en
      // Next 16 / Sentry 10.51 (404). Les events partent direct vers ingest.sentry.io.
      // Inconvenient : peut etre bloque par certains ad-blockers cote client.
      // Cache les erreurs d'upload de source maps si pas de token (dev)
      sourcemaps: {
        disable: !process.env.SENTRY_AUTH_TOKEN,
      },
      // Desactive la telemetrie du plugin Sentry
      telemetry: false,
    })
  : nextConfig;
