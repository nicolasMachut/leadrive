"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useIsManagerOrSuperAdmin } from "@/lib/auth";
import { useSelfSignupStatus } from "./useSelfSignupStatus";

/**
 * Bannière cross-page d'activation WhatsApp Business (epic 25).
 *
 * <p>Deux variantes d'affichage :</p>
 * <ul>
 *   <li><strong>SELF_SIGNUP incomplet</strong> : signale que l'envoi est
 *       indisponible et redirige vers la page d'activation.</li>
 *   <li><strong>Tout est OK</strong> : la banner est cachee.</li>
 * </ul>
 *
 * <p>La banner se cache automatiquement sur la page d'activation elle-meme
 * pour eviter le doublon visuel.</p>
 *
 * <p>Vendeur : message adapte (pas de bouton, contact manager).</p>
 */
export default function WhatsAppSelfSignupBanner() {
  const { status, loading } = useSelfSignupStatus();
  const isManager = useIsManagerOrSuperAdmin();
  const pathname = usePathname();

  if (loading || !status) return null;
  // Ne pas redoubler la communication sur la page d'activation elle-meme.
  if (pathname?.startsWith("/parametres/whatsapp/activation")) return null;

  // Etat de sante runtime : un compte ACTIF peut etre restreint apres coup. Cette
  // variante prime sur le cas "activation a completer" et s'affiche meme quand tous
  // les templates sont approuves.
  const estRestreint = status.etatCompte === "RESTREINT";
  // Numero/sender indisponible : envois en echec alors que le compte est ACTIF.
  // Distinct de RESTREINT (pas une restriction Meta). Ne s'affiche pas si deja restreint.
  const numeroEnEchec = !estRestreint && status.numeroEnEchec;

  // Cas nominal final : activation terminee, tous templates approuves, pas de
  // restriction ET pas d'envois en echec -> banniere cachee.
  if (status.tousTemplatesApprouves && !estRestreint && !numeroEnEchec) return null;

  // Trois variantes, par priorite : restriction Meta > numero en echec > activation a completer.
  // Aucune n'a de CTA "Activer" cote app pour les deux premieres (rien a (re)activer),
  // on renvoie vers la page WhatsApp pour le detail.
  let titre: string;
  let sousTitre: string;
  let cta: string;
  if (estRestreint) {
    titre = "Compte WhatsApp Business restreint";
    sousTitre = status.messageEtat
        || "L'envoi de messages WhatsApp est suspendu. Vérifiez l'état de votre compte WhatsApp Business.";
    cta = "Voir les détails";
  } else if (numeroEnEchec) {
    titre = "Envoi WhatsApp en échec";
    sousTitre = status.messageEnvoi
        || "Vos messages WhatsApp ne partent plus : vérifiez la configuration de votre numéro.";
    cta = "Voir les détails";
  } else {
    titre = "Activation WhatsApp Business à compléter";
    sousTitre = status.messageBlocage || status.messageGuidage
        || "Connectez WhatsApp Business pour activer l'envoi de messages.";
    cta = "Activer maintenant";
  }

  const palette = "border-danger/20 bg-danger/10";
  const txtTitre = "text-danger";
  const txtSousTitre = "text-danger/80";
  const btnClass = "bg-danger hover:bg-danger/90 focus:ring-danger";
  const txtIcone = "text-danger";

  return (
    <div className={`flex min-h-[73px] items-center justify-between gap-3 border-b px-4 py-3 ${palette}`}>
      <div className="flex min-w-0 items-center gap-3">
        <svg
          className={`h-4 w-4 flex-shrink-0 ${txtIcone}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
        <div className="min-w-0">
          <p className={`text-sm font-semibold ${txtTitre}`}>{titre}</p>
          <p className={`text-xs font-normal ${txtSousTitre} mt-0.5`}>
            {sousTitre}
            {!isManager && (
              <span className="ml-1 italic">Contactez votre manager pour finaliser.</span>
            )}
          </p>
        </div>
      </div>
      {isManager && (
        <Link
          href="/parametres/whatsapp/activation"
          className={`flex-shrink-0 rounded-lg px-3 py-1.5 text-xs font-semibold text-white shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-offset-1 ${btnClass}`}
        >
          {cta}
        </Link>
      )}
    </div>
  );
}
