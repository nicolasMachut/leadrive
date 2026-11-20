package fr.leadrevive.lead.mapper;

import fr.leadrevive.lead.dto.RepLeadDto;
import fr.leadrevive.lead.entity.Lead;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface LeadMapper {

    @Mapping(source = "id", target = "idLead")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "nom", target = "nom")
    @Mapping(source = "telephone", target = "telephone")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "marque", target = "marque")
    @Mapping(source = "modele", target = "modele")
    @Mapping(source = "source", target = "source")
    @Mapping(source = "territoire", target = "territoire")
    @Mapping(source = "etape", target = "etape")
    @Mapping(source = "scoring", target = "scoring")
    @Mapping(source = "hotnessScore", target = "hotnessScore")
    @Mapping(source = "hotnessCalculeLe", target = "hotnessCalculeLe")
    @Mapping(source = "devis", target = "devis")
    @Mapping(source = "reprise", target = "reprise")
    @Mapping(source = "typeOpportunite", target = "typeOpportunite")
    @Mapping(source = "dateModificationDevis", target = "dateModificationDevis")
    @Mapping(source = "statut", target = "statut")
    @Mapping(source = "vendeurId", target = "vendeurId")
    @Mapping(source = "importId", target = "importId")
    @Mapping(source = "creeLe", target = "creeLe")
    @Mapping(source = "donneesSupplementaires", target = "donneesSupplementaires")
    @Mapping(source = "modeImport", target = "modeImport")
    @Mapping(source = "archive", target = "archive")
    @Mapping(source = "premierMessageAutoPrevuLe", target = "premierMessageAutoPrevuLe")
    // Champs derives calcules cote service (LeadService) apres mapping, via
    // PremierMessageAutoFenetre + config du tenant : pas de source dans l'entity.
    @Mapping(target = "premierMessageAutoEffectifLe", ignore = true)
    @Mapping(target = "premierMessageAutoHorsPlage", ignore = true)
    RepLeadDto toDto(Lead entity);
}
