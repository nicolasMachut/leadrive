"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { useApiClient, useConcessionName, formatErreur } from "@/lib/auth";
import AppLayout from "@/components/AppLayout";
import InfoIcon from "@/components/common/InfoIcon";
import PageHeader from "@/components/common/PageHeader";
import Button from "@/components/common/Button";
import Alert from "@/components/common/Alert";
import Modal from "@/components/common/Modal";
import Skeleton from "@/components/common/Skeleton";
import ErrorState from "@/components/common/ErrorState";
import { Input, FieldLabel, inputFieldClass } from "@/components/common/Input";
import { formatPhoneInput, sanitizePhoneInput } from "@/lib/phone";
import AdresseAutocomplete from "@/components/common/AdresseAutocomplete";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface Coordonnees {
  nomConcession: string;
  horairesOuverture: string;
  adresseComplete: string;
  telephoneConcession: string;
  siteWeb: string;
  emailContact: string;
  premierMessageAutoActif: boolean;
  premierMessageAutoDelaiMinutes: number;
  premierMessageAutoFenetreDebutHeure: number;
  premierMessageAutoFenetreFinHeure: number;
}

const EMPTY: Coordonnees = {
  nomConcession: "",
  horairesOuverture: "",
  adresseComplete: "",
  telephoneConcession: "",
  siteWeb: "",
  emailContact: "",
  premierMessageAutoActif: false,
  premierMessageAutoDelaiMinutes: 5,
  premierMessageAutoFenetreDebutHeure: 8,
  premierMessageAutoFenetreFinHeure: 20,
};

// Cle des champs "envoi auto" pour le suivi des modifications (dirty check).
const ENVOI_AUTO_KEYS: (keyof Coordonnees)[] = [
  "premierMessageAutoActif",
  "premierMessageAutoDelaiMinutes",
  "premierMessageAutoFenetreDebutHeure",
  "premierMessageAutoFenetreFinHeure",
];

// Options du selecteur d'heure de DEBUT (0h a 23h).
const HEURES_DEBUT = Array.from({ length: 24 }, (_, h) => h);
// Options du selecteur d'heure de FIN (1h a 24h ; la fin est exclusive, 24 = minuit
// pour couvrir jusqu'a 23h59). Aligne avec la borne backend @Max(24).
const HEURES_FIN = Array.from({ length: 24 }, (_, h) => h + 1);

// Libelle d'une heure de fin : 24 -> "Minuit", sinon "{h}h".
function labelHeureFin(h: number): string {
  return h === 24 ? "Minuit" : `${h}h`;
}

// Champs texte "Coordonnees" (exclut nomConcession, gere ailleurs, et les champs
// numeriques/booleen de l'envoi auto qui ont leurs propres controles).
type FieldKey = "horairesOuverture" | "adresseComplete" | "telephoneConcession" | "siteWeb" | "emailContact";

interface FieldDef {
  key: FieldKey;
  label: string;
  placeholder: string;
  tooltip: string;
  type?: "tel" | "url" | "email";
}

const FIELDS: FieldDef[] = [
  {
    key: "adresseComplete",
    label: "Adresse complète",
    placeholder: "12 avenue de la République, 69003 Lyon",
    tooltip: "Adresse postale de votre établissement. L'IA la communique au prospect s'il la demande, et elle apparaît sur votre fiche WhatsApp Business.",
  },
  {
    key: "telephoneConcession",
    label: "Téléphone",
    placeholder: "+33 4 78 12 34 56",
    tooltip: "Numéro principal de votre établissement. L'IA le communique aux prospects qui le demandent.",
    type: "tel",
  },
  {
    key: "horairesOuverture",
    label: "Horaires d'ouverture",
    placeholder: "Lun-Ven 9h-19h, Sam 9h-18h",
    tooltip: "Horaires d'ouverture de votre établissement. L'IA respecte ces horaires pour proposer des créneaux d'appel.",
  },
  {
    key: "siteWeb",
    label: "Site web",
    placeholder: "https://www.votre-etablissement.fr",
    tooltip: "URL de votre site web public. Apparaît dans la fiche WhatsApp Business des prospects.",
    type: "url",
  },
  {
    key: "emailContact",
    label: "Email de contact",
    placeholder: "contact@votre-etablissement.fr",
    tooltip: "Email professionnel de contact. Apparaît dans la fiche WhatsApp Business des prospects.",
    type: "email",
  },
];

const ALL_FIELDS: FieldKey[] = FIELDS.map((f) => f.key);

// Puces d'exemples pour les horaires (texte libre) : cliquer complete le champ.
// `label` = ce qu'on affiche sur la puce, `snippet` = ce qu'on insere dans le champ.
const HORAIRES_EXEMPLES: { label: string; snippet: string }[] = [
  { label: "Lun-Ven 9h-19h", snippet: "Lun-Ven 9h-19h" },
  { label: "+ Samedi 9h-18h", snippet: "Sam 9h-18h" },
  { label: "+ Samedi matin", snippet: "Sam 9h-13h" },
  { label: "Fermé dimanche", snippet: "fermé dimanche" },
];

// Regexes alignees avec la validation backend (ReqTenantAiConfigDto).
// Vides accepteees (champs optionnels). Sinon contraintes appliquees.
const URL_REGEX = /^(https?:\/\/)?([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(:[0-9]+)?(\/.*)?$/;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function valideChamp(field: FieldDef, value: string): boolean {
  if (!value) return true; // champ vide = OK (optionnel)
  if (field.type === "url") return URL_REGEX.test(value);
  if (field.type === "email") return EMAIL_REGEX.test(value);
  return true;
}

function htmlInputType(type?: FieldDef["type"]): "tel" | "email" | "url" | "text" {
  return type === "tel" ? "tel" : type === "email" ? "email" : type === "url" ? "url" : "text";
}

export default function ConfigIaPage() {
  const apiFetch = useApiClient();
  const concessionName = useConcessionName();
  const router = useRouter();

  const [config, setConfig] = useState<Coordonnees>({ ...EMPTY });
  const [initialConfig, setInitialConfig] = useState<Coordonnees>({ ...EMPTY });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  // Erreur de chargement (vs erreur d'enregistrement) : seule celle-ci porte un "Réessayer".
  const [loadError, setLoadError] = useState("");
  const [successVisible, setSuccessVisible] = useState(false);
  // Validation differee : on n'affiche l'erreur d'un champ qu'apres que
  // l'utilisateur l'a quitte (blur), pour ne pas le harceler en cours de saisie.
  const [touched, setTouched] = useState<Partial<Record<FieldKey, boolean>>>({});
  // Navigation interne interceptee quand des modifications ne sont pas enregistrees.
  const [pendingNav, setPendingNav] = useState<string | null>(null);
  // Indice clavier (⌘S vs Ctrl+S), resolu apres montage pour eviter un mismatch d'hydratation.
  const [shortcutLabel, setShortcutLabel] = useState("");
  // Nom affiche a vos prospects sur WhatsApp (verified name Meta) : lecture seule,
  // peut differer du nom d'etablissement saisi ci-dessous. Sert a lever l'ambiguite.
  const [nomWhatsapp, setNomWhatsapp] = useState<string | null>(null);

  useEffect(() => {
    const isMac = /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent);
    setShortcutLabel(isMac ? "⌘S" : "Ctrl+S");
  }, []);

  // Recupere le nom affiche cote WhatsApp (fetch passif, non bloquant).
  useEffect(() => {
    const controller = new AbortController();
    apiFetch(`${API_BASE}/api/parametres/whatsapp/self-signup/statut`, { signal: controller.signal })
      .then(async (res) => {
        if (!res.ok) return;
        const data = await res.json();
        if (!controller.signal.aborted) setNomWhatsapp(data.nomAffiche ?? null);
      })
      .catch(() => { /* fetch passif : pas de nom WhatsApp affiche si indisponible */ });
    return () => controller.abort();
  }, [apiFetch]);

  const chargerConfig = useCallback(() => {
    setLoading(true);
    setLoadError("");
    apiFetch(`${API_BASE}/api/ai/config`)
      .then(async (res) => {
        if (res.status === 204) {
          setConfig({ ...EMPTY });
          setInitialConfig({ ...EMPTY });
          return;
        }
        if (!res.ok) throw new Error(`Erreur ${res.status}`);
        const data = await res.json();
        const cleaned = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, v ?? ""]));
        const merged = { ...EMPTY, ...cleaned };
        setConfig(merged);
        setInitialConfig(merged);
      })
      .catch((err) => setLoadError(formatErreur(err)))
      .finally(() => setLoading(false));
  }, [apiFetch]);

  useEffect(() => { chargerConfig(); }, [chargerConfig]);

  const dirty = useMemo(
    () =>
      ALL_FIELDS.some((k) => (config[k] ?? "") !== (initialConfig[k] ?? "")) ||
      ENVOI_AUTO_KEYS.some((k) => config[k] !== initialConfig[k]),
    [config, initialConfig]
  );

  // Plage d'envoi coherente : debut strictement avant fin (aligne backend).
  const plageValide = useMemo(
    () =>
      !config.premierMessageAutoActif ||
      config.premierMessageAutoFenetreDebutHeure < config.premierMessageAutoFenetreFinHeure,
    [config.premierMessageAutoActif, config.premierMessageAutoFenetreDebutHeure, config.premierMessageAutoFenetreFinHeure]
  );

  // Bloque le save si un champ contraint (URL / email) est invalide cote client
  // ou si la plage d'envoi auto est incoherente, pour eviter un aller-retour 400.
  const formulaireValide = useMemo(
    () => FIELDS.every((f) => valideChamp(f, String(config[f.key] ?? ""))) && plageValide,
    [config, plageValide]
  );

  const handleSave = useCallback(async () => {
    setSaving(true);
    setError("");
    try {
      // Le tel est stocke en E.164 sans espaces. L'input affiche du formate, on sanitize au save.
      const payload = {
        ...config,
        nomConcession: concessionName,
        telephoneConcession: sanitizePhoneInput(config.telephoneConcession || ""),
      };
      const res = await apiFetch(`${API_BASE}/api/ai/config`, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      setInitialConfig(config);
      setSuccessVisible(true);
      setTimeout(() => setSuccessVisible(false), 2500);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setSaving(false);
    }
  }, [apiFetch, config, concessionName]);

  function handleChange(field: FieldKey, value: string) {
    setConfig((prev) => ({ ...prev, [field]: value }));
  }

  function handleReset() {
    setConfig({ ...initialConfig });
    setTouched({});
  }

  // Garde anti-perte (fermeture / rafraichissement / URL externe) : confirm natif du navigateur.
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  // Garde anti-perte (navigation interne Next.js) : intercepte les clics sur les
  // liens internes et ouvre une confirmation. beforeunload ne couvre pas ce cas
  // car l'App Router navigue sans recharger la page.
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: MouseEvent) => {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      const anchor = (e.target as HTMLElement | null)?.closest("a");
      if (!anchor) return;
      const href = anchor.getAttribute("href");
      if (!href || href.startsWith("#")) return;
      if (anchor.target && anchor.target !== "_self") return;
      const url = new URL(href, window.location.href);
      if (url.origin !== window.location.origin) return; // lien externe : beforeunload prend le relais
      if (url.pathname === window.location.pathname) return; // meme page
      e.preventDefault();
      setPendingNav(url.pathname + url.search + url.hash);
    };
    document.addEventListener("click", handler, true);
    return () => document.removeEventListener("click", handler, true);
  }, [dirty]);

  // Accelerateur clavier : Cmd/Ctrl+S enregistre, comme dans un editeur.
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "s") {
        e.preventDefault();
        if (dirty && formulaireValide && !saving) handleSave();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [dirty, formulaireValide, saving, handleSave]);

  function confirmerNavigation() {
    const cible = pendingNav;
    setPendingNav(null);
    if (cible) router.push(cible);
  }

  return (
    <AppLayout>
      <div className="mx-auto max-w-3xl px-6 py-8 pb-32">
        <PageHeader
          eyebrow="Configuration"
          title="Mon établissement"
          subtitle={concessionName || "—"}
        />

        {/* Nom affiché à vos prospects sur WhatsApp (lecture seule) : peut différer
            du nom d'établissement ci-dessus. On l'affiche pour lever l'ambiguïté. */}
        {nomWhatsapp && (
          <div className="mb-4 rounded-lg border border-[var(--border)] bg-[var(--muted)]/40 p-3">
            <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
              Nom affiché à vos prospects sur WhatsApp
            </p>
            <p className="mt-0.5 text-sm font-medium text-[var(--foreground)]">{nomWhatsapp}</p>
            <p className="mt-1 text-xs text-[var(--color-lr-text-secondary)]">
              C&apos;est le nom que voient vos prospects comme expéditeur. Il est validé par
              WhatsApp Business et peut différer du nom saisi ci-dessous.
            </p>
          </div>
        )}

        {loadError && (
          <ErrorState message={loadError} onRetry={chargerConfig} />
        )}
        {error && (
          <div role="alert" className="mb-4">
            <Alert tone="danger">{error}</Alert>
          </div>
        )}

        {/* Section Coordonnees, masquee si le chargement a echoue (sinon formulaire
            vide trompeur : l'utilisateur pourrait re-enregistrer par-dessus). */}
        {!loadError && (
        <section className="mb-6 rounded-xl border border-[var(--border)] bg-[var(--card)] p-6">
          <header className="mb-6">
            <h2 className="font-display text-[22px] font-bold tracking-display-tight text-[var(--foreground)]">
              Coordonnées
            </h2>
            <p className="mt-2 text-[14px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              L&apos;IA communique ces informations aux prospects qui les demandent. Un champ vide laisse l&apos;IA renvoyer vers un conseiller, jamais elle n&apos;invente.
            </p>
          </header>

          {loading ? (
            <div className="space-y-5">
              {Array.from({ length: FIELDS.length }).map((_, i) => (
                <div key={i}>
                  <Skeleton className="mb-2 h-4 w-32" />
                  <Skeleton className="h-10 w-full" />
                </div>
              ))}
            </div>
          ) : (
            <div className="space-y-5">
              {FIELDS.map((field) => {
                const raw = String(config[field.key] ?? "");
                const display = field.type === "tel" ? formatPhoneInput(raw) : raw;
                const showError = !valideChamp(field, raw) && Boolean(touched[field.key]);
                const messageErreur =
                  field.type === "url"
                    ? "URL invalide. Exemple : https://www.votre-site.fr"
                    : field.type === "email"
                      ? "Email invalide. Exemple : contact@etablissement.fr"
                      : "";
                const labelNode = <InfoIcon>{field.tooltip}</InfoIcon>;

                if (field.key === "adresseComplete") {
                  return (
                    <div key={field.key}>
                      <FieldLabel label={field.label} labelNode={labelNode} htmlFor={field.key} />
                      <AdresseAutocomplete
                        id={field.key}
                        value={display}
                        onChange={(v) => handleChange(field.key, v)}
                        placeholder={field.placeholder}
                        className={inputFieldClass(false)}
                      />
                    </div>
                  );
                }

                if (field.key === "horairesOuverture") {
                  // Ajoute un morceau au champ (ex. "Sam 9h-18h") avec separateur, sans
                  // ecraser la saisie et sans doublon. Les horaires sont du texte libre lu
                  // par l'IA : ces puces guident sans imposer de format.
                  const ajouterExemple = (snippet: string) => {
                    const courant = raw.trim().replace(/[,;\s]+$/, "");
                    if (courant.toLowerCase().includes(snippet.toLowerCase())) return;
                    handleChange(field.key, courant ? `${courant}, ${snippet}` : snippet);
                  };
                  return (
                    <div key={field.key}>
                      <Input
                        id={field.key}
                        label={field.label}
                        labelNode={labelNode}
                        hint="Écrivez librement, comme vous le diriez. L'IA s'en sert pour proposer des créneaux d'appel adaptés."
                        value={display}
                        onChange={(e) => handleChange(field.key, e.target.value)}
                        placeholder={field.placeholder}
                      />
                      <div className="mt-2 flex flex-wrap items-center gap-1.5">
                        <span className="mr-0.5 text-xs text-[var(--color-lr-text-tertiary)]">Exemples :</span>
                        {HORAIRES_EXEMPLES.map((ex) => (
                          <button
                            key={ex.label}
                            type="button"
                            onClick={() => ajouterExemple(ex.snippet)}
                            className="rounded-full border border-[var(--border)] bg-[var(--card)] px-2.5 py-1 text-xs text-[var(--color-lr-text-secondary)] transition-colors hover:border-accent hover:bg-accent/5 hover:text-[var(--foreground)]"
                          >
                            {ex.label}
                          </button>
                        ))}
                      </div>
                    </div>
                  );
                }

                return (
                  <Input
                    key={field.key}
                    id={field.key}
                    label={field.label}
                    labelNode={labelNode}
                    type={htmlInputType(field.type)}
                    inputMode={field.type === "tel" ? "tel" : field.type === "email" ? "email" : field.type === "url" ? "url" : undefined}
                    autoComplete={field.type === "tel" ? "tel" : field.type === "email" ? "email" : field.type === "url" ? "url" : undefined}
                    value={display}
                    onChange={(e) => handleChange(field.key, e.target.value)}
                    onBlur={() => setTouched((prev) => ({ ...prev, [field.key]: true }))}
                    placeholder={field.placeholder}
                    invalid={showError}
                    errorMessage={showError ? messageErreur : undefined}
                  />
                );
              })}
            </div>
          )}
        </section>
        )}

        {/* Premier message automatique (leads recus par email). Masque si le
            chargement a echoue (meme logique que Coordonnees). */}
        {!loadError && (
        <section className="mb-6 rounded-xl border border-[var(--border)] bg-[var(--card)] p-6">
          <header className="mb-6">
            <h2 className="font-display text-[22px] font-bold tracking-display-tight text-[var(--foreground)]">
              Premier message automatique
            </h2>
            <p className="mt-2 text-[14px] leading-relaxed text-[var(--color-lr-text-secondary)]">
              Pour les leads reçus par email, l&apos;IA envoie automatiquement le premier message
              WhatsApp après un court délai, sans intervention de votre part.
            </p>
          </header>

          {loading ? (
            <div className="space-y-5">
              <Skeleton className="h-6 w-64" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : (
            <div className="space-y-5">
              {/* Toggle d'activation */}
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-sm font-semibold text-[var(--foreground)]">
                    Activer l&apos;envoi automatique du premier message
                  </p>
                  <p className="mt-1 text-xs text-[var(--color-lr-text-secondary)]">
                    S&apos;applique uniquement aux leads reçus par email. Nécessite WhatsApp activé
                    sur votre établissement.
                  </p>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={config.premierMessageAutoActif}
                  onClick={() =>
                    setConfig((prev) => ({ ...prev, premierMessageAutoActif: !prev.premierMessageAutoActif }))
                  }
                  className={`relative mt-0.5 inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
                    config.premierMessageAutoActif ? "bg-accent" : "bg-[var(--muted)]"
                  }`}
                >
                  <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-[var(--card)] shadow transition-transform ${
                      config.premierMessageAutoActif ? "translate-x-6" : "translate-x-1"
                    }`}
                  />
                </button>
              </div>

              {config.premierMessageAutoActif && (
                <div className="space-y-5 border-t border-[var(--border)] pt-5">
                  {/* Delai */}
                  <div className="max-w-xs">
                    <Input
                      id="premierMessageAutoDelaiMinutes"
                      label="Délai avant envoi (minutes)"
                      labelNode={
                        <InfoIcon>
                          Temps d&apos;attente après réception du lead avant l&apos;envoi du premier
                          message. Laisse une marge pour reprendre la main manuellement si besoin.
                        </InfoIcon>
                      }
                      type="number"
                      min={0}
                      max={1440}
                      value={String(config.premierMessageAutoDelaiMinutes)}
                      onChange={(e) => {
                        const n = parseInt(e.target.value, 10);
                        setConfig((prev) => ({
                          ...prev,
                          premierMessageAutoDelaiMinutes: Number.isNaN(n) ? 0 : Math.min(1440, Math.max(0, n)),
                        }));
                      }}
                    />
                  </div>

                  {/* Plage d'envoi */}
                  <div>
                    <FieldLabel
                      label="Plage d'envoi"
                      labelNode={
                        <InfoIcon>
                          Les messages ne partent que dans cette plage horaire. Un lead reçu en
                          dehors est mis en attente et envoyé au prochain créneau (à l&apos;heure de
                          début).
                        </InfoIcon>
                      }
                      htmlFor="premierMessageAutoFenetreDebutHeure"
                    />
                    <div className="flex items-center gap-3">
                      <select
                        id="premierMessageAutoFenetreDebutHeure"
                        value={config.premierMessageAutoFenetreDebutHeure}
                        onChange={(e) =>
                          setConfig((prev) => ({
                            ...prev,
                            premierMessageAutoFenetreDebutHeure: parseInt(e.target.value, 10),
                          }))
                        }
                        className={inputFieldClass(false)}
                      >
                        {HEURES_DEBUT.map((h) => (
                          <option key={h} value={h}>{`${h}h`}</option>
                        ))}
                      </select>
                      <span className="text-sm text-[var(--color-lr-text-secondary)]">à</span>
                      <select
                        id="premierMessageAutoFenetreFinHeure"
                        value={config.premierMessageAutoFenetreFinHeure}
                        onChange={(e) =>
                          setConfig((prev) => ({
                            ...prev,
                            premierMessageAutoFenetreFinHeure: parseInt(e.target.value, 10),
                          }))
                        }
                        className={inputFieldClass(!plageValide)}
                      >
                        {HEURES_FIN.map((h) => (
                          <option key={h} value={h}>{labelHeureFin(h)}</option>
                        ))}
                      </select>
                    </div>
                    {!plageValide && (
                      <p className="mt-1.5 text-xs text-danger">
                        L&apos;heure de début doit être antérieure à l&apos;heure de fin.
                      </p>
                    )}
                  </div>

                  {/* Rappel de conformité : l'auto-envoi part sans action humaine.
                      La base légale du contact relève de l'établissement (responsable de traitement). */}
                  <div className="rounded-lg border border-warning/30 bg-warning/10 px-3 py-2.5 text-xs leading-relaxed text-[var(--color-lr-text-secondary)]">
                    En activant l&apos;envoi automatique, vous confirmez disposer d&apos;une base légale
                    pour contacter ces prospects par WhatsApp <strong className="font-semibold text-[var(--foreground)]">sans action manuelle préalable</strong>.
                    Votre établissement reste responsable de traitement pour ces envois (RGPD).
                  </div>
                </div>
              )}
            </div>
          )}
        </section>
        )}
      </div>

      {/* Barre sticky save : uniquement pour la section Coordonnees.
          left-0 sur mobile (pas de sidebar), lg:left-56 sur desktop pour
          aligner la barre avec la zone main apres la Sidebar (w-56). */}
      {!loading && (
        <div
          className={`fixed bottom-0 left-0 right-0 z-30 border-t border-[var(--border)] bg-[var(--card)]/95 backdrop-blur-sm transition-transform duration-300 lg:left-56 ${
            dirty || successVisible ? "translate-y-0" : "translate-y-full pointer-events-none"
          }`}
        >
          <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-6 py-3">
            <div className="flex items-center gap-2 text-sm">
              {successVisible ? (
                <span role="status" aria-live="polite" className="flex items-center gap-1.5 font-medium text-success">
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  Coordonnées enregistrées
                </span>
              ) : dirty ? (
                <span className="flex items-center gap-1.5 text-[var(--color-lr-text-secondary)]">
                  <span className="h-1.5 w-1.5 rounded-full bg-warning" /> Modifications non enregistrées
                </span>
              ) : null}
            </div>
            <div className="flex items-center gap-2">
              {dirty && !saving && shortcutLabel && (
                <kbd className="hidden items-center rounded border border-[var(--border)] bg-[var(--muted)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--color-lr-text-tertiary)] sm:inline-flex">
                  {shortcutLabel}
                </kbd>
              )}
              <Button variant="secondary" onClick={handleReset} disabled={saving || !dirty}>
                Annuler les modifications
              </Button>
              <Button
                variant="primary"
                onClick={handleSave}
                disabled={saving || !dirty || !formulaireValide}
                title={!formulaireValide ? "Corrigez les champs en erreur avant d'enregistrer" : undefined}
                className="inline-flex items-center gap-2"
              >
                {saving && (
                  <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                  </svg>
                )}
                {saving ? "Enregistrement…" : "Enregistrer"}
              </Button>
            </div>
          </div>
        </div>
      )}

      <Modal
        open={pendingNav !== null}
        onClose={() => setPendingNav(null)}
        title="Modifications non enregistrées"
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setPendingNav(null)}>
              Rester sur la page
            </Button>
            <Button variant="danger" onClick={confirmerNavigation}>
              Quitter sans enregistrer
            </Button>
          </>
        }
      >
        <p className="text-sm leading-relaxed text-[var(--color-lr-text-secondary)]">
          Vos coordonnées ont été modifiées mais pas encore enregistrées. Si vous quittez maintenant, ces changements seront perdus.
        </p>
      </Modal>
    </AppLayout>
  );
}
