package fr.leadrevive.lead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepLeadDto {

    private Long idLead;
    private Long tenantId;
    private String nom;
    private String telephone;
    private String email;
    private String marque;
    private String modele;
    private String source;
    private String territoire;
    private String etape;
    private BigDecimal scoring;
    /** Score d'engagement comportemental (0-1), recalcule par l'IA apres chaque message du prospect. */
    private BigDecimal hotnessScore;
    private LocalDateTime hotnessCalculeLe;
    private String devis;
    private Boolean reprise;
    private String typeOpportunite;
    private LocalDateTime dateModificationDevis;
    private String statut;
    private Long vendeurId;
    private Long importId;
    private LocalDateTime creeLe;
    private Map<String, Object> donneesSupplementaires;
    private String modeImport;
    /** true si le lead est archivé (sorti des listes actives). Permet à la fiche
     *  d'afficher la bannière "archivé" + le bouton "Désarchiver" au lieu de "Archiver". */
    private Boolean archive;
    /** Horodatage de l'envoi auto du premier message WhatsApp en attente (null si aucun).
     *  Echeance BRUTE armee (creation + delai), sans tenir compte de la plage horaire.
     *  Conserve car necessaire a l'undo de "Annuler" (re-arme avec la valeur d'origine). */
    private LocalDateTime premierMessageAutoPrevuLe;
    /** Heure d'envoi EFFECTIVE calculee (tenant compte de la plage horaire d'envoi).
     *  null si aucun envoi n'est arme. C'est cette valeur qu'il faut afficher.
     *  Enrichi cote LeadService apres le mapping (Setter dedie). */
    @Setter
    private LocalDateTime premierMessageAutoEffectifLe;
    /** true si l'envoi effectif est repousse hors de son echeance brute (hors plage horaire).
     *  Enrichi cote LeadService apres le mapping (Setter dedie). */
    @Setter
    private boolean premierMessageAutoHorsPlage;
}
