package fr.leadrevive.ai.service;

import fr.leadrevive.ai.dto.RepTenantAiConfigDto;
import fr.leadrevive.ai.dto.ReqTenantAiConfigDto;
import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.repository.TenantAiConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAiConfigService {

    private final TenantAiConfigRepository tenantAiConfigRepository;

    @Cacheable(value = "tenantAiConfig", key = "#tenantId")
    @Transactional(readOnly = true)
    public TenantAiConfig getConfig(final Long tenantId) {
        log.info("Recuperation config IA - tenantId: {}", tenantId);
        return tenantAiConfigRepository.findByTenantId(tenantId).orElse(null);
    }

    @Transactional(readOnly = true)
    public RepTenantAiConfigDto getConfigDto(final Long tenantId) {
        final TenantAiConfig config = getConfig(tenantId);
        if (config == null) {
            return null;
        }
        return toDto(config);
    }

    @CacheEvict(value = "tenantAiConfig", key = "#tenantId")
    @Transactional
    public RepTenantAiConfigDto saveConfig(final Long tenantId, final ReqTenantAiConfigDto request) {
        log.info("Sauvegarde config IA - tenantId: {}", tenantId);
        final TenantAiConfig config = tenantAiConfigRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    final TenantAiConfig nouveau = new TenantAiConfig();
                    nouveau.setTenantId(tenantId);
                    return nouveau;
                });

        config.setNomConcession(request.getNomConcession());
        config.setHorairesOuverture(request.getHorairesOuverture());
        config.setAdresseComplete(request.getAdresseComplete());
        config.setTelephoneConcession(request.getTelephoneConcession());
        config.setSiteWeb(request.getSiteWeb());
        config.setEmailContact(request.getEmailContact());

        // Premier message automatique (leads import email). Les champs numeriques
        // gardent leur valeur BDD (ou le defaut) si le client ne les envoie pas.
        if (request.getPremierMessageAutoActif() != null) {
            config.setPremierMessageAutoActif(request.getPremierMessageAutoActif());
        }
        if (request.getPremierMessageAutoDelaiMinutes() != null) {
            config.setPremierMessageAutoDelaiMinutes(request.getPremierMessageAutoDelaiMinutes());
        }
        if (request.getPremierMessageAutoFenetreDebutHeure() != null) {
            config.setPremierMessageAutoFenetreDebutHeure(request.getPremierMessageAutoFenetreDebutHeure());
        }
        if (request.getPremierMessageAutoFenetreFinHeure() != null) {
            config.setPremierMessageAutoFenetreFinHeure(request.getPremierMessageAutoFenetreFinHeure());
        }
        // Coherence de la plage : debut strictement inferieur a fin. Exception metier ->
        // GlobalExceptionHandler renvoie un 400 avec ce message clair.
        if (config.getPremierMessageAutoFenetreDebutHeure() >= config.getPremierMessageAutoFenetreFinHeure()) {
            throw new IllegalArgumentException(
                    "L'heure de debut de la plage d'envoi doit etre anterieure a l'heure de fin.");
        }

        final TenantAiConfig saved = tenantAiConfigRepository.save(config);
        log.info("Config IA sauvegardee - tenantId: {}", tenantId);

        // Re-push du profil WhatsApp si la concession a une config ACTIF et que
        // les champs impactant le profil Meta ont change (nomConcession, horaires, adresse).
        // Post-sunset LEGACY : le profil WhatsApp Business (display name, logo, description)
        // est gere directement par Meta via le flow Embedded Signup. Pas de re-push
        // cote Twilio Senders API — le manager gere son profil Meta Business.

        return toDto(saved);
    }

    private RepTenantAiConfigDto toDto(final TenantAiConfig config) {
        return RepTenantAiConfigDto.builder()
                .id(config.getId())
                .nomConcession(config.getNomConcession())
                .horairesOuverture(config.getHorairesOuverture())
                .adresseComplete(config.getAdresseComplete())
                .telephoneConcession(config.getTelephoneConcession())
                .siteWeb(config.getSiteWeb())
                .emailContact(config.getEmailContact())
                .premierMessageAutoActif(config.getPremierMessageAutoActif())
                .premierMessageAutoDelaiMinutes(config.getPremierMessageAutoDelaiMinutes())
                .premierMessageAutoFenetreDebutHeure(config.getPremierMessageAutoFenetreDebutHeure())
                .premierMessageAutoFenetreFinHeure(config.getPremierMessageAutoFenetreFinHeure())
                .build();
    }
}
