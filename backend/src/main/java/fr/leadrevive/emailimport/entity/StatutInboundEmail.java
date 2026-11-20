package fr.leadrevive.emailimport.entity;

/**
 * Statut d'un email recu via SendGrid Inbound Parse.
 *
 * <p>Cycle de vie typique :
 * <ul>
 *   <li>PARSE_OK : email recu, IA a extrait des donnees</li>
 *   <li>LEAD_CREE : lead cree avec succes</li>
 *   <li>LEAD_DEDUPLIQUE : un lead existant a ete enrichi (match telephone ou email)</li>
 *   <li>LEAD_INCOMPLET : lead cree mais sans telephone (a completer par le manager)</li>
 *   <li>PARSE_ECHEC : IA n'a pas reussi a parser (confiance &lt; 30 ou erreur)</li>
 *   <li>REJETE_SPAM : IA a detecte du spam / newsletter / confirmation auto</li>
 *   <li>REJETE_RATE_LIMIT : trop d'emails recus dans la fenetre, pas parse</li>
 * </ul>
 * </p>
 */
public enum StatutInboundEmail {
    PARSE_OK,
    LEAD_CREE,
    LEAD_DEDUPLIQUE,
    LEAD_INCOMPLET,
    PARSE_ECHEC,
    REJETE_SPAM,
    REJETE_RATE_LIMIT
}
