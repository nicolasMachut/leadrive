package fr.leadrevive.emailimport.dto;

import fr.leadrevive.emailimport.entity.StatutInboundEmail;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Reponse pour la liste des emails entrants (admin + manager).
 *
 * <p>Le body complet et le resultatParsing brut ne sont retournes que dans
 * le detail (endpoint singulier), pas dans la liste paginee, pour eviter
 * de saturer les reponses.</p>
 */
@Getter
@Builder
public class RepInboundEmailLogDto {

    private final Long id;
    private final Long tenantId;
    private final String emailFrom;
    private final String emailSubject;
    private final StatutInboundEmail statut;
    private final Long leadId;
    private final String sourceDetectee;
    private final Integer dureeParsingMs;
    private final String raisonRejet;
    private final LocalDateTime creeLe;
    /** Renseigne uniquement dans le detail. */
    private final String emailBodyRaw;
    /** Renseigne uniquement dans le detail. */
    private final String resultatParsing;
}
