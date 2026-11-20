package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.integration.TwilioSubAccountApiClient;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestre la creation et la persistance d'un sub-account Twilio par tenant
 * (epic 25).
 *
 * <p><strong>Idempotence</strong> : avant tout appel reseau, on persiste une
 * cle d'idempotence (UUID v4) sur {@link TenantWhatsappConfig}. Si le call
 * Twilio reussit mais que la BDD timeout au moment de stocker le SID/token,
 * on peut rejouer la creation : Twilio retournera le sub deja cree (recherche
 * par friendlyName base sur la cle d'idempotence).</p>
 *
 * <p><strong>Dry-run</strong> : si {@code twilio.dry-run=true}, simule la
 * creation avec un SID et un token fictifs (utile en local).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioSubAccountService {

    private static final String FRIENDLY_NAME_PREFIX = "leadrive-tenant-";

    private final TwilioSubAccountApiClient subAccountClient;
    private final TenantWhatsappConfigRepository configRepository;
    private final TwilioConfig twilioConfig;

    /**
     * Cree (ou retrouve) le sub-account Twilio pour le tenant courant.
     *
     * <p>Le sub-account est cree une seule fois par tenant. Si {@code twilioSubAccountSid}
     * est deja renseigne, retourne immediatement sans appel reseau.</p>
     *
     * <p>Le token retourne par Twilio est passe directement au converter JPA
     * {@code EncryptedStringConverter} via le setter, jamais retourne en clair.</p>
     */
    @Transactional
    public TenantWhatsappConfig assurerSubAccount(final Long tenantId) {
        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId)
                .orElseGet(() -> creerConfigInitiale(tenantId));

        if (config.getTwilioSubAccountSid() != null && !config.getTwilioSubAccountSid().isBlank()) {
            log.info("Sub-account Twilio deja present - tenantId: {}, sid: {}",
                    tenantId, config.getTwilioSubAccountSid());
            return config;
        }

        if (config.getSubAccountIdempotencyKey() == null || config.getSubAccountIdempotencyKey().isBlank()) {
            config.setSubAccountIdempotencyKey(UUID.randomUUID().toString());
            // Sauvegarde immediate de la cle AVANT l'appel Twilio : si le call Twilio
            // reussit mais qu'on plante apres, on retrouve le sub par friendlyName.
            configRepository.save(config);
        }

        final String friendlyName = FRIENDLY_NAME_PREFIX + tenantId + "-" + config.getSubAccountIdempotencyKey();

        final TwilioSubAccountApiClient.SubAccountResponse response;
        if (twilioConfig.isDryRun()) {
            // Simulation dry-run : SID + token fictifs deterministes.
            log.info("DRY-RUN : creation sub-account simulee - tenantId: {}", tenantId);
            response = new TwilioSubAccountApiClient.SubAccountResponse(
                    "AC_dryrun_" + tenantId,
                    "dryrun_token_" + tenantId,
                    friendlyName,
                    "active"
            );
        } else {
            // Recherche d'abord (retry-then-lookup) puis creation si introuvable.
            final TwilioSubAccountApiClient.SubAccountResponse existant =
                    subAccountClient.rechercherParFriendlyName(friendlyName);
            if (existant != null && existant.sid() != null) {
                log.info("Sub-account existant retrouve par friendlyName - tenantId: {}, sid: {}",
                        tenantId, existant.sid());
                response = existant;
            } else {
                response = subAccountClient.creerSubAccount(friendlyName);
            }
        }

        config.setTwilioSubAccountSid(response.sid());
        // Le setter passe par EncryptedStringConverter : token chiffre AES-256-GCM en BDD.
        config.setTwilioSubAuthToken(response.authToken());
        return configRepository.save(config);
    }

    private TenantWhatsappConfig creerConfigInitiale(final Long tenantId) {
        final TenantWhatsappConfig nouvelle = new TenantWhatsappConfig();
        nouvelle.setTenantId(tenantId);
        return configRepository.save(nouvelle);
    }
}
