package fr.leadrevive.tache.service;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tache.entity.PrioriteTacheEnum;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.Tache;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import fr.leadrevive.tache.repository.TacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Cree automatiquement des taches quand l'IA detecte certains evenements.
 * Source = "IA_AUTO" pour toutes les taches creees ici.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TacheIAService {

    private final TacheRepository tacheRepository;
    private final LeadRepository leadRepository;

    /**
     * Appele quand l'IA detecte un RDV dans une conversation WhatsApp.
     * Cree une tache RDV dans le calendrier du vendeur assigne.
     */
    /**
     * Appele quand l'IA detecte un RDV dans une conversation WhatsApp.
     * Cree une tache RDV dans le calendrier du vendeur assigne.
     * @return la tache creee, ou null si non creee (doublon, lead introuvable)
     */
    @Transactional
    public Tache onRdvDetecte(final Long leadId, final Long tenantId, final LocalDateTime dateHeureRdv, final Long conversationUserId) {
        log.info("RDV detecte par IA - leadId: {}, dateHeure: {}", leadId, dateHeureRdv);

        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId).orElse(null);
        if (lead == null) {
            log.warn("Lead non trouve - leadId: {}", leadId);
            return null;
        }
        // Vendeur assigné au lead, sinon le créateur de la conversation
        final Long vendeurIdTache = lead.getVendeurId() != null ? lead.getVendeurId() : conversationUserId;
        if (vendeurIdTache == null) {
            log.warn("Pas de vendeur ni de createur de conversation - leadId: {}", leadId);
            return null;
        }

        // Si une tache RDV A_FAIRE existe deja, mettre a jour le creneau si different
        final java.util.Optional<Tache> existante = tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(
                leadId, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE);
        if (existante.isPresent()) {
            final Tache tacheExistante = existante.get();
            if (!dateHeureRdv.equals(tacheExistante.getDateEcheance())) {
                final LocalDateTime ancienCreneau = tacheExistante.getDateEcheance();
                tacheExistante.setDateEcheance(dateHeureRdv);
                tacheRepository.save(tacheExistante);
                log.info("Tache RDV mise a jour avec nouveau creneau - leadId: {}, ancien: {}, nouveau: {}",
                        leadId, ancienCreneau, dateHeureRdv);
            } else {
                log.info("Tache RDV deja existante avec le meme creneau - leadId: {}", leadId);
            }
            return tacheExistante;
        }

        final String vehicule = buildVehiculeLabel(lead);
        final String titre = "RDV " + lead.getNom() + (vehicule != null ? " — " + vehicule : "");

        final Tache tache = new Tache();
        tache.setTenantId(tenantId);
        tache.setVendeurId(vendeurIdTache);
        tache.setLeadId(leadId);
        tache.setTitre(titre);
        tache.setDescription("RDV détecté automatiquement par l'IA dans la conversation WhatsApp");
        tache.setTypeTache(TypeTacheEnum.RDV);
        tache.setDateEcheance(dateHeureRdv);
        tache.setDureeMinutes(30);
        tache.setPriorite(PrioriteTacheEnum.HAUTE);
        tache.setStatut(StatutTacheEnum.A_FAIRE);
        tache.setSource("IA_AUTO");
        tache.setRappelMinutesAvant(60); // 1h avant
        tache.setRappelEnvoye(false);
        tacheRepository.save(tache);

        log.info("Tache RDV creee automatiquement - leadId: {}, dateHeure: {}, vendeurId: {}", leadId, dateHeureRdv, lead.getVendeurId());
        return tache;
    }

    /**
     * Date/heure du RDV A_FAIRE en cours pour ce lead (ou null). Sert de CONTEXTE a
     * l'extraction IA : un re-planning ("vers 15h", "plutot 16h") doit rester le meme
     * jour que ce RDV, et l'analyse doit savoir qu'un RDV etait deja acte (→ decalage
     * = changement confirme, pas negociation neuve).
     */
    @Transactional(readOnly = true)
    public LocalDateTime dateRdvEnCours(final Long leadId) {
        return tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(
                        leadId, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE)
                .map(Tache::getDateEcheance)
                .orElse(null);
    }

    /**
     * Annule la tache RDV existante (statut A_FAIRE) pour un lead, par exemple quand
     * l'IA detecte que le prospect change d'avis (REFUS apres avoir accepte) ou demande
     * un STOP. Sans cette annulation, le RDV resterait dans le planning du vendeur et
     * declencherait des rappels inutiles.
     *
     * @return true si une tache a ete annulee, false sinon
     */
    @Transactional
    public boolean annulerRdvSiExiste(final Long leadId, final String motif) {
        final java.util.Optional<Tache> existante = tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(
                leadId, TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE);
        if (existante.isEmpty()) {
            return false;
        }
        final Tache tache = existante.get();
        tache.setStatut(StatutTacheEnum.ANNULEE);
        final String descriptionExistante = tache.getDescription() != null ? tache.getDescription() : "";
        tache.setDescription(descriptionExistante + "\n[Annulee automatiquement : " + motif + "]");
        tacheRepository.save(tache);
        log.info("Tache RDV annulee automatiquement - leadId: {}, motif: {}", leadId, motif);
        return true;
    }

    /**
     * Appele quand un nouveau lead est importe.
     * Cree une tache "Appeler" dans les 2h (heures ouvrables).
     */
    @Transactional
    public void onNouveauLead(final Long leadId, final Long tenantId) {
        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId).orElse(null);
        if (lead == null || lead.getVendeurId() == null) return;

        final boolean dejaExistant = tacheRepository.existsByLeadIdAndTypeTacheAndStatut(
                leadId, TypeTacheEnum.APPEL, StatutTacheEnum.A_FAIRE);
        if (dejaExistant) return;

        // Echeance = maintenant + 2h, bornee aux heures ouvrables (9h-18h)
        LocalDateTime echeance = LocalDateTime.now().plusHours(2);
        if (echeance.toLocalTime().isAfter(LocalTime.of(18, 0))) {
            echeance = echeance.plusDays(1).with(LocalTime.of(9, 0));
        }
        if (echeance.toLocalTime().isBefore(LocalTime.of(9, 0))) {
            echeance = echeance.with(LocalTime.of(9, 0));
        }

        final Tache tache = new Tache();
        tache.setTenantId(tenantId);
        tache.setVendeurId(lead.getVendeurId());
        tache.setLeadId(leadId);
        tache.setTitre("Appeler " + lead.getNom());
        tache.setTypeTache(TypeTacheEnum.APPEL);
        tache.setDateEcheance(echeance);
        tache.setPriorite(PrioriteTacheEnum.NORMALE);
        tache.setStatut(StatutTacheEnum.A_FAIRE);
        tache.setSource("IA_AUTO");
        tache.setRappelMinutesAvant(15);
        tache.setRappelEnvoye(false);
        tacheRepository.save(tache);

        log.info("Tache Appel creee pour nouveau lead - leadId: {}", leadId);
    }

    /**
     * Appele apres un premier contact sans RDV obtenu.
     * Cree une tache de relance a +48h.
     */
    @Transactional
    public void onContactSansRdv(final Long leadId, final Long tenantId) {
        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId).orElse(null);
        if (lead == null || lead.getVendeurId() == null) return;

        final boolean dejaExistant = tacheRepository.existsByLeadIdAndTypeTacheAndStatut(
                leadId, TypeTacheEnum.RELANCE, StatutTacheEnum.A_FAIRE);
        if (dejaExistant) return;

        final Tache tache = new Tache();
        tache.setTenantId(tenantId);
        tache.setVendeurId(lead.getVendeurId());
        tache.setLeadId(leadId);
        tache.setTitre("Relancer " + lead.getNom());
        tache.setTypeTache(TypeTacheEnum.RELANCE);
        tache.setDateEcheance(LocalDateTime.now().plusHours(48));
        tache.setPriorite(PrioriteTacheEnum.NORMALE);
        tache.setStatut(StatutTacheEnum.A_FAIRE);
        tache.setSource("IA_AUTO");
        tache.setRappelMinutesAvant(15);
        tache.setRappelEnvoye(false);
        tacheRepository.save(tache);

        log.info("Tache Relance creee pour contact sans RDV - leadId: {}", leadId);
    }

    /**
     * Appele quand le max de relances WhatsApp automatiques est atteint sans reponse.
     * Cree une tache de relance manuelle pour le vendeur.
     */
    @Transactional
    public void onMaxRelancesAtteint(final Long leadId, final Long tenantId, final Long conversationUserId) {
        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId).orElse(null);
        if (lead == null) return;

        final boolean dejaExistant = tacheRepository.existsByLeadIdAndTypeTacheAndStatut(
                leadId, TypeTacheEnum.RELANCE, StatutTacheEnum.A_FAIRE);
        if (dejaExistant) return;

        final Long vendeurIdTache = lead.getVendeurId() != null ? lead.getVendeurId() : conversationUserId;
        if (vendeurIdTache == null) return;

        final Tache tache = new Tache();
        tache.setTenantId(tenantId);
        tache.setVendeurId(vendeurIdTache);
        tache.setLeadId(leadId);
        tache.setTitre("Appeler " + lead.getNom() + " (sans réponse WhatsApp)");
        tache.setDescription("Le prospect n'a pas répondu après les relances automatiques WhatsApp. Un appel téléphonique est recommandé.");
        tache.setTypeTache(TypeTacheEnum.RELANCE);
        tache.setDateEcheance(LocalDateTime.now().plusHours(2));
        tache.setPriorite(PrioriteTacheEnum.HAUTE);
        tache.setStatut(StatutTacheEnum.A_FAIRE);
        tache.setSource("IA_AUTO");
        tache.setRappelMinutesAvant(15);
        tache.setRappelEnvoye(false);
        tacheRepository.save(tache);

        log.info("Tache Relance creee apres max relances WhatsApp - leadId: {}", leadId);
    }

    /**
     * Appele quand le vendeur declare une vente en cours via le feedback post-RDV
     * (suite=VENTE_EN_COURS). Cree une tache "Confirmer la vente" dans son planning
     * pour qu'il y revienne avec le montant exact. Sans ca, le feedback est
     * enregistre mais aucune entite Vente n'est creee => stats CA biaisees, lead
     * marque TERMINE sans trace de vente.
     */
    @Transactional
    public void onVenteEnCoursDeclaree(final Long leadId, final Long tenantId, final Long vendeurId) {
        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId).orElse(null);
        if (lead == null) return;
        // Idempotence : si une tache "Confirmer vente" existe deja A_FAIRE, ne pas en creer une 2e.
        final List<Tache> existantes = tacheRepository.findByLeadIdAndTypeTacheAndStatutOrderByCreeLe(
                leadId, TypeTacheEnum.AUTRE, StatutTacheEnum.A_FAIRE);
        final boolean dejaCree = existantes.stream()
                .anyMatch(t -> t.getTitre() != null && t.getTitre().startsWith("Confirmer la vente"));
        if (dejaCree) return;

        final Tache tache = new Tache();
        tache.setTenantId(tenantId);
        tache.setVendeurId(vendeurId != null ? vendeurId : lead.getVendeurId());
        tache.setLeadId(leadId);
        tache.setTitre("Confirmer la vente : " + lead.getNom());
        tache.setDescription("Vente declaree en cours via le feedback RDV. Renseigner le montant "
                + "exact et la date de signature pour finaliser la vente (et les stats CA).");
        tache.setTypeTache(TypeTacheEnum.AUTRE);
        tache.setDateEcheance(LocalDateTime.now().plusDays(3));
        tache.setPriorite(PrioriteTacheEnum.HAUTE);
        tache.setStatut(StatutTacheEnum.A_FAIRE);
        tache.setSource("FEEDBACK_RDV");
        tache.setRappelMinutesAvant(60);
        tache.setRappelEnvoye(false);
        tacheRepository.save(tache);
        log.info("Tache 'Confirmer la vente' creee apres feedback - leadId: {}, vendeurId: {}",
                leadId, vendeurId);
    }

    private String buildVehiculeLabel(final Lead lead) {
        if (lead.getMarque() != null || lead.getModele() != null) {
            return ((lead.getMarque() != null ? lead.getMarque() : "") + " " + (lead.getModele() != null ? lead.getModele() : "")).trim();
        }
        return null;
    }
}
