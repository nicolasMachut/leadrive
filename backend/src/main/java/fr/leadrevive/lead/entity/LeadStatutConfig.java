package fr.leadrevive.lead.entity;

import java.util.Map;

/**
 * Couleurs et labels centralisés pour les statuts de leads.
 * Source unique utilisée par le Kanban, les stats, et tout service
 * qui a besoin d'afficher un statut avec sa couleur.
 */
public final class LeadStatutConfig {

    public record Config(String label, String couleur) {}

    public static final Map<LeadStatutEnum, Config> CONFIGS = Map.of(
            LeadStatutEnum.NOUVEAU, new Config("Nouveau", "#6366F1"),
            LeadStatutEnum.CONTACTE, new Config("Contacté", "#F59E0B"),
            LeadStatutEnum.EN_COURS, new Config("Conversation en cours", "#10B981"),
            LeadStatutEnum.RDV_PRIS, new Config("RDV pris", "#06B6D4"),
            LeadStatutEnum.TERMINE, new Config("Terminé", "#6B7280")
    );

    private LeadStatutConfig() {}
}
