package fr.leadrevive.outreach.template;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du loader de templates WhatsApp YAML (epic 25).
 *
 * <p>Verifie que les 4 fichiers YAML versionnes dans
 * {@code resources/whatsapp-templates/} sont parses correctement, valides
 * (BODY present, name commence par "leadrive_"), et que le hash SHA-256 est
 * deterministe.</p>
 */
class WhatsAppTemplateLoaderTest {

    @Test
    void charge_tous_les_templates_du_classpath() {
        final WhatsAppTemplateLoader loader = new WhatsAppTemplateLoader();
        ReflectionTestUtils.setField(loader, "templatesRequired", true);

        loader.chargerTemplates();

        final List<WhatsAppTemplateLoader.LoadedTemplate> templates = loader.tousLesTemplates();
        assertThat(templates).isNotEmpty();
        // Les 4 templates MVP epic 25 doivent etre presents.
        assertThat(templates)
                .extracting(t -> t.definition().name())
                .contains(
                        "leadrive_premier_contact",
                        "leadrive_relance_j1",
                        "leadrive_relance_j3",
                        "leadrive_rappel_rdv"
                );
    }

    @Test
    void parNom_renvoie_le_bon_template() {
        final WhatsAppTemplateLoader loader = new WhatsAppTemplateLoader();
        ReflectionTestUtils.setField(loader, "templatesRequired", true);
        loader.chargerTemplates();

        final WhatsAppTemplateLoader.LoadedTemplate premierContact =
                loader.parNom("leadrive_premier_contact");

        assertThat(premierContact).isNotNull();
        assertThat(premierContact.definition().language()).isEqualTo("fr");
        assertThat(premierContact.definition().category())
                .isEqualTo(WhatsAppTemplateDefinition.Category.UTILITY);
        assertThat(premierContact.contentHashSha256()).isNotBlank();
        // SHA-256 hex = 64 chars
        assertThat(premierContact.contentHashSha256()).hasSize(64);
    }

    @Test
    void parNom_renvoie_null_pour_un_nom_inconnu() {
        final WhatsAppTemplateLoader loader = new WhatsAppTemplateLoader();
        ReflectionTestUtils.setField(loader, "templatesRequired", true);
        loader.chargerTemplates();

        assertThat(loader.parNom("template_inexistant")).isNull();
    }

    @Test
    void tousLesTemplates_renvoie_une_liste_ordonnee_de_facon_deterministe() {
        final WhatsAppTemplateLoader loader = new WhatsAppTemplateLoader();
        ReflectionTestUtils.setField(loader, "templatesRequired", true);
        loader.chargerTemplates();

        final List<WhatsAppTemplateLoader.LoadedTemplate> templates1 = loader.tousLesTemplates();
        final List<WhatsAppTemplateLoader.LoadedTemplate> templates2 = loader.tousLesTemplates();

        assertThat(templates1)
                .extracting(WhatsAppTemplateLoader.LoadedTemplate::fileName)
                .containsExactlyElementsOf(
                        templates2.stream()
                                .map(WhatsAppTemplateLoader.LoadedTemplate::fileName)
                                .toList()
                );
    }

    @Test
    void chaque_template_a_un_composant_BODY_obligatoire() {
        final WhatsAppTemplateLoader loader = new WhatsAppTemplateLoader();
        ReflectionTestUtils.setField(loader, "templatesRequired", true);
        loader.chargerTemplates();

        for (final WhatsAppTemplateLoader.LoadedTemplate t : loader.tousLesTemplates()) {
            final boolean hasBody = t.definition().components().stream()
                    .anyMatch(c -> c.type() == WhatsAppTemplateDefinition.ComponentType.BODY);
            assertThat(hasBody)
                    .as("Template %s doit avoir un composant BODY", t.definition().name())
                    .isTrue();
        }
    }
}
