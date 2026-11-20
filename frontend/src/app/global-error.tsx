"use client";

import * as Sentry from "@sentry/nextjs";
import { useEffect } from "react";

/**
 * Fallback dernier recours quand le root layout lui-meme crash (avant que
 * error.tsx ne puisse rendre). Doit etre minimaliste, autonome, sans
 * dependances de styling externes (le layout n'a pas pu monter).
 *
 * Sentry capture toujours l'erreur. Le visuel reste sobre, palette inline
 * (Atelier sand + ember + ink) pour rester cohérent même sans Tailwind.
 */
export default function GlobalError({
  error,
}: {
  error: Error & { digest?: string };
}) {
  useEffect(() => {
    Sentry.captureException(error);
  }, [error]);

  return (
    <html lang="fr">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          backgroundColor: "#F4EFE5",
          color: "#1A1B23",
          fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
          display: "flex",
          flexDirection: "column",
          alignItems: "flex-start",
          justifyContent: "center",
          padding: "0 24px",
        }}
      >
        <div
          style={{
            height: 3,
            background: "linear-gradient(90deg, #A8401F 0%, #D9603A 50%, #E07B5A 100%)",
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
          }}
        />
        <div style={{ maxWidth: 640, marginLeft: "auto", marginRight: "auto", width: "100%" }}>
          <p
            style={{
              fontFamily: "ui-monospace, 'JetBrains Mono', monospace",
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: "0.22em",
              textTransform: "uppercase",
              color: "#D9603A",
              margin: 0,
            }}
          >
            Erreur critique
          </p>
          <h1
            style={{
              margin: "16px 0 0",
              fontSize: 40,
              lineHeight: 1.05,
              letterSpacing: "-0.035em",
              fontWeight: 700,
              color: "#1A1B23",
            }}
          >
            Application indisponible.
          </h1>
          <p
            style={{
              margin: "20px 0 0",
              fontSize: 15,
              lineHeight: 1.6,
              color: "#4B4D58",
              maxWidth: 560,
            }}
          >
            Une erreur a empêché l&apos;affichage de l&apos;interface. L&apos;équipe a été
            notifiée automatiquement. Réessayez dans quelques secondes, vos données
            ne sont pas affectées.
          </p>
          {error.digest && (
            <p
              style={{
                margin: "16px 0 0",
                fontFamily: "ui-monospace, monospace",
                fontSize: 11,
                color: "#8B8676",
              }}
            >
              Référence : <span style={{ color: "#1A1B23" }}>{error.digest}</span>
            </p>
          )}
          <div style={{ marginTop: 36, display: "flex", flexWrap: "wrap", gap: 12 }}>
            <button
              type="button"
              onClick={() => window.location.reload()}
              style={{
                background: "linear-gradient(180deg, #E07B5A 0%, #D9603A 55%, #B84A28 100%)",
                color: "#FFFFFF",
                border: "1px solid rgba(255,255,255,0.16)",
                borderRadius: 12,
                padding: "12px 20px",
                fontSize: 14,
                fontWeight: 600,
                cursor: "pointer",
                boxShadow:
                  "inset 0 1px 0 rgba(255,255,255,0.30), 0 1px 2px rgba(120,50,25,0.32), 0 4px 12px rgba(217,96,58,0.25)",
              }}
            >
              Réessayer
            </button>
            <a
              href="/"
              style={{
                background: "#FBF8F1",
                color: "#1A1B23",
                border: "1px solid #D8CFB8",
                borderRadius: 12,
                padding: "12px 20px",
                fontSize: 14,
                fontWeight: 500,
                textDecoration: "none",
              }}
            >
              Retour à l&apos;accueil
            </a>
          </div>
        </div>
      </body>
    </html>
  );
}
