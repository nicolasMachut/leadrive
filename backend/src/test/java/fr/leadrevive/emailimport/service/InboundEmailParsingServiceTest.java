package fr.leadrevive.emailimport.service;

import fr.leadrevive.emailimport.service.InboundEmailParsingService.ResultatParsing;
import fr.leadrevive.emailimport.service.InboundEmailParsingService.StatutResultat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires du parsing de la reponse IA (methode pure {@code parseResponse}).
 * Cible la robustesse aux ecarts de structure frequents des LLM.
 */
class InboundEmailParsingServiceTest {

    @Test
    void wrapperPrincipalPresent_extraitLeLead() {
        final String json = """
                {"principal": {"nom": "Dupont", "prenom": "Jean", "telephone": "0612345678",
                 "email": "jean@dupont.fr", "marque": "Peugeot", "modele": "208", "confiance": 85}}
                """;

        final ResultatParsing r = InboundEmailParsingService.parseResponse(json, 42L);

        assertAll(
                () -> assertEquals(StatutResultat.OK, r.statut()),
                () -> assertEquals("Dupont", r.nom()),
                () -> assertEquals("0612345678", r.telephone()),
                () -> assertEquals(85, r.confiance())
        );
    }

    @Test
    void wrapperPrincipalOublie_maisChampsALaRacine_rattrapeLeLead() {
        // [FIX 2026-07-15] L'IA omet parfois le wrapper "principal" et met les
        // champs a la racine. On doit rattraper le lead, pas echouer.
        final String json = """
                {"nom": "Martin", "telephone": "0699887766", "marque": "Renault", "confiance": 70}
                """;

        final ResultatParsing r = InboundEmailParsingService.parseResponse(json, 10L);

        assertAll(
                () -> assertEquals(StatutResultat.OK, r.statut()),
                () -> assertEquals("Martin", r.nom()),
                () -> assertEquals("0699887766", r.telephone()),
                () -> assertEquals(70, r.confiance())
        );
    }

    @Test
    void jsonValideSansPrincipalNiChampLead_rejetPropre_pasEchec() {
        // [BUG 2026-07-15] Un email "Re:" (thread cite, aucune donnee de lead) donnait
        // un PARSE_ECHEC "manque 'principal'". On veut un rejet propre (pas une erreur systeme).
        final String json = "{\"rejet\": false, \"raison\": \"rien d'exploitable\"}";

        final ResultatParsing r = InboundEmailParsingService.parseResponse(json, 5L);

        assertAll(
                () -> assertEquals(StatutResultat.REJET_SPAM, r.statut()),
                () -> assertNotNull(r.resultatBrut(), "Le brut IA doit etre conserve pour audit")
        );
    }

    @Test
    void principalNull_rejetPropre() {
        final String json = "{\"principal\": null}";

        final ResultatParsing r = InboundEmailParsingService.parseResponse(json, 0L);

        assertEquals(StatutResultat.REJET_SPAM, r.statut());
    }

    @Test
    void rejetSpamExplicite_estRespecte() {
        final String json = "{\"rejet\": true, \"raison\": \"newsletter\"}";

        final ResultatParsing r = InboundEmailParsingService.parseResponse(json, 0L);

        assertAll(
                () -> assertEquals(StatutResultat.REJET_SPAM, r.statut()),
                () -> assertEquals("newsletter", r.raisonRejet())
        );
    }

    @Test
    void confianceInsuffisante_estSignalee() {
        final String json = "{\"principal\": {\"nom\": \"Faible\", \"confiance\": 12}}";

        final ResultatParsing r = InboundEmailParsingService.parseResponse(json, 0L);

        assertAll(
                () -> assertEquals(StatutResultat.CONFIANCE_INSUFFISANTE, r.statut()),
                () -> assertEquals(12, r.confiance())
        );
    }

    @Test
    void jsonMalforme_echecAvecBrutConserve() {
        final String json = "{ ceci n'est pas du JSON";

        final ResultatParsing r = InboundEmailParsingService.parseResponse(json, 0L);

        assertAll(
                () -> assertEquals(StatutResultat.ECHEC, r.statut()),
                () -> assertNotNull(r.resultatBrut(), "Le brut doit etre conserve pour debug")
        );
    }

    @Test
    void reponseVide_echec() {
        final ResultatParsing r = InboundEmailParsingService.parseResponse("  ", 0L);

        assertAll(
                () -> assertEquals(StatutResultat.ECHEC, r.statut()),
                () -> assertNull(r.nom())
        );
    }

    @Test
    void fencesMarkdown_sontNettoyees() {
        final String json = "```json\n{\"principal\": {\"nom\": \"Fence\", \"confiance\": 90}}\n```";

        final ResultatParsing r = InboundEmailParsingService.parseResponse(json, 0L);

        assertAll(
                () -> assertEquals(StatutResultat.OK, r.statut()),
                () -> assertEquals("Fence", r.nom())
        );
    }
}
