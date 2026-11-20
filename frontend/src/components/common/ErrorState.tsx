import Button from "./Button";

interface ErrorStateProps {
  /** Message d'erreur (ex. retour de formatErreur). */
  message?: string;
  /** Titre court. Defaut: "Chargement impossible". */
  title?: string;
  /** Callback de relance. Si fourni, affiche un bouton "Réessayer". */
  onRetry?: () => void;
  className?: string;
}

/**
 * État d'erreur de CHARGEMENT, centralisé pour rester cohérent dans toute l'app.
 * À utiliser dans la zone qui aurait dû contenir les données (pleine page mono-colonne
 * ou colonne d'un layout), JAMAIS en toast (éphémère + se duplique en StrictMode).
 * Pour une erreur d'ACTION (mutation), garder un toast.
 */
export default function ErrorState({
  message,
  title = "Chargement impossible",
  onRetry,
  className = "",
}: ErrorStateProps) {
  return (
    <div role="alert" className={`flex flex-col items-center gap-3 px-6 py-12 text-center ${className}`}>
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-danger/10 text-danger">
        <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v3.75m0 3.75h.008M10.34 3.94l-7.5 12.99A1.5 1.5 0 004.14 19.5h15.72a1.5 1.5 0 001.3-2.57l-7.5-12.99a1.5 1.5 0 00-2.6 0z" />
        </svg>
      </div>
      <div className="space-y-1">
        <p className="text-sm font-semibold text-[var(--foreground)]">{title}</p>
        {message && <p className="text-xs text-[var(--color-lr-text-secondary)]">{message}</p>}
      </div>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>Réessayer</Button>
      )}
    </div>
  );
}
