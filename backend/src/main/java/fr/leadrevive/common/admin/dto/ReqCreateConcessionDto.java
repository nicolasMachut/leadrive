package fr.leadrevive.common.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Saisie de creation d'un etablissement par le SUPER_ADMIN.
 *
 * <p>L'onboarding WhatsApp (numero, code OTP, profil) est ensuite a la charge
 * du manager depuis "Mon etablissement". L'admin n'a pas a pre-saisir de
 * numero, ce qui evite la friction et garantit que c'est bien le manager qui
 * choisit + valide son numero.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReqCreateConcessionDto {

    @NotBlank
    private String nomConcession;
}
