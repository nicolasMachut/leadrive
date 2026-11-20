import { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes, forwardRef } from "react";

// Largeur/forme/typo communes a tous les champs. La couleur de bordure et le
// focus sont separes (FIELD_NORMAL / FIELD_INVALID) pour eviter le conflit
// d'utilitaires Tailwind border-* quand on bascule en etat d'erreur.
const FIELD_BASE =
  "w-full rounded-lg border px-3 py-2 text-sm text-[var(--foreground)] " +
  "placeholder-[var(--color-lr-text-tertiary)] focus:outline-none focus:ring-1 " +
  "transition-colors disabled:opacity-60 disabled:cursor-not-allowed";

const FIELD_NORMAL = "border-[var(--border)] bg-[var(--card)] focus:border-accent focus:ring-accent/30";
const FIELD_INVALID = "border-danger bg-[var(--card)] focus:border-danger focus:ring-danger/30";

/**
 * Classe d'un champ de formulaire partage. `invalid` bascule en style danger.
 * Exporte pour les champs custom (ex. AdresseAutocomplete) qui ne passent pas
 * par <Input> mais doivent rester visuellement coherents.
 */
export function inputFieldClass(invalid = false): string {
  return `${FIELD_BASE} ${invalid ? FIELD_INVALID : FIELD_NORMAL}`;
}

interface FieldLabelProps {
  label?: string;
  hint?: string;
  htmlFor?: string;
  /** Contenu rendu a droite du label (ex. <InfoIcon> d'aide contextuelle). */
  labelNode?: ReactNode;
}

export function FieldLabel({ label, hint, htmlFor, labelNode }: FieldLabelProps) {
  if (!label && !hint && !labelNode) return null;
  return (
    <div className="mb-1.5">
      {(label || labelNode) && (
        <label htmlFor={htmlFor} className="flex items-center gap-1.5 text-sm font-medium text-[var(--foreground)]">
          {label}
          {labelNode}
        </label>
      )}
      {hint && <p className="mt-0.5 text-xs text-[var(--color-lr-text-secondary)]">{hint}</p>}
    </div>
  );
}

interface InputOwnProps extends FieldLabelProps {
  /** Affiche le champ en etat d'erreur (bordure danger + aria-invalid). */
  invalid?: boolean;
  /** Message d'erreur affiche sous le champ et relie via aria-describedby. */
  errorMessage?: string;
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & InputOwnProps>(
  function Input({ label, hint, labelNode, invalid = false, errorMessage, className = "", id, ...props }, ref) {
    const errorId = invalid && errorMessage && id ? `${id}-error` : undefined;
    return (
      <div>
        <FieldLabel label={label} hint={hint} htmlFor={id} labelNode={labelNode} />
        <input
          ref={ref}
          id={id}
          aria-invalid={invalid || undefined}
          aria-describedby={errorId}
          className={`${inputFieldClass(invalid)} ${className}`}
          {...props}
        />
        {invalid && errorMessage && (
          <p id={errorId} className="mt-1.5 text-xs text-danger">{errorMessage}</p>
        )}
      </div>
    );
  },
);

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & FieldLabelProps>(
  function Select({ label, hint, labelNode, className = "", id, children, ...props }, ref) {
    return (
      <div>
        <FieldLabel label={label} hint={hint} htmlFor={id} labelNode={labelNode} />
        <select ref={ref} id={id} className={`${inputFieldClass(false)} ${className}`} {...props}>
          {children}
        </select>
      </div>
    );
  },
);

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & FieldLabelProps>(
  function Textarea({ label, hint, labelNode, className = "", id, ...props }, ref) {
    return (
      <div>
        <FieldLabel label={label} hint={hint} htmlFor={id} labelNode={labelNode} />
        <textarea ref={ref} id={id} className={`${inputFieldClass(false)} ${className}`} {...props} />
      </div>
    );
  },
);
