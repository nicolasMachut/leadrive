package fr.leadrevive.lead.exception;

import lombok.Getter;

@Getter
public class LeadNotFoundException extends RuntimeException {

    private final Long leadId;

    public LeadNotFoundException(final Long leadId) {
        super("Lead non trouve - id: " + leadId);
        this.leadId = leadId;
    }
}
