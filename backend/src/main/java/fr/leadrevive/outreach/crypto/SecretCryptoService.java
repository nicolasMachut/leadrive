package fr.leadrevive.outreach.crypto;

/**
 * Service de chiffrement symetrique pour les secrets persistes en BDD.
 *
 * <p>Cas d'usage MVP : auth tokens Twilio sub-accounts (epic 25). Les valeurs
 * sont chiffrees a l'ecriture, dechiffrees a la lecture, jamais mises en
 * cache memoire dechiffrees.</p>
 *
 * <p><strong>Roadmap KMS</strong> : la version courante utilise une master key
 * unique en variable d'environnement. Pour la production a grande echelle, on
 * basculera vers <em>envelope encryption</em> avec un KMS (AWS KMS / GCP KMS) :
 * une DEK (Data Encryption Key) par ligne, chiffree par la master key KMS.
 * Permet la rotation de la master key sans re-chiffrer la BDD entiere.
 * L'interface reste la meme, seule l'implementation change.</p>
 *
 * <p><strong>Format chiffre</strong> : prefixe versionne {@code v1:} suivi du
 * texte chiffre en base64. Le prefixe permet de migrer le schema chiffrement
 * (v2 = envelope KMS) sans casser les anciennes valeurs.</p>
 */
public interface SecretCryptoService {

    /**
     * Chiffre un texte clair.
     *
     * @param plaintext texte en clair (peut etre null ou vide)
     * @return texte chiffre prefixe {@code v1:} ou {@code null} si plaintext null
     */
    String encrypt(String plaintext);

    /**
     * Dechiffre un texte chiffre.
     *
     * @param ciphertext texte chiffre avec prefixe version
     * @return texte clair ou {@code null} si ciphertext null
     * @throws IllegalArgumentException si le format est invalide ou la cle ne correspond pas
     */
    String decrypt(String ciphertext);
}
