package fr.leadrevive.csvimport.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.csvimport.dto.ImportAnalyseRep;
import fr.leadrevive.csvimport.dto.ImportMappingRep;
import fr.leadrevive.csvimport.dto.MappingValidationReq;
import fr.leadrevive.csvimport.dto.RepCsvPreviewDto;
import fr.leadrevive.csvimport.dto.RepImportResultDto;
import fr.leadrevive.csvimport.dto.RepLeadImportDto;
import fr.leadrevive.csvimport.dto.ReqPasteImportDto;
import fr.leadrevive.csvimport.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final CsvImportService csvImportService;
    private final AuthService authService;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ImportAnalyseRep> upload(@RequestParam("file") final MultipartFile fichier) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        final ImportAnalyseRep result = csvImportService.upload(fichier, tenantId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/paste")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<ImportAnalyseRep> paste(@RequestBody final ReqPasteImportDto req) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        final ImportAnalyseRep result = csvImportService.paste(req.contenu(), tenantId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{id}/mapping")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ImportMappingRep analyserMapping(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return csvImportService.analyserMapping(id, tenantId);
    }

    @PostMapping("/{id}/mapping/validate")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public RepCsvPreviewDto validerMapping(@PathVariable("id") final Long id,
                                            @RequestBody final MappingValidationReq req) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        return csvImportService.validerMapping(id, tenantId, userId, req);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public RepCsvPreviewDto preview(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return csvImportService.preview(id, tenantId);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public RepImportResultDto validate(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return csvImportService.validate(id, tenantId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR', 'SUPER_ADMIN')")
    public List<RepLeadImportDto> getImports() {
        final Long tenantId = authService.getCurrentTenantId();
        return csvImportService.getImports(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR', 'SUPER_ADMIN')")
    public RepLeadImportDto getImport(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return csvImportService.getImport(id, tenantId);
    }
}
