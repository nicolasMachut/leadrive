package fr.leadrevive.common.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepConcessionSimpleDto {
    private Long id;
    private String nom;
    private LocalDateTime creeLe;

    // Aperçu WhatsApp pour le tableau admin (null si pas encore configure)
    private String whatsappStatut;
    private String whatsappFrom;
}
