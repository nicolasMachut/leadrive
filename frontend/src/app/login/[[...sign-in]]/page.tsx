"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useSignIn, useClerk, useAuth } from "@clerk/nextjs";
import { extractClerkError } from "@/lib/clerk-errors";
import ForceLightMode from "@/components/common/ForceLightMode";
import LeadriveLogo from "@/components/common/LeadriveLogo";

// Clerk v6 : la resource legacy expose toutes les methodes (prepareSecondFactor, attemptSecondFactor...)
// alors que useSignIn() renvoie une version "future" qui en manque certaines.
interface LegacySignInResource {
  create: (params: { identifier: string; password: string }) => Promise<unknown>;
  prepareSecondFactor: (params: { strategy: string }) => Promise<unknown>;
  attemptSecondFactor: (params: { strategy: string; code: string }) => Promise<unknown>;
  status: string | null;
  createdSessionId: string | null;
}

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { signIn } = useSignIn();
  const clerk = useClerk();
  const { setActive } = clerk;
  const { isLoaded, isSignedIn } = useAuth();

  // Banniere d'info contextuelle selon la raison du redirect :
  // - account-disabled : tentative de connexion refusee par le backend (mirror INACTIF,
  //   tenant supprime). Vient de verifierMirrorPuisRediriger sur cette page meme.
  // - session-ended : ejection en cours de navigation (useApiClient detecte un 401
  //   apres un succes de login).
  const reason = searchParams.get("reason");
  const sessionEndedMessage = reason === "account-disabled"
    ? "Ce compte n'est plus actif. Votre établissement a peut-être été désactivé. Contactez votre administrateur si c'est une erreur."
    : reason === "session-ended"
    ? "Votre session a pris fin. Votre compte a peut-être été désactivé ou votre établissement supprimé. Si ce n'est pas le cas, reconnectez-vous ci-dessous."
    : null;

  function getLegacySignIn(): LegacySignInResource | null {
    const client = (clerk as unknown as { client?: { signIn?: LegacySignInResource } }).client;
    return client?.signIn ?? null;
  }
  const [email, setEmail] = useState("");
  const [motDePasse, setMotDePasse] = useState("");
  const [code2fa, setCode2fa] = useState("");
  const [step, setStep] = useState<"credentials" | "2fa">("credentials");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  // Compteur de cooldown anti-spam pour le renvoi de code 2FA (30 secondes).
  // Le user clique "Renvoyer le code" → on relance prepareSecondFactor + on
  // lance un decompte. Pendant le decompte le bouton est disabled pour eviter
  // qu'un user impatient genere 50 codes en 5 secondes.
  const [resendCooldown, setResendCooldown] = useState(0);

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const t = setTimeout(() => setResendCooldown((c) => Math.max(0, c - 1)), 1000);
    return () => clearTimeout(t);
  }, [resendCooldown]);

  async function renvoyerCode2fa() {
    if (resendCooldown > 0 || loading) return;
    setError("");
    setLoading(true);
    try {
      const legacy = getLegacySignIn();
      if (!legacy) {
        setError("Clerk client non initialisé");
        return;
      }
      await legacy.prepareSecondFactor({ strategy: "email_code" });
      setInfo("Un nouveau code a été envoyé à votre email.");
      setResendCooldown(30);
    } catch (err) {
      setError(extractClerkError(err));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    // Skip auto-redirect si :
    // - submit en cours : handleSubmit gere lui-meme le redirect apres verif backend
    // - error visible : l'utilisateur vient d'etre ejecte / compte desactive, on
    //   doit lui laisser voir le message avant tout redirect
    if (loading || error) return;
    if (isLoaded && isSignedIn) {
      // Passe par /api/me pour 2 raisons :
      // 1. Premier login post-invitation manager : le mirror est en INVITE
      //    sans clerk_user_id. /api/me declenche le link via email match dans
      //    ClerkJwtConverter, sinon le dashboard ferait des appels avec un JWT
      //    custom (template leadrevive-backend, sans email) et serait rejete
      //    (DisabledException -> 401 -> session-ended).
      // 2. Redirection contextuelle (SUPER_ADMIN -> /admin, manager non
      //    onboarde -> /onboarding) sans flasher /dashboard.
      verifierMirrorPuisRediriger();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoaded, isSignedIn, loading, error]);

  /**
   * Verifie cote backend que le user a un mirror actif AVANT de rediriger
   * vers /dashboard. Sans ca, on a un flash dashboard puis ejection
   * (useApiClient detecte 401 et signOut). Si le check echoue, on signOut
   * Clerk en redirigeant sur /login avec un param de raison (sinon Clerk
   * redirige par defaut vers la landing page "/").
   */
  async function verifierMirrorPuisRediriger() {
    const apiBase = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
    try {
      const token = await clerk.session?.getToken();
      if (!token) {
        // Pas de token = pas connecte cote Clerk, on continue (le useEffect fera le redirect normal)
        router.push("/dashboard");
        return;
      }
      const res = await fetch(`${apiBase}/api/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.status === 401 || res.status === 403) {
        // Compte desactive cote Leadrive (mirror INACTIF, tenant supprime...)
        // SignOut + redirect explicite sur /login (sinon Clerk redirige vers "/")
        await clerk.signOut({ redirectUrl: "/login?reason=account-disabled" });
        return;
      }
      // Redirection contextuelle selon le role (evite le flash via /dashboard
      // qui ensuite redirige) :
      // - SUPER_ADMIN -> /admin direct (pas de mirror local, pas de dashboard)
      // - MANAGER pas onboarde -> /onboarding direct
      // - Sinon -> /dashboard
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
      // Erreur reseau : on laisse passer, le user verra l'erreur sur /dashboard
      router.push("/dashboard");
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isLoaded) return;
    setError("");
    setInfo("");
    setLoading(true);

    try {
      await signIn.create({
        identifier: email,
        password: motDePasse,
      });

      if (signIn.status === "complete" && signIn.createdSessionId) {
        await setActive({ session: signIn.createdSessionId });
        await verifierMirrorPuisRediriger();
        return;
      }

      const status = signIn.status as string;
      if (status === "needs_second_factor") {
        const legacy = getLegacySignIn();
        if (!legacy) {
          setError("Clerk client non initialisé");
          return;
        }
        await legacy.prepareSecondFactor({ strategy: "email_code" });
        setStep("2fa");
        setInfo("Un code de vérification a été envoyé à votre email.");
        // Lance le cooldown 30s du bouton "Renvoyer le code" pour le premier envoi
        setResendCooldown(30);
      } else {
        setError("Email ou mot de passe incorrect.");
      }
    } catch (err) {
      setError(extractClerkError(err));
    } finally {
      setLoading(false);
    }
  }

  async function handle2faSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isLoaded) return;
    setError("");
    setLoading(true);

    try {
      const legacy = getLegacySignIn();
      if (!legacy) {
        setError("Clerk client non initialisé");
        return;
      }
      await legacy.attemptSecondFactor({ strategy: "email_code", code: code2fa });

      if (legacy.status === "complete" && legacy.createdSessionId) {
        await setActive({ session: legacy.createdSessionId });
        await verifierMirrorPuisRediriger();
      } else {
        setError("Code invalide ou expiré.");
      }
    } catch (err) {
      setError(extractClerkError(err));
    } finally {
      setLoading(false);
    }
  }

  const inputClass =
    "w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-4 py-2.5 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-secondary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-colors";

  return (
    <div className="flex flex-1 min-h-screen">
      <ForceLightMode />
      {/* Left branded panel, surface ignition + eyebrow mono + display title +
          temoignage editorial. Plus de texte plat, on installe une vraie
          presence brand. */}
      <div className="relative hidden flex-col justify-between overflow-hidden bg-[var(--color-ignition)] px-12 py-12 text-white lg:flex lg:w-1/2 xl:w-[45%]">
        {/* Pattern road-stripes signature + glow ember */}
        <div className="absolute inset-0 bg-stripes-pattern opacity-[0.025] pointer-events-none" />
        <div
          className="pointer-events-none absolute -right-32 bottom-20 h-[460px] w-[460px] rounded-full opacity-25 blur-3xl"
          style={{ background: "radial-gradient(circle, rgba(217,96,58,0.55) 0%, transparent 70%)" }}
          aria-hidden="true"
        />

        {/* Top : logo + eyebrow. Stagger : logo immediat, titre +150ms, testimonial +300ms. */}
        <div className="relative z-10 animate-card-mount">
          <a href="/" className="inline-flex items-center gap-3">
            <LeadriveLogo variant="dark" height={40} priority className="w-auto" />
          </a>
        </div>

        {/* Centre : display title editorial */}
        <div className="relative z-10 animate-card-mount-delayed">
          <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.22em] text-white/55">
            <span className="text-[var(--color-accent-light)]">Performance commerciale</span>
            <span className="mx-2">·</span>
            Automobile
          </p>
          <h2 className="mt-5 font-display text-[40px] font-bold leading-[1.05] tracking-display-tight text-white">
            Exploitez le potentiel
            <br />
            <span className="text-[var(--color-accent-light)]">de vos leads.</span>
          </h2>
          <p className="mt-5 max-w-md text-[15px] leading-relaxed text-white/65">
            L&apos;IA qui contacte vos prospects en moins de 5 minutes, génère des RDV et prépare vos vendeurs avant chaque appel.
          </p>
        </div>

        {/* Bottom : pull-quote temoignage editorial. Le label "Pilote en cours"
            remplace l'anonymisation generique : transparence sur l'etat reel
            du produit (pas de vrai client signe encore), evite le pattern fake
            testimonial classique. */}
        <figure className="relative z-10 max-w-md border-t border-white/10 pt-6 animate-card-mount-delayed-2">
          <blockquote className="font-display text-[18px] italic font-medium leading-snug tracking-[-0.01em] text-white/85">
            «&nbsp;En trois semaines, on a recontacté 200 leads dormants. Trois ventes signées qui ne seraient jamais arrivées sans Leadrive.&nbsp;»
          </blockquote>
          <figcaption className="mt-3 font-mono text-[10px] uppercase tracking-[0.18em] text-white/45">
            Garage multimarques · Pilote en cours
          </figcaption>
        </figure>
      </div>

      {/* Right login form */}
      <div className="flex flex-1 items-center justify-center bg-[var(--background)] px-6 py-12">
        <div className="w-full max-w-md animate-fade-in">
          {/* Mobile logo */}
          <div className="mb-8 lg:hidden">
            <LeadriveLogo height={32} priority className="w-auto" />
          </div>

          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
            {step === "credentials" ? "Connexion" : "Vérification"}
          </p>
          <h1 className="mt-2 mb-2 font-display text-[32px] font-bold leading-[1.05] tracking-display-tight text-[var(--foreground)]">
            {step === "credentials" ? "Bienvenue." : "Code à deux facteurs."}
          </h1>
          <p className="mb-6 text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
            {step === "credentials"
              ? "Connectez-vous pour accéder à votre espace."
              : "Saisissez le code de vérification reçu par email."}
          </p>

          {sessionEndedMessage && step === "credentials" && (
            <div className="mb-6 flex items-start gap-2.5 rounded-lg border border-warning/30 bg-warning/5 p-3">
              <svg className="mt-0.5 h-4 w-4 shrink-0 text-warning" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
              </svg>
              <p className="text-xs text-[var(--foreground)]">{sessionEndedMessage}</p>
            </div>
          )}

          {step === "credentials" ? (
            <form onSubmit={handleSubmit} className="flex flex-col gap-5">
              <div>
                <label htmlFor="email" className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">
                  Email
                </label>
                <input
                  id="email"
                  type="email"
                  placeholder="jean.dupont@garage.fr"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                  className={inputClass}
                />
              </div>
              <div>
                <div className="mb-1.5 flex items-center justify-between">
                  <label htmlFor="motDePasse" className="block text-sm font-medium text-[var(--foreground)]">
                    Mot de passe
                  </label>
                  <Link
                    href="/login/forgot-password"
                    className="text-xs font-medium text-accent hover:text-accent-hover"
                  >
                    Mot de passe oublié ?
                  </Link>
                </div>
                <input
                  id="motDePasse"
                  type="password"
                  placeholder="Votre mot de passe"
                  value={motDePasse}
                  onChange={(e) => setMotDePasse(e.target.value)}
                  required
                  autoComplete="current-password"
                  className={inputClass}
                />
              </div>

              {/* Clerk CAPTCHA / bot protection element (required for some setups) */}
              <div id="clerk-captcha" />

              <button
                type="submit"
                disabled={loading || !isLoaded}
                className="rounded-lg btn-primary py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
              >
                {loading ? "Connexion..." : "Se connecter"}
              </button>
            </form>
          ) : (
            <form onSubmit={handle2faSubmit} className="flex flex-col gap-5">
              <div>
                <label htmlFor="code2fa" className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">
                  Code reçu par email
                </label>
                <input
                  id="code2fa"
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  placeholder="123456"
                  value={code2fa}
                  onChange={(e) => setCode2fa(e.target.value)}
                  required
                  autoComplete="one-time-code"
                  autoFocus
                  className={inputClass}
                />
              </div>

              <button
                type="submit"
                disabled={loading || !isLoaded}
                className="rounded-lg btn-primary py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-50"
              >
                {loading ? "Vérification..." : "Valider"}
              </button>

              {/* Renvoyer le code 2FA : disabled pendant 30s post-envoi pour eviter
                  le spam. Affiche le compteur countdown pour donner du feedback. */}
              <button
                type="button"
                onClick={renvoyerCode2fa}
                disabled={resendCooldown > 0 || loading}
                className="text-center text-xs text-[var(--color-lr-text-secondary)] transition-colors hover:text-accent disabled:cursor-not-allowed disabled:hover:text-[var(--color-lr-text-tertiary)]"
              >
                {resendCooldown > 0
                  ? `Renvoyer le code dans ${resendCooldown}s`
                  : "Renvoyer le code"}
              </button>

              <button
                type="button"
                onClick={() => { setStep("credentials"); setCode2fa(""); setError(""); setInfo(""); }}
                className="text-center text-xs text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"
              >
                ← Retour
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

          {/* Footer accueil nouveaux venus : si tu n'as pas encore de compte,
              Leadrive est sur invitation (B2B + accompagnement) ou via demande
              de demo. On ne propose pas de "Creer un compte" libre. */}
          <p className="mt-8 border-t border-[var(--border)] pt-6 text-center text-[13px] text-[var(--color-lr-text-secondary)]">
            Pas encore de compte&nbsp;?{" "}
            <Link href="/#demo" className="font-medium text-accent transition-colors hover:text-accent-hover">
              Demander une démo
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
