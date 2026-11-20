package fr.leadrevive.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepImpactFeedbackDto {

    private Long feedbackId;
    private Long leadId;
    private String nomProspect;
}
