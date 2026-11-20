import Image from "next/image";

type Variant = "auto" | "light" | "dark";

interface LeadriveLogoProps {
  /**
   * - `auto` : 2 images dont l'une est cachée selon `dark:` (par défaut, pour app + login).
   * - `light` : forcer le logo clair (à utiliser sur fond sombre).
   * - `dark` : forcer le logo sombre (à utiliser sur fond clair, ex landing brand).
   */
  variant?: Variant;
  /** Hauteur en px. La largeur s'adapte automatiquement. Default 24. */
  height?: number;
  /** ClassName supplémentaire appliqué à chaque image. */
  className?: string;
  /** Texte alternatif pour les lecteurs d'écran. Default "Leadrive". */
  alt?: string;
  /**
   * `priority` next/image (preload + pas de lazy). Activer sur les logos
   * au-dessus du fold (header app, hero landing, login).
   */
  priority?: boolean;
}

/**
 * Logo Leadrive avec gestion light/dark + optimisation next/image.
 * Remplace les 17 occurrences `<img src="/leadrive_logo_*.svg" />` éparpillées
 * dans l'app par un composant unique.
 *
 * Note : `width` et `height` sont requis par next/image. On dérive la largeur
 * du ratio intrinsèque RÉEL du SVG (210x56 = 3.75:1) pour que le couple
 * (height fixe + width:auto) respecte exactement ce ratio : sinon next/image
 * détecte une distorsion et émet le warning "width or height modified".
 */
const LOGO_RATIO = 210 / 56;

export default function LeadriveLogo({
  variant = "auto",
  height = 24,
  className = "",
  alt = "Leadrive",
  priority = false,
}: LeadriveLogoProps) {
  const width = Math.round(height * LOGO_RATIO);

  if (variant === "light") {
    return (
      <Image
        src="/leadrive_logo_light.svg"
        alt={alt}
        width={width}
        height={height}
        priority={priority}
        className={className}
        style={{ width: "auto", height: `${height}px` }}
      />
    );
  }

  if (variant === "dark") {
    return (
      <Image
        src="/leadrive_logo_dark.svg"
        alt={alt}
        width={width}
        height={height}
        priority={priority}
        className={className}
        style={{ width: "auto", height: `${height}px` }}
      />
    );
  }

  // auto : light visible en thème clair, dark visible en thème sombre.
  // `leadrive-logo-shared` = view-transition-name unique : le browser morph
  // le logo entre les routes (landing → /login → /dashboard) au lieu d'un
  // fade brutal. Chrome/Edge/Safari 116+, Firefox fallback instant.
  return (
    <>
      <Image
        src="/leadrive_logo_light.svg"
        alt={alt}
        width={width}
        height={height}
        priority={priority}
        className={`${className} dark:hidden leadrive-logo-shared`}
        style={{ width: "auto", height: `${height}px` }}
      />
      <Image
        src="/leadrive_logo_dark.svg"
        alt={alt}
        width={width}
        height={height}
        priority={priority}
        className={`${className} hidden dark:block leadrive-logo-shared`}
        style={{ width: "auto", height: `${height}px` }}
      />
    </>
  );
}

interface LeadriveIconProps {
  /** Auto = light/dark switcher. Default. */
  variant?: Variant;
  /** Taille carré en px. Default 40. */
  size?: number;
  className?: string;
  alt?: string;
  priority?: boolean;
}

/**
 * Icône carré Leadrive (favicon style). Utilisé dans la sidebar.
 */
export function LeadriveIcon({
  variant = "auto",
  size = 40,
  className = "",
  alt = "Leadrive",
  priority = false,
}: LeadriveIconProps) {
  if (variant === "light") {
    return (
      <Image
        src="/leadrive_icon.svg"
        alt={alt}
        width={size}
        height={size}
        priority={priority}
        className={className}
      />
    );
  }
  if (variant === "dark") {
    return (
      <Image
        src="/leadrive_icon_dark.svg"
        alt={alt}
        width={size}
        height={size}
        priority={priority}
        className={className}
      />
    );
  }
  return (
    <>
      <Image
        src="/leadrive_icon.svg"
        alt={alt}
        width={size}
        height={size}
        priority={priority}
        className={`${className} dark:hidden`}
      />
      <Image
        src="/leadrive_icon_dark.svg"
        alt={alt}
        width={size}
        height={size}
        priority={priority}
        className={`${className} hidden dark:block`}
      />
    </>
  );
}
