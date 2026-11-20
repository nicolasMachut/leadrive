package fr.leadrevive.user.mapper;

import fr.leadrevive.user.dto.RepUserDto;
import fr.leadrevive.user.dto.ReqCreateUserDto;
import fr.leadrevive.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    @Mapping(source = "id", target = "idUtilisateur")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "nom", target = "nom")
    @Mapping(source = "prenom", target = "prenom")
    @Mapping(source = "role", target = "role")
    @Mapping(source = "actif", target = "actif")
    RepUserDto toDto(User entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "actif", constant = "true")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "nom", target = "nom")
    @Mapping(source = "prenom", target = "prenom")
    @Mapping(target = "role", ignore = true)
    User toEntity(ReqCreateUserDto dto);
}
