package fr.leadrevive.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepTenantDto {

    private Long idTenant;
    private String nom;
    private String adresse;
    private String telephone;
    private String emailContact;
    private String timezone;
    private Boolean actif;
}
