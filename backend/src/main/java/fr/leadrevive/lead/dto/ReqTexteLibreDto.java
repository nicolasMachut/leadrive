package fr.leadrevive.lead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReqTexteLibreDto {

    @NotBlank(message = "Le texte ne peut pas être vide")
    @Size(min = 20, message = "Le texte doit contenir au moins 20 caractères")
    private String texte;
}
