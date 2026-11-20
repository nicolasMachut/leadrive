package fr.leadrevive.csvimport.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du parser CSV/Excel : robustesse aux formats reels rencontres
 * (BOM UTF-8 typique des exports Salesforce, separateurs variables, etc.).
 */
class FileParserFactoryTest {

    private final FileParserFactory parser = new FileParserFactory();

    @Test
    void parseCsv_avecBomUtf8_premiereColonneCorrectementParse() throws Exception {
        // BOM UTF-8 = bytes EF BB BF en debut. Sans le strip, la 1ere colonne devient
        // "﻿Nom" et ne matche plus le mapping => 1ere colonne perdue silencieusement.
        final String csv = "﻿Nom,Email,Telephone\nDupont,jean@test.com,+33612345678\n";
        final MockMultipartFile fichier = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        final FileParserFactory.ParseResult result = parser.parse(fichier);

        // La 1ere colonne doit etre "Nom" sans le BOM, sinon le mapping IA ne la reconnaitra pas.
        assertThat(result.headers()).containsExactly("Nom", "Email", "Telephone");
        assertThat(result.headers().get(0)).doesNotStartWith("﻿");
        assertThat(result.lignes()).hasSize(1);
    }

    @Test
    void parseCsv_separateurPointVirgule_detecte() throws Exception {
        // Format Excel France : separateur point-virgule par defaut.
        final String csv = "Nom;Email;Telephone\nDupont;jean@test.com;+33612345678\n";
        final MockMultipartFile fichier = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        final FileParserFactory.ParseResult result = parser.parse(fichier);

        assertThat(result.headers()).containsExactly("Nom", "Email", "Telephone");
        assertThat(result.lignes().get(0)).containsExactly("Dupont", "jean@test.com", "+33612345678");
    }

    @Test
    void parseCsv_separateurVirgule_detecte() throws Exception {
        final String csv = "Nom,Email,Telephone\nDupont,jean@test.com,+33612345678\n";
        final MockMultipartFile fichier = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        final FileParserFactory.ParseResult result = parser.parse(fichier);

        assertThat(result.headers()).containsExactly("Nom", "Email", "Telephone");
    }

    @Test
    void parseCsv_lignesVides_ignorees() throws Exception {
        // Lignes vides peuvent apparaitre dans des exports CSV (saut a la fin).
        final String csv = "Nom,Email\n\nDupont,jean@test.com\n\n";
        final MockMultipartFile fichier = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        final FileParserFactory.ParseResult result = parser.parse(fichier);

        assertThat(result.lignes()).hasSize(1);
    }

    @Test
    void parseTexte_separateurTabulation_detecte() {
        // Copier-coller depuis Excel = separateur tabulation par defaut.
        final String texte = "Nom\tEmail\tTelephone\nDupont\tjean@test.com\t+33612345678\n";
        final FileParserFactory.ParseResult result = parser.parseTexte(texte);
        assertThat(result.headers()).containsExactly("Nom", "Email", "Telephone");
        assertThat(result.lignes().get(0)).containsExactly("Dupont", "jean@test.com", "+33612345678");
    }
}
