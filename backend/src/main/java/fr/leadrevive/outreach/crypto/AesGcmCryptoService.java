package fr.leadrevive.outreach.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Implementation MVP de {@link SecretCryptoService} : AES-256-GCM avec master key
 * unique injectee via la variable d'environnement {@code TWILIO_ENCRYPTION_KEY}
 * (base64 d'une cle 32 bytes / 256 bits).
 *
 * <p><strong>Format du ciphertext</strong> : {@code v1:base64(iv || ciphertext || tag)}.
 * IV (12 bytes) prepende au ciphertext + tag GCM (16 bytes) en suffixe (geres par
 * la JCE). Encode tout en base64.</p>
 *
 * <p><strong>Generation de la cle</strong> :
 * <pre>openssl rand -base64 32</pre>
 * </p>
 *
 * <p><strong>Limitations connues du MVP</strong> :</p>
 * <ul>
 *   <li>Master key unique : la rotation impose de re-chiffrer toute la BDD.</li>
 *   <li>Cle stockee en clair en variable d'env Railway : risque si fuite env.</li>
 *   <li>Pas de DEK per row : compromission master key = toute la BDD compromise.</li>
 * </ul>
 *
 * <p><strong>Migration KMS future</strong> : creer une classe
 * {@code KmsEnvelopeCryptoService} qui chiffre une DEK locale par KMS, garde
 * la DEK chiffree dans une colonne separee. Le prefixe {@code v2:} discriminera
 * les anciennes valeurs lors du dechiffrement.</p>
 */
@Service
@Slf4j
public class AesGcmCryptoService implements SecretCryptoService {

    private static final String ALGO = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String PREFIX_V1 = "v1:";

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${secret.crypto.master-key:#{null}}")
    private String masterKeyBase64;

    private SecretKey secretKey;

    @PostConstruct
    void initKey() {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            // Fail-fast au boot : on refuse de demarrer sans cle de chiffrement.
            // L'absence de cle = aucune persistance de secrets, mieux vaut crasher
            // que d'ecrire des tokens en clair en prod.
            throw new IllegalStateException(
                    "Variable d'environnement TWILIO_ENCRYPTION_KEY manquante ou vide. "
                    + "Generez une cle avec : openssl rand -base64 32 "
                    + "et exportez-la dans Railway sous secret.crypto.master-key. "
                    + "Voir application.yml mapping."
            );
        }
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(masterKeyBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "TWILIO_ENCRYPTION_KEY n'est pas un base64 valide.", ex);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "TWILIO_ENCRYPTION_KEY doit etre une cle AES-256 (32 bytes). "
                    + "Recu: " + keyBytes.length + " bytes."
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGO);
        log.info("SecretCryptoService initialise - algo: {}, keyBits: 256", TRANSFORMATION);
    }

    @Override
    public String encrypt(final String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            final byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // Concatenation : iv (12) || ciphertext || tag (16). Le tag est deja
            // en suffixe du ciphertext par la JCE.
            final byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX_V1 + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Echec du chiffrement AES-GCM", ex);
        }
    }

    @Override
    public String decrypt(final String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        if (!ciphertext.startsWith(PREFIX_V1)) {
            throw new IllegalArgumentException(
                    "Ciphertext sans prefixe de version reconnu. Attendu: v1:..."
            );
        }
        try {
            final byte[] payload = Base64.getDecoder().decode(ciphertext.substring(PREFIX_V1.length()));
            if (payload.length < IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
                throw new IllegalArgumentException("Payload chiffre trop court");
            }
            final byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH_BYTES);
            final byte[] cipherBytes = new byte[payload.length - IV_LENGTH_BYTES];
            System.arraycopy(payload, IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            final byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Echec du dechiffrement AES-GCM", ex);
        }
    }
}
