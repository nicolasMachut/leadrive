package fr.leadrevive.outreach.dto;

import java.util.List;

/**
 * Statut du flow Self Sign-up WhatsApp Business pour le tenant courant (epic 25).
 *
 * <p>Etats principaux exposes a l'UI :</p>
 * <ul>
 *   <li>sans {@code wabaId} : flow non encore demarre.</li>
 *   <li>{@code subAccountCree=true} : Twilio sub-account cree, en attente de
 *       connexion Meta Business.</li>
 *   <li>avec {@code wabaId} : Meta connecte, templates en cours d'approbation
 *       (voir {@code templates}).</li>
 *   <li>{@code tousTemplatesApprouves=true} : activation complete, envois autorises.</li>
 * </ul>
 *
 * @param subAccountCree              true si le sub-account Twilio est cree
 * @param wabaId                      WhatsApp Business Account ID Meta (peut etre null)
 * @param metaBusinessId              Meta Business ID (peut etre null)
 * @param numeroAffiche               numero E.164 si l'onboarding a abouti
 * @param nomAffiche                  nom affiche a vos prospects sur WhatsApp (verified
 *                                    name valide Meta). Peut differer du nom d'etablissement
 *                                    saisi dans l'app : c'est ce que voit reellement le prospect.
 * @param tousTemplatesApprouves      true si tous les templates Meta sont APPROUVE
 * @param templates                   liste des templates et leur statut (pour timeline UI)
 * @param messageGuidage              texte court a afficher selon l'etat
 * @param messageBlocage              raison si l'envoi est bloque (ou null si OK)
 * @param etatCompte                  etat de sante runtime du compte ({@code ACTIF},
 *                                    {@code RESTREINT}...). Distinct de la progression
 *                                    d'onboarding : peut regresser apres activation.
 * @param messageEtat                 message user-facing quand le compte est restreint
 *                                    (ou null si OK). Sert a (re)afficher la banniere.
 * @param numeroEnEchec               true si le numero/sender WhatsApp est indisponible
 *                                    (envois recents en echec deterministe) alors que le
 *                                    compte est ACTIF. Distinct de {@code RESTREINT} : ce
 *                                    n'est pas une restriction Meta mais un probleme de
 *                                    numero a corriger cote etablissement.
 * @param messageEnvoi                message user-facing quand {@code numeroEnEchec} (ou null)
 */
public record RepSelfSignupStatutDto(
        boolean subAccountCree,
        String wabaId,
        String metaBusinessId,
        String numeroAffiche,
        String nomAffiche,
        boolean tousTemplatesApprouves,
        List<TemplateStatutDto> templates,
        String messageGuidage,
        String messageBlocage,
        String etatCompte,
        String messageEtat,
        boolean numeroEnEchec,
        String messageEnvoi
) {

    public record TemplateStatutDto(
            String name,
            String statut,
            String metaRejectReason,
            int nbTentatives
    ) {
    }
}
