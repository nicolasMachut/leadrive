package fr.leadrevive.fixtures;

import fr.leadrevive.lead.entity.SourceVenteEnum;
import fr.leadrevive.lead.entity.VenteConfirmee;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class VenteConfirmeeFixture {

    private static final EasyRandom GENERATOR = new EasyRandom(new EasyRandomParameters());

    private VenteConfirmeeFixture() {
    }

    public static VenteConfirmee vente() {
        final VenteConfirmee v = GENERATOR.nextObject(VenteConfirmee.class);
        v.setSource(SourceVenteEnum.WHATSAPP_IA);
        return v;
    }

    public static VenteConfirmee venteComplete(final Long leadId, final Long userId, final BigDecimal montant,
                                               final LocalDate dateVente, final SourceVenteEnum source,
                                               final String commentaire) {
        final VenteConfirmee v = vente();
        v.setLeadId(leadId);
        v.setUserId(userId);
        v.setMontant(montant);
        v.setDateVente(dateVente);
        v.setSource(source);
        v.setCommentaire(commentaire);
        return v;
    }
}
