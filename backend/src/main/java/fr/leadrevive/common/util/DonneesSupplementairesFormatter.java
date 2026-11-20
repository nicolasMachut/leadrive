package fr.leadrevive.common.util;

import java.util.Map;

/**
 * Formate les données supplémentaires d'un lead (JSONB) en texte lisible pour les prompts IA.
 */
public final class DonneesSupplementairesFormatter {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("immatriculation", "Immatriculation"),
            Map.entry("vin", "Numéro VIN"),
            Map.entry("kilometrage", "Kilométrage"),
            Map.entry("energie", "Énergie"),
            Map.entry("boiteVitesse", "Boîte de vitesses"),
            Map.entry("annee", "Année"),
            Map.entry("dateMiseEnCirculation", "Date mise en circulation"),
            Map.entry("couleurExterieure", "Couleur extérieure"),
            Map.entry("couleurInterieure", "Couleur intérieure"),
            Map.entry("puissanceFiscale", "Puissance fiscale (CV)"),
            Map.entry("puissanceCh", "Puissance (ch)"),
            Map.entry("carrosserie", "Carrosserie"),
            Map.entry("finition", "Finition"),
            Map.entry("version", "Version complète"),
            Map.entry("premiereMain", "Première main"),
            Map.entry("vehiculeImporte", "Véhicule importé"),
            Map.entry("entretienAJour", "Entretien à jour"),
            Map.entry("modeFinancement", "Mode de financement"),
            Map.entry("intentionAchat", "Intention d'achat"),
            Map.entry("numeroDossier", "Numéro de dossier"),
            Map.entry("commentaireProspect", "Message du prospect"),
            Map.entry("pointDeVente", "Point de vente souhaité"),
            Map.entry("adresseProspect", "Adresse/CP du prospect"),
            Map.entry("civilite", "Civilité"),
            Map.entry("prixMarche", "Prix marché"),
            Map.entry("prixReprise", "Prix reprise"),
            Map.entry("prixVente", "Prix de vente"),
            Map.entry("referenceAnnonce", "Référence annonce"),
            Map.entry("typeTransaction", "Type de transaction"),
            Map.entry("sourceDetaillee", "Source détaillée")
    );

    private DonneesSupplementairesFormatter() {
    }

    public static String formater(final Map<String, Object> donnees) {
        if (donnees == null || donnees.isEmpty()) return "Aucune donnée supplémentaire disponible.";

        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Object> entry : donnees.entrySet()) {
            final String label = LABELS.getOrDefault(entry.getKey(), entry.getKey());
            final Object value = entry.getValue();
            if (value == null) continue;

            if (value instanceof Boolean) {
                sb.append("- ").append(label).append(" : ").append((Boolean) value ? "Oui" : "Non").append("\n");
            } else {
                String formatted = value.toString();
                if ("kilometrage".equals(entry.getKey())) formatted += " km";
                if (entry.getKey().contains("prix")) formatted += " EUR";
                sb.append("- ").append(label).append(" : ").append(formatted).append("\n");
            }
        }
        return sb.isEmpty() ? "Aucune donnée supplémentaire disponible." : sb.toString();
    }
}
