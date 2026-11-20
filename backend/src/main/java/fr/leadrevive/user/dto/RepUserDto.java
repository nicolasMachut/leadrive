package fr.leadrevive.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepUserDto {

    private Long idUtilisateur;
    private Long tenantId;
    private String email;
    private String nom;
    private String prenom;
    private String role;
    private Boolean actif;
}
