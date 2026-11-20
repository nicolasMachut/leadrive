"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useSignUp, useSignIn, useClerk, useAuth } from "@clerk/nextjs";
import { extractClerkError } from "@/lib/clerk-errors";
import ForceLightMode from "@/components/common/ForceLightMode";
import LeadriveLogo from "@/components/common/LeadriveLogo";

export default function AcceptInvitationPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex items-center justify-center bg-[var(--background)]">
        <ForceLightMode />
        <p className="text-sm text-[var(--color-lr-text-secondary)]">Chargement...</p>
      </div>
    }>
      <AcceptInvitationForm />
    </Suspense>
  );
}

function AcceptInvitationForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { signUp } = useSignUp();
  const { signIn } = useSignIn();
  const clerk = useClerk();
  const { setActive } = clerk;
  const { isSignedIn } = useAuth();

  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const ticket = searchParams.get("__clerk_ticket");
  const status = searchParams.get("__clerk_status");

  // Apres acceptation d'invitation, on doit declencher un /api/me avant de
  // rediriger : le mirror manager est en INVITE sans clerk_user_id, /api/me
  // le linke via email match (avec fallback Clerk API cote backend si l'email
  // n'est pas dans le JWT). Permet aussi la redirection contextuelle
  // /onboarding pour un manager fraichement invite, sans flash /dashboard.
  async function verifierMirrorPuisRediriger() {
    const apiBase = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
    try {
      const token = await clerk.session?.getToken();
      if (!token) {
        router.push("/dashboard");
        return;
      }
      const res = await fetch(`${apiBase}/api/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const me = await res.json();
        if (me.role === "SUPER_ADMIN") {
          router.push("/admin");
          return;
        }
        if (me.role === "MANAGER" && !me.onboardingTermine) {
          router.push("/onboarding");
          return;
        }
      }
      router.push("/dashboard");
    } catch {
      router.push("/dashboard");
    }
  }

  useEffect(() => {
    if (isSignedIn) {
      verifierMirrorPuisRediriger();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isSignedIn]);

  if (!ticket) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[var(--background)] px-6">
        <div className="w-full max-w-md text-center">
          <div className="mb-6 flex items-center justify-center gap-3">
            <LeadriveLogo height={32} priority className="w-auto" />
          </div>
          <h1 className="text-xl font-bold text-[var(--foreground)] mb-2">Lien d&apos;invitation invalide</h1>
          <p className="text-sm text-[var(--color-lr-text-secondary)] mb-6">Ce lien d&apos;invitation est invalide ou a expiré.</p>
          <Link href="/login" className="text-sm font-medium text-accent hover:text-accent-hover">
            Retour à la connexion
          </Link>
        </div>
      </div>
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");

    if (password !== passwordConfirm) {
      setError("Les mots de passe ne correspondent pas.");
      return;
    }
    if (password.length < 8) {
      setError("Le mot de passe doit contenir au moins 8 caractères.");
      return;
    }

    setLoading(true);

    try {
      if (status === "sign_up") {
        await signUp.create({
          strategy: "ticket",
          ticket,
          password,
        } as Parameters<typeof signUp.create>[0]);

        if (signUp.status === "complete" && signUp.createdSessionId) {
          await setActive({ session: signUp.createdSessionId });
          await verifierMirrorPuisRediriger();
        } else {
          setError("Erreur lors de la création du compte. Statut : " + signUp.status);
        }
      } else if (status === "sign_in") {
        await signIn.create({
          strategy: "ticket",
          ticket,
        } as Parameters<typeof signIn.create>[0]);

        if (signIn.status === "complete" && signIn.createdSessionId) {
          await setActive({ session: signIn.createdSessionId });
          await verifierMirrorPuisRediriger();
        } else {
          setError("Erreur lors de la connexion.");
        }
      } else {
        await verifierMirrorPuisRediriger();
      }
    } catch (err: unknown) {
      setError(extractClerkError(err));
    } finally {
      setLoading(false);
    }
  }

  const inputClass =
    "w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-4 py-2.5 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-secondary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-colors";

  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--background)] px-6 py-12">
      <ForceLightMode />
      <div className="w-full max-w-md animate-fade-in">
        <div className="mb-8 flex items-center gap-3">
          <LeadriveLogo variant="light" height={32} priority className="w-auto" />
        </div>

        <h1 className="mb-2 text-2xl font-bold text-[var(--foreground)]">
          Bienvenue dans l&apos;équipe
        </h1>
        <p className="mb-8 text-sm text-[var(--color-lr-text-secondary)]">
          Choisissez votre mot de passe pour activer votre compte.
        </p>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div>
            <label htmlFor="password" className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">
              Mot de passe
            </label>
            <input
              id="password"
              type="password"
              placeholder="Minimum 8 caractères"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
              autoComplete="new-password"
              className={inputClass}
            />
          </div>

          <div>
            <label htmlFor="passwordConfirm" className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">
              Confirmer le mot de passe
            </label>
            <input
              id="passwordConfirm"
              type="password"
              placeholder="Confirmez votre mot de passe"
              value={passwordConfirm}
              onChange={(e) => setPasswordConfirm(e.target.value)}
              required
              minLength={8}
              autoComplete="new-password"
              className={inputClass}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="rounded-lg btn-primary py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
          >
            {loading ? "Activation en cours..." : "Activer mon compte"}
          </button>
        </form>

        {error && (
          <div className="mt-4 rounded-lg bg-danger/10 border border-danger/20 p-3 text-sm text-danger">
            {error}
          </div>
        )}
      </div>
    </div>
  );
}
