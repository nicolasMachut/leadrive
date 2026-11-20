/**
 * Icône "archive" (boîte de rangement) partagée. Extrait du SVG dupliqué ~4x
 * dans les pages prospects (fiche + liste) pour respecter la règle "pas de
 * duplication". Décoratif par défaut (aria-hidden) : le libellé accessible est
 * porté par le bouton parent.
 */
export default function ArchiveIcon({ className = "h-4 w-4" }: { className?: string }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.5}
        d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4"
      />
    </svg>
  );
}
