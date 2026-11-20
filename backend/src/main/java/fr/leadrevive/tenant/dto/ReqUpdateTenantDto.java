package fr.leadrevive.tenant.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqUpdateTenantDto {

    private String nom;
    private String adresse;
    private String telephone;

    @Email(message = "L'email de contact doit etre valide")
    private String emailContact;

    private String timezone;
}
