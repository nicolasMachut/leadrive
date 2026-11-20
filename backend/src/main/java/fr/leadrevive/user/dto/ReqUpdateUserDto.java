package fr.leadrevive.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqUpdateUserDto {

    @Email(message = "L'email doit etre valide")
    private String email;

    private String nom;
    private String prenom;

    @Pattern(regexp = "MANAGER|VENDEUR", message = "Le role doit etre MANAGER ou VENDEUR")
    private String role;
}
