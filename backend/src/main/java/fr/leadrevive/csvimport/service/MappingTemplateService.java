package fr.leadrevive.csvimport.service;

import fr.leadrevive.csvimport.dto.SplitProposition;
import fr.leadrevive.csvimport.entity.MappingTemplate;
import fr.leadrevive.csvimport.repository.MappingTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gère les templates de mapping CSV réutilisables par concession.
 * Quand un fichier CSV est importé avec un format déjà connu, le template
 * est retrouvé automatiquement → pas d'appel IA → import quasi-instantané.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MappingTemplateService {

    private final MappingTemplateRepository mappingTemplateRepository;

    /**
     * Détecte un template existant dont les headers correspondent exactement
     * aux headers du CSV uploadé (insensible à la casse et aux espaces).
     */
    @Transactional(readOnly = true)
    public Optional<MappingTemplate> detecterTemplate(final Long tenantId, final List<String> headers) {
        log.info("Détection template - tenantId: {}, headers: {}", tenantId, headers.size());
        final List<String> headersNormalises = headers.stream()
                .map(h -> h.trim().toLowerCase())
                .toList();

        final List<MappingTemplate> templates = mappingTemplateRepository
                .findByTenantIdOrderByDerniereUtilisationDesc(tenantId);

        for (final MappingTemplate template : templates) {
            final List<String> templateHeaders = template.getHeadersOriginaux().stream()
                    .map(h -> h.trim().toLowerCase())
                    .toList();
            if (templateHeaders.equals(headersNormalises)) {
                log.info("Template trouvé - id: {}, nom: {}", template.getId(), template.getNom());
                return Optional.of(template);
            }
        }

        // Template partiel : même headers + colonnes supplémentaires
        for (final MappingTemplate template : templates) {
            final List<String> templateHeaders = template.getHeadersOriginaux().stream()
                    .map(h -> h.trim().toLowerCase())
                    .toList();
            if (headersNormalises.containsAll(templateHeaders)) {
                log.info("Template partiel trouvé (colonnes en plus) - id: {}", template.getId());
                return Optional.of(template);
            }
        }

        log.info("Aucun template trouvé pour ces headers");
        return Optional.empty();
    }

    @Transactional
    public MappingTemplate sauvegarderTemplate(final Long tenantId, final String nom,
                                                final List<String> headers, final Map<String, String> mappings,
                                                final SplitProposition split, final String formatDate,
                                                final Long userId) {
        log.info("Sauvegarde template - tenantId: {}, nom: {}", tenantId, nom);
        final MappingTemplate template = new MappingTemplate();
        template.setTenantId(tenantId);
        template.setNom(nom);
        template.setHeadersOriginaux(headers);
        template.setMappings(mappings);
        if (split != null) {
            template.setOptionsSplit(Map.of(
                    "colonne", split.colonne(),
                    "type", split.type(),
                    "ordreNomPrenom", split.ordreDetecte()
            ));
        }
        template.setFormatDate(formatDate);
        template.setNombreUtilisations(1);
        template.setDerniereUtilisation(LocalDateTime.now());
        template.setCreePar(userId);
        return mappingTemplateRepository.save(template);
    }

    @Transactional
    public void mettreAJourUtilisation(final Long templateId) {
        mappingTemplateRepository.findById(templateId).ifPresent(t -> {
            t.setNombreUtilisations(t.getNombreUtilisations() + 1);
            t.setDerniereUtilisation(LocalDateTime.now());
            mappingTemplateRepository.save(t);
        });
    }

    @Transactional(readOnly = true)
    public List<MappingTemplate> listerTemplates(final Long tenantId) {
        return mappingTemplateRepository.findByTenantIdOrderByDerniereUtilisationDesc(tenantId);
    }

    @Transactional
    public MappingTemplate modifierTemplate(final Long id, final Long tenantId,
                                             final String nom, final Map<String, String> mappings) {
        log.info("Modification template - id: {}, tenantId: {}", id, tenantId);
        final MappingTemplate template = mappingTemplateRepository.findById(id)
                .filter(t -> t.getTenantId().equals(tenantId))
                .orElseThrow(() -> new RuntimeException("Template non trouvé"));
        if (nom != null && !nom.isBlank()) {
            template.setNom(nom);
        }
        if (mappings != null && !mappings.isEmpty()) {
            template.setMappings(mappings);
        }
        return mappingTemplateRepository.save(template);
    }

    @Transactional
    public void supprimerTemplate(final Long id, final Long tenantId) {
        mappingTemplateRepository.findById(id)
                .filter(t -> t.getTenantId().equals(tenantId))
                .ifPresent(mappingTemplateRepository::delete);
    }
}
