package fr.leadrevive.emailimport.service;

import fr.leadrevive.common.tenant.TenantContext;
import fr.leadrevive.emailimport.dto.RepInboundEmailLogDto;
import fr.leadrevive.emailimport.dto.RepSourcesStatsDto;
import fr.leadrevive.emailimport.dto.RepTenantEmailConfigDto;
import fr.leadrevive.emailimport.entity.InboundEmailLog;
import fr.leadrevive.emailimport.entity.StatutInboundEmail;
import fr.leadrevive.emailimport.entity.TenantEmailConfig;
import fr.leadrevive.emailimport.repository.InboundEmailLogRepository;
import fr.leadrevive.emailimport.repository.TenantEmailConfigRepository;
import fr.leadrevive.lead.repository.LeadRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service principal pour les operations email import cote manager + admin.
 *
 * <p>Cote manager : recuperation de la config + stats + logs filtres au tenant courant
 * (filtre Hibernate {@code tenantFilter} actif).</p>
 *
 * <p>Cote admin : memes operations en mode cross-tenant (filtre Hibernate desactive),
 * + listing global, + creation retroactive (deleguee a {@code AdminService}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailImportService {

    private static final int PERIODE_STATS_JOURS = 30;

    private final TenantEmailConfigRepository tenantEmailConfigRepository;
    private final InboundEmailLogRepository inboundEmailLogRepository;
    private final LeadRepository leadRepository;
    private final EntityManager entityManager;

    /**
     * Recupere la config email du tenant courant (manager).
     * Retourne {@code null} si le tenant n'a pas (encore) d'adresse email
     * — typiquement les concessions creees avant l'epic 24.
     */
    @Transactional(readOnly = true)
    public RepTenantEmailConfigDto getConfigPourTenantCourant() {
        final Long tenantId = TenantContext.getCurrentTenantId();
        log.info("Recuperation config email - tenantId: {}", tenantId);
        return tenantEmailConfigRepository.findByTenantId(tenantId)
                .map(this::versDTO)
                .orElse(null);
    }

    /**
     * Recupere la config email d'un tenant (admin, cross-tenant).
     */
    @Transactional(readOnly = true)
    public RepTenantEmailConfigDto getConfigAdmin(final Long tenantId) {
        log.info("Recuperation config email admin - tenantId: {}", tenantId);
        return executerCrossTenant(() -> tenantEmailConfigRepository.findByTenantId(tenantId)
                .map(this::versDTO)
                .orElse(null));
    }

    /**
     * Liste paginee des emails recus par le tenant courant (manager).
     */
    @Transactional(readOnly = true)
    public Page<RepInboundEmailLogDto> listerLogsManager(final Pageable pageable) {
        final Long tenantId = TenantContext.getCurrentTenantId();
        log.info("Liste logs email manager - tenantId: {}", tenantId);
        return inboundEmailLogRepository.findByTenantIdOrderByCreeLeDesc(tenantId, pageable)
                .map(this::versDtoListe);
    }

    /**
     * Liste paginee admin avec filtres optionnels (cross-tenant).
     */
    @Transactional(readOnly = true)
    public Page<RepInboundEmailLogDto> listerLogsAdmin(final Long tenantId,
                                                        final StatutInboundEmail statut,
                                                        final Pageable pageable) {
        log.info("Liste logs email admin - tenantIdFiltre: {}, statut: {}", tenantId, statut);
        return executerCrossTenant(() ->
                inboundEmailLogRepository.findAllAdmin(tenantId, statut, pageable)
                        .map(this::versDtoListe));
    }

    /** Detail d'un email (admin, cross-tenant). */
    @Transactional(readOnly = true)
    public Optional<RepInboundEmailLogDto> getLogAdmin(final Long id) {
        log.info("Detail log email admin - id: {}", id);
        return executerCrossTenant(() -> inboundEmailLogRepository.findById(id).map(this::versDtoDetail));
    }

    /**
     * Stats par source de lead sur la periode (manager, tenant courant).
     */
    @Transactional(readOnly = true)
    public RepSourcesStatsDto statsSourcesManager(final int periodeJours) {
        final Long tenantId = TenantContext.getCurrentTenantId();
        log.info("Stats sources manager - tenantId: {}, periode: {}", tenantId, periodeJours);
        final LocalDateTime depuis = LocalDateTime.now().minusDays(periodeJours);
        return calculerStats(periodeJours, leadRepository.statsParSourceParTenant(tenantId, depuis));
    }

    /**
     * Stats par source de lead (admin, cross-tenant, filtre optionnel par tenant).
     */
    @Transactional(readOnly = true)
    public RepSourcesStatsDto statsSourcesAdmin(final int periodeJours, final Long tenantId) {
        log.info("Stats sources admin - tenantIdFiltre: {}, periode: {}", tenantId, periodeJours);
        final LocalDateTime depuis = LocalDateTime.now().minusDays(periodeJours);
        return executerCrossTenant(() ->
                calculerStats(periodeJours, leadRepository.statsParSourceGlobal(tenantId, depuis)));
    }

    private RepSourcesStatsDto calculerStats(final int periodeJours, final List<Object[]> rows) {
        long total = 0L;
        final java.util.List<RepSourcesStatsDto.SourceStatDto> sources = new java.util.ArrayList<>();
        for (final Object[] row : rows) {
            final String source = (String) row[0];
            final long nbLeads = ((Number) row[1]).longValue();
            final long nbRdv = ((Number) row[2]).longValue();
            final double taux = nbLeads > 0
                    ? Math.round((double) nbRdv / nbLeads * 1000.0) / 10.0
                    : 0.0;
            sources.add(RepSourcesStatsDto.SourceStatDto.builder()
                    .source(source)
                    .nbLeads(nbLeads)
                    .nbRdv(nbRdv)
                    .tauxConversion(taux)
                    .build());
            total += nbLeads;
        }
        return RepSourcesStatsDto.builder()
                .periodeJours(periodeJours)
                .totalLeads(total)
                .sources(sources)
                .build();
    }

    private RepTenantEmailConfigDto versDTO(final TenantEmailConfig config) {
        final LocalDateTime depuis = LocalDateTime.now().minusDays(PERIODE_STATS_JOURS);
        final List<Object[]> statsParStatut = inboundEmailLogRepository.countByStatutSince(
                config.getTenantId(), depuis);

        long total = 0L;
        long nbCrees = 0L;
        long nbDedup = 0L;
        long nbIncomplets = 0L;
        long nbEchec = 0L;
        long nbSpam = 0L;
        for (final Object[] row : statsParStatut) {
            final StatutInboundEmail statut = (StatutInboundEmail) row[0];
            final long count = ((Number) row[1]).longValue();
            total += count;
            switch (statut) {
                case LEAD_CREE -> nbCrees += count;
                case LEAD_DEDUPLIQUE -> nbDedup += count;
                case LEAD_INCOMPLET -> nbIncomplets += count;
                case PARSE_ECHEC -> nbEchec += count;
                case REJETE_SPAM -> nbSpam += count;
                default -> { /* ignored */ }
            }
        }
        final long parsingOk = nbCrees + nbDedup + nbIncomplets;
        final double taux = total > 0
                ? Math.round((double) parsingOk / total * 1000.0) / 10.0
                : 0.0;

        final java.util.List<RepTenantEmailConfigDto.AncienneAdresseDto> historique =
                config.getAnciennesAdresses() != null
                        ? config.getAnciennesAdresses().stream()
                                .map(a -> RepTenantEmailConfigDto.AncienneAdresseDto.builder()
                                        .adresse(a.adresse())
                                        .remplaceeLe(a.remplaceeLe())
                                        .build())
                                .toList()
                        : java.util.List.of();

        return RepTenantEmailConfigDto.builder()
                .id(config.getId())
                .tenantId(config.getTenantId())
                .emailAddress(config.getEmailAddress())
                .statut(config.getStatut())
                .nbEmailsRecus(config.getNbEmailsRecus())
                .dernierEmailRecuLe(config.getDernierEmailRecuLe())
                .creeLe(config.getCreeLe())
                .anciennesAdresses(historique)
                .stats(RepTenantEmailConfigDto.StatsPeriodeDto.builder()
                        .periodeJours(PERIODE_STATS_JOURS)
                        .nbEmailsRecus(total)
                        .nbLeadsCrees(nbCrees + nbIncomplets)
                        .nbLeadsDedupliques(nbDedup)
                        .nbParseEchec(nbEchec)
                        .nbRejetSpam(nbSpam)
                        .tauxParsingOk(taux)
                        .build())
                .build();
    }

    private RepInboundEmailLogDto versDtoListe(final InboundEmailLog log) {
        return RepInboundEmailLogDto.builder()
                .id(log.getId())
                .tenantId(log.getTenantId())
                .emailFrom(log.getEmailFrom())
                .emailSubject(log.getEmailSubject())
                .statut(log.getStatut())
                .leadId(log.getLeadId())
                .sourceDetectee(log.getSourceDetectee())
                .dureeParsingMs(log.getDureeParsingMs())
                .raisonRejet(log.getRaisonRejet())
                .creeLe(log.getCreeLe())
                .build();
    }

    private RepInboundEmailLogDto versDtoDetail(final InboundEmailLog log) {
        return RepInboundEmailLogDto.builder()
                .id(log.getId())
                .tenantId(log.getTenantId())
                .emailFrom(log.getEmailFrom())
                .emailSubject(log.getEmailSubject())
                .statut(log.getStatut())
                .leadId(log.getLeadId())
                .sourceDetectee(log.getSourceDetectee())
                .dureeParsingMs(log.getDureeParsingMs())
                .raisonRejet(log.getRaisonRejet())
                .creeLe(log.getCreeLe())
                .emailBodyRaw(log.getEmailBodyRaw())
                .resultatParsing(log.getResultatParsing())
                .build();
    }

    /**
     * Execute un bloc en mode cross-tenant (filtre Hibernate desactive).
     * Restaure le filtre au tenant courant en fin de bloc.
     */
    private <T> T executerCrossTenant(final java.util.function.Supplier<T> bloc) {
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            return bloc.get();
        } finally {
            final Long tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            }
        }
    }
}
