package fr.leadrevive.common.security;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tache.entity.Tache;
import fr.leadrevive.tache.repository.TacheRepository;
import fr.leadrevive.user.entity.RoleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Garde-fou de visibilite intra-tenant : verifie qu'un VENDEUR n'accede pas
 * aux ressources (lead, tache, conversation) d'un autre vendeur du meme tenant.
 *
 * <p>Centralise le pattern :
 * <pre>
 *   if (role == VENDEUR &amp;&amp; ressource.vendeurId != currentUserId) -> 404 anonyme
 * </pre>
 *
 * <p>Retourne 404 (pas 403) pour ne pas confirmer l'existence d'une ressource
 * d'un autre vendeur (oracle d'enumeration).
 *
 * <p>Les MANAGER et SUPER_ADMIN passent sans check supplementaire (visibilite tenant
 * complete pour le manager, cross-tenant via filtre desactive pour l'admin).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadAccessGuard {

    private final AuthService authService;
    private final LeadRepository leadRepository;
    private final TacheRepository tacheRepository;

    /**
     * Verifie que l'utilisateur courant a le droit d'acceder a ce lead.
     * Charge l'entite, valide le tenant ET la propriete (pour VENDEUR).
     *
     * @return le Lead charge si l'acces est autorise
     * @throws ResponseStatusException 404 si lead introuvable ou non accessible
     */
    public Lead requireAccessibleLead(final Long leadId) {
        final Long tenantId = authService.getCurrentTenantId();
        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead introuvable"));

        if (isVendeur() && !isOwnedByCurrentUser(lead.getVendeurId())) {
            log.warn("Acces refuse a un lead non-assigne au vendeur courant - leadId: {}, vendeurLead: {}, userCourant: {}",
                    leadId, lead.getVendeurId(), authService.getCurrentUserId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead introuvable");
        }
        return lead;
    }

    /**
     * Verifie qu'un VENDEUR ne se voit pas attribuer (via PUT) un champ qu'il ne
     * doit pas pouvoir muter (par exemple {@code vendeurId} d'un lead).
     * Throw 403 si tentative de mutation interdite.
     */
    public void interdireSiVendeur(final String champ) {
        if (isVendeur()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Champ '" + champ + "' non modifiable par un vendeur.");
        }
    }

    /**
     * Verifie qu'une tache est accessible : meme tenant ET (pour VENDEUR) appartient
     * a l'utilisateur courant.
     */
    public Tache requireAccessibleTache(final Long tacheId) {
        final Long tenantId = authService.getCurrentTenantId();
        final Tache tache = tacheRepository.findById(tacheId)
                .filter(t -> tenantId != null && tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tache introuvable"));

        if (isVendeur() && !isOwnedByCurrentUser(tache.getVendeurId())) {
            log.warn("Acces refuse a une tache non-assignee au vendeur courant - tacheId: {}, vendeurTache: {}, userCourant: {}",
                    tacheId, tache.getVendeurId(), authService.getCurrentUserId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tache introuvable");
        }
        return tache;
    }

    private boolean isVendeur() {
        return "VENDEUR".equals(authService.getCurrentRole());
    }

    private boolean isOwnedByCurrentUser(final Long ownerVendeurId) {
        final Long currentUserId = authService.getCurrentUserId();
        return ownerVendeurId != null && currentUserId != null && ownerVendeurId.equals(currentUserId);
    }
}
