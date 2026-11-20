package fr.leadrevive.outreach.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter qui chiffre/dechiffre les String via {@link SecretCryptoService}.
 *
 * <p><strong>Probleme classique JPA</strong> : un converter JPA est instancie
 * par Hibernate (pas Spring), donc on ne peut pas y injecter directement un
 * bean. Solution : un {@link ApplicationContextAware} static holder fournit
 * l'instance Spring du service au converter au moment ou il en a besoin.</p>
 *
 * <p><strong>Usage</strong> :</p>
 * <pre>{@code
 * @Column(name = "twilio_sub_auth_token")
 * @Convert(converter = EncryptedStringConverter.class)
 * private String twilioSubAuthToken;
 * }</pre>
 *
 * <p>Le {@code autoApply = false} est volontaire : on ne veut PAS chiffrer
 * automatiquement TOUS les String de la BDD, seulement ceux annotes
 * {@code @Convert} explicitement.</p>
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(final String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        return SpringCryptoBridge.service().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(final String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        return SpringCryptoBridge.service().decrypt(dbData);
    }

    /**
     * Pont statique entre Hibernate (qui instancie le converter sans Spring)
     * et le bean {@link SecretCryptoService}. Spring l'initialise au demarrage,
     * le converter y accede ensuite par appel statique.
     */
    @Component
    static class SpringCryptoBridge implements ApplicationContextAware {

        private static SecretCryptoService cryptoService;

        @Autowired
        public SpringCryptoBridge(final SecretCryptoService cryptoService) {
            SpringCryptoBridge.cryptoService = cryptoService;
        }

        @Override
        public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
            // Pas besoin du context ici, l'injection par constructeur suffit.
            // L'interface est implementee pour garantir l'initialisation avant
            // toute conversion JPA.
        }

        static SecretCryptoService service() {
            if (cryptoService == null) {
                throw new IllegalStateException(
                        "SecretCryptoService non initialise. "
                        + "Le bean SpringCryptoBridge n'a pas " +
                                "ete charge avant "
                        + "qu'une conversion JPA chiffree soit demandee."
                );
            }
            return cryptoService;
        }
    }
}
