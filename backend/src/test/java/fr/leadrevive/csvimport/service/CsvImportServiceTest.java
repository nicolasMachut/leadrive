package fr.leadrevive.csvimport.service;

import tools.jackson.databind.json.JsonMapper;
import fr.leadrevive.csvimport.dto.ImportAnalyseRep;
import fr.leadrevive.csvimport.dto.RepImportResultDto;
import fr.leadrevive.csvimport.entity.LeadImport;
import fr.leadrevive.csvimport.mapper.LeadImportMapper;
import fr.leadrevive.csvimport.repository.LeadImportRepository;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.lead.service.DeduplicationService;
import fr.leadrevive.lead.service.LeadValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock
    private LeadImportRepository leadImportRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private DeduplicationService deduplicationService;

    @Mock
    private LeadImportMapper leadImportMapper;

    @Mock
    private FileParserFactory fileParserFactory;

    @Mock
    private MappingIAService mappingIAService;

    @Mock
    private MappingTemplateService mappingTemplateService;

    @Mock
    private fr.leadrevive.common.realtime.SseConnectionService sseConnectionService;

    private CsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        csvImportService = new CsvImportService(
                leadImportRepository,
                leadRepository,
                new LeadValidationService(),
                deduplicationService,
                leadImportMapper,
                JsonMapper.builder().build(),
                fileParserFactory,
                mappingIAService,
                mappingTemplateService,
                sseConnectionService
        );
    }

    @Test
    void upload_csvValide_retourneHeadersEtEchantillon() throws Exception {
        final String csv = """
                Nom du compte,Téléphone mobile,Contact : Adresse e-mail,Marque,Modèle
                Dupont Jean,+33612345678,dupont@test.com,Audi,A3
                Martin Paul,+33698765432,martin@test.com,BMW,Série 1
                """;

        final MockMultipartFile fichier = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(fileParserFactory.parse(any())).thenReturn(new FileParserFactory.ParseResult(
                java.util.List.of("Nom du compte", "Téléphone mobile", "Contact : Adresse e-mail", "Marque", "Modèle"),
                java.util.List.of(
                        java.util.List.of("Dupont Jean", "+33612345678", "dupont@test.com", "Audi", "A3"),
                        java.util.List.of("Martin Paul", "+33698765432", "martin@test.com", "BMW", "Série 1")
                ),
                fr.leadrevive.csvimport.entity.FormatDetecte.CSV_VIRGULE
        ));

        final LeadImport savedImport = new LeadImport();
        savedImport.setId(1L);
        when(leadImportRepository.save(any())).thenReturn(savedImport);

        final ImportAnalyseRep result = csvImportService.upload(fichier, 1L, 1L);

        assertThat(result.getImportId()).isEqualTo(1L);
        assertThat(result.getHeaders()).contains("Nom du compte", "Marque");
        assertThat(result.getEchantillon()).hasSize(2);
        assertThat(result.getNombreLignesTotal()).isEqualTo(2);
    }

    @Test
    void upload_csvAvecScoring_parseCorrectement() throws Exception {
        final MockMultipartFile fichier = new MockMultipartFile(
                "file", "leads.csv", "text/csv", "h".getBytes(StandardCharsets.UTF_8));

        when(fileParserFactory.parse(any())).thenReturn(new FileParserFactory.ParseResult(
                java.util.List.of("Nom du compte", "Scoring-Score Temps Reel"),
                java.util.List.of(java.util.List.of("Dupont", "0,6")),
                fr.leadrevive.csvimport.entity.FormatDetecte.CSV_VIRGULE
        ));

        final LeadImport savedImport = new LeadImport();
        savedImport.setId(1L);
        when(leadImportRepository.save(any())).thenReturn(savedImport);

        final ImportAnalyseRep result = csvImportService.upload(fichier, 1L, 1L);

        assertThat(result.getEchantillon().getFirst().get(1)).isEqualTo("0,6");
    }
}
