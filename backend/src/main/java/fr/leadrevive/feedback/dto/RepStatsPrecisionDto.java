package fr.leadrevive.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepStatsPrecisionDto {

    private int totalFeedbacks;
    private boolean precisionVisible;
    private int precisionIa;
    private String palierActuel;
    private String palierDescription;
    private String palierSuivant;
    private int feedbacksRestantsPalier;
}
