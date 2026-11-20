package fr.leadrevive.outreach.template;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Charge les templates WhatsApp YAML depuis {@code resources/whatsapp-templates/}
 * au demarrage de l'application et les expose en cache memoire.
 *
 * <p>Les templates sont la <strong>source de verite</strong> du wording envoye
 * aux prospects. Toute modification passe par une PR Git, ce qui garantit
 * traçabilite + revue.</p>
 *
 * <p>Le loader calcule egalement un hash SHA-256 du contenu YAML pour permettre
 * au service de soumission de detecter qu'un template a evolue et qu'il faut
 * potentiellement le re-soumettre.</p>
 *
 * <p><strong>Fail-fast au boot</strong> : si un fichier YAML est invalide
 * (champ manquant, categorie inconnue), l'application refuse de demarrer.
 * Mieux vaut un crash au boot qu'un envoi de message corrompu en prod.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateLoader {

    private static final String TEMPLATES_LOCATION_PATTERN =
            "classpath:whatsapp-templates/*.yml";

    /** Placeholder de variable Meta/Twilio dans un body : {@code {{1}}}, {@code {{2}}}... */
    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("\\{\\{\\s*(\\d+)\\s*\\}\\}");

    private final ResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    @Value("${whatsapp.templates.required:true}")
    private boolean templatesRequired;

    /**
     * Cache immuable des templates : name (ex: "leadrive_premier_contact") -> definition.
     * Charge une seule fois au @PostConstruct.
     */
    private Map<String, LoadedTemplate> templates = Map.of();

    @PostConstruct
    void chargerTemplates() {
        log.info("Chargement des templates WhatsApp - pattern: {}", TEMPLATES_LOCATION_PATTERN);
        final ObjectMapper yamlMapper = YAMLMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
        final Map<String, LoadedTemplate> chargees = new HashMap<>();
        try {
            final Resource[] resources = resourceResolver.getResources(TEMPLATES_LOCATION_PATTERN);
            log.info("Templates YAML detectes - nombre: {}", resources.length);
            if (resources.length == 0 && templatesRequired) {
                throw new IllegalStateException(
                        "Aucun template WhatsApp trouve dans whatsapp-templates/. "
                        + "L'epic 25 requiert au moins un template au boot."
                );
            }
            for (final Resource resource : resources) {
                final String fileName = resource.getFilename();
                Objects.requireNonNull(fileName, "Resource sans nom de fichier");
                log.info("Chargement du template - fichier: {}", fileName);
                final byte[] bytes = lireToutLeFichier(resource);
                final String yamlContent = new String(bytes, StandardCharsets.UTF_8);
                final String hash = hashSha256(bytes);
                final WhatsAppTemplateDefinition definition =
                        yamlMapper.readValue(bytes, WhatsAppTemplateDefinition.class);
                validate(definition, fileName);
                final LoadedTemplate loaded = new LoadedTemplate(
                        definition, yamlContent, hash, fileName);
                final LoadedTemplate previous = chargees.put(definition.name(), loaded);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Template name duplique: " + definition.name()
                            + " (fichiers " + previous.fileName() + " et " + fileName + ")"
                    );
                }
            }
            this.templates = Map.copyOf(chargees);
            log.info("Templates WhatsApp charges avec succes - count: {}", this.templates.size());
        } catch (IOException ex) {
            throw new IllegalStateException("Echec du chargement des templates WhatsApp", ex);
        }
    }

    /**
     * Valide qu'un template parse a tous les champs requis pour Meta.
     * Reject precoce avant la soumission Twilio.
     */
    private void validate(final WhatsAppTemplateDefinition definition, final String fileName) {
        if (!definition.name().startsWith("leadrive_")) {
            throw new IllegalStateException(
                    "Template " + fileName + ": le name doit commencer par 'leadrive_' "
                    + "(eviter les collisions avec les templates existants du WABA client). "
                    + "Got: " + definition.name()
            );
        }
        if (definition.components().isEmpty()) {
            throw new IllegalStateException(
                    "Template " + fileName + ": au moins un component requis (au minimum BODY)."
            );
        }
        final boolean hasBody = definition.components().stream()
                .anyMatch(c -> c.type() == WhatsAppTemplateDefinition.ComponentType.BODY);
        if (!hasBody) {
            throw new IllegalStateException(
                    "Template " + fileName + ": le composant BODY est obligatoire pour Meta."
            );
        }
        for (final WhatsAppTemplateDefinition.Component comp : definition.components()) {
            if (comp.type() == WhatsAppTemplateDefinition.ComponentType.BODY
                    && (comp.text() == null || comp.text().isBlank())) {
                throw new IllegalStateException(
                        "Template " + fileName + ": le BODY doit avoir un text non vide."
                );
            }
        }
        // Coherence placeholders {{n}} <-> variables declarees. Sans ce garde, un
        // BODY qui reference plus (ou moins) de variables que la liste 'variables:'
        // passe au boot mais casse a l'usage : Twilio rejette si le nombre de content
        // variables envoyees != placeholders, ou une variable reste non substituee
        // devant le prospect. (Cas reel corrige : rappel_rdv 4 placeholders / 2 envoyees.)
        for (final WhatsAppTemplateDefinition.Component comp : definition.components()) {
            if (comp.type() != WhatsAppTemplateDefinition.ComponentType.BODY) {
                continue;
            }
            final java.util.SortedSet<Integer> indices = new java.util.TreeSet<>();
            final java.util.regex.Matcher matcher = PLACEHOLDER.matcher(comp.text());
            while (matcher.find()) {
                indices.add(Integer.parseInt(matcher.group(1)));
            }
            final int nbVariables = comp.variables().size();
            if (indices.size() != nbVariables) {
                throw new IllegalStateException(
                        "Template " + fileName + ": le BODY reference " + indices.size()
                        + " variable(s) distincte(s) " + indices + " mais 'variables:' en declare "
                        + nbVariables + ". Les deux doivent correspondre (sinon rejet Twilio a "
                        + "l'envoi ou variable non substituee devant le prospect).");
            }
            for (int i = 1; i <= nbVariables; i++) {
                if (!indices.contains(i)) {
                    throw new IllegalStateException(
                            "Template " + fileName + ": placeholders du BODY a numeroter 1..N "
                            + "sans trou. Manquant: {{" + i + "}} (trouves: " + indices + ").");
                }
            }
        }
    }

    private byte[] lireToutLeFichier(final Resource resource) throws IOException {
        try (final InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private String hashSha256(final byte[] bytes) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 non disponible sur la JVM ?", ex);
        }
    }

    /**
     * Liste tous les templates a soumettre a un nouveau WABA.
     * Garantie : la collection est ordonnee selon le nom de fichier
     * (deterministe pour les tests).
     */
    public List<LoadedTemplate> tousLesTemplates() {
        return this.templates.values().stream()
                .sorted((a, b) -> a.fileName().compareTo(b.fileName()))
                .toList();
    }

    /**
     * Recupere un template par son nom Meta (ex: "leadrive_premier_contact").
     * @return null si introuvable.
     */
    public LoadedTemplate parNom(final String name) {
        return this.templates.get(name);
    }

    /**
     * Template charge + metadata utiles a la soumission.
     */
    public record LoadedTemplate(
            WhatsAppTemplateDefinition definition,
            String yamlContent,
            String contentHashSha256,
            String fileName
    ) { }
}
