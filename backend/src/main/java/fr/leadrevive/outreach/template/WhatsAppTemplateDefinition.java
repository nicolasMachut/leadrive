package fr.leadrevive.outreach.template;

import java.util.List;
import java.util.Objects;

/**
 * Definition d'un template WhatsApp Business charge depuis un fichier YAML
 * dans {@code resources/whatsapp-templates/}.
 *
 * <p>Source de verite pour la soumission a Twilio Content API + Meta. Chaque
 * concession (WABA) doit soumettre ses propres templates. Les fichiers YAML
 * du repo sont la reference : si on modifie un YAML, le nouveau WABA recevra
 * la nouvelle version, les WABAs existants gardent leur version approuvee.</p>
 *
 * <p><strong>Immutabilite</strong> : volontaire (record + List immuables) pour
 * que la definition soit partageable entre threads sans risque.</p>
 *
 * <p>Format YAML attendu :</p>
 * <pre>{@code
 * name: leadrive_premier_contact
 * language: fr
 * category: MARKETING
 * components:
 *   - type: BODY
 *     text: "Bonjour {{1}}, ..."
 *     variables:
 *       - description: "Prenom"
 *         example: "Jean"
 * }</pre>
 */
public record WhatsAppTemplateDefinition(
        String name,
        String language,
        Category category,
        List<Component> components
) {

    public WhatsAppTemplateDefinition {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(language, "language is required");
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(components, "components is required");
        components = List.copyOf(components);
    }

    /**
     * Categories Meta : MARKETING (promo, relance, broadcast) ou UTILITY
     * (transactionnel : rappel RDV, confirmation commande). La categorie
     * impacte le tarif et la latence de validation Meta.
     */
    public enum Category {
        MARKETING,
        UTILITY,
        AUTHENTICATION
    }

    /**
     * Types de composants Meta supportes : HEADER, BODY, FOOTER, BUTTONS.
     * BODY est obligatoire. HEADER/FOOTER/BUTTONS sont optionnels.
     */
    public enum ComponentType {
        HEADER,
        BODY,
        FOOTER,
        BUTTONS
    }

    /**
     * Composant d'un template (header, body, footer, buttons).
     */
    public record Component(
            ComponentType type,
            String format,
            String text,
            List<Variable> variables
    ) {
        public Component {
            Objects.requireNonNull(type, "component type is required");
            variables = variables == null ? List.of() : List.copyOf(variables);
        }
    }

    /**
     * Variable d'un template ({{1}}, {{2}}, ...). Meta exige une description
     * + un exemple pour chaque variable lors de la soumission, sinon le
     * template est rejete.
     */
    public record Variable(
            String description,
            String example
    ) {
        public Variable {
            Objects.requireNonNull(description, "variable description is required");
            Objects.requireNonNull(example, "variable example is required");
        }
    }
}
