"use client";

import { Suspense, useEffect, useState, useRef, useCallback } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useApiClient, formatErreur } from "@/lib/auth";
import AppLayout from "@/components/AppLayout";
import PageHeader from "@/components/common/PageHeader";
import Tabs from "@/components/common/Tabs";
import EmptyState from "@/components/common/EmptyState";
import Button from "@/components/common/Button";
import Alert from "@/components/common/Alert";
import Skeleton from "@/components/common/Skeleton";
import ErrorState from "@/components/common/ErrorState";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

type TabKey = "import" | "historique" | "templates";
type ModeKey = "fichier" | "coller" | "texte";

// Libelles user-facing des statuts d'import (enum backend ImportStatutEnum).
const STATUT_IMPORT_LABELS: Record<string, string> = {
  EN_COURS: "En cours",
  TERMINE: "Terminé",
  ERREUR: "Erreur",
};

export default function ImportPage() {
  return <Suspense><ImportPageContent /></Suspense>;
}

function ImportPageContent() {
  const apiFetch = useApiClient();
  const router = useRouter();
  const searchParams = useSearchParams();

  // Etat initial depuis l'URL (?tab=historique&mode=coller) : un F5 restitue la vue.
  const [tab, setTab] = useState<TabKey>(
    searchParams.get("tab") === "historique" ? "historique" : "import",
  );
  const [mode, setMode] = useState<ModeKey>(() => {
    const m = searchParams.get("mode");
    return m === "coller" || m === "texte" ? m : "fichier";
  });

  const [file, setFile] = useState<File | null>(null);
  const [pasteContent, setPasteContent] = useState("");
  const [texteLibre, setTexteLibre] = useState("");
  const [textePreview, setTextePreview] = useState<Record<string, unknown> | null>(null);
  const [texteSaving, setTexteSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Historique
  const [imports, setImports] = useState<{ idImport: number; nomFichier: string; dateImport: string; nombreLignes: number; nombreImportees: number; nombreErreurs: number; nombreDoublons: number; statut: string; typeFichier: string }[]>([]);
  const [importsLoading, setImportsLoading] = useState(false);
  const [importsError, setImportsError] = useState("");

  const [error, setError] = useState("");

  // Reflete tab/mode dans l'URL sans recharger (regle #10 : F5 restitue l'etat).
  const syncUrl = useCallback(
    (next: { tab?: TabKey; mode?: ModeKey }) => {
      const params = new URLSearchParams(Array.from(searchParams.entries()));
      if (next.tab) params.set("tab", next.tab);
      if (next.mode) params.set("mode", next.mode);
      router.replace(`/import?${params.toString()}`, { scroll: false });
    },
    [router, searchParams],
  );

  function changeTab(v: TabKey) {
    if (v === "templates") {
      router.push("/import/templates");
      return;
    }
    setTab(v);
    syncUrl({ tab: v });
  }

  function changeMode(m: ModeKey) {
    setMode(m);
    syncUrl({ mode: m });
  }

  const fetchImports = useCallback(() => {
    setImportsLoading(true);
    setImportsError("");
    apiFetch(`${API_BASE}/api/imports`)
      .then(async (res) => { if (!res.ok) throw new Error(`Erreur ${res.status}`); return res.json(); })
      .then((data) => setImports(Array.isArray(data) ? data : []))
      .catch((err) => setImportsError(formatErreur(err)))
      .finally(() => setImportsLoading(false));
  }, [apiFetch]);

  useEffect(() => { if (tab === "historique") fetchImports(); }, [tab, fetchImports]);

  async function handleUpload() {
    if (!file) return;
    setUploading(true);
    setError("");

    try {
      const formData = new FormData();
      formData.append("file", file);

      const res = await apiFetch(`${API_BASE}/api/imports/upload`, {
        method: "POST",
        body: formData,
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => null);
        throw new Error(errData?.message || `Erreur ${res.status}`);
      }
      const data = await res.json();
      // Rediriger vers la page de mapping
      router.push(`/import/${data.importId}/mapping`);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setUploading(false);
    }
  }

  async function handlePaste() {
    if (!pasteContent.trim()) return;
    setUploading(true);
    setError("");

    try {
      const res = await apiFetch(`${API_BASE}/api/imports/paste`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ contenu: pasteContent }),
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => null);
        throw new Error(errData?.message || `Erreur ${res.status}`);
      }
      const data = await res.json();
      router.push(`/import/${data.importId}/mapping`);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setUploading(false);
    }
  }

  async function handleParseTexteLibre() {
    if (!texteLibre.trim() || texteLibre.trim().length < 20) return;
    setUploading(true);
    setError("");
    setTextePreview(null);
    try {
      const res = await apiFetch(`${API_BASE}/api/leads/parse-texte`, {
        method: "POST",
        body: JSON.stringify({ texte: texteLibre }),
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const data = await res.json();
      setTextePreview(data);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setUploading(false);
    }
  }

  async function handleCreerDepuisTexte() {
    if (!textePreview) return;
    setTexteSaving(true);
    setError("");
    try {
      const body = { ...textePreview, texteOriginal: texteLibre };
      const res = await apiFetch(`${API_BASE}/api/leads/from-texte`, {
        method: "POST",
        body: JSON.stringify(body),
      });
      if (res.status === 409) {
        setError("Un prospect avec ce téléphone ou email existe déjà.");
        return;
      }
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const lead = await res.json();
      router.push(`/leads/${lead.idLead}`);
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setTexteSaving(false);
    }
  }

  function handleDrag(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      setFile(e.dataTransfer.files[0]);
    }
  }

  const conf = Number(textePreview?.confiance);
  const confValide = Number.isFinite(conf);

  return (
    <AppLayout>
      <div className="mx-auto max-w-4xl px-6 py-6 space-y-6">
        <PageHeader
          eyebrow="Acquisition"
          title="Importer des prospects"
          subtitle="Ajoutez vos leads en quelques secondes, depuis un fichier, un copier-coller, ou un email reçu."
        />

        {/* Onglets : Import / Historique / Templates (le 3e navigue vers une page dediee) */}
        <Tabs
          value={tab}
          onChange={(v) => changeTab(v as TabKey)}
          items={[
            { value: "import", label: "Nouvel import" },
            { value: "historique", label: "Historique" },
            { value: "templates", label: "Templates" },
          ]}
        />

        {/* Conteneur des onglets : key={tab} force le remount du sous-arbre
            quand on change d'onglet, ce qui rejoue l'animation tab-content-enter.
            Sans le key, React garde le meme noeud et l'animation ne se declenche
            qu'une fois (au mount initial). */}
        <div key={tab} className="animate-tab-content">
        {/* Onglet historique */}
        {tab === "historique" && (
          <div className="card-leadrive">
            {importsLoading && (
              <div className="p-4 space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="flex items-center gap-4 py-2">
                    <Skeleton className="h-4 w-32" />
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-4 w-12" />
                    <Skeleton className="h-4 w-12" />
                    <Skeleton className="h-4 w-12" />
                    <Skeleton className="h-5 w-16 rounded-full" />
                  </div>
                ))}
              </div>
            )}
            {!importsLoading && importsError && (
              <ErrorState message={importsError} onRetry={fetchImports} />
            )}
            {!importsLoading && !importsError && imports.length === 0 && (
              <EmptyState
                eyebrow="Historique vide"
                eyebrowTone="accent"
                title="Aucun import pour l'instant."
                description="Vos imports passés s'afficheront ici avec leurs résultats (importés, erreurs, doublons)."
                visual={
                  <div className="flex h-14 w-14 items-center justify-center rounded-full bg-accent/10">
                    <svg className="h-6 w-6 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                        d="M9 17v-2a4 4 0 014-4h4m-4 4l4-4m0 0l-4-4m4 4H7a4 4 0 00-4 4v1" />
                    </svg>
                  </div>
                }
                action={
                  <Button variant="primary" onClick={() => changeTab("import")}>
                    Lancer mon premier import
                  </Button>
                }
                size="md"
                align="center"
                divider
              />
            )}
            {!importsLoading && !importsError && imports.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-[var(--border)] text-[var(--color-lr-text-secondary)]">
                      <th scope="col" className="px-4 py-3 font-medium">Fichier</th>
                      <th scope="col" className="px-4 py-3 font-medium">Format</th>
                      <th scope="col" className="px-4 py-3 font-medium">Date</th>
                      <th scope="col" className="px-4 py-3 font-medium">Importés</th>
                      <th scope="col" className="px-4 py-3 font-medium">Erreurs</th>
                      <th scope="col" className="px-4 py-3 font-medium">Doublons</th>
                      <th scope="col" className="px-4 py-3 font-medium">Statut</th>
                    </tr>
                  </thead>
                  <tbody>
                    {imports.map((imp, i) => (
                      <tr key={imp.idImport || i} className="border-b border-[var(--border)] last:border-b-0 text-[var(--foreground)] hover:bg-[var(--muted)]/30 transition-colors">
                        <td className="px-4 py-3 font-medium">{imp.nomFichier || "-"}</td>
                        <td className="px-4 py-3 text-[var(--color-lr-text-secondary)] text-xs">{formatLabel(imp.typeFichier)}</td>
                        <td className="px-4 py-3 text-[var(--color-lr-text-secondary)]">{imp.dateImport ? new Date(imp.dateImport).toLocaleDateString("fr-FR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "-"}</td>
                        <td className="px-4 py-3 font-mono text-success">{imp.nombreImportees ?? "-"}</td>
                        <td className="px-4 py-3 font-mono text-danger">{imp.nombreErreurs ?? "-"}</td>
                        <td className="px-4 py-3 font-mono text-warning">{imp.nombreDoublons ?? "-"}</td>
                        <td className="px-4 py-3">
                          <span className={`rounded-full px-2 py-1 text-xs font-medium ${imp.statut === "TERMINE" ? "bg-success/10 text-success" : imp.statut === "ERREUR" ? "bg-danger/10 text-danger" : "bg-warning/10 text-warning"}`}>
                            {STATUT_IMPORT_LABELS[imp.statut] ?? imp.statut}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* Onglet nouvel import */}
        {tab === "import" && (
          <>
            {error && (
              <div role="alert">
                <Alert tone="danger">{error}</Alert>
              </div>
            )}

            {/* Choix du mode : 3 cartes visuelles avec icone + description */}
            <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
              <ModeChoiceCard
                actif={mode === "fichier"}
                onClick={() => changeMode("fichier")}
                titre="Importer un fichier"
                description="CSV, Excel, TSV"
                pour="Plusieurs dizaines à milliers de prospects"
                icone={
                  <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                      d="M9 13h6m-3-3v6m5 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                }
              />
              <ModeChoiceCard
                actif={mode === "coller"}
                onClick={() => changeMode("coller")}
                titre="Coller des données"
                description="Excel, DMS, tableau"
                pour="Copier-coller depuis votre outil"
                icone={
                  <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                      d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                  </svg>
                }
              />
              <ModeChoiceCard
                actif={mode === "texte"}
                onClick={() => changeMode("texte")}
                titre="Coller un email"
                description="Email LeBonCoin, La Centrale…"
                pour="1 prospect à la fois, l'IA extrait tout"
                icone={
                  <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                      d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                }
              />
            </div>

            <section className="card-leadrive p-6 mt-2">

            {mode === "fichier" && (
              <>
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="display-h2 text-[var(--foreground)]">
                    Sélectionner un fichier
                  </h2>
                  <div className="flex gap-1.5">
                    {["CSV", "Excel", "TSV"].map(f => (
                      <span key={f} className="rounded-md bg-[var(--muted)] px-2 py-0.5 text-[10px] font-medium text-[var(--color-lr-text-secondary)] uppercase tracking-wide">
                        {f}
                      </span>
                    ))}
                  </div>
                </div>

                <div
                  role="button"
                  tabIndex={0}
                  aria-label="Sélectionner un fichier à importer, ou glissez-le dans cette zone"
                  onDragEnter={handleDrag}
                  onDragLeave={handleDrag}
                  onDragOver={handleDrag}
                  onDrop={handleDrop}
                  onClick={() => fileInputRef.current?.click()}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      fileInputRef.current?.click();
                    }
                  }}
                  className={`group flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed p-12 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 ${
                    dragActive
                      ? "border-accent bg-accent/5 scale-[1.01]"
                      : "border-[var(--border)] hover:border-accent/50 hover:bg-[var(--muted)]/30"
                  }`}
                >
                  <div className={`mb-4 flex h-14 w-14 items-center justify-center rounded-full transition-colors ${
                    dragActive ? "bg-accent text-white" : "bg-accent/10 text-accent group-hover:bg-accent/20"
                  }`}>
                    <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                        d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                    </svg>
                  </div>
                  <p className="text-base font-medium text-[var(--foreground)]">
                    {dragActive ? "Relâchez pour déposer" : "Glissez un fichier ici"}
                  </p>
                  <p className="mt-1 text-sm text-[var(--color-lr-text-secondary)]">
                    ou <span className="text-accent font-medium">cliquez pour sélectionner</span>
                  </p>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".csv,.tsv,.xlsx,.xls"
                    className="hidden"
                    onChange={(e) => {
                      if (e.target.files && e.target.files[0]) {
                        setFile(e.target.files[0]);
                      }
                    }}
                  />
                </div>

                {file && (
                  <div className="mt-4 flex items-center gap-3 rounded-lg border border-accent/30 bg-accent/5 px-4 py-3">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent/10 text-accent">
                      <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                          d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                      </svg>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="truncate text-sm font-medium text-[var(--foreground)]">{file.name}</p>
                      <p className="text-xs text-[var(--color-lr-text-secondary)]">{(file.size / 1024).toFixed(1)} Ko</p>
                    </div>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setFile(null);
                      }}
                      className="text-xs font-medium text-[var(--color-lr-text-secondary)] hover:text-danger transition-colors"
                    >
                      Retirer
                    </button>
                  </div>
                )}

                <div className="mt-6">
                  <Button onClick={handleUpload} disabled={!file || uploading}>
                    {uploading ? "Analyse en cours..." : "Analyser et mapper →"}
                  </Button>
                </div>
              </>
            )}

            {mode === "coller" && (
              <>
                <h2 className="display-h2 mb-4 text-[var(--foreground)]">
                  Coller vos données
                </h2>
                <p className="mb-4 text-sm text-[var(--color-lr-text-secondary)]">
                  Copiez-collez depuis Excel, votre DMS, ou un email. La première ligne doit contenir les en-têtes.
                </p>

                <textarea
                  value={pasteContent}
                  onChange={(e) => setPasteContent(e.target.value)}
                  placeholder="Collez ici vos données depuis Excel, votre DMS, ou un email..."
                  className="w-full rounded-xl border border-[var(--border)] bg-[var(--card)] p-4 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-tertiary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30"
                  rows={10}
                />

                <div className="mt-6">
                  <Button onClick={handlePaste} disabled={!pasteContent.trim() || uploading}>
                    {uploading ? "Analyse en cours..." : "Analyser et mapper"}
                  </Button>
                </div>
              </>
            )}

            {mode === "texte" && (
              <>
                <h2 className="display-h2 mb-4 text-[var(--foreground)]">
                  Coller un email ou texte libre
                </h2>
                <p className="mb-4 text-sm text-[var(--color-lr-text-secondary)]">
                  Collez le contenu d&apos;un email de lead. L&apos;IA extrait automatiquement toutes les informations.
                </p>

                {!textePreview ? (
                  <>
                    <textarea
                      value={texteLibre}
                      onChange={(e) => setTexteLibre(e.target.value)}
                      placeholder="Collez ici le contenu d'un email de lead..."
                      className="w-full rounded-xl border border-[var(--border)] bg-[var(--card)] p-4 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-tertiary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30"
                      rows={12}
                    />
                    <div className="mt-6">
                      <Button
                        onClick={handleParseTexteLibre}
                        disabled={!texteLibre.trim() || texteLibre.trim().length < 20 || uploading}
                      >
                        {uploading ? "Analyse IA en cours..." : "Analyser avec l'IA"}
                      </Button>
                    </div>
                  </>
                ) : (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <h3 className="text-sm font-semibold text-[var(--foreground)]">Prévisualisation</h3>
                      <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                        !confValide ? "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]"
                        : conf >= 80 ? "bg-success/10 text-success"
                        : conf >= 50 ? "bg-warning/10 text-warning"
                        : "bg-danger/10 text-danger"
                      }`}>
                        {confValide ? `Confiance : ${conf}%` : "Confiance inconnue"}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                      {[
                        { label: "Nom", key: "nom" },
                        { label: "Prénom", key: "prenom" },
                        { label: "Téléphone", key: "telephone" },
                        { label: "Email", key: "email" },
                        { label: "Marque", key: "marque" },
                        { label: "Modèle", key: "modele" },
                        { label: "Source détectée", key: "sourceDetectee" },
                      ].map((field) => (
                        <div key={field.key}>
                          <label htmlFor={`preview-${field.key}`} className="mb-1 block text-xs font-medium text-[var(--color-lr-text-secondary)]">{field.label}</label>
                          <input
                            id={`preview-${field.key}`}
                            type="text"
                            value={(textePreview[field.key] as string) || ""}
                            onChange={(e) => setTextePreview({ ...textePreview, [field.key]: e.target.value })}
                            placeholder="(non détecté)"
                            className="w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-sm text-[var(--foreground)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent/30"
                          />
                        </div>
                      ))}
                    </div>

                    {(() => {
                      const ds = textePreview.donneesSupplementaires as Record<string, string | number | boolean> | undefined;
                      if (!ds || Object.keys(ds).length === 0) return null;
                      return (
                        <details className="rounded-lg border border-[var(--border)]">
                          <summary className="cursor-pointer px-4 py-2.5 text-xs font-semibold text-[var(--color-lr-text-secondary)]">
                            Données véhicule ({Object.keys(ds).length} champs détectés)
                          </summary>
                          <div className="grid grid-cols-2 gap-2 px-4 pb-3">
                            {Object.entries(ds).map(([key, val]) => (
                              <div key={key} className="flex items-center justify-between rounded bg-[var(--muted)] px-2 py-1">
                                <span className="text-[11px] text-[var(--color-lr-text-secondary)]">{key}</span>
                                <span className="text-[11px] font-medium text-[var(--foreground)]">{String(val)}</span>
                              </div>
                            ))}
                          </div>
                        </details>
                      );
                    })()}

                    <div className="flex gap-3">
                      <Button variant="secondary" onClick={() => setTextePreview(null)}>
                        Modifier le texte
                      </Button>
                      <Button onClick={handleCreerDepuisTexte} disabled={texteSaving}>
                        {texteSaving ? "Création..." : "Créer le prospect"}
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}
            </section>

            {/* Cross-link vers l'import auto par email */}
            <div className="rounded-xl border border-accent/30 bg-accent/5 p-4">
              <div className="flex items-start gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent/10 text-accent">
                  <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                      d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-[var(--foreground)]">
                    Vous recevez régulièrement des emails de leads ?
                  </p>
                  <p className="mt-1 text-sm text-[var(--color-lr-text-secondary)]">
                    Activez l&apos;import <strong>automatique</strong> : une adresse Leadrive dédiée
                    reçoit vos emails LeBonCoin, La Centrale… directement, l&apos;IA crée les
                    leads sans aucune action de votre part.
                  </p>
                  <Link
                    href="/email-import"
                    className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline"
                  >
                    Configurer l&apos;import par email →
                  </Link>
                </div>
              </div>
            </div>
          </>
        )}
        </div>
      </div>
    </AppLayout>
  );
}

function ModeChoiceCard({
  actif,
  onClick,
  titre,
  description,
  pour,
  icone,
}: {
  actif: boolean;
  onClick: () => void;
  titre: string;
  description: string;
  pour: string;
  icone: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-pressed={actif}
      onClick={onClick}
      className={`flex flex-col items-start gap-2 rounded-xl border-2 p-4 text-left transition-all ${
        actif
          ? "border-accent bg-accent/5 shadow-sm"
          : "border-[var(--border)] bg-[var(--card)] hover:border-accent/40 hover:bg-[var(--muted)]/30"
      }`}
    >
      <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${
        actif ? "bg-accent text-white" : "bg-[var(--muted)] text-[var(--color-lr-text-secondary)]"
      }`}>
        {icone}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-[var(--foreground)]">
          {titre}
        </p>
        <p className="text-xs text-[var(--color-lr-text-secondary)]">{description}</p>
      </div>
      <p className="text-[11px] text-[var(--color-lr-text-tertiary)]">{pour}</p>
    </button>
  );
}

function formatLabel(typeFichier: string | null | undefined): string {
  if (!typeFichier) return "-";
  const labels: Record<string, string> = {
    CSV_VIRGULE: "CSV",
    CSV_POINT_VIRGULE: "CSV (;)",
    TSV: "TSV",
    EXCEL_XLSX: "Excel",
    EXCEL_XLS: "Excel (.xls)",
    COLLER_TEXTE: "Collé",
  };
  return labels[typeFichier] || typeFichier;
}
