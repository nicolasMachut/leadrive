package fr.leadrevive.tenant.mapper;

import fr.leadrevive.tenant.dto.RepTenantDto;
import fr.leadrevive.tenant.dto.ReqCreateTenantDto;
import fr.leadrevive.tenant.entity.Tenant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface TenantMapper {

    @Mapping(source = "id", target = "idTenant")
    @Mapping(source = "nom", target = "nom")
    @Mapping(source = "adresse", target = "adresse")
    @Mapping(source = "telephone", target = "telephone")
    @Mapping(source = "emailContact", target = "emailContact")
    @Mapping(source = "timezone", target = "timezone")
    @Mapping(source = "actif", target = "actif")
    RepTenantDto toDto(Tenant entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "timezone", constant = "Europe/Paris")
    @Mapping(target = "actif", constant = "true")
    @Mapping(source = "nom", target = "nom")
    @Mapping(source = "adresse", target = "adresse")
    @Mapping(source = "telephone", target = "telephone")
    @Mapping(source = "emailContact", target = "emailContact")
    Tenant toEntity(ReqCreateTenantDto dto);
}
