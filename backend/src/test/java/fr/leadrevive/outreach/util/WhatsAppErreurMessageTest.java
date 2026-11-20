package fr.leadrevive.outreach.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du traducteur de motif d'echec WhatsApp (code Twilio -> phrase claire FR).
 */
class WhatsAppErreurMessageTest {

    @Test
    void lisible_null_retourneNull() {
        assertThat(WhatsAppErreurMessage.lisible(null)).isNull();
        assertThat(WhatsAppErreurMessage.lisible("  ")).isNull();
    }

    @Test
    void lisible_horsFenetre24h_messageDedie() {
        assertThat(WhatsAppErreurMessage.lisible("[63016] Out of session window"))
                .contains("24 h");
    }

    @Test
    void lisible_codeInconnu_messageGenerique() {
        final String msg = WhatsAppErreurMessage.lisible("[99999] something");
        assertThat(msg).contains("échoué");
        // Aucun code technique exposé a l'utilisateur.
        assertThat(msg).doesNotContain("99999");
    }

    @Test
    void lisible_sansCode_messageGenerique() {
        assertThat(WhatsAppErreurMessage.lisible("erreur brute sans code")).contains("échoué");
    }

    @Test
    void code_extraitLeCodeTwilio() {
        assertThat(WhatsAppErreurMessage.code("[63016] blabla")).isEqualTo("63016");
        assertThat(WhatsAppErreurMessage.code("pas de code")).isNull();
        assertThat(WhatsAppErreurMessage.code(null)).isNull();
    }

    @Test
    void estNumeroIndisponible_vraiPourCodesSenderInjoignable() {
        assertThat(WhatsAppErreurMessage.estNumeroIndisponible("[63007] Channel not found")).isTrue();
        assertThat(WhatsAppErreurMessage.estNumeroIndisponible("[63051] not operational")).isTrue();
    }

    @Test
    void estNumeroIndisponible_fauxPourEchecParMessageOuInconnu() {
        // 63016 = hors fenetre 24h (par message), pas un numero casse.
        assertThat(WhatsAppErreurMessage.estNumeroIndisponible("[63016] out of window")).isFalse();
        assertThat(WhatsAppErreurMessage.estNumeroIndisponible("[99999] autre")).isFalse();
        assertThat(WhatsAppErreurMessage.estNumeroIndisponible(null)).isFalse();
    }
}
