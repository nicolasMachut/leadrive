interface SkeletonProps {
  className?: string;
}

export default function Skeleton({ className = "h-4 w-full" }: SkeletonProps) {
  return <div className={`rounded-lg bg-[var(--muted)] animate-shimmer ${className}`} />;
}

/**
 * Loader signature Atelier : 3 speed-streaks horizontales qui défilent dans une
 * zone rectangulaire. Remplace le spinner rond générique. Lecture instantanée
 * "ça avance, ça accélère" cohérente avec le North Star "instrument de bord".
 *
 * Usage : `<SkeletonInstrument label="Chargement du pipeline" />`
 */
export function SkeletonInstrument({
  label,
  className = "",
}: {
  label?: string;
  className?: string;
}) {
  return (
    <div
      className={`relative flex h-32 items-center justify-center overflow-hidden rounded-xl border border-[var(--border)] bg-[var(--card)] ${className}`}
      role="status"
      aria-live="polite"
      aria-label={label ?? "Chargement"}
    >
      {/* 3 streaks horizontaux qui défilent, signature loader Atelier */}
      <div className="pointer-events-none absolute inset-y-0 left-0 w-full">
        <div
          className="absolute top-[35%] h-[2px] w-12 bg-[var(--color-accent)]/70 animate-speed-streak-1"
          style={{ borderRadius: 2 }}
        />
        <div
          className="absolute top-[50%] h-[2px] w-16 bg-[var(--color-accent)]/55 animate-speed-streak-2"
          style={{ borderRadius: 2 }}
        />
        <div
          className="absolute top-[65%] h-[2px] w-10 bg-[var(--color-accent)]/40 animate-speed-streak-3"
          style={{ borderRadius: 2 }}
        />
      </div>
      {label && (
        <p className="relative z-10 font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-lr-text-tertiary)]">
          {label}
        </p>
      )}
    </div>
  );
}

export function SkeletonGrid({ count = 4, className = "h-28" }: { count?: number; className?: string }) {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} className={`${className} rounded-xl border border-[var(--border)] bg-[var(--card)]`} />
      ))}
    </div>
  );
}

/**
 * Skeleton qui mime une table : header + rows. Permet d'eviter le flash
 * structurel quand le loading affiche des blocs generiques avant qu'une vraie
 * table avec thead+tbody n'apparaisse.
 */
export function SkeletonTable({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
      <div className="border-b border-[var(--border)] bg-[var(--muted)] px-4 py-3 grid gap-4" style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}>
        {Array.from({ length: cols }).map((_, i) => (
          <div key={i} className="h-4 w-20 rounded animate-shimmer" />
        ))}
      </div>
      <div>
        {Array.from({ length: rows }).map((_, r) => (
          <div key={r} className="border-b border-[var(--border)] px-4 py-4 grid gap-4 last:border-b-0" style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}>
            {Array.from({ length: cols }).map((_, c) => (
              <div key={c} className="h-4 w-full max-w-[160px] rounded animate-shimmer" />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
