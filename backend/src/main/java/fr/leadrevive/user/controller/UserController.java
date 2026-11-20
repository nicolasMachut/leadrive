package fr.leadrevive.user.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.user.dto.RepUserDto;
import fr.leadrevive.user.dto.ReqCreateUserDto;
import fr.leadrevive.user.dto.ReqUpdateUserDto;
import fr.leadrevive.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepUserDto getUser(@PathVariable("id") final Long id) {
        final Long currentTenantId = authService.getCurrentTenantId();
        // Un VENDEUR ne peut lire QUE son propre profil (pas celui de ses collègues/managers).
        if ("VENDEUR".equals(authService.getCurrentRole()) && !id.equals(authService.getCurrentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
        final RepUserDto user = userService.getUser(id);
        // 403 (et pas RuntimeException → 500) si l'utilisateur n'est pas dans le tenant courant.
        if (currentTenantId == null || !currentTenantId.equals(user.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
        return user;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public List<RepUserDto> getUsersByTenant() {
        final Long tenantId = authService.getCurrentTenantId();
        return userService.getUsersByTenant(tenantId);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<RepUserDto> createUser(@RequestBody @Valid final ReqCreateUserDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final RepUserDto created = userService.createUser(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public RepUserDto updateUser(@PathVariable("id") final Long id,
                                  @RequestBody @Valid final ReqUpdateUserDto request) {
        return userService.updateUser(id, request);
    }
}
