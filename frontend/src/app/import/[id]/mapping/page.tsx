"use client";

import { useEffect, useState, useCallback } from "react";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useApiClient, formatErreur } from "@/lib/auth";
import AppLayout from "@/components/AppLayout";
import ErrorState from "@/components/common/ErrorState";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

const CHAMPS_CIBLES = [
  { value: "nom", label: "Nom" },
  { value: "prenom", label: "Prénom" },
  { value: "nom_complet", label: "Nom complet (à séparer)" },
  { value: "telephone", label: "Téléphone" },
  { value: "email", label: "Email" },
  { value: "marque", label: "Marque" },
  { value: "modele", label: "Modèle" },
  { value: "territoire", label: "Territoire" },
  { value: "etape", label: "Étape" },

  { value: "devis", label: "Devis" },
  { value: "reprise", label: "Reprise" },
  { value: "type", label: "Type" },
  { value: "dateModificationDevis", label: "Date modification devis" },
  { value: "budget", label: "Budget" },
  { value: "vendeurAssigne", label: "Vendeur assigné" },
  { value: "commentaire", label: "Commentaire" },
  { value: "IGNORER", label: "Ignorer" },
];

interface ColonneMapping {
  headerOriginal: string;
  cible: string;
  confiance: number;
  raison: string | null;
  echantillon: string[];
}

interface MappingData {
  source: string;
  templateId: number | null;
  colonnes: ColonneMapping[];
  formatDateDetecte: string | null;
  splitDetecte: { colonne: string; type: string; ordreDetecte: string } | null;
}

interface PreviewRow {
  numeroLigne: number;
  donnees: Record<string, string>;
  valide: boolean;
  erreurs: string[];
  doublon: boolean;
}

interface ImportResult {
  nombreLignes: number;
  nombreImportees: number;
  nombreErreurs: number;
  nombreDoublons: number;
  statut: string;
}

export default function MappingPage() {
  const apiFetch = useApiClient();
  const router = useRouter();
  const params = useParams();
  const searchParams = useSearchParams();
  const importId = params.id as string;
  const retour = searchParams.get("retour");

  const [step, setStep] = useState<"mapping" | "preview" | "result">("mapping");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [erreurChargement, setErreurChargement] = useState(false);

  // Mapping state
  const [mappingData, setMappingData] = useState<MappingData | null>(null);
  const [colonnes, setColonnes] = useState<ColonneMapping[]>([]);
  const [splitEnabled, setSplitEnabled] = useState(false);
  const [splitOrdre, setSplitOrdre] = useState<"NOM_PRENOM" | "PRENOM_NOM">("NOM_PRENOM");
  const [splitColonne, setSplitColonne] = useState<string | null>(null);
  const [formatDate, setFormatDate] = useState<string>("");
  const [nomTemplate, setNomTemplate] = useState("");

  // Preview state
  const [preview, setPreview] = useState<PreviewRow[]>([]);
  const [previewDoublons, setPreviewDoublons] = useState(0);
  const [validating, setValidating] = useState(false);

  // Result state
  const [result, setResult] = useState<ImportResult | null>(null);
  const [importing, setImporting] = useState(false);

  // Charger le mapping (template ou IA)
  const chargerMapping = useCallback(async () => {
    setLoading(true);
    setError("");
    setErreurChargement(false);
    try {
      const res = await apiFetch(`${API_BASE}/api/imports/${importId}/mapping`, {
        method: "POST",
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data: MappingData = await res.json();
      setMappingData(data);
      setColonnes(data.colonnes);
      if (data.formatDateDetecte) setFormatDate(data.formatDateDetecte);
      if (data.splitDetecte) {
        setSplitEnabled(true);
        setSplitColonne(data.splitDetecte.colonne);
        setSplitOrdre(data.splitDetecte.ordreDetecte === "PRENOM_NOM" ? "PRENOM_NOM" : "NOM_PRENOM");
      }
    } catch (err) {
      setError(formatErreur(err));
      setErreurChargement(true);
    } finally {
      setLoading(false);
    }
  }, [apiFetch, importId]);

  useEffect(() => { chargerMapping(); }, [chargerMapping]);

  function updateCible(index: number, newCible: string) {
    setColonnes(prev => prev.map((col, i) => i === index ? { ...col, cible: newCible } : col));
    // Si on choisit nom_complet, activer le split
    if (newCible === "nom_complet") {
      setSplitEnabled(true);
      setSplitColonne(colonnes[index].headerOriginal);
    }
  }

  async function handleValiderMapping() {
    setValidating(true);
    setError("");
    try {
      const mappings: Record<string, string> = {};
      for (const col of colonnes) {
        mappings[col.headerOriginal] = col.cible;
      }

      const body: Record<string, unknown> = {
        mappings,
        formatDate: formatDate || null,
        nomTemplate: nomTemplate || null,
      };

      if (splitEnabled && splitColonne) {
        body.splitOption = {
          colonne: splitColonne,
          ordreNomPrenom: splitOrdre,
        };
      }

      const res = await apiFetch(`${API_BASE}/api/imports/${importId}/mapping/validate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });

      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data = await res.json();
      setPreview(data.lignes || []);
      setPreviewDoublons(data.nombreDoublonsPotentiels || 0);
      setStep("preview");
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setValidating(false);
    }
  }

  async function handleImport() {
    setImporting(true);
    setError("");
    try {
      const res = await apiFetch(`${API_BASE}/api/imports/${importId}/validate`, {
        method: "POST",
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data: ImportResult = await res.json();
      setResult(data);
      setStep("result");
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setImporting(false);
    }
  }

  const steps = [
    { num: 1, label: "Mapping" },
    { num: 2, label: "Preview" },
    { num: 3, label: "Résultat" },
  ];

  const currentStepNum = step === "mapping" ? 1 : step === "preview" ? 2 : 3;

  return (
    <AppLayout>
      <div className="px-6 py-6">
        <h1 className="mb-4 text-2xl font-bold text-[var(--foreground)]">
          Mapping des colonnes
        </h1>

        {/* Stepper */}
        <div className="mb-8 flex items-center justify-center gap-2">
          {steps.map((s, i) => (
            <div key={s.num} className="flex items-center gap-2">
              <div
                className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-medium ${
                  currentStepNum >= s.num
                    ? "bg-accent text-white"
                    : "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]"
                }`}
              >
                {s.num}
              </div>
              <span
                className={`text-sm font-medium ${
                  currentStepNum >= s.num
                    ? "text-[var(--foreground)]"
                    : "text-[var(--color-lr-text-tertiary)]"
                }`}
              >
                {s.label}
              </span>
              {i < steps.length - 1 && (
                <div
                  className={`mx-2 h-0.5 w-8 ${
                    currentStepNum > s.num ? "bg-accent" : "bg-[var(--border)]"
                  }`}
                />
              )}
            </div>
          ))}
        </div>

        {error && !erreurChargement && (
          <div className="mb-6 rounded-lg bg-danger/10 p-3 text-sm text-danger">
            {error}
          </div>
        )}

        {/* Step 1: Mapping */}
        {step === "mapping" && (
          <>
            {loading && (
              <div className="rounded-xl bg-[var(--card)] p-8 shadow-sm">
                <p className="text-sm text-[var(--color-lr-text-secondary)]">Analyse en cours...</p>
              </div>
            )}

            {!loading && erreurChargement && (
              <div className="rounded-xl bg-[var(--card)] shadow-sm">
                <ErrorState message={error} onRetry={chargerMapping} />
              </div>
            )}

            {!loading && !erreurChargement && mappingData && (
              <div className="space-y-6">
                {/* Bandeau source */}
                {mappingData.source === "TEMPLATE" && (
                  <div className="rounded-lg bg-success/10 p-3 text-sm text-success">
                    Mapping retrouvé automatiquement depuis un template existant. Vous pouvez valider directement.
                  </div>
                )}
                {mappingData.source === "IA" && (
                  <div className="rounded-lg bg-accent/10 p-3 text-sm text-accent">
                    Mapping proposé par l&apos;IA, vérifiez les colonnes en orange et rouge.
                  </div>
                )}

                {/* Tableau de mapping */}
                <div className="rounded-xl bg-[var(--card)] shadow-sm">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                      <thead>
                        <tr className="border-b border-[var(--border)] text-[var(--color-lr-text-secondary)]">
                          <th className="px-4 py-3 font-medium">Colonne CSV</th>
                          <th className="px-4 py-3 font-medium">Exemples</th>
                          <th className="px-4 py-3 font-medium">Champ cible</th>
                          <th className="px-4 py-3 font-medium w-24 text-center">Confiance</th>
                        </tr>
                      </thead>
                      <tbody>
                        {colonnes.map((col, idx) => (
                          <tr key={idx} className="border-b border-[var(--border)]">
                            <td className="px-4 py-3 font-medium text-[var(--foreground)]">
                              {col.headerOriginal}
                            </td>
                            <td className="px-4 py-3 text-xs text-[var(--color-lr-text-tertiary)] max-w-[200px] truncate">
                              {col.echantillon?.join(", ") || "-"}
                            </td>
                            <td className="px-4 py-3">
                              <select
                                value={col.cible}
                                onChange={(e) => updateCible(idx, e.target.value)}
                                className={`rounded-lg border px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-accent ${
                                  col.cible === "IGNORER"
                                    ? "border-[var(--border)] text-[var(--color-lr-text-tertiary)]"
                                    : col.confiance >= 80
                                      ? "border-success/40"
                                      : col.confiance >= 50
                                        ? "border-warning/40"
                                        : "border-danger/40"
                                }`}
                              >
                                {CHAMPS_CIBLES.map(c => (
                                  <option key={c.value} value={c.value}>{c.label}</option>
                                ))}
                              </select>
                            </td>
                            <td className="px-4 py-3 text-center">
                              <ConfidenceBadge confiance={col.confiance} cible={col.cible} />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Split nom/prénom */}
                {colonnes.some(c => c.cible === "nom_complet") && (
                  <div className="rounded-xl bg-[var(--card)] p-4 shadow-sm">
                    <h3 className="mb-3 text-sm font-semibold text-[var(--foreground)]">
                      Séparation nom / prénom
                    </h3>
                    <div className="flex items-center gap-3 mb-3">
                      <label className="flex items-center gap-2 text-sm text-[var(--foreground)]">
                        <input
                          type="checkbox"
                          checked={splitEnabled}
                          onChange={(e) => setSplitEnabled(e.target.checked)}
                          className="rounded border-[var(--border)] text-accent focus:ring-accent"
                        />
                        Séparer nom et prénom
                      </label>
                    </div>
                    {splitEnabled && (
                      <div className="flex gap-4">
                        <label className="flex items-center gap-2 text-sm text-[var(--color-lr-text-secondary)]">
                          <input
                            type="radio"
                            name="splitOrdre"
                            checked={splitOrdre === "NOM_PRENOM"}
                            onChange={() => setSplitOrdre("NOM_PRENOM")}
                            className="text-accent focus:ring-accent"
                          />
                          Nom Prénom (Dupont Jean)
                        </label>
                        <label className="flex items-center gap-2 text-sm text-[var(--color-lr-text-secondary)]">
                          <input
                            type="radio"
                            name="splitOrdre"
                            checked={splitOrdre === "PRENOM_NOM"}
                            onChange={() => setSplitOrdre("PRENOM_NOM")}
                            className="text-accent focus:ring-accent"
                          />
                          Prénom Nom (Jean Dupont)
                        </label>
                      </div>
                    )}
                  </div>
                )}

                {/* Nom template optionnel */}
                <div className="rounded-xl bg-[var(--card)] p-4 shadow-sm">
                  <label className="block text-sm font-medium text-[var(--foreground)] mb-2">
                    Nom du template (optionnel)
                  </label>
                  <input
                    type="text"
                    value={nomTemplate}
                    onChange={(e) => setNomTemplate(e.target.value)}
                    placeholder="Ex: Import Salesforce, Export DataCar..."
                    className="w-full rounded-lg border border-[var(--border)] bg-[var(--muted)] px-3 py-2 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-tertiary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  />
                  <p className="mt-1 text-xs text-[var(--color-lr-text-tertiary)]">
                    Ce mapping sera sauvegardé pour réutilisation lors des prochains imports.
                  </p>
                </div>

                {/* Actions */}
                <div className="flex gap-3">
                  <Link
                    href="/import"
                    className="rounded-lg bg-[var(--muted)] px-4 py-2.5 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--border)]"
                  >
                    Annuler
                  </Link>
                  <button
                    onClick={handleValiderMapping}
                    disabled={validating}
                    className="rounded-lg btn-primary px-6 py-2.5 text-sm font-medium text-white transition-colors disabled:opacity-50"
                  >
                    {validating ? "Validation..." : "Valider et prévisualiser"}
                  </button>
                </div>
              </div>
            )}
          </>
        )}

        {/* Step 2: Preview */}
        {step === "preview" && (
          <section className="space-y-6">
            <div className="rounded-xl bg-[var(--card)] p-6 shadow-sm">
              <h2 className="mb-4 text-base font-semibold text-[var(--foreground)]">
                Prévisualisation ({preview.length} premières lignes)
              </h2>

              {/* Compteurs */}
              <div className="mb-4 flex gap-4 text-sm">
                <span className="text-success">{preview.filter(r => r.valide && !r.doublon).length} valides</span>
                <span className="text-warning">{previewDoublons} doublons</span>
                <span className="text-danger">{preview.filter(r => !r.valide).length} erreurs</span>
              </div>

              {preview.length > 0 && (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead>
                      <tr className="border-b border-[var(--border)] text-[var(--color-lr-text-secondary)]">
                        <th className="px-3 py-2 font-medium">#</th>
                        <th className="px-3 py-2 font-medium">Statut</th>
                        {Object.keys(preview[0]?.donnees || {}).map((col) => (
                          <th key={col} className="px-3 py-2 font-medium">
                            {col}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {preview.map((row, i) => (
                        <tr key={i} className="border-b border-[var(--border)]">
                          <td className="px-3 py-2 text-[var(--color-lr-text-secondary)]">{row.numeroLigne}</td>
                          <td className="px-3 py-2">
                            <span
                              className={`rounded-full px-2 py-1 text-xs font-medium ${
                                row.doublon
                                  ? "bg-warning/10 text-warning"
                                  : row.valide
                                    ? "bg-success/10 text-success"
                                    : "bg-danger/10 text-danger"
                              }`}
                            >
                              {row.doublon ? "DOUBLON" : row.valide ? "VALIDE" : "INVALIDE"}
                            </span>
                            {row.erreurs?.length > 0 && (
                              <p className="mt-1 text-xs text-danger">{row.erreurs.join(", ")}</p>
                            )}
                          </td>
                          {Object.values(row.donnees || {}).map((val, j) => (
                            <td key={j} className="px-3 py-2 text-[var(--foreground)]">
                              {val || "-"}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              <div className="mt-6 flex gap-3">
                <button
                  onClick={() => setStep("mapping")}
                  className="rounded-lg bg-[var(--muted)] px-4 py-2 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--border)]"
                >
                  Modifier le mapping
                </button>
                <button
                  onClick={handleImport}
                  disabled={importing}
                  className="rounded-lg btn-primary px-6 py-2 text-sm font-medium text-white transition-colors disabled:opacity-50"
                >
                  {importing ? "Import en cours..." : "Lancer l'import"}
                </button>
              </div>
            </div>
          </section>
        )}

        {/* Step 3: Result */}
        {step === "result" && result && (
          <section className="rounded-xl bg-[var(--card)] p-8 shadow-sm">
            <div className="mb-6 flex flex-col items-center text-center">
              <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-success/10">
                <svg className="h-9 w-9 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <h2 className="text-2xl font-bold text-[var(--foreground)]">
                {result.nombreImportees} lead{result.nombreImportees > 1 ? "s" : ""} importé{result.nombreImportees > 1 ? "s" : ""}
              </h2>
              <p className="mt-2 text-sm text-[var(--color-lr-text-secondary)]">
                {result.nombreDoublons > 0 && `${result.nombreDoublons} doublon${result.nombreDoublons > 1 ? "s" : ""} ignoré${result.nombreDoublons > 1 ? "s" : ""}. `}
                {result.nombreErreurs > 0 && `${result.nombreErreurs} ligne${result.nombreErreurs > 1 ? "s" : ""} en erreur.`}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <ResultCard label="Total" value={result.nombreLignes} color="text-[var(--foreground)]" />
              <ResultCard label="Importés" value={result.nombreImportees} color="text-success" />
              <ResultCard label="Doublons" value={result.nombreDoublons} color="text-warning" />
              <ResultCard label="Erreurs" value={result.nombreErreurs} color="text-danger" />
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              {retour === "onboarding" ? (
                <Link href="/onboarding" className="rounded-lg btn-primary px-6 py-2.5 text-sm font-semibold text-white transition-colors">
                  Continuer la configuration
                </Link>
              ) : (
                <>
                  <Link href="/leads" className="rounded-lg btn-primary px-6 py-2.5 text-sm font-semibold text-white transition-colors">
                    Voir les leads
                  </Link>
                  <Link href="/import" className="rounded-lg border border-[var(--border)] bg-[var(--card)] px-4 py-2.5 text-sm font-medium text-[var(--foreground)] transition-colors hover:bg-[var(--muted)]">
                    Nouvel import
                  </Link>
                </>
              )}
            </div>
          </section>
        )}
      </div>
    </AppLayout>
  );
}

function ConfidenceBadge({ confiance, cible }: { confiance: number; cible: string }) {
  if (cible === "IGNORER") {
    return <span className="rounded-full bg-[var(--muted)] px-2 py-1 text-xs font-medium text-[var(--color-lr-text-tertiary)]">—</span>;
  }
  if (confiance >= 80) {
    return <span className="rounded-full bg-success/10 px-2 py-1 text-xs font-medium text-success">{confiance}%</span>;
  }
  if (confiance >= 50) {
    return <span className="rounded-full bg-warning/10 px-2 py-1 text-xs font-medium text-warning">{confiance}%</span>;
  }
  return <span className="rounded-full bg-danger/10 px-2 py-1 text-xs font-medium text-danger">{confiance}%</span>;
}

function ResultCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="rounded-lg bg-[var(--muted)] p-4 text-center">
      <p className="text-sm text-[var(--color-lr-text-secondary)]">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${color}`}>{value}</p>
    </div>
  );
}
