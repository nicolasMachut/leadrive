/**
 * Utilitaires de saisie / formatage de numéros de téléphone (FR + international).
 *
 * Pattern unique réutilisé dans tous les champs `tel` du produit (activation
 * WhatsApp, panneau fiche lead, création concession, etc.). Sans ces helpers,
 * chaque écran réimplémentait sa propre logique (ou aucune) → numéros mal formés
 * envoyés au backend, espaces parasites, copier-coller d'emails accepté.
 *
 * - {@link sanitizePhoneInput} : autorise UNIQUEMENT les chiffres et un éventuel
 *   `+` en tête (format E.164). Bloque texte, lettres, ponctuation autre.
 * - {@link formatPhoneInput} : formate visuellement en groupes de 2 chiffres :
 *     "0612345678"   → "06 12 34 56 78"
 *     "+33612345678" → "+33 6 12 34 56 78"
 *     "+447700123456"→ "+44 7700 123 456" (formats étrangers : groupes de 4/3/3)
 */

/**
 * Nettoie une saisie utilisateur : garde uniquement chiffres + 1 `+` en tête.
 * Tous les autres caractères (lettres, ponctuation, espaces) sont supprimés.
 */
export function sanitizePhoneInput(value: string): string {
  if (!value) return "";
  // 1er pass : ne garde que chiffres et `+`.
  const digitsAndPlus = value.replace(/[^\d+]/g, "");
  // 2e pass : un seul `+` autorisé, uniquement en tête.
  if (digitsAndPlus.startsWith("+")) {
    return "+" + digitsAndPlus.slice(1).replace(/\+/g, "");
  }
  return digitsAndPlus.replace(/\+/g, "");
}

/**
 * Formate visuellement un numéro pour l'affichage dans l'input.
 * Format FR national : "06 12 34 56 78" (groupes de 2 chiffres après le 0).
 * Format FR international : "+33 6 12 34 56 78" (1 chiffre + groupes de 2).
 * Autres internationaux : groupes de 3 chiffres apres l'indicatif (heuristique
 * lisible sans dependance externe).
 */
export function formatPhoneInput(value: string): string {
  const digits = sanitizePhoneInput(value);
  if (!digits) return "";

  // FR international (+33 6 12 34 56 78)
  if (digits.startsWith("+33")) {
    const reste = digits.slice(3);
    if (reste.length === 0) return "+33";
    // Premier chiffre seul (6 ou 7 pour mobile, 1-5/9 pour fixe), puis groupes de 2.
    const groupes: string[] = [reste.slice(0, 1)];
    for (let i = 1; i < reste.length; i += 2) {
      groupes.push(reste.slice(i, i + 2));
    }
    return "+33 " + groupes.filter(Boolean).join(" ");
  }

  // FR national (06 12 34 56 78)
  if (digits.startsWith("0") && !digits.startsWith("+")) {
    const groupes: string[] = [];
    for (let i = 0; i < digits.length; i += 2) {
      groupes.push(digits.slice(i, i + 2));
    }
    return groupes.filter(Boolean).join(" ");
  }

  // Autre international (+44, +1, etc.), groupes de 3 apres l'indicatif (max 4 chiffres).
  if (digits.startsWith("+")) {
    // Cherche l'indicatif (1 a 4 chiffres apres le +, on prend 2 par défaut).
    const indicatif = digits.slice(0, 3);
    const reste = digits.slice(3);
    const groupes: string[] = [];
    for (let i = 0; i < reste.length; i += 3) {
      groupes.push(reste.slice(i, i + 3));
    }
    return (indicatif + " " + groupes.filter(Boolean).join(" ")).trim();
  }

  // Pas de prefixe : on laisse brut (cas atypique).
  return digits;
}

/**
 * Convertit un numero saisi (national FR, 0033, ou international) en E.164
 * pour envoi au backend (qui exige `^\+[1-9]\d{7,14}$`).
 *
 * Regles :
 *   - "+44 7700 123 456" → "+447700123456"   (deja E.164, juste sanitize)
 *   - "00 33 6 12 34 56 78" → "+33612345678" (0033 -> +)
 *   - "06 12 34 56 78" → "+33612345678"       (FR national -> +33)
 *   - "44 7700..." (sans + ni 0)             → "44 7700..." (laisse tel quel)
 *
 * NE PAS appliquer formatPhoneInput avant : cette fonction attend une saisie
 * utilisateur (avec ou sans espaces).
 */
export function toE164(value: string): string {
  const clean = sanitizePhoneInput(value);
  if (!clean) return "";
  if (clean.startsWith("+")) return clean;
  if (clean.startsWith("00")) return "+" + clean.slice(2);
  if (clean.startsWith("0") && clean.length === 10) return "+33" + clean.slice(1);
  return clean;
}

/**
 * Valide qu'un numero (sanitize, sans espaces) est un E.164 plausible.
 * - FR mobile : 06/07 (10 chiffres) ou +33[67] (12 chiffres)
 * - FR fixe : 0[1-5,9] (10 chiffres) ou +33[1-5,9] (12 chiffres)
 * - International generique : +<indicatif><numero> avec 8 a 15 chiffres
 *
 * NE PAS appliquer formatPhoneInput avant : cette fonction attend du digits-only
 * eventuellement prefixe d'un +.
 */
export function isValidPhone(value: string): boolean {
  if (!value) return false;
  const clean = sanitizePhoneInput(value);
  // FR national : 06/07 + 8 chiffres, ou 0[1-5,9] + 8 chiffres
  if (/^0[1-79]\d{8}$/.test(clean)) return true;
  // FR international : +33 puis [1-9] (sans le 0 initial) + 8 chiffres
  if (/^\+33[1-79]\d{8}$/.test(clean)) return true;
  // International generique : +indicatif + reste, 8 a 15 chiffres au total (norme E.164)
  if (/^\+[1-9]\d{7,14}$/.test(clean)) return true;
  // 0033... format alternatif
  if (/^0033[1-79]\d{8}$/.test(clean)) return true;
  return false;
}
