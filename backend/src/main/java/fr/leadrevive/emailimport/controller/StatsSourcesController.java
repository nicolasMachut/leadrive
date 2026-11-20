package fr.leadrevive.emailimport.controller;

import fr.leadrevive.emailimport.dto.RepSourcesStatsDto;
import fr.leadrevive.emailimport.service.EmailImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint stats sources de leads pour le manager (tenant courant).
 *
 * <p>Aggrege tous les leads (toutes origines : CSV, texte libre, email) par
 * source detectee, pour aider le manager a identifier ses canaux d'acquisition
 * les plus rentables.</p>
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
public class StatsSourcesController {

    private final EmailImportService emailImportService;

    @GetMapping("/sources")
    public RepSourcesStatsDto statsSources(@RequestParam(defaultValue = "30") final int periode) {
        return emailImportService.statsSourcesManager(periode);
    }
}
