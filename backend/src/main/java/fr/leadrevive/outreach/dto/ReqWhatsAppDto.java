package fr.leadrevive.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReqWhatsAppDto {

    @NotBlank(message = "Le numero destinataire est obligatoire")
    private String numeroDestinataire;
}
