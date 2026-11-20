"use client";

import { useEffect, useRef, useState } from "react";

interface AdresseSuggestion {
  label: string;
  context: string;
  postcode: string;
  city: string;
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  id?: string;
}

/**
 * Autocomplete d'adresse francais utilisant l'API Adresse gouvernementale
 * (api-adresse.data.gouv.fr).
 *
 * <p><strong>Avantages</strong> : gratuite, sans cle API, sans inscription,
 * limite 50 req/sec/IP (large). Couvre toutes les adresses francaises (BAN -
 * Base Adresse Nationale).</p>
 *
 * <p>L'utilisateur peut aussi saisir manuellement (pour les adresses non
 * referencees ou hors France) : le champ reste editable. Les suggestions
 * sont juste une aide.</p>
 *
 * <p>Cf. <a href="https://adresse.data.gouv.fr/api-doc/adresse">doc API</a>.</p>
 */
export default function AdresseAutocomplete({
  value, onChange, placeholder = "Commencez à taper l'adresse...", className = "", id,
}: Props) {
  const [suggestions, setSuggestions] = useState<AdresseSuggestion[]>([]);
  const [open, setOpen] = useState(false);
  const [focused, setFocused] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    // Debounce 250ms : evite de spammer l'API a chaque touche.
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!focused || value.length < 3) {
      setSuggestions([]);
      setOpen(false);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      try {
        const res = await fetch(
          `https://api-adresse.data.gouv.fr/search/?q=${encodeURIComponent(value)}&limit=5&type=housenumber`,
        );
        if (!res.ok) return;
        const data = await res.json();
        const items: AdresseSuggestion[] = (data.features ?? []).map((f: { properties: { label: string; context: string; postcode: string; city: string } }) => ({
          label: f.properties.label,
          context: f.properties.context,
          postcode: f.properties.postcode,
          city: f.properties.city,
        }));
        setSuggestions(items);
        setOpen(items.length > 0);
      } catch {
        // Reseau coupe ou API HS -> on n'affiche juste pas de suggestions.
        // Le user peut quand meme taper son adresse a la main.
      }
    }, 250);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [value, focused]);

  // Ferme au clic exterieur
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) {
      document.addEventListener("mousedown", handleClickOutside);
      return () => document.removeEventListener("mousedown", handleClickOutside);
    }
  }, [open]);

  return (
    <div ref={containerRef} className="relative">
      <input
        id={id}
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        placeholder={placeholder}
        autoComplete="off"
        className={className}
      />
      {open && suggestions.length > 0 && (
        <div className="absolute left-0 right-0 top-full z-30 mt-1 max-h-60 overflow-y-auto rounded-lg border border-[var(--border)] bg-[var(--card)] shadow-lg">
          {suggestions.map((s, i) => (
            <button
              key={i}
              type="button"
              onMouseDown={(e) => {
                // mousedown plutot que click : evite le blur de l'input qui ferme le menu
                // avant le click ne soit declenche.
                e.preventDefault();
                onChange(s.label);
                setOpen(false);
              }}
              className="block w-full px-3 py-2 text-left text-sm transition-colors hover:bg-[var(--muted)]"
            >
              <span className="text-[var(--foreground)]">{s.label}</span>
              <span className="ml-2 text-xs text-[var(--color-lr-text-tertiary)]">
                {s.context}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
