package fr.leadrevive.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReqMessageDto {
    @NotBlank(message = "Le contenu du message est obligatoire")
    private String contenu;
}
