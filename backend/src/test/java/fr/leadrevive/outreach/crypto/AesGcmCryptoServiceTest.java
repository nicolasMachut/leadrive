package fr.leadrevive.outreach.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires du chiffrement AES-256-GCM utilise pour les secrets persistes
 * (notamment les auth tokens Twilio sub-account de l'epic 25).
 *
 * <p>Verifie : round-trip, format prefixe, rejet des cles invalides, rejet des
 * ciphertexts corrompus.</p>
 */
class AesGcmCryptoServiceTest {

    // Cle 32 bytes base64 — generee uniquement pour ce test, jamais utilisee ailleurs.
    private static final String TEST_KEY_BASE64 = "eXoX3C+yHeHKkNy90bjAclTiYC28GoBxerEFl/gwhdk=";

    private AesGcmCryptoService crypto;

    @BeforeEach
    void setUp() {
        crypto = new AesGcmCryptoService();
        ReflectionTestUtils.setField(crypto, "masterKeyBase64", TEST_KEY_BASE64);
        crypto.initKey();
    }

    @Test
    void roundTrip_renvoie_le_meme_texte() {
        final String plaintext = "auth_token_twilio_super_secret_123";

        final String encrypted = crypto.encrypt(plaintext);
        final String decrypted = crypto.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_genere_un_prefixe_v1() {
        final String encrypted = crypto.encrypt("test");

        assertThat(encrypted).startsWith("v1:");
    }

    @Test
    void encrypt_genere_des_ciphertexts_differents_pour_le_meme_plaintext() {
        // Garantie de GCM avec IV aleatoire : deux chiffrements du meme plaintext
        // produisent des sorties differentes. Empeche les attaques par comparaison.
        final String plaintext = "auth_token";

        final String first = crypto.encrypt(plaintext);
        final String second = crypto.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo(plaintext);
        assertThat(crypto.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_null_renvoie_null() {
        assertThat(crypto.encrypt(null)).isNull();
    }

    @Test
    void decrypt_null_renvoie_null() {
        assertThat(crypto.decrypt(null)).isNull();
    }

    @Test
    void decrypt_sans_prefixe_v1_leve_exception() {
        assertThatThrownBy(() -> crypto.decrypt("YWJjZGVm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v1:");
    }

    @Test
    void decrypt_avec_ciphertext_corrompu_leve_exception() {
        // Bit flipping : on prend un ciphertext valide et on remplace 1 char base64.
        final String good = crypto.encrypt("valeur");
        final String corrupted = good.substring(0, good.length() - 5) + "AAAAA";

        assertThatThrownBy(() -> crypto.decrypt(corrupted))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initKey_refuse_une_cle_de_taille_invalide() {
        final AesGcmCryptoService service = new AesGcmCryptoService();
        // 16 bytes -> AES-128, on veut AES-256
        ReflectionTestUtils.setField(service, "masterKeyBase64", "AAAAAAAAAAAAAAAAAAAAAA==");

        assertThatThrownBy(service::initKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void initKey_refuse_une_cle_vide() {
        final AesGcmCryptoService service = new AesGcmCryptoService();
        ReflectionTestUtils.setField(service, "masterKeyBase64", "");

        assertThatThrownBy(service::initKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TWILIO_ENCRYPTION_KEY");
    }

    @Test
    void initKey_refuse_une_cle_qui_nest_pas_base64() {
        final AesGcmCryptoService service = new AesGcmCryptoService();
        ReflectionTestUtils.setField(service, "masterKeyBase64", "!!!!not_base64!!!!");

        assertThatThrownBy(service::initKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }
}
