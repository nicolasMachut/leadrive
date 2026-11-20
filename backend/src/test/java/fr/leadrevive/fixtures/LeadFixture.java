package fr.leadrevive.fixtures;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

import java.math.BigDecimal;

public final class LeadFixture {

    private static final EasyRandomParameters PARAMETERS = new EasyRandomParameters()
            .excludeField(field -> field.getName().equals("rawData"));

    private static final EasyRandom GENERATOR = new EasyRandom(PARAMETERS);

    private LeadFixture() {
    }

    public static Lead lead() {
        final Lead lead = GENERATOR.nextObject(Lead.class);
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        lead.setRawData(null);
        return lead;
    }

    public static Lead leadAvecScoring(final BigDecimal scoring) {
        final Lead lead = lead();
        lead.setScoring(scoring);
        lead.setHotnessScore(null);
        lead.setHotnessCalculeLe(null);
        return lead;
    }

    public static Lead leadAvecTenant(final Long tenantId) {
        final Lead lead = lead();
        lead.setTenantId(tenantId);
        return lead;
    }
}
