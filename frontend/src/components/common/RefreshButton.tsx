// ============================================================================
// RefreshButton — bouton refresh icon-only standardisé.
// Extraction de 3 duplications (DashboardVendeur, DashboardManager, ActionCenter)
// qui avaient le même SVG path + classes inline. Source unique de vérité pour
// le geste "rafraîchir cette zone" du tableau de bord.
// ============================================================================

interface RefreshButtonProps {
  onClick: () => void;
  loading?: boolean;
  /** Label aria/title. Par défaut "Rafraîchir". */
  label?: string;
  /** Hauteur du bouton : "sm" = 36px (h-9 w-9), "md" = 40px (h-10 w-10). */
  size?: "sm" | "md";
  className?: string;
}

export default function RefreshButton({
  onClick,
  loading = false,
  label = "Rafraîchir",
  size = "sm",
  className = "",
}: RefreshButtonProps) {
  const sizeClass = size === "md" ? "h-10 w-10" : "h-9 w-9";
  const iconSize = size === "md" ? "h-4 w-4" : "h-4 w-4";
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={loading}
      className={`flex ${sizeClass} shrink-0 items-center justify-center rounded-lg text-[var(--color-lr-text-secondary)] transition-colors hover:bg-[var(--muted)] hover:text-[var(--foreground)] disabled:opacity-50 disabled:cursor-not-allowed ${className}`}
      title={label}
      aria-label={label}
    >
      {loading ? (
        <div className={`${iconSize} animate-spin rounded-full border-2 border-[var(--border)] border-t-accent`} />
      ) : (
        <svg className={iconSize} fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
      )}
    </button>
  );
}
