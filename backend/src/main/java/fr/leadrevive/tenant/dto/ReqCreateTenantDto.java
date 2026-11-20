package fr.leadrevive.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqCreateTenantDto {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private String adresse;

    private String telephone;

    @Email(message = "L'email de contact doit etre valide")
    private String emailContact;
}
