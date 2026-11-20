package fr.leadrevive.csvimport.mapper;

import fr.leadrevive.csvimport.dto.RepLeadImportDto;
import fr.leadrevive.csvimport.entity.LeadImport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface LeadImportMapper {

    @Mapping(source = "id", target = "idImport")
    @Mapping(source = "nomFichier", target = "nomFichier")
    @Mapping(source = "dateImport", target = "dateImport")
    @Mapping(source = "nombreLignes", target = "nombreLignes")
    @Mapping(source = "nombreImportees", target = "nombreImportees")
    @Mapping(source = "nombreErreurs", target = "nombreErreurs")
    @Mapping(source = "nombreDoublons", target = "nombreDoublons")
    @Mapping(source = "statut", target = "statut")
    @Mapping(source = "mappingColonnes", target = "mappingColonnes")
    @Mapping(source = "erreurs", target = "erreurs")
    @Mapping(source = "importePar", target = "importePar")
    @Mapping(source = "typeFichier", target = "typeFichier")
    RepLeadImportDto toDto(LeadImport entity);
}
