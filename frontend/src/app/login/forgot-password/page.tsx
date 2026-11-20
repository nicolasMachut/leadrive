"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSignIn, useAuth, useClerk } from "@clerk/nextjs";
import { extractClerkError } from "@/lib/clerk-errors";
import ForceLightMode from "@/components/common/ForceLightMode";
import LeadriveLogo from "@/components/common/LeadriveLogo";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const { isSignedIn, isLoaded } = useAuth();
  const { signIn } = useSignIn();
  const { setActive } = useClerk();
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [nouveauMotDePasse, setNouveauMotDePasse] = useState("");
  const [step, setStep] = useState<"email" | "code">("email");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");

  async function handleEmailSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isLoaded) return;
    setError("");
    setInfo("");
    setLoading(true);
    try {
      // Cast: la strategie reset_password_email_code n'est pas dans les types Clerk stricts mais est valide cote API
      await signIn.create({
        strategy: "reset_password_email_code",
        identifier: email,
      } as unknown as Parameters<typeof signIn.create>[0]);
      setStep("code");
      setInfo("Un code de vérification a été envoyé à votre email.");
    } catch (err) {
      setError(extractClerkError(err));
    } finally {
      setLoading(false);
    }
  }

  async function handleResetSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isLoaded) return;
    setError("");
    setLoading(true);
    try {
      // Cast: l'API stricte de Clerk v6 ne type pas attemptFirstFactor sur SignInFutureResource
      await (signIn as unknown as {
        attemptFirstFactor: (params: { strategy: string; code: string; password: string }) => Promise<unknown>;
      }).attemptFirstFactor({
        strategy: "reset_password_email_code",
        code,
        password: nouveauMotDePasse,
      });
      if (signIn.status === "complete" && signIn.createdSessionId) {
        await setActive({ session: signIn.createdSessionId });
        router.push("/dashboard");
      } else {
        setError("Étape supplémentaire requise. Contactez le support.");
      }
    } catch (err) {
      setError(extractClerkError(err));
    } finally {
      setLoading(false);
    }
  }

  if (isSignedIn) {
    router.push("/dashboard");
    return null;
  }

  const inputClass =
    "w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-4 py-2.5 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-secondary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-colors";

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--background)] px-6 py-12">
      <ForceLightMode />
      <div className="w-full max-w-md animate-fade-in">
        <div className="mb-8">
          <LeadriveLogo height={32} priority className="w-auto" />
        </div>

        <h1 className="mb-2 text-2xl font-bold text-[var(--foreground)]">
          {step === "email" ? "Mot de passe oublié" : "Réinitialiser le mot de passe"}
        </h1>
        <p className="mb-8 text-sm text-[var(--color-lr-text-secondary)]">
          {step === "email"
            ? "Entrez votre email, nous vous enverrons un code de vérification."
            : "Saisissez le code reçu par email et choisissez un nouveau mot de passe."}
        </p>

        {step === "email" ? (
          <form onSubmit={handleEmailSubmit} className="flex flex-col gap-5">
            <div>
              <label htmlFor="email" className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">
                Email
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
                className={inputClass}
              />
            </div>
            <button
              type="submit"
              disabled={loading || !isLoaded}
              className="rounded-lg btn-primary py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
            >
              {loading ? "Envoi..." : "Envoyer le code"}
            </button>
          </form>
        ) : (
          <form onSubmit={handleResetSubmit} className="flex flex-col gap-5">
            <div>
              <label htmlFor="code" className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">
                Code de vérification
              </label>
              <input
                id="code"
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
                autoComplete="one-time-code"
                className={inputClass}
                placeholder="123456"
              />
            </div>
            <div>
              <label htmlFor="newPassword" className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">
                Nouveau mot de passe
              </label>
              <input
                id="newPassword"
                type="password"
                value={nouveauMotDePasse}
                onChange={(e) => setNouveauMotDePasse(e.target.value)}
                required
                minLength={8}
                autoComplete="new-password"
                className={inputClass}
              />
            </div>
            <button
              type="submit"
              disabled={loading || !isLoaded}
              className="rounded-lg btn-primary py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
            >
              {loading ? "Réinitialisation..." : "Réinitialiser"}
            </button>
          </form>
        )}

        {info && (
          <div className="mt-4 rounded-lg bg-accent/10 border border-accent/30 p-3 text-sm text-accent">
            {info}
          </div>
        )}
        {error && (
          <div className="mt-4 rounded-lg bg-danger/10 border border-danger/20 p-3 text-sm text-danger">
            {error}
          </div>
        )}

        <div className="mt-6 text-center">
          <Link href="/login" className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]">
            ← Retour à la connexion
          </Link>
        </div>
      </div>
    </div>
  );
}
