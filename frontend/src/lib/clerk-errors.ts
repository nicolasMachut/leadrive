/**
 * Traduit les erreurs Clerk en messages utilisateur clairs en francais.
 * Mappe les codes d'erreur Clerk les plus courants vers des messages explicites
 * et actionnables (ex. "mot de passe oublie ?", "verifier votre email", etc.).
 */

interface ClerkError {
  code?: string;
  message?: string;
  longMessage?: string;
  meta?: { paramName?: string };
}

interface ClerkErrorResponse {
  errors?: ClerkError[];
}

const CODE_TO_MESSAGE: Record<string, string> = {
  // Identification
  form_identifier_not_found: "Aucun compte ne correspond a cet email. Si vous avez recu une invitation, utilisez le lien dans l'email pour activer votre compte.",
  form_identifier_exists: "Un compte existe deja avec cet email.",
  form_param_format_invalid: "Le format est invalide.",
  form_param_nil: "Ce champ est obligatoire.",

  // Mot de passe
  form_password_incorrect: "Mot de passe incorrect.",
  form_password_length_too_short: "Le mot de passe doit contenir au moins 8 caracteres.",
  form_password_pwned: "Ce mot de passe a ete compromis dans une fuite de donnees. Choisissez-en un autre.",
  form_password_validation_failed: "Le mot de passe ne respecte pas les criteres de securite.",
  form_password_not_strong_enough: "Mot de passe trop faible. Ajoutez des chiffres, majuscules ou caracteres speciaux.",

  // Session
  session_exists: "Vous etes deja connecte.",
  session_token_expired: "Votre session a expire, reconnectez-vous.",
  authentication_invalid: "Session invalide, reconnectez-vous.",

  // Code de verification
  form_code_incorrect: "Code de verification incorrect.",
  verification_expired: "Le code a expire. Demandez un nouveau code.",
  verification_failed: "La verification a echoue. Reessayez.",
  verification_already_verified: "Ce code a deja ete utilise.",

  // Invitation / ticket
  ticket_invalid: "Ce lien d'invitation est invalide ou a deja ete utilise.",
  ticket_expired: "Ce lien d'invitation a expire. Contactez votre manager pour en recevoir un nouveau.",
  organization_membership_quota_exceeded: "Cet établissement a atteint sa limite de membres (invitations en attente comprises). Retirez un membre ou annulez une invitation en attente avant d'en inviter un nouveau.",

  // Rate limiting
  too_many_requests: "Trop de tentatives. Reessayez dans quelques minutes.",
  too_many_attempts: "Trop de tentatives. Reessayez dans quelques minutes.",

  // CAPTCHA
  captcha_invalid: "La verification anti-bot a echoue. Rechargez la page et reessayez.",
  captcha_unavailable: "La verification anti-bot est indisponible. Rechargez la page.",

  // Email
  form_identifier_not_allowed_access: "Cet email n'a pas acces a la plateforme.",
  email_address_not_allowed: "Cet email n'est pas autorise.",
};

/**
 * Extrait un message d'erreur lisible depuis une erreur Clerk ou generique.
 */
export function extractClerkError(err: unknown): string {
  const clerkErr = err as ClerkErrorResponse;

  if (clerkErr.errors?.length) {
    const firstError = clerkErr.errors[0];
    // 1. Essayer le mapping par code
    if (firstError.code && CODE_TO_MESSAGE[firstError.code]) {
      return CODE_TO_MESSAGE[firstError.code];
    }
    // 2. Fallback sur le longMessage Clerk (souvent en anglais)
    if (firstError.longMessage) return firstError.longMessage;
    if (firstError.message) return firstError.message;
  }

  if (err instanceof Error) return err.message;
  return "Une erreur est survenue. Reessayez ou contactez le support.";
}
