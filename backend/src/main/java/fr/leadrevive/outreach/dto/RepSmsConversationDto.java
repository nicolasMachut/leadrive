package fr.leadrevive.outreach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepSmsConversationDto {

    private Long id;
    private String conversationId;
    private String statut;
    private String numeroProspect;
    private Integer nombreMessages;
    private LocalDateTime dernierMessageLe;
    private LocalDateTime creeLe;
    private List<RepSmsMessageDto> messages;
}
