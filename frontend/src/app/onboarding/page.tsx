"use client";

import { Suspense, useEffect, useState, useCallback } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useApiClient, useCurrentRole, useMe, useConcessionName, formatErreur } from "@/lib/auth";
import { useUpdateOnboarding } from "@/lib/onboarding";
import ImportLeadsForm from "@/components/imports/ImportLeadsForm";
import ImportEmailHero from "@/components/imports/ImportEmailHero";
import AdresseAutocomplete from "@/components/common/AdresseAutocomplete";
import LeadriveLogo from "@/components/common/LeadriveLogo";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

const STEPS = [
  { label: "Bienvenue" },
  { label: "Établissement" },
  { label: "Prospects" },
  { label: "Equipe" },
];

interface ConfigIa {
  horairesOuverture: string;
  adresseComplete: string;
  telephoneConcession: string;
  siteWeb: string;
  emailContact: string;
}

const EMPTY_CONFIG: ConfigIa = {
  horairesOuverture: "",
  adresseComplete: "",
  telephoneConcession: "",
  siteWeb: "",
  emailContact: "",
};

interface InviteRow {
  prenom: string;
  nom: string;
  email: string;
}

function OnboardingContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const apiFetch = useApiClient();
  const { role, isLoaded: roleLoaded } = useCurrentRole();
  const me = useMe();
  const concessionName = useConcessionName();
  const updateOnboarding = useUpdateOnboarding();
  const isReset = searchParams.get("reset") === "1";

  const [step, setStep] = useState(0);
  const [config, setConfig] = useState<ConfigIa>({ ...EMPTY_CONFIG });
  const [configLoaded, setConfigLoaded] = useState(false);
  const [saving, setSaving] = useState(false);

  // Equipe
  const [invites, setInvites] = useState<InviteRow[]>([{ prenom: "", nom: "", email: "" }]);
  const [inviting, setInviting] = useState(false);
  const [invitesSent, setInvitesSent] = useState(0);

  const [error, setError] = useState("");

  // Redirect si pas manager ou onboarding deja termine
  useEffect(() => {
    if (roleLoaded && role !== "MANAGER") {
      router.replace("/dashboard");
    }
  }, [roleLoaded, role, router]);

  useEffect(() => {
    if (me && me.onboardingTermine && !isReset) {
      router.replace("/dashboard");
    }
  }, [me, router, isReset]);

  // Reprendre a la bonne etape (sauf si c'est un reset)
  useEffect(() => {
    if (me && !isReset && me.onboardingEtape !== null && me.onboardingEtape > 0) {
      setStep(me.onboardingEtape);
    }
  }, [me, isReset]);

  // Charger config IA existante
  useEffect(() => {
    apiFetch(`${API_BASE}/api/ai/config`)
      .then(async (res) => {
        if (res.status === 204) return;
        if (res.ok) {
          const data = await res.json();
          setConfig({
            horairesOuverture: data.horairesOuverture || "",
            adresseComplete: data.adresseComplete || "",
            telephoneConcession: data.telephoneConcession || "",
            siteWeb: data.siteWeb || "",
            emailContact: data.emailContact || "",
          });
        }
      })
      .finally(() => setConfigLoaded(true));
  }, [apiFetch]);

  // Track le step max atteint pour permettre le retour
  const [maxStep, setMaxStep] = useState(0);

  useEffect(() => {
    if (step > maxStep) setMaxStep(step);
  }, [step, maxStep]);

  const goNext = useCallback(async () => {
    const nextStep = step + 1;
    setError("");
    try {
      await updateOnboarding({ onboardingEtape: nextStep });
    } catch {
      // non-bloquant
    }
    setStep(nextStep);
  }, [step, updateOnboarding]);

  function goBack() {
    if (step > 0) {
      setError("");
      setStep(step - 1);
    }
  }

  function goToStep(target: number) {
    if (target <= maxStep) {
      setError("");
      setStep(target);
    }
  }

  async function handleSaveConfig() {
    setSaving(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/ai/config`, {
        method: "PUT",
        body: JSON.stringify({ ...config, nomConcession: concessionName }),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      await goNext();
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleInviteAll() {
    const validInvites = invites.filter((inv) => inv.email.trim());
    if (validInvites.length === 0) return;
    setInviting(true);
    setError("");
    let sent = 0;
    const echecs: string[] = [];
    for (const inv of validInvites) {
      try {
        const res = await apiFetch(`${API_BASE}/api/equipe/inviter`, {
          method: "POST",
          body: JSON.stringify(inv),
        });
        if (res.ok) {
          sent++;
        } else {
          // Ne PAS avaler l'echec silencieusement : le manager doit savoir pourquoi
          // (email deja utilise, limite de membres atteinte, etc.).
          const texte = await res.text();
          echecs.push(`${inv.email} : ${formatErreur(new Error(texte || `Erreur ${res.status}`))}`);
        }
      } catch (err) {
        echecs.push(`${inv.email} : ${formatErreur(err)}`);
      }
    }
    setInvitesSent(sent);
    if (echecs.length > 0) {
      setError(`Certaines invitations n'ont pas pu être envoyées. ${echecs.join(" ")}`);
    }
    setInviting(false);
  }

  async function handleFinish() {
    try {
      await updateOnboarding({ onboardingTermine: true, onboardingEtape: STEPS.length });
    } catch {
      // non-bloquant
    }
    router.replace("/dashboard?tour=1");
  }

  if (!roleLoaded || role !== "MANAGER") return null;

  const inputClass = "w-full rounded-lg border border-[var(--border)] bg-[var(--background)] px-3 py-2.5 text-sm text-[var(--foreground)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30 transition-colors";

  return (
    <div className="flex min-h-screen flex-col bg-[var(--background)]">
      {/* Header */}
      <header className="flex items-center justify-between border-b border-[var(--border)] px-6 py-4">
        <LeadriveLogo height={28} priority className="w-auto" />
        <button
          onClick={handleFinish}
          className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)] transition-colors"
        >
          Passer la configuration
        </button>
      </header>

      {/* Stepper */}
      <div className="mx-auto w-full max-w-2xl px-6 pt-8 mb-8">
        <div className="flex items-center">
          {STEPS.map((s, i) => (
            <div key={s.label} className={`flex items-center ${i < STEPS.length - 1 ? "flex-1" : ""}`}>
              <button
                type="button"
                onClick={() => goToStep(i)}
                disabled={i > maxStep}
                className="flex flex-col items-center min-w-[60px] disabled:cursor-default cursor-pointer"
              >
                <div
                  className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-medium transition-all duration-300 ${
                    i < step
                      ? "bg-accent text-white scale-100"
                      : i === step
                        ? "bg-accent text-white ring-4 ring-accent/20 scale-110"
                        : i <= maxStep
                          ? "bg-accent/20 text-accent"
                          : "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]"
                  }`}
                >
                  {i < step ? (
                    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                  ) : (
                    i + 1
                  )}
                </div>
                <span className={`mt-1.5 text-xs whitespace-nowrap ${i <= step ? "text-accent font-medium" : i <= maxStep ? "text-accent/70" : "text-[var(--color-lr-text-tertiary)]"}`}>
                  {s.label}
                </span>
              </button>
              {i < STEPS.length - 1 && (
                <div className="mx-3 mt-[-18px] h-0.5 flex-1 overflow-hidden rounded bg-[var(--border)]">
                  <div
                    className="h-full bg-accent transition-all duration-700"
                    style={{ width: i < step ? "100%" : "0%", transitionTimingFunction: "cubic-bezier(0.16, 1, 0.3, 1)" }}
                  />
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Contenu : key={step} force le remount à chaque changement d'étape,
          ce qui rejoue l'animation tab-content-enter (fade + slide 4px) et donne
          un signal visuel "tu viens de passer à l'étape suivante". */}
      <div key={step} className="mx-auto w-full max-w-2xl flex-1 px-6 pb-12 animate-tab-content">
        {error && (
          <div className="mb-6 rounded-lg bg-danger/10 border border-danger/20 p-3 text-sm text-danger">{error}</div>
        )}

        {/* Step 0 : Bienvenue, hero ignition signature Atelier */}
        {step === 0 && (
          <div className="relative overflow-hidden rounded-2xl bg-[var(--color-ignition)] py-16 px-8 text-center">
            <div className="absolute inset-0 bg-stripes-pattern opacity-[0.03] pointer-events-none" />
            {/* Glow ember bas-droite, moment chaud signature */}
            <div
              className="pointer-events-none absolute -right-24 bottom-0 h-[320px] w-[320px] rounded-full opacity-25 blur-3xl"
              style={{ background: "radial-gradient(circle, rgba(217,96,58,0.55) 0%, transparent 70%)" }}
              aria-hidden="true"
            />
            <div className="relative">
              <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent-light)]">
                Installation · 1 sur 4
              </p>
              <h1 className="mt-4 mb-3 font-display text-[36px] font-bold leading-[1.05] tracking-display-tight text-white">
                Bienvenue{me?.prenom ? `, ${me.prenom}` : ""}.
              </h1>
              <p className="mx-auto mb-8 max-w-md text-[15px] leading-relaxed text-white/65">
                En trois étapes rapides, configurez votre établissement pour que l&apos;IA puisse générer des messages personnalisés pour vos prospects.
              </p>
              <button
                onClick={goNext}
                className="btn-phare rounded-xl px-8 py-3 text-[14px] font-semibold transition-colors"
              >
                C&apos;est parti
              </button>
            </div>
          </div>
        )}

        {/* Step 1 : Config concession */}
        {step === 1 && (
          <div>
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
              Installation · 2 sur 4
            </p>
            <h2 className="display-h1 mt-2 mb-2 text-[var(--foreground)]">
              Configurez votre établissement.
            </h2>
            <p className="mb-6 text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              Ces informations permettent à l&apos;IA de personnaliser les messages envoyés à vos prospects.
            </p>
            {!configLoaded ? (
              <div className="space-y-4">
                {[1, 2, 3, 4, 5].map((i) => (
                  <div key={i} className="h-12 rounded-lg animate-pulse bg-[var(--muted)]" />
                ))}
              </div>
            ) : (
              <div className="space-y-4">
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">Nom de l&apos;établissement</label>
                  <input type="text" value={concessionName} readOnly disabled className={`${inputClass} opacity-70 cursor-not-allowed`} />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">Horaires d&apos;ouverture</label>
                  <input type="text" value={config.horairesOuverture} onChange={(e) => setConfig({ ...config, horairesOuverture: e.target.value })} placeholder="Lun-Sam 9h-19h" className={inputClass} />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">Adresse complète</label>
                  <AdresseAutocomplete
                    value={config.adresseComplete}
                    onChange={(v) => setConfig({ ...config, adresseComplete: v })}
                    placeholder="12 avenue de la République, 69003 Lyon"
                    className={inputClass}
                  />
                  <p className="mt-1 text-xs text-[var(--color-lr-text-tertiary)]">L&apos;IA donnera cette adresse aux prospects qui la demandent.</p>
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">Téléphone de l&apos;établissement</label>
                  <input type="text" value={config.telephoneConcession} onChange={(e) => setConfig({ ...config, telephoneConcession: e.target.value })} placeholder="+33 4 78 12 34 56" className={inputClass} />
                  <p className="mt-1 text-xs text-[var(--color-lr-text-tertiary)]">L&apos;IA communiquera ce numéro aux prospects qui le demandent.</p>
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">Site web</label>
                  <input type="url" value={config.siteWeb} onChange={(e) => setConfig({ ...config, siteWeb: e.target.value })} placeholder="https://www.votre-etablissement.fr" className={inputClass} />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-[var(--foreground)]">Email de contact</label>
                  <input type="email" value={config.emailContact} onChange={(e) => setConfig({ ...config, emailContact: e.target.value })} placeholder="contact@votre-etablissement.fr" className={inputClass} />
                </div>
                <div className="flex items-center justify-between pt-4">
                  <div className="flex items-center gap-4">
                    <button onClick={goBack} className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)] flex items-center gap-1">
                      <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
                      Retour
                    </button>
                    <button onClick={goNext} className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]">
                      Plus tard
                    </button>
                  </div>
                  <button
                    onClick={handleSaveConfig}
                    disabled={saving}
                    className="rounded-lg btn-primary px-6 py-2.5 text-sm font-medium text-white transition-colors disabled:opacity-50"
                  >
                    {saving ? "Sauvegarde..." : "Continuer"}
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Step 2 : Import */}
        {step === 2 && (
          <div>
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
              Installation · 3 sur 4
            </p>
            <h2 className="display-h1 mt-2 mb-2 text-[var(--foreground)]">
              Importez vos premiers prospects.
            </h2>
            <p className="mb-6 text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              Configurez l&apos;import automatique pour ne plus jamais saisir un lead à la main, ou importez immédiatement vos prospects actuels.
            </p>

            {/* Solution principale mise en avant : import automatique par email */}
            <ImportEmailHero />

            {/* Solution secondaire repliable : import manuel */}
            <details className="mt-6 group">
              <summary className="cursor-pointer flex items-center justify-between rounded-lg border border-[var(--border)] bg-[var(--card)] px-4 py-3 text-sm font-medium text-[var(--foreground)] hover:bg-[var(--muted)] list-none [&::-webkit-details-marker]:hidden">
                <span>Je préfère importer manuellement maintenant (CSV, copier-coller, email)</span>
                <svg className="h-4 w-4 transition-transform group-open:rotate-180 text-[var(--color-lr-text-secondary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </summary>
              <div className="mt-4 rounded-lg border border-[var(--border)] bg-[var(--card)] p-4">
                <p className="text-xs text-[var(--color-lr-text-secondary)] mb-4">
                  Trois façons d&apos;importer manuellement : un fichier (CSV, Excel), un copier-coller depuis votre DMS, ou un email de lead. L&apos;IA détecte automatiquement les colonnes.
                </p>
                <ImportLeadsForm
                  retourPath="onboarding"
                  compact
                  onTexteLibreSuccess={() => { void goNext(); }}
                />
              </div>
            </details>

            <div className="flex items-center justify-between pt-6">
              <button onClick={goBack} className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)] flex items-center gap-1">
                <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
                Retour
              </button>
              <button onClick={goNext} className="text-sm font-medium text-accent hover:text-accent-hover">
                Continuer →
              </button>
            </div>
          </div>
        )}

        {/* Step 3 : Equipe */}
        {step === 3 && (
          <div>
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--color-accent)]">
              Installation · 4 sur 4
            </p>
            <h2 className="display-h1 mt-2 mb-2 text-[var(--foreground)]">
              Invitez votre équipe.
            </h2>
            <p className="mb-6 text-[15px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              Vos vendeurs recevront un email d&apos;invitation pour accéder à Leadrive.
            </p>

            {invitesSent > 0 ? (
              <div className="text-center py-8">
                <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-success/10">
                  <svg className="h-8 w-8 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                </div>
                <p className="text-lg font-semibold text-[var(--foreground)]">{invitesSent} invitation{invitesSent > 1 ? "s" : ""} envoyée{invitesSent > 1 ? "s" : ""}</p>
                <button onClick={handleFinish} className="mt-6 rounded-lg btn-primary px-6 py-2.5 text-sm font-medium text-white transition-colors">
                  Terminer
                </button>
              </div>
            ) : (
              <>
                <div className="space-y-3">
                  {invites.map((inv, i) => (
                    <div key={i} className="flex gap-3 items-start">
                      <input
                        type="text"
                        placeholder="Prenom"
                        value={inv.prenom}
                        onChange={(e) => {
                          const copy = [...invites];
                          copy[i] = { ...copy[i], prenom: e.target.value };
                          setInvites(copy);
                        }}
                        className={`${inputClass} flex-1`}
                      />
                      <input
                        type="text"
                        placeholder="Nom"
                        value={inv.nom}
                        onChange={(e) => {
                          const copy = [...invites];
                          copy[i] = { ...copy[i], nom: e.target.value };
                          setInvites(copy);
                        }}
                        className={`${inputClass} flex-1`}
                      />
                      <input
                        type="email"
                        placeholder="Email"
                        value={inv.email}
                        onChange={(e) => {
                          const copy = [...invites];
                          copy[i] = { ...copy[i], email: e.target.value };
                          setInvites(copy);
                        }}
                        className={`${inputClass} flex-[2]`}
                      />
                      {invites.length > 1 && (
                        <button
                          onClick={() => setInvites(invites.filter((_, j) => j !== i))}
                          className="mt-2 text-[var(--color-lr-text-tertiary)] hover:text-danger"
                        >
                          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                          </svg>
                        </button>
                      )}
                    </div>
                  ))}
                </div>

                <button
                  onClick={() => setInvites([...invites, { prenom: "", nom: "", email: "" }])}
                  className="mt-3 text-sm text-accent hover:text-accent-hover font-medium"
                >
                  + Ajouter un vendeur
                </button>

                <div className="flex items-center justify-between pt-6">
                  <div className="flex items-center gap-4">
                    <button onClick={goBack} className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)] flex items-center gap-1">
                      <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
                      Retour
                    </button>
                    <button onClick={handleFinish} className="text-sm text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]">
                      Plus tard
                    </button>
                  </div>
                  <button
                    onClick={async () => { await handleInviteAll(); }}
                    disabled={inviting || !invites.some((inv) => inv.email.trim())}
                    className="rounded-lg btn-primary px-6 py-2.5 text-sm font-medium text-white transition-colors disabled:opacity-50"
                  >
                    {inviting ? "Envoi..." : "Inviter et terminer"}
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default function OnboardingPage() {
  return (
    <Suspense>
      <OnboardingContent />
    </Suspense>
  );
}
