package fr.leadrevive.emailimport.service;

import fr.leadrevive.emailimport.config.EmailImportConfig;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service de deduplication des leads importes par email.
 *
 * <p>Strategie :
 * <ol>
 *   <li>Si telephone present, cherche par telephone (matche le mieux car normalise +33)</li>
 *   <li>Sinon, fallback sur email</li>
 * </ol>
 * Les criteres ne sont JAMAIS combines : un meme prospect peut soumettre 2 fois
 * avec le meme telephone et 2 emails differents (un sur LBC, un sur LaCentrale)
 * -&gt; on dedupe par telephone, on enrichit avec les nouvelles infos.</p>
 *
 * <p>La fenetre de deduplication est configurable via {@link EmailImportConfig}
 * (defaut 48h). Au-dela, un nouveau lead est cree (le prospect a probablement
 * relance manuellement, c'est une opportunite distincte).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeduplicationService {

    private final LeadRepository leadRepository;
    private final EmailImportConfig emailImportConfig;

    /**
     * Cherche un lead existant pour deduplication.
     *
     * @param tenantId tenant cible
     * @param telephone telephone normalise du prospect, ou null
     * @param email email du prospect, ou null
     * @return Optional du lead existant si match, vide si nouveau prospect
     */
    public Optional<Lead> chercherDoublon(final Long tenantId,
                                           final String telephone,
                                           final String email) {
        final LocalDateTime depuis = LocalDateTime.now()
                .minusHours(emailImportConfig.getDeduplicationFenetreHeures());

        if (telephone != null && !telephone.isBlank()) {
            final List<Lead> matches = leadRepository.findByTenantIdAndTelephoneAndCreeLeSince(
                    tenantId, telephone, depuis);
            if (!matches.isEmpty()) {
                final Lead lead = matches.get(0);
                log.info("Doublon detecte par telephone - tenantId: {}, leadId: {}", tenantId, lead.getId());
                return Optional.of(lead);
            }
        }

        if (email != null && !email.isBlank()) {
            final List<Lead> matches = leadRepository.findByTenantIdAndEmailAndCreeLeSince(
                    tenantId, email, depuis);
            if (!matches.isEmpty()) {
                final Lead lead = matches.get(0);
                log.info("Doublon detecte par email - tenantId: {}, leadId: {}", tenantId, lead.getId());
                return Optional.of(lead);
            }
        }

        return Optional.empty();
    }
}
