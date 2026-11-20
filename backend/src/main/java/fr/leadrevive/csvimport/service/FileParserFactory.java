package fr.leadrevive.csvimport.service;

import fr.leadrevive.csvimport.entity.FormatDetecte;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Détecte automatiquement le format d'un fichier (CSV, TSV, Excel)
 * et parse les headers + données.
 */
@Service
@Slf4j
public class FileParserFactory {

    private static final int MAX_LIGNES = 10_000;

    public record ParseResult(
            List<String> headers,
            List<List<String>> lignes,
            FormatDetecte format
    ) {}

    /**
     * Parse un fichier uploadé. Détecte automatiquement le format.
     */
    public ParseResult parse(final MultipartFile fichier) throws IOException {
        final String filename = fichier.getOriginalFilename() != null ? fichier.getOriginalFilename().toLowerCase() : "";
        final byte[] bytes = fichier.getBytes();

        // Excel .xlsx (ZIP magic bytes PK\x03\x04)
        if (filename.endsWith(".xlsx") || (bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B)) {
            return parseExcel(bytes, true);
        }

        // Excel .xls (OLE2 magic bytes \xD0\xCF\x11\xE0)
        if (filename.endsWith(".xls") || (bytes.length >= 4 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF)) {
            return parseExcel(bytes, false);
        }

        // TSV
        if (filename.endsWith(".tsv")) {
            return parseCsv(bytes, '\t', FormatDetecte.TSV);
        }

        // CSV : détecter le séparateur
        final char separateur = detecterSeparateur(bytes);
        final FormatDetecte format = separateur == '\t' ? FormatDetecte.TSV
                : separateur == ';' ? FormatDetecte.CSV_POINT_VIRGULE
                : FormatDetecte.CSV_VIRGULE;
        return parseCsv(bytes, separateur, format);
    }

    /**
     * Parse du texte collé (copier-coller depuis Excel ou DMS).
     */
    public ParseResult parseTexte(final String contenu) {
        final byte[] bytes = contenu.getBytes(StandardCharsets.UTF_8);
        final char separateur = detecterSeparateur(bytes);
        final FormatDetecte format = separateur == '\t' ? FormatDetecte.COLLER_TEXTE : FormatDetecte.COLLER_TEXTE;
        try {
            return parseCsv(bytes, separateur, format);
        } catch (final IOException e) {
            throw new RuntimeException("Erreur de parsing du texte collé", e);
        }
    }

    // ── CSV/TSV Parser ──────────────────────────────────────────────────────

    private ParseResult parseCsv(final byte[] bytes, final char separateur, final FormatDetecte format) throws IOException {
        final Charset charset = detecterEncodage(bytes);
        final List<String> headers = new ArrayList<>();
        final List<List<String>> lignes = new ArrayList<>();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), charset))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IOException("Fichier vide");

            // BOM UTF-8 (﻿) : presents sur exports Excel/Salesforce, invisible mais
            // casse la 1ere colonne (le nom de header ne matche plus le mapping). String.trim()
            // ne le supprime pas car ﻿ n'est pas un whitespace ASCII.
            if (headerLine.startsWith("﻿")) {
                headerLine = headerLine.substring(1);
            }

            for (final String h : parseCsvLine(headerLine, separateur)) {
                headers.add(h.trim());
            }

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < MAX_LIGNES) {
                if (line.isBlank()) continue;
                final List<String> values = new ArrayList<>();
                for (final String v : parseCsvLine(line, separateur)) {
                    values.add(v.trim());
                }
                // Pad si moins de colonnes que les headers
                while (values.size() < headers.size()) values.add("");
                lignes.add(values);
                count++;
            }
        }

        log.info("CSV parsé - format: {}, headers: {}, lignes: {}", format, headers.size(), lignes.size());
        return new ParseResult(headers, lignes, format);
    }

    private String[] parseCsvLine(final String line, final char sep) {
        final List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        final StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == sep && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    // ── Excel Parser ────────────────────────────────────────────────────────

    private ParseResult parseExcel(final byte[] bytes, final boolean isXlsx) throws IOException {
        try (final Workbook workbook = isXlsx
                ? new XSSFWorkbook(new ByteArrayInputStream(bytes))
                : new HSSFWorkbook(new ByteArrayInputStream(bytes))) {

            final Sheet sheet = workbook.getSheetAt(0);
            final List<String> headers = new ArrayList<>();
            final List<List<String>> lignes = new ArrayList<>();

            final Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IOException("Fichier Excel vide");

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                final Cell cell = headerRow.getCell(i);
                headers.add(cell != null ? cellToString(cell) : "Colonne " + (i + 1));
            }

            for (int r = 1; r <= Math.min(sheet.getLastRowNum(), MAX_LIGNES); r++) {
                final Row row = sheet.getRow(r);
                if (row == null) continue;
                final List<String> values = new ArrayList<>();
                for (int c = 0; c < headers.size(); c++) {
                    final Cell cell = row.getCell(c);
                    values.add(cell != null ? cellToString(cell) : "");
                }
                lignes.add(values);
            }

            final FormatDetecte format = isXlsx ? FormatDetecte.EXCEL_XLSX : FormatDetecte.EXCEL_XLS;
            log.info("Excel parsé - format: {}, headers: {}, lignes: {}", format, headers.size(), lignes.size());
            return new ParseResult(headers, lignes, format);
        }
    }

    private String cellToString(final Cell cell) {
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue();
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return DateTimeFormatter.ofPattern("dd/MM/yyyy").format(
                        cell.getLocalDateTimeCellValue().toLocalDate());
            }
            final double val = cell.getNumericCellValue();
            if (val == Math.floor(val) && !Double.isInfinite(val)) {
                return String.valueOf((long) val); // Évite "6.0" → "6"
            }
            return String.valueOf(val);
        }
        if (cell.getCellType() == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
        if (cell.getCellType() == CellType.FORMULA) {
            try { return String.valueOf(cell.getNumericCellValue()); }
            catch (final Exception e) { return cell.getStringCellValue(); }
        }
        return "";
    }

    // ── Détection séparateur ─────────────────────────────────────────────────

    private char detecterSeparateur(final byte[] bytes) {
        final String preview = new String(bytes, 0, Math.min(bytes.length, 4000), StandardCharsets.UTF_8);
        final String[] lines = preview.split("\n", 6);
        if (lines.length < 2) return ',';

        final Map<Character, int[]> counts = new LinkedHashMap<>();
        counts.put(';', new int[lines.length]);
        counts.put(',', new int[lines.length]);
        counts.put('\t', new int[lines.length]);

        for (int i = 0; i < Math.min(lines.length, 5); i++) {
            for (final char c : lines[i].toCharArray()) {
                if (counts.containsKey(c)) counts.get(c)[i]++;
            }
        }

        // Priorité au point-virgule si constant (format français)
        char best = ',';
        int bestScore = 0;
        for (final var entry : counts.entrySet()) {
            final int[] arr = entry.getValue();
            if (arr[0] == 0) continue;
            boolean constant = true;
            for (int i = 1; i < Math.min(arr.length, 5); i++) {
                if (arr[i] != arr[0]) { constant = false; break; }
            }
            final int score = constant ? arr[0] * 10 : arr[0];
            // Boost pour ; (format FR)
            final int adjustedScore = entry.getKey() == ';' ? score + 5 : score;
            if (adjustedScore > bestScore) {
                bestScore = adjustedScore;
                best = entry.getKey();
            }
        }

        log.debug("Séparateur détecté: '{}' (score: {})", best == '\t' ? "TAB" : String.valueOf(best), bestScore);
        return best;
    }

    // ── Détection encodage ──────────────────────────────────────────────────

    private Charset detecterEncodage(final byte[] bytes) {
        // Heuristique simple : si UTF-8 est valide, on l'utilise.
        // Sinon fallback sur Windows-1252 (très courant pour les exports DMS français).
        try {
            final String test = new String(bytes, StandardCharsets.UTF_8);
            // Vérifie qu'il n'y a pas de caractère de remplacement
            if (!test.contains("\uFFFD")) return StandardCharsets.UTF_8;
        } catch (final Exception e) {
            // pas UTF-8
        }
        return Charset.forName("Windows-1252");
    }
}
