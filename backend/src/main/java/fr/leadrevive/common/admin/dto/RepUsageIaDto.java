package fr.leadrevive.common.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepUsageIaDto {

    private Long concessionId;
    private String nomConcession;
    private long nbAppels;
    private BigDecimal coutEstime;
    private LocalDateTime lastAppel;
}
