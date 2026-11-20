package fr.leadrevive.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReqAideChatDto {

    @NotBlank
    private String question;

    /** Route Next.js courante (ex: "/leads/123") pour donner du contexte au LLM. Optionnel. */
    private String routeCourante;

    /** Historique des messages precedents de la conversation d'aide (max 10), pour le contexte. Optionnel. */
    private List<MessageHistorique> historique;

    /** "aide" (defaut) ou "simulation". Si null/vide, mode aide par defaut. */
    private String mode;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageHistorique {
        /** "user" ou "assistant" */
        private String role;
        private String contenu;
    }
}
