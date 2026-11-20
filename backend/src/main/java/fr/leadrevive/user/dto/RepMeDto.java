package fr.leadrevive.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepMeDto {
    private Long id;
    private String email;
    private String prenom;
    private String nom;
    private String role;
    private Long tenantId;
    private Boolean onboardingTermine;
    private Integer onboardingEtape;
}
