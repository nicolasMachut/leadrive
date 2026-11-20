package fr.leadrevive.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepFeedbackRdvDto {

    private Long feedbackId;
    private int totalFeedbacks;
    private int precisionIa;
    private boolean precisionVisible;
    private String palierActuel;
    private String palierSuivant;
    private int feedbacksRestantsPalier;
    private boolean nouveauPalier;
}
