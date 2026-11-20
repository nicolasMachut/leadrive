package fr.leadrevive.csvimport.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqMappingDto {

    @NotEmpty(message = "Le mapping des colonnes est obligatoire")
    private Map<String, String> mapping;
}
