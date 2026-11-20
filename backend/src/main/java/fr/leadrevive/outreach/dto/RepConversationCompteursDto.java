package fr.leadrevive.outreach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepConversationCompteursDto {
    private Integer enCours;
    private Integer rdvObtenus;
    private Integer nonLues;
    private Integer modeHumain;
}
