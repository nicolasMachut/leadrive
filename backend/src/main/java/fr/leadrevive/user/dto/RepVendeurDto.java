package fr.leadrevive.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepVendeurDto {

    private Long id;
    private String email;
    private String nom;
    private String prenom;
    private String role;
    private String statut;
    private Boolean actif;
    private LocalDateTime derniereConnexion;
}
