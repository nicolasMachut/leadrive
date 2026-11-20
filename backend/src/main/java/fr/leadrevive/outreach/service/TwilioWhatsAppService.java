package fr.leadrevive.outreach.service;

import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.exception.WhatsAppNonConfigureException;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.outreach.template.WhatsAppTemplateDefinition;
import fr.leadrevive.outreach.template.WhatsAppTemplateLoader;
import java.net.URI;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service d'envoi de messages WhatsApp via Twilio sub-account par tenant.
 *
 * <p>Chaque methode d'envoi prend un {@code tenantId} en parametre, resout
 * dynamiquement le numero expediteur ET les credentials du sub-account Twilio
 * via {@link TenantWhatsappConfigRepository}.</p>
 *
 * <p>Post-sunset LEGACY (V064), chaque tenant a son propre sub-account Twilio.
 * Les messages sont envoyes avec un {@link TwilioRestClient} construit a la
 * volee a partir des credentials dechiffres du sub-account (plus de
 * {@code Twilio.init()} global pour l'envoi).</p>
 *
 * <p>Le @Retry et @CircuitBreaker n'agissent que sur les vraies erreurs Twilio
 * (5xx, timeout). Les erreurs metier (config absente, message invalide) sont
 * ignorees via la config Resilience4j (voir application.yml).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwilioWhatsAppService {

    private static final Pattern MOBILE_FR_PATTERN = Pattern.compile("^\\+33[67][0-9]{8}$");
    /** Accepte tout numero international valide : + suivi de 8 a 15 chiffres (norme E.164). */
    private static final Pattern NUMERO_INTERNATIONAL_PATTERN = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final TwilioConfig twilioConfig;
    private final TenantWhatsappConfigRepository tenantWhatsappConfigRepository;
    private final TenantWhatsappTemplateRepository tenantWhatsappTemplateRepository;
    private final WhatsAppTemplateLoader templateLoader;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${leadrevive.backend-url:}")
    private String backendUrl;

    /**
     * Resout la config WhatsApp active du tenant.
     * Le tenant DOIT avoir une config en statut ACTIF.
     *
     * @throws WhatsAppNonConfigureException si pas de config ACTIF
     */
    private TenantWhatsappConfig resoudreConfig(final Long tenantId) {
        return tenantWhatsappConfigRepository.findByTenantId(tenantId)
                .filter(c -> c.getStatut() == StatutWhatsappConfigEnum.ACTIF)
                .orElseThrow(() -> new WhatsAppNonConfigureException(tenantId));
    }

    /**
     * Construit un {@link TwilioRestClient} per-tenant a partir des credentials
     * du sub-account. Le auth token est dechiffre a la volee par le JPA converter
     * et n'est jamais cache en memoire au-dela de l'appel.
     */
    private TwilioRestClient buildTwilioClient(final TenantWhatsappConfig config) {
        final String subSid = config.getTwilioSubAccountSid();
        final String subToken = config.getTwilioSubAuthToken();
        if (subSid == null || subToken == null) {
            throw new IllegalStateException(
                    "Sub-account Twilio absent pour tenantId=" + config.getTenantId()
                    + " — onboarding Self Sign-up non termine.");
        }
        return new TwilioRestClient.Builder(subSid, subToken).build();
    }

    @Retry(name = "twilioCall")
    @CircuitBreaker(name = "twilioCall", fallbackMethod = "envoyerWhatsAppFallback")
    public String envoyerWhatsApp(final String numero, final String contenu, final Long tenantId) {
        final TenantWhatsappConfig config = resoudreConfig(tenantId);
        final String whatsappFrom = config.getWhatsappFrom();

        log.info("Envoi WhatsApp - tenant: {}, destinataire: {}, longueur: {}",
                tenantId, PiiMasker.phone(numero), contenu.length());

        final String numeroFormate = formaterNumeroFr(numero);
        if (!NUMERO_INTERNATIONAL_PATTERN.matcher(numeroFormate).matches()) {
            throw new IllegalArgumentException("Numero invalide (format E.164 attendu : +XXXXXXXXXXX) : " + numero);
        }

        if (twilioConfig.isDryRun()) {
            final String fakeId = "dryrun-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("DRY RUN WhatsApp - tenant: {}, destinataire: {}, contenu: {}, fakeId: {}",
                    tenantId, PiiMasker.phone(numeroFormate), PiiMasker.content(contenu), fakeId);
            return fakeId;
        }

        final TwilioRestClient client = buildTwilioClient(config);
        final com.twilio.rest.api.v2010.account.MessageCreator creator = Message.creator(
                new PhoneNumber("whatsapp:" + numeroFormate),
                new PhoneNumber("whatsapp:" + whatsappFrom),
                contenu
        );
        if (backendUrl != null && !backendUrl.isBlank()) {
            creator.setStatusCallback(URI.create(backendUrl + "/api/webhook/twilio/status"));
        }
        final Message message = creator.create(client);

        final String messageSid = message.getSid();
        log.info("WhatsApp envoye - tenant: {}, destinataire: {}, sid: {}",
                tenantId, PiiMasker.phone(numeroFormate), messageSid);
        return messageSid;
    }

    /**
     * Envoie un WhatsApp via Content Template (premier message hors fenetre 24h).
     * @param contentVariables map des variables du template {"1": "Jean", "2": "Nora", ...}
     * @return le SID du message envoye
     */
    @Retry(name = "twilioCall")
    @CircuitBreaker(name = "twilioCall", fallbackMethod = "envoyerTemplateFallback")
    public String envoyerWhatsAppTemplate(final String numero, final Map<String, String> contentVariables, final Long tenantId) {
        final TenantWhatsappConfig config = resoudreConfig(tenantId);
        final String whatsappFrom = config.getWhatsappFrom();
        final String templateSid = tenantWhatsappTemplateRepository
                .findByTenantIdAndTemplateName(tenantId, "leadrive_premier_contact")
                .map(TenantWhatsappTemplate::getTemplateSid)
                .orElse(null);
        if (templateSid == null || templateSid.isBlank()) {
            log.warn("Template leadrive_premier_contact non trouve pour tenantId={}", tenantId);
            throw new IllegalStateException(
                    "Template leadrive_premier_contact non trouve pour tenantId=" + tenantId
                    + " — verifier que les templates ont ete soumis pour ce tenant");
        }

        final String numeroFormate = formaterNumeroFr(numero);
        log.info("Envoi WhatsApp template - tenant: {}, destinataire: {}, templateSid: {}",
                tenantId, PiiMasker.phone(numeroFormate), templateSid);

        if (twilioConfig.isDryRun()) {
            final String fakeId = "dryrun-tpl-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("DRY RUN WhatsApp template - tenant: {}, destinataire: {}, fakeId: {}",
                    tenantId, PiiMasker.phone(numeroFormate), fakeId);
            return fakeId;
        }

        final String json = serialiserVariables(contentVariables);
        final TwilioRestClient client = buildTwilioClient(config);

        final com.twilio.rest.api.v2010.account.MessageCreator templateCreator = Message.creator(
                new PhoneNumber("whatsapp:" + numeroFormate),
                new PhoneNumber("whatsapp:" + whatsappFrom),
                "" // body vide pour les templates
        )
        .setContentSid(templateSid)
        .setContentVariables(json);
        if (backendUrl != null && !backendUrl.isBlank()) {
            templateCreator.setStatusCallback(URI.create(backendUrl + "/api/webhook/twilio/status"));
        }
        final Message message = templateCreator.create(client);

        final String messageSid = message.getSid();
        log.info("WhatsApp template envoye - tenant: {}, destinataire: {}, sid: {}",
                tenantId, PiiMasker.phone(numeroFormate), messageSid);
        return messageSid;
    }

    /**
     * Envoie un WhatsApp via un template specifique (utilise pour les relances J+1 et J+3).
     * @param numero numero du destinataire
     * @param templateSid le Content SID du template a utiliser
     * @param contentVariables variables du template {"1": "Jean", "2": "Nora", ...}
     * @return le SID du message envoye
     */
    @Retry(name = "twilioCall")
    @CircuitBreaker(name = "twilioCall", fallbackMethod = "envoyerRelanceTemplateFallback")
    public String envoyerRelanceTemplate(final String numero, final String templateSid,
                                          final Map<String, String> contentVariables, final Long tenantId) {
        final TenantWhatsappConfig config = resoudreConfig(tenantId);
        final String whatsappFrom = config.getWhatsappFrom();
        if (templateSid == null || templateSid.isBlank()) {
            throw new IllegalStateException("Template SID de relance non configure");
        }

        final String numeroFormate = formaterNumeroFr(numero);
        log.info("Envoi WhatsApp relance template - tenant: {}, destinataire: {}, templateSid: {}",
                tenantId, PiiMasker.phone(numeroFormate), templateSid);

        if (twilioConfig.isDryRun()) {
            final String fakeId = "dryrun-relance-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("DRY RUN WhatsApp relance - tenant: {}, destinataire: {}, fakeId: {}",
                    tenantId, PiiMasker.phone(numeroFormate), fakeId);
            return fakeId;
        }

        final String json = serialiserVariables(contentVariables);
        final TwilioRestClient client = buildTwilioClient(config);

        final com.twilio.rest.api.v2010.account.MessageCreator creator = Message.creator(
                new PhoneNumber("whatsapp:" + numeroFormate),
                new PhoneNumber("whatsapp:" + whatsappFrom),
                ""
        )
        .setContentSid(templateSid)
        .setContentVariables(json);
        if (backendUrl != null && !backendUrl.isBlank()) {
            creator.setStatusCallback(URI.create(backendUrl + "/api/webhook/twilio/status"));
        }
        final Message message = creator.create(client);

        log.info("WhatsApp relance envoye - tenant: {}, destinataire: {}, sid: {}",
                tenantId, PiiMasker.phone(numeroFormate), message.getSid());
        return message.getSid();
    }

    /**
     * Serialise un map en JSON {"1":"val1","2":"val2",...} pour les ContentVariables Twilio.
     * On utilise Jackson plutot que de construire le JSON a la main : un \, un retour
     * chariot ou un caractere Unicode de controle dans une variable produirait du JSON
     * invalide et Twilio renverrait 21655 "Invalid Content variables".
     */
    private String serialiserVariables(final Map<String, String> contentVariables) {
        if (contentVariables == null || contentVariables.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(contentVariables);
        } catch (final Exception e) {
            throw new IllegalStateException("Echec serialisation ContentVariables Twilio: " + e.getMessage(), e);
        }
    }

    /**
     * Serialise les variables d'un template en JSON pour les persister sur le
     * {@code SmsMessage} (en vue d'un rejeu lors d'un "Reessayer"). Public pour
     * etre appelable depuis les services qui enregistrent les messages template.
     */
    public String serialiserContentVariables(final Map<String, String> contentVariables) {
        return serialiserVariables(contentVariables);
    }

    /**
     * Deserialise le JSON des variables d'un template stocke sur le {@code SmsMessage}.
     * Retourne une map vide si null/blank/illisible (le rejeu pourra tout de meme
     * partir avec un template sans variables plutot que d'echouer durement).
     */
    public Map<String, String> deserialiserContentVariables(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json,
                    new tools.jackson.core.type.TypeReference<Map<String, String>>() { });
        } catch (final Exception e) {
            log.warn("ContentVariables illisibles, rejeu sans variables - cause: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Resout le Content SID du template "premier contact" pour un tenant (ou null).
     * Utilise pour memoriser sur le SmsMessage de quel template provient le premier
     * message, afin de pouvoir le rejouer a l'identique lors d'un "Reessayer".
     */
    public String resoudrePremierContactSid(final Long tenantId) {
        return tenantWhatsappTemplateRepository
                .findByTenantIdAndTemplateName(tenantId, "leadrive_premier_contact")
                .map(TenantWhatsappTemplate::getTemplateSid)
                .orElse(null);
    }

    private String envoyerRelanceTemplateFallback(final String numero, final String templateSid,
                                                    final Map<String, String> contentVariables,
                                                    final Long tenantId, final Throwable t) {
        log.error("Fallback envoi WhatsApp relance - tenant: {}, destinataire: {}, cause: {}",
                tenantId, PiiMasker.phone(numero), t.getMessage());
        throw new RuntimeException("Service WhatsApp temporairement indisponible: " + t.getMessage());
    }

    private String envoyerTemplateFallback(final String numero, final Map<String, String> contentVariables,
                                            final Long tenantId, final Throwable t) {
        log.error("Fallback envoi WhatsApp template - tenant: {}, destinataire: {}, cause: {}",
                tenantId, PiiMasker.phone(numero), t.getMessage());
        throw new RuntimeException("Service WhatsApp temporairement indisponible: " + t.getMessage());
    }

    private String envoyerWhatsAppFallback(final String numero, final String contenu,
                                            final Long tenantId, final Throwable t) {
        log.error("Fallback envoi WhatsApp - tenant: {}, destinataire: {}, cause: {}",
                tenantId, PiiMasker.phone(numero), t.getMessage());
        throw new RuntimeException("Service WhatsApp temporairement indisponible: " + t.getMessage());
    }

    public String formaterNumeroFr(final String numero) {
        if (numero == null || numero.isBlank()) {
            return "";
        }
        String nettoye = numero.replaceAll("[\\s.\\-()]", "");
        // Indicatif international avec 00 (ex: 0033659637042)
        if (nettoye.startsWith("00") && nettoye.length() > 4) {
            nettoye = "+" + nettoye.substring(2);
        }
        // Format local francais (ex: 0600000002)
        if (nettoye.startsWith("0") && nettoye.length() == 10) {
            nettoye = "+33" + nettoye.substring(1);
        }
        // Pas de prefixe du tout (ex: 659637042)
        if (!nettoye.startsWith("+")) {
            nettoye = "+33" + nettoye;
        }
        return nettoye;
    }

    public String getAssistantPrenom() {
        return twilioConfig.getAssistantPrenom();
    }

    /**
     * Recupere le body du template WhatsApp premier contact avec les variables {{1}}, {{2}}, etc.
     * Lit directement depuis le YAML loader (source de verite versionnee dans Git)
     * plutot que d'appeler l'API Twilio Content.
     * Cache en memoire pour eviter de reparser a chaque envoi. Cache invalide apres redemarrage
     * ou expiration du cache global (Caffeine 10min).
     * Retourne null si le template ne peut pas etre recupere — l'appelant doit gerer ce cas (echec d'envoi).
     */
    @Cacheable(value = "twilioTemplateBody", key = "'premier_contact'")
    public String getTemplateBody() {
        final WhatsAppTemplateLoader.LoadedTemplate loaded = templateLoader.parNom("leadrive_premier_contact");
        if (loaded == null) {
            log.warn("Template YAML leadrive_premier_contact introuvable dans le loader");
            return null;
        }
        return loaded.definition().components().stream()
                .filter(c -> c.type() == WhatsAppTemplateDefinition.ComponentType.BODY)
                .map(WhatsAppTemplateDefinition.Component::text)
                .findFirst()
                .orElse(null);
    }

    /**
     * Remplace les placeholders {{1}}, {{2}}, etc. dans un body par les valeurs fournies.
     */
    public String appliquerVariables(final String templateBody, final Map<String, String> variables) {
        if (templateBody == null) return null;
        String resultat = templateBody;
        for (final Map.Entry<String, String> entry : variables.entrySet()) {
            resultat = resultat.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resultat;
    }

    public boolean estMobileFrancais(final String numero) {
        final String formate = formaterNumeroFr(numero);
        return MOBILE_FR_PATTERN.matcher(formate).matches();
    }

    public boolean estNumeroValide(final String numero) {
        final String formate = formaterNumeroFr(numero);
        return NUMERO_INTERNATIONAL_PATTERN.matcher(formate).matches();
    }
}
