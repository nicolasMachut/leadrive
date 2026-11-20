package fr.leadrevive.lead.service;

import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.lead.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final LeadRepository leadRepository;

    public boolean estDoublon(final Long tenantId, final String email, final String telephone) {
        log.info("Verification doublon - tenantId: {}, email: {}, telephone: {}",
                tenantId, PiiMasker.email(email), PiiMasker.phone(telephone));

        // Variantes ...AndArchiveFalse : on ne considere comme doublon qu'un lead ACTIF.
        // Un contact archive (invisible dans les listes) ne doit pas provoquer un 409
        // contre un lead qu'on ne peut pas voir (coherent avec la dedup email import).
        if (email != null && !email.isBlank()) {
            final boolean doublonEmail = leadRepository.findByTenantIdAndEmailAndArchiveFalse(tenantId, email).isPresent();
            if (doublonEmail) {
                log.info("Doublon detecte par email - email: {}", PiiMasker.email(email));
                return true;
            }
        }

        if (telephone != null && !telephone.isBlank()) {
            final boolean doublonTelephone = leadRepository.findByTenantIdAndTelephoneAndArchiveFalse(tenantId, telephone).isPresent();
            if (doublonTelephone) {
                log.info("Doublon detecte par telephone - telephone: {}", PiiMasker.phone(telephone));
                return true;
            }
        }

        return false;
    }
}
