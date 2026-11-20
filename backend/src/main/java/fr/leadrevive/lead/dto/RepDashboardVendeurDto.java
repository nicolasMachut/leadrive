package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepDashboardVendeurDto {
    private Integer mesAppelsAujourdhui;
    private Integer mesRdvSemaine;
    private Integer aRappelerMaintenant;
    private Integer objectifMensuelRdv;
    private Integer mesRdvMois;
    private List<RepLeadChaudDto> leadsChauds;
    private List<RepLeadChaudDto> enAttenteRetour;
}
