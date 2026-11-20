"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useApiClient, formatErreur } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface ImportLeadsFormProps {
  /** Si défini, ajoute `?retour=...` à la redirection vers /import/{id}/mapping après upload/paste. */
  retourPath?: string;
  /** Style compact pour l'onboarding (sans card blanche, sans titre). */
  compact?: boolean;
  /**
   * Override de la redirection après création d'un lead via "Coller un email".
   * Si défini, le composant n'appelle PAS router.push(`/leads/{id}`), c'est au parent de gérer la suite (ex: passer à l'étape suivante de l'onboarding).
   */
  onTexteLibreSuccess?: (leadId: number) => void;
}

export default function ImportLeadsForm({ retourPath, compact = false, onTexteLibreSuccess }: ImportLeadsFormProps) {
  const apiFetch = useApiClient();
  const router = useRouter();

  const [mode, setMode] = useState<"fichier" | "coller" | "texte">("fichier");
  const [file, setFile] = useState<File | null>(null);
  const [pasteContent, setPasteContent] = useState("");
  const [texteLibre, setTexteLibre] = useState("");
  const [textePreview, setTextePreview] = useState<Record<string, unknown> | null>(null);
  const [texteSaving, setTexteSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState("");

  // Sanitize retourPath : on n'accepte que des chemins internes (commencant par "/")
  // pour eviter un open redirect (ex: retour=https://attacker.com/login). Cf OWASP A1.
  const retourPathSafe = retourPath && retourPath.startsWith("/") && !retourPath.startsWith("//") ? retourPath : null;
  const mappingSuffix = retourPathSafe ? `?retour=${encodeURIComponent(retourPathSafe)}` : "";

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
      router.push(`/import/${data.importId}/mapping${mappingSuffix}`);
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
      router.push(`/import/${data.importId}/mapping${mappingSuffix}`);
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
      if (onTexteLibreSuccess) {
        onTexteLibreSuccess(lead.idLead);
      } else {
        router.push(`/leads/${lead.idLead}`);
      }
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setTexteSaving(false);
    }
  }

  function handleDrag(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") setDragActive(true);
    else if (e.type === "dragleave") setDragActive(false);
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files?.[0]) setFile(e.dataTransfer.files[0]);
  }

  const containerClass = compact
    ? ""
    : "rounded-xl bg-[var(--card)] p-6 shadow-sm";

  return (
    <section className={containerClass}>
      {error && (
        <div className="mb-6 rounded-lg bg-danger/10 p-3 text-sm text-danger">
          {error}
        </div>
      )}

      {/* Toggle fichier / coller / email */}
      <div className="mb-6 flex gap-1 rounded-lg bg-[var(--muted)] p-1">
        <button
          onClick={() => setMode("fichier")}
          className={`flex-1 rounded-md px-4 py-2 text-sm font-medium transition-colors ${mode === "fichier" ? "bg-[var(--card)] text-[var(--foreground)] shadow-sm" : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"}`}
        >
          Importer un fichier
        </button>
        <button
          onClick={() => setMode("coller")}
          className={`flex-1 rounded-md px-4 py-2 text-sm font-medium transition-colors ${mode === "coller" ? "bg-[var(--card)] text-[var(--foreground)] shadow-sm" : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"}`}
        >
          Coller vos données
        </button>
        <button
          onClick={() => setMode("texte")}
          className={`flex-1 rounded-md px-4 py-2 text-sm font-medium transition-colors ${mode === "texte" ? "bg-[var(--card)] text-[var(--foreground)] shadow-sm" : "text-[var(--color-lr-text-secondary)] hover:text-[var(--foreground)]"}`}
        >
          Coller un email
        </button>
      </div>

      {mode === "fichier" && (
        <>
          <h2 className="mb-4 text-base font-semibold text-[var(--foreground)]">
            Sélectionner un fichier
          </h2>
          <p className="mb-4 text-sm text-[var(--color-lr-text-secondary)]">
            Formats acceptés : CSV, TSV, Excel (.xlsx, .xls)
          </p>

          <div
            onDragEnter={handleDrag}
            onDragLeave={handleDrag}
            onDragOver={handleDrag}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className={`flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed p-12 transition-colors ${
              dragActive
                ? "border-accent bg-accent/5"
                : "border-[var(--border)] hover:border-[var(--color-lr-text-tertiary)]"
            }`}
          >
            <svg className="mb-3 h-10 w-10 text-[var(--color-lr-text-tertiary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
            <p className="text-sm text-[var(--color-lr-text-secondary)]">
              Glissez un fichier ici ou cliquez pour sélectionner
            </p>
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,.tsv,.xlsx,.xls"
              className="hidden"
              onChange={(e) => { if (e.target.files?.[0]) setFile(e.target.files[0]); }}
            />
          </div>

          {file && (
            <div className="mt-4 flex items-center justify-between rounded-lg bg-[var(--muted)] p-3">
              <span className="text-sm text-[var(--foreground)]">
                {file.name} ({(file.size / 1024).toFixed(1)} Ko)
              </span>
              <button
                onClick={(e) => { e.stopPropagation(); setFile(null); }}
                className="text-sm text-danger hover:opacity-80"
              >
                Supprimer
              </button>
            </div>
          )}

          <button
            onClick={handleUpload}
            disabled={!file || uploading}
            className="mt-6 rounded-lg btn-primary px-6 py-2.5 text-sm font-medium text-white transition-colors disabled:opacity-50"
          >
            {uploading ? "Analyse en cours..." : "Analyser et mapper"}
          </button>
        </>
      )}

      {mode === "coller" && (
        <>
          <h2 className="mb-4 text-base font-semibold text-[var(--foreground)]">
            Coller vos données
          </h2>
          <p className="mb-4 text-sm text-[var(--color-lr-text-secondary)]">
            Copiez-collez depuis Excel, votre DMS, ou un email. La première ligne doit contenir les en-têtes.
          </p>

          <textarea
            value={pasteContent}
            onChange={(e) => setPasteContent(e.target.value)}
            placeholder="Collez ici vos données depuis Excel, votre DMS, ou un email..."
            className="w-full rounded-xl border border-[var(--border)] bg-[var(--muted)] p-4 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-tertiary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            rows={10}
          />

          <button
            onClick={handlePaste}
            disabled={!pasteContent.trim() || uploading}
            className="mt-6 rounded-lg btn-primary px-6 py-2.5 text-sm font-medium text-white transition-colors disabled:opacity-50"
          >
            {uploading ? "Analyse en cours..." : "Analyser et mapper"}
          </button>
        </>
      )}

      {mode === "texte" && (
        <>
          <h2 className="mb-4 text-base font-semibold text-[var(--foreground)]">
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
                className="w-full rounded-xl border border-[var(--border)] bg-[var(--muted)] p-4 text-sm text-[var(--foreground)] placeholder-[var(--color-lr-text-tertiary)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                rows={12}
              />
              <button
                onClick={handleParseTexteLibre}
                disabled={!texteLibre.trim() || texteLibre.trim().length < 20 || uploading}
                className="mt-6 rounded-lg btn-primary px-6 py-2.5 text-sm font-medium text-white transition-colors disabled:opacity-50"
              >
                {uploading ? "Analyse IA en cours..." : "Analyser avec l'IA"}
              </button>
            </>
          ) : (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-[var(--foreground)]">Prévisualisation</h3>
                <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                  (textePreview.confiance as number) >= 80 ? "bg-success/10 text-success"
                  : (textePreview.confiance as number) >= 50 ? "bg-warning/10 text-warning"
                  : "bg-danger/10 text-danger"
                }`}>
                  Confiance : {textePreview.confiance as number}%
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
                    <label className="mb-1 block text-xs font-medium text-[var(--color-lr-text-secondary)]">{field.label}</label>
                    <input
                      type="text"
                      value={(textePreview[field.key] as string) || ""}
                      onChange={(e) => setTextePreview({ ...textePreview, [field.key]: e.target.value })}
                      placeholder="(non détecté)"
                      className="w-full rounded-lg border border-[var(--border)] bg-[var(--card)] px-3 py-2 text-sm text-[var(--foreground)] focus:border-accent focus:outline-none"
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
                <button
                  onClick={() => { setTextePreview(null); }}
                  className="rounded-lg border border-[var(--border)] px-4 py-2 text-sm font-medium text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)]"
                >
                  Modifier le texte
                </button>
                <button
                  onClick={handleCreerDepuisTexte}
                  disabled={texteSaving}
                  className="rounded-lg btn-primary px-6 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  {texteSaving ? "Création..." : "Créer le prospect"}
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </section>
  );
}
