package fr.leadrevive.user.service;

import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.user.dto.ReqCreateUserDto;
import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserBuilder {

    private final UserMapper userMapper;

    public User buildFromRequest(final ReqCreateUserDto request, final Long tenantId) {
        log.info("Construction de l'utilisateur depuis la requete - email: {}", PiiMasker.email(request.getEmail()));
        // Note: avec Clerk, le mot de passe n'est plus gere cote backend.
        // Cette methode reste utile pour creer un stub user qui sera ensuite lie via clerk_user_id par le webhook.
        final User user = userMapper.toEntity(request);
        user.setTenantId(tenantId);
        user.setRole(RoleEnum.valueOf(request.getRole()));
        return user;
    }
}
