package fr.leadrevive.csvimport.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.csvimport.dto.RepMappingTemplateDto;
import fr.leadrevive.csvimport.dto.ReqUpdateTemplateDto;
import fr.leadrevive.csvimport.entity.MappingTemplate;
import fr.leadrevive.csvimport.service.MappingTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mapping-templates")
@RequiredArgsConstructor
public class MappingTemplateController {

    private final MappingTemplateService mappingTemplateService;
    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public List<RepMappingTemplateDto> lister() {
        final Long tenantId = authService.getCurrentTenantId();
        return mappingTemplateService.listerTemplates(tenantId).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public RepMappingTemplateDto getById(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        final MappingTemplate template = mappingTemplateService.listerTemplates(tenantId).stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Template non trouvé"));
        return toDto(template);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public RepMappingTemplateDto modifier(@PathVariable("id") final Long id,
                                           @RequestBody final ReqUpdateTemplateDto req) {
        final Long tenantId = authService.getCurrentTenantId();
        final MappingTemplate updated = mappingTemplateService.modifierTemplate(id, tenantId, req.nom(), req.mappings());
        return toDto(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Void> supprimer(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        mappingTemplateService.supprimerTemplate(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    private RepMappingTemplateDto toDto(final MappingTemplate template) {
        return RepMappingTemplateDto.builder()
                .id(template.getId())
                .nom(template.getNom())
                .headersOriginaux(template.getHeadersOriginaux())
                .mappings(template.getMappings())
                .optionsSplit(template.getOptionsSplit())
                .formatDate(template.getFormatDate())
                .nombreUtilisations(template.getNombreUtilisations())
                .derniereUtilisation(template.getDerniereUtilisation())
                .build();
    }
}
