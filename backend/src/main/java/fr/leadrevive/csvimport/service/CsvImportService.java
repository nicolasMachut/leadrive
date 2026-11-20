package fr.leadrevive.csvimport.service;

import org.apache.commons.text.WordUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import fr.leadrevive.csvimport.dto.ColonneProposition;
import fr.leadrevive.csvimport.dto.ImportAnalyseRep;
import fr.leadrevive.csvimport.dto.ImportMappingRep;
import fr.leadrevive.csvimport.dto.MappingProposition;
import fr.leadrevive.csvimport.dto.MappingValidationReq;
import fr.leadrevive.csvimport.dto.RepCsvPreviewDto;
import fr.leadrevive.csvimport.dto.RepImportResultDto;
import fr.leadrevive.csvimport.dto.RepLeadImportDto;
import fr.leadrevive.csvimport.dto.SplitOption;
import fr.leadrevive.csvimport.dto.SplitProposition;
import fr.leadrevive.csvimport.entity.FormatDetecte;
import fr.leadrevive.csvimport.entity.ImportStatutEnum;
import fr.leadrevive.csvimport.entity.LeadImport;
import fr.leadrevive.csvimport.entity.MappingTemplate;
import fr.leadrevive.csvimport.exception.CsvParsingException;
import fr.leadrevive.csvimport.exception.ImportNotFoundException;
import fr.leadrevive.common.realtime.SseConnectionService;
import fr.leadrevive.common.realtime.dto.RealtimeEventDto;
import fr.leadrevive.csvimport.mapper.LeadImportMapper;
import fr.leadrevive.csvimport.repository.LeadImportRepository;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.lead.service.DeduplicationService;
import fr.leadrevive.lead.service.LeadValidationService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Duration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final LeadImportRepository leadImportRepository;
    private final LeadRepository leadRepository;
    private final LeadValidationService leadValidationService;
    private final DeduplicationService deduplicationService;
    private final LeadImportMapper leadImportMapper;
    private final ObjectMapper objectMapper;
    private final FileParserFactory fileParserFactory;
    private final MappingIAService mappingIAService;
    private final MappingTemplateService mappingTemplateService;
    private final SseConnectionService sseConnectionService;

    /**
     * Cache des données parsées en attente de validation (headers + lignes).
     * TTL 30 min, max 100 imports concurrents.
     */
    private final Cache<Long, CachedImportData> importDataCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(100)
            .build();

    /**
     * Cache du mapping validé par l'utilisateur, en attente de l'import final.
     */
    private final Cache<Long, ValidatedMapping> validatedMappingCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(100)
            .build();

    record CachedImportData(List<String> headers, List<List<String>> lignes, FormatDetecte format) {}
    record ValidatedMapping(Map<String, String> mappings, SplitOption splitOption, String formatDate, String nomTemplate, String source, Long templateId) {}

    // ── Étape 1 : Upload et analyse ────────────────────────────────────────

    @Transactional
    public ImportAnalyseRep upload(final MultipartFile fichier, final Long tenantId, final Long userId) {
        log.info("Upload fichier - nom: {}, tenantId: {}", fichier.getOriginalFilename(), tenantId);

        final FileParserFactory.ParseResult parseResult;
        try {
            parseResult = fileParserFactory.parse(fichier);
        } catch (final IOException e) {
            throw new CsvParsingException("Erreur de lecture du fichier: " + e.getMessage());
        }

        if (parseResult.lignes().isEmpty()) {
            throw new CsvParsingException("Le fichier est vide ou ne contient que des headers");
        }

        final LeadImport leadImport = new LeadImport();
        leadImport.setTenantId(tenantId);
        leadImport.setNomFichier(fichier.getOriginalFilename());
        leadImport.setDateImport(LocalDateTime.now());
        leadImport.setNombreLignes(parseResult.lignes().size());
        leadImport.setStatut(ImportStatutEnum.EN_COURS);
        leadImport.setImportePar(userId);
        leadImport.setTypeFichier(parseResult.format());
        final LeadImport saved = leadImportRepository.save(leadImport);

        importDataCache.put(saved.getId(), new CachedImportData(parseResult.headers(), parseResult.lignes(), parseResult.format()));

        final List<List<String>> echantillon = parseResult.lignes().stream().limit(5).toList();

        log.info("Fichier analysé - idImport: {}, format: {}, lignes: {}", saved.getId(), parseResult.format(), parseResult.lignes().size());

        return ImportAnalyseRep.builder()
                .importId(saved.getId())
                .headers(parseResult.headers())
                .echantillon(echantillon)
                .formatDetecte(parseResult.format())
                .nombreLignesTotal(parseResult.lignes().size())
                .build();
    }

    @Transactional
    public ImportAnalyseRep paste(final String contenu, final Long tenantId, final Long userId) {
        log.info("Import copier-coller - tenantId: {}", tenantId);

        if (contenu == null || contenu.isBlank()) {
            throw new CsvParsingException("Le contenu collé est vide");
        }

        final FileParserFactory.ParseResult parseResult = fileParserFactory.parseTexte(contenu);

        if (parseResult.lignes().isEmpty()) {
            throw new CsvParsingException("Aucune donnée détectée dans le texte collé");
        }

        final String nomFichier = "Copier-coller du " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        final LeadImport leadImport = new LeadImport();
        leadImport.setTenantId(tenantId);
        leadImport.setNomFichier(nomFichier);
        leadImport.setDateImport(LocalDateTime.now());
        leadImport.setNombreLignes(parseResult.lignes().size());
        leadImport.setStatut(ImportStatutEnum.EN_COURS);
        leadImport.setImportePar(userId);
        leadImport.setTypeFichier(FormatDetecte.COLLER_TEXTE);
        final LeadImport saved = leadImportRepository.save(leadImport);

        importDataCache.put(saved.getId(), new CachedImportData(parseResult.headers(), parseResult.lignes(), FormatDetecte.COLLER_TEXTE));

        final List<List<String>> echantillon = parseResult.lignes().stream().limit(5).toList();

        log.info("Copier-coller analysé - idImport: {}, lignes: {}", saved.getId(), parseResult.lignes().size());

        return ImportAnalyseRep.builder()
                .importId(saved.getId())
                .headers(parseResult.headers())
                .echantillon(echantillon)
                .formatDetecte(FormatDetecte.COLLER_TEXTE)
                .nombreLignesTotal(parseResult.lignes().size())
                .build();
    }

    // ── Étape 2 : Mapping intelligent ──────────────────────────────────────

    @Transactional
    public ImportMappingRep analyserMapping(final Long importId, final Long tenantId) {
        log.info("Analyse mapping - idImport: {}", importId);

        final CachedImportData data = getCachedData(importId);

        // Cas A : template existant
        final Optional<MappingTemplate> templateOpt = mappingTemplateService.detecterTemplate(tenantId, data.headers());
        if (templateOpt.isPresent()) {
            final MappingTemplate template = templateOpt.get();
            final List<ImportMappingRep.ColonneMappingRep> colonnes = new ArrayList<>();

            for (int i = 0; i < data.headers().size(); i++) {
                final String header = data.headers().get(i);
                final String cible = template.getMappings().get(header);
                final List<String> echantillonColonne = extraireEchantillonColonne(data, i);
                colonnes.add(ImportMappingRep.ColonneMappingRep.builder()
                        .headerOriginal(header)
                        .cible(cible != null ? cible : "IGNORER")
                        .confiance(100)
                        .raison(null)
                        .echantillon(echantillonColonne)
                        .build());
            }

            SplitProposition split = null;
            if (template.getOptionsSplit() != null && !template.getOptionsSplit().isEmpty()) {
                split = new SplitProposition(
                        (String) template.getOptionsSplit().get("colonne"),
                        (String) template.getOptionsSplit().get("type"),
                        (String) template.getOptionsSplit().get("ordreNomPrenom")
                );
            }

            return ImportMappingRep.builder()
                    .source("TEMPLATE")
                    .templateId(template.getId())
                    .colonnes(colonnes)
                    .formatDateDetecte(template.getFormatDate())
                    .splitDetecte(split)
                    .build();
        }

        // Cas B : analyse IA
        final List<List<String>> echantillon = data.lignes().stream().limit(5).toList();
        final MappingProposition proposition = mappingIAService.analyserColonnes(data.headers(), echantillon, tenantId);

        final List<ImportMappingRep.ColonneMappingRep> colonnes = new ArrayList<>();
        for (int i = 0; i < data.headers().size(); i++) {
            final String header = data.headers().get(i);
            final List<String> echantillonColonne = extraireEchantillonColonne(data, i);

            ColonneProposition cp = null;
            if (proposition.mappings() != null) {
                for (final ColonneProposition p : proposition.mappings()) {
                    if (p.headerOriginal() != null && p.headerOriginal().equalsIgnoreCase(header)) {
                        cp = p;
                        break;
                    }
                }
            }

            colonnes.add(ImportMappingRep.ColonneMappingRep.builder()
                    .headerOriginal(header)
                    .cible(cp != null ? cp.cible() : "IGNORER")
                    .confiance(cp != null ? cp.confiance() : 0)
                    .raison(cp != null ? cp.raison() : null)
                    .echantillon(echantillonColonne)
                    .build());
        }

        return ImportMappingRep.builder()
                .source("IA")
                .templateId(null)
                .colonnes(colonnes)
                .formatDateDetecte(proposition.formatDateDetecte())
                .splitDetecte(proposition.splitDetecte())
                .build();
    }

    // ── Étape 3 : Validation du mapping ────────────────────────────────────

    @Transactional
    public RepCsvPreviewDto validerMapping(final Long importId, final Long tenantId, final Long userId,
                                            final MappingValidationReq req) {
        log.info("Validation mapping - idImport: {}", importId);

        final CachedImportData data = getCachedData(importId);

        // Garde-fou 1 : aucune colonne ne doit pointer vers la meme cible (sauf IGNORER).
        // Sinon, la 2e colonne ecrase silencieusement la 1ere dans appliquerMappingDynamique
        // -> noms/emails/telephones corrompus en base sans alerte.
        validerPasDeDoubleMapping(req.mappings());

        // Garde-fou 2 : au moins un champ identifiant doit etre mappe, sinon l'import
        // creerait 0 leads valides avec un message d'erreur generique par ligne.
        validerAuMoinsUnChampIdentifiant(req.mappings());

        // Sauvegarder le mapping validé en cache pour l'import final
        validatedMappingCache.put(importId, new ValidatedMapping(
                req.mappings(), req.splitOption(), req.formatDate(), req.nomTemplate(), "VALIDATED", null
        ));

        // Sauvegarder le template
        final String nomTemplate = req.nomTemplate() != null && !req.nomTemplate().isBlank()
                ? req.nomTemplate()
                : genererNomTemplate(importId, tenantId);
        mappingTemplateService.sauvegarderTemplate(
                tenantId, nomTemplate, data.headers(), req.mappings(),
                req.splitOption() != null
                        ? new SplitProposition(req.splitOption().colonne(), "NOM_COMPLET", req.splitOption().ordreNomPrenom())
                        : null,
                req.formatDate(), userId
        );

        // Stocker le mapping dans l'entité LeadImport
        final LeadImport leadImport = leadImportRepository.findByIdAndTenantId(importId, tenantId)
                .orElseThrow(() -> new ImportNotFoundException(importId));
        try {
            leadImport.setMappingColonnes(objectMapper.writeValueAsString(req.mappings()));
        } catch (final JacksonException e) {
            log.warn("Erreur sérialisation mapping - cause: {}", e.getMessage());
        }
        leadImportRepository.save(leadImport);

        // Générer le preview avec le mapping appliqué
        return genererPreview(importId, tenantId, data, req.mappings(), req.splitOption(), req.formatDate());
    }

    // ── Étape 4 : Preview ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RepCsvPreviewDto preview(final Long importId, final Long tenantId) {
        log.info("Prévisualisation import - idImport: {}", importId);

        leadImportRepository.findByIdAndTenantId(importId, tenantId)
                .orElseThrow(() -> new ImportNotFoundException(importId));

        final CachedImportData data = getCachedData(importId);
        final ValidatedMapping mapping = validatedMappingCache.getIfPresent(importId);

        if (mapping != null) {
            return genererPreview(importId, tenantId, data, mapping.mappings(), mapping.splitOption(), mapping.formatDate());
        }

        // Fallback : preview sans mapping (ancien comportement pour compatibilité)
        return genererPreviewSansMapping(importId, tenantId, data);
    }

    // ── Étape 5 : Import définitif ─────────────────────────────────────────

    @Transactional
    public RepImportResultDto validate(final Long importId, final Long tenantId) {
        return validate(importId, tenantId, null);
    }

    @Transactional
    public RepImportResultDto validate(final Long importId, final Long tenantId, final Long vendeurIdAssign) {
        log.info("Lancement import - idImport: {}, tenantId: {}", importId, tenantId);

        final LeadImport leadImport = leadImportRepository.findByIdAndTenantId(importId, tenantId)
                .orElseThrow(() -> new ImportNotFoundException(importId));

        final CachedImportData data = getCachedData(importId);
        final ValidatedMapping mapping = validatedMappingCache.getIfPresent(importId);

        int nombreImportees = 0;
        int nombreErreurs = 0;
        int nombreDoublons = 0;
        final List<Map<String, String>> erreursDetail = new ArrayList<>();

        for (int i = 0; i < data.lignes().size(); i++) {
            final List<String> ligne = data.lignes().get(i);
            final int numeroLigne = i + 2;

            try {
                final Map<String, String> donneesMappees;
                if (mapping != null) {
                    donneesMappees = appliquerMappingDynamique(data.headers(), ligne, mapping.mappings(), mapping.splitOption(), mapping.formatDate());
                } else {
                    donneesMappees = appliquerMappingLegacy(data.headers(), ligne);
                }

                final String nom = donneesMappees.get("nom");
                final String email = donneesMappees.get("email");
                final String telephone = donneesMappees.get("telephone");

                final List<String> erreurs = leadValidationService.validerLead(nom, email, telephone);
                if (!erreurs.isEmpty()) {
                    nombreErreurs++;
                    erreursDetail.add(Map.of("ligne", String.valueOf(numeroLigne), "erreurs", String.join("; ", erreurs)));
                    continue;
                }

                final String telephoneNettoye = leadValidationService.nettoyerTelephone(telephone);

                if (deduplicationService.estDoublon(tenantId, email, telephoneNettoye)) {
                    nombreDoublons++;
                    continue;
                }

                final Lead lead = construireLead(donneesMappees, data.headers(), ligne, tenantId, importId);
                if (vendeurIdAssign != null) {
                    lead.setVendeurId(vendeurIdAssign);
                }
                leadRepository.save(lead);
                nombreImportees++;

            } catch (final Exception e) {
                nombreErreurs++;
                erreursDetail.add(Map.of("ligne", String.valueOf(numeroLigne), "erreurs", e.getMessage()));
                log.warn("Erreur import ligne {} - cause: {}", numeroLigne, e.getMessage());
            }
        }

        leadImport.setNombreImportees(nombreImportees);
        leadImport.setNombreErreurs(nombreErreurs);
        leadImport.setNombreDoublons(nombreDoublons);
        leadImport.setStatut(nombreErreurs > 0 && nombreImportees == 0 ? ImportStatutEnum.ERREUR : ImportStatutEnum.TERMINE);

        try {
            leadImport.setErreurs(objectMapper.writeValueAsString(erreursDetail));
        } catch (final JacksonException e) {
            log.error("Erreur sérialisation erreurs - cause: {}", e.getMessage());
        }

        leadImportRepository.save(leadImport);
        importDataCache.invalidate(importId);
        validatedMappingCache.invalidate(importId);

        log.info("Import terminé - idImport: {}, importées: {}, erreurs: {}, doublons: {}",
                importId, nombreImportees, nombreErreurs, nombreDoublons);

        if (nombreImportees > 0) {
            sseConnectionService.broadcast(tenantId,
                    RealtimeEventDto.leadBatchImported(nombreImportees, "Import CSV"));
        }

        return RepImportResultDto.builder()
                .idImport(importId)
                .nombreLignes(data.lignes().size())
                .nombreImportees(nombreImportees)
                .nombreErreurs(nombreErreurs)
                .nombreDoublons(nombreDoublons)
                .statut(leadImport.getStatut().getValeur())
                .build();
    }

    // ── Historique ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RepLeadImportDto> getImports(final Long tenantId) {
        log.info("Récupération des imports - tenantId: {}", tenantId);
        return leadImportRepository.findTop200ByTenantIdOrderByDateImportDesc(tenantId).stream()
                .map(leadImportMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RepLeadImportDto getImport(final Long importId, final Long tenantId) {
        log.info("Récupération de l'import - idImport: {}", importId);
        final LeadImport leadImport = leadImportRepository.findByIdAndTenantId(importId, tenantId)
                .orElseThrow(() -> new ImportNotFoundException(importId));
        return leadImportMapper.toDto(leadImport);
    }

    // ── Helpers privés ─────────────────────────────────────────────────────

    private CachedImportData getCachedData(final Long importId) {
        final CachedImportData data = importDataCache.getIfPresent(importId);
        if (data == null) {
            throw new CsvParsingException("Données non disponibles. Veuillez re-uploader le fichier.");
        }
        return data;
    }

    private List<String> extraireEchantillonColonne(final CachedImportData data, final int colIndex) {
        return data.lignes().stream()
                .limit(3)
                .map(ligne -> colIndex < ligne.size() ? ligne.get(colIndex) : "")
                .toList();
    }

    private static final Set<String> CIBLES_IDENTIFIANT = Set.of("nom", "nom_complet", "email", "telephone");

    private void validerPasDeDoubleMapping(final Map<String, String> mappings) {
        final Map<String, List<String>> ciblesParColonnes = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : mappings.entrySet()) {
            final String cible = entry.getValue();
            if (cible == null || "IGNORER".equalsIgnoreCase(cible)) continue;
            ciblesParColonnes.computeIfAbsent(cible, k -> new ArrayList<>()).add(entry.getKey());
        }
        final List<String> conflits = new ArrayList<>();
        for (final Map.Entry<String, List<String>> entry : ciblesParColonnes.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflits.add("Le champ '" + entry.getKey() + "' est associé à plusieurs colonnes : "
                        + String.join(", ", entry.getValue()));
            }
        }
        if (!conflits.isEmpty()) {
            throw new CsvParsingException("Mapping invalide : " + String.join(" ; ", conflits)
                    + ". Choisissez une seule colonne par champ ou utilisez 'Ignorer' pour les doublons.");
        }
    }

    private void validerAuMoinsUnChampIdentifiant(final Map<String, String> mappings) {
        final boolean auMoinsUn = mappings.values().stream()
                .filter(cible -> cible != null && !"IGNORER".equalsIgnoreCase(cible))
                .anyMatch(CIBLES_IDENTIFIANT::contains);
        if (!auMoinsUn) {
            throw new CsvParsingException("Mapping invalide : au moins une colonne doit être associée à 'Nom', "
                    + "'Email' ou 'Téléphone' pour identifier les prospects.");
        }
    }

    private String genererNomTemplate(final Long importId, final Long tenantId) {
        return leadImportRepository.findByIdAndTenantId(importId, tenantId)
                .map(li -> "Import " + li.getNomFichier() + " du " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .orElse("Import du " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    private Map<String, String> appliquerMappingDynamique(final List<String> headers, final List<String> ligne,
                                                          final Map<String, String> mappings,
                                                          final SplitOption splitOption, final String formatDate) {
        final Map<String, String> resultat = new LinkedHashMap<>();

        for (int i = 0; i < headers.size(); i++) {
            final String header = headers.get(i);
            final String cible = mappings.get(header);
            if (cible == null || "IGNORER".equalsIgnoreCase(cible)) continue;

            final String valeur = i < ligne.size() ? ligne.get(i) : "";
            if (valeur.isBlank()) continue;

            if ("nom_complet".equals(cible) && splitOption != null && splitOption.colonne() != null
                    && splitOption.colonne().equalsIgnoreCase(header)) {
                // Split nom/prénom
                final String[] parts = valeur.trim().split("\\s+", 2);
                if (parts.length == 2) {
                    if ("PRENOM_NOM".equals(splitOption.ordreNomPrenom())) {
                        resultat.put("prenom", parts[0]);
                        resultat.put("nom", parts[1]);
                    } else {
                        resultat.put("nom", parts[0]);
                        resultat.put("prenom", parts[1]);
                    }
                } else {
                    resultat.put("nom", valeur.trim());
                }
            } else {
                resultat.put(cible, valeur);
            }
        }

        // Si on a un prénom séparé, construire le nom complet
        if (resultat.containsKey("prenom") && resultat.containsKey("nom")) {
            final String nomComplet = resultat.get("prenom") + " " + resultat.get("nom");
            resultat.put("nom", WordUtils.capitalizeFully(nomComplet.trim(), ' ', '-'));
        } else if (resultat.containsKey("nom")) {
            resultat.put("nom", WordUtils.capitalizeFully(resultat.get("nom").trim(), ' ', '-'));
        }

        return resultat;
    }

    /**
     * Mapping legacy (ancien format hardcodé) pour la rétrocompatibilité
     * quand aucun mapping dynamique n'est disponible.
     */
    private static final Map<String, String> MAPPING_LEGACY = Map.ofEntries(
            Map.entry("Nom du compte", "nom"),
            Map.entry("Téléphone mobile", "telephone"),
            Map.entry("Contact : Adresse e-mail", "email"),
            Map.entry("Marque", "marque"),
            Map.entry("Modèle", "modele"),
            Map.entry("Territoire", "territoire"),
            Map.entry("Étape", "etape"),

            Map.entry("Devis", "devis"),
            Map.entry("Reprise", "reprise"),
            Map.entry("Type", "typeOpportunite"),
            Map.entry("Last Modification Quote Or Interest", "dateModificationDevis")
    );

    private Map<String, String> appliquerMappingLegacy(final List<String> headers, final List<String> ligne) {
        final Map<String, String> resultat = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            final String header = headers.get(i);
            final String cible = MAPPING_LEGACY.get(header);
            if (cible == null) continue;
            final String valeur = i < ligne.size() ? ligne.get(i) : "";
            if (!valeur.isBlank()) {
                resultat.put(cible, valeur);
            }
        }
        return resultat;
    }

    private RepCsvPreviewDto genererPreview(final Long importId, final Long tenantId,
                                             final CachedImportData data, final Map<String, String> mappings,
                                             final SplitOption splitOption, final String formatDate) {
        final List<RepCsvPreviewDto.RepPreviewLineDto> previewLines = new ArrayList<>();
        int nombreDoublons = 0;
        final List<List<String>> echantillon = data.lignes().stream().limit(10).toList();

        for (int i = 0; i < echantillon.size(); i++) {
            final List<String> ligne = echantillon.get(i);
            final Map<String, String> donneesMappees = appliquerMappingDynamique(data.headers(), ligne, mappings, splitOption, formatDate);

            final String nom = donneesMappees.get("nom");
            final String email = donneesMappees.get("email");
            final String telephone = donneesMappees.get("telephone");

            final List<String> erreurs = leadValidationService.validerLead(nom, email, telephone);
            final boolean doublon = deduplicationService.estDoublon(tenantId, email,
                    leadValidationService.nettoyerTelephone(telephone));

            if (doublon) nombreDoublons++;

            previewLines.add(RepCsvPreviewDto.RepPreviewLineDto.builder()
                    .numeroLigne(i + 1)
                    .donnees(donneesMappees)
                    .valide(erreurs.isEmpty())
                    .erreurs(erreurs)
                    .doublon(doublon)
                    .build());
        }

        return RepCsvPreviewDto.builder()
                .idImport(importId)
                .lignes(previewLines)
                .nombreDoublonsPotentiels(nombreDoublons)
                .build();
    }

    private RepCsvPreviewDto genererPreviewSansMapping(final Long importId, final Long tenantId,
                                                       final CachedImportData data) {
        final List<RepCsvPreviewDto.RepPreviewLineDto> previewLines = new ArrayList<>();
        int nombreDoublons = 0;
        final List<List<String>> echantillon = data.lignes().stream().limit(5).toList();

        for (int i = 0; i < echantillon.size(); i++) {
            final List<String> ligne = echantillon.get(i);
            final Map<String, String> donneesMappees = appliquerMappingLegacy(data.headers(), ligne);

            final String nom = donneesMappees.get("nom");
            final String email = donneesMappees.get("email");
            final String telephone = donneesMappees.get("telephone");

            final List<String> erreurs = leadValidationService.validerLead(nom, email, telephone);
            final boolean doublon = deduplicationService.estDoublon(tenantId, email,
                    leadValidationService.nettoyerTelephone(telephone));

            if (doublon) nombreDoublons++;

            previewLines.add(RepCsvPreviewDto.RepPreviewLineDto.builder()
                    .numeroLigne(i + 1)
                    .donnees(donneesMappees)
                    .valide(erreurs.isEmpty())
                    .erreurs(erreurs)
                    .doublon(doublon)
                    .build());
        }

        return RepCsvPreviewDto.builder()
                .idImport(importId)
                .lignes(previewLines)
                .nombreDoublonsPotentiels(nombreDoublons)
                .build();
    }

    private Lead construireLead(final Map<String, String> donneesMappees,
                                final List<String> headers, final List<String> ligneBrute,
                                final Long tenantId, final Long importId) {
        final Lead lead = new Lead();
        lead.setTenantId(tenantId);
        lead.setImportId(importId);
        lead.setSource("CSV");
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        // RGPD : trace du consentement initial. La concession importe les leads dans son
        // parcours commercial — consentement implicite. La source permet l'audit.
        lead.setConsentementContact(true);
        lead.setConsentementDate(java.time.LocalDateTime.now());
        lead.setConsentementSource("csv_import");

        final String nomBrut = donneesMappees.get("nom");
        lead.setNom(nomBrut != null ? WordUtils.capitalizeFully(nomBrut.trim(), ' ', '-') : null);
        lead.setEmail(donneesMappees.get("email"));
        lead.setTelephone(leadValidationService.nettoyerTelephone(donneesMappees.get("telephone")));
        lead.setMarque(donneesMappees.get("marque"));
        lead.setModele(donneesMappees.get("modele"));
        lead.setTerritoire(donneesMappees.get("territoire"));
        lead.setEtape(donneesMappees.get("etape"));
        lead.setDevis(donneesMappees.get("devis"));
        lead.setTypeOpportunite(donneesMappees.getOrDefault("typeOpportunite", donneesMappees.get("type")));

        final String repriseStr = donneesMappees.get("reprise");
        if (repriseStr != null && !repriseStr.isBlank()) {
            lead.setReprise("oui".equalsIgnoreCase(repriseStr.trim())
                    || "true".equalsIgnoreCase(repriseStr.trim())
                    || "o".equalsIgnoreCase(repriseStr.trim())
                    || "1".equals(repriseStr.trim()));
        }

        final String dateStr = donneesMappees.get("dateModificationDevis");
        if (dateStr != null && !dateStr.isBlank()) {
            lead.setDateModificationDevis(parserDate(dateStr));
        }

        // Mode import
        lead.setModeImport(fr.leadrevive.lead.entity.ModeImport.IMPORT_CSV);

        // Raw data : reconstruire la ligne brute en Map
        try {
            final Map<String, String> rawMap = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                rawMap.put(headers.get(i), i < ligneBrute.size() ? ligneBrute.get(i) : "");
            }
            lead.setRawData(objectMapper.writeValueAsString(rawMap));

            // Stocker les colonnes non mappées dans donneesSupplementaires
            final Set<String> champsMappés = donneesMappees.keySet();
            final Map<String, Object> donneesSupp = new HashMap<>();
            for (final Map.Entry<String, String> entry : rawMap.entrySet()) {
                final String valeur = entry.getValue();
                if (valeur != null && !valeur.isBlank() && !champsMappés.contains(entry.getKey())
                        && !"IGNORER".equalsIgnoreCase(entry.getKey())) {
                    donneesSupp.put(entry.getKey(), valeur.trim());
                }
            }
            if (!donneesSupp.isEmpty()) {
                lead.setDonneesSupplementaires(donneesSupp);
            }
        } catch (final JacksonException e) {
            log.warn("Erreur sérialisation rawData - cause: {}", e.getMessage());
        }

        return lead;
    }

    private LocalDateTime parserDate(final String dateStr) {
        final String[] formats = {
                "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "dd-MM-yyyy",
                "MM/dd/yyyy", "dd.MM.yyyy"
        };
        for (final String format : formats) {
            try {
                if (format.contains("HH")) {
                    return LocalDateTime.parse(dateStr.trim(), DateTimeFormatter.ofPattern(format));
                } else {
                    return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(format)).atStartOfDay();
                }
            } catch (final DateTimeParseException ignored) {
                // Essayer le format suivant
            }
        }
        log.warn("Date invalide ignorée - valeur: {}", dateStr);
        return null;
    }
}
