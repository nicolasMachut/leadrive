package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepHistoriqueStatutDto {

    private String statutPrecedent;
    private String statutSuivant;
    private String source;
    private String commentaire;
    private LocalDateTime date;
}
