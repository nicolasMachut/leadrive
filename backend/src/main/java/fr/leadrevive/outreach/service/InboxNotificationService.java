package fr.leadrevive.outreach.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gere les emitters SSE de l'inbox avec un filtrage par utilisateur (vendeur).
 *
 * <p>Sans filtrage, un broadcast tenant-wide envoie le contenu des messages
 * WhatsApp de VENDEUR_B au front de VENDEUR_A (meme tenant) : fuite intra-tenant
 * contradictoire avec le durcissement vendeur cross-vendeur.</p>
 *
 * <p>API publique :
 * <ul>
 *   <li>{@code enregistrerClient(tenantId, userId, role)} : enregistre un emitter avec
 *       son contexte (utilise pour le filtrage)</li>
 *   <li>{@code notifier(...)} / {@code notifierApresCommit(...)} : broadcast tenant complet
 *       (events agreges/global, lus par managers)</li>
 *   <li>{@code notifierPourVendeur(...)} / {@code notifierApresCommitPourVendeur(...)} :
 *       envoie aux MANAGER du tenant ET au VENDEUR cible uniquement</li>
 * </ul></p>
 */
@Service
@Slf4j
public class InboxNotificationService {

    /**
     * Encapsule un emitter avec son contexte d'identite, indispensable pour
     * filtrer les broadcasts par vendeur (P1-2 audit visibilite).
     */
    private static final class EmitterAvecUser {
        final SseEmitter emitter;
        final Long userId;
        final String role;

        EmitterAvecUser(final SseEmitter emitter, final Long userId, final String role) {
            this.emitter = emitter;
            this.userId = userId;
            this.role = role;
        }
    }

    private final Map<Long, List<EmitterAvecUser>> emittersParTenant = new ConcurrentHashMap<>();

    /**
     * @param tenantId tenant du client connecte
     * @param userId   userId du client (peut etre null pour SUPER_ADMIN ou mirror absent)
     * @param role     "MANAGER" / "VENDEUR" / "SUPER_ADMIN" — pilote le filtrage
     */
    public SseEmitter enregistrerClient(final Long tenantId, final Long userId, final String role) {
        // Garde-fou : un user sans mirror BDD (donc sans tenantId) peut atteindre cet
        // endpoint via un JWT Clerk valide. ConcurrentHashMap.computeIfAbsent throw
        // NPE sur null key — on rejette plus proprement avant.
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Utilisateur non rattache a une concession");
        }
        final SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 min TTL
        final EmitterAvecUser wrapped = new EmitterAvecUser(emitter, userId, role);
        emittersParTenant.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(wrapped);

        emitter.onCompletion(() -> supprimerEmitter(tenantId, wrapped));
        emitter.onTimeout(() -> supprimerEmitter(tenantId, wrapped));
        emitter.onError(e -> supprimerEmitter(tenantId, wrapped));

        log.info("Client SSE inbox enregistre - tenantId: {}, userId: {}, role: {}", tenantId, userId, role);
        return emitter;
    }

    /**
     * Backward-compat : enregistre un emitter sans contexte utilisateur (recevra
     * tous les broadcasts tenant). A eviter pour les nouveaux callsites.
     */
    public SseEmitter enregistrerClient(final Long tenantId) {
        return enregistrerClient(tenantId, null, null);
    }

    /**
     * Envoie une notification SSE immediatement a TOUS les emitters du tenant.
     *
     * Quand un client est deconnecte (Broken pipe), on appelle completeWithError
     * sur l'emitter pour notifier Tomcat que la connection est morte.
     */
    public void notifier(final Long tenantId, final String eventName, final Object data) {
        notifierAvecFiltre(tenantId, null, eventName, data);
    }

    /**
     * Envoie une notification SSE uniquement aux MANAGER du tenant ET au VENDEUR
     * cible {@code vendeurIdCible}. Utilise pour les events lies a une conversation
     * specifique (nouveau_message, handoff_humain) : un autre vendeur du meme
     * tenant ne doit pas voir le contenu.
     */
    public void notifierPourVendeur(final Long tenantId, final Long vendeurIdCible,
                                     final String eventName, final Object data) {
        notifierAvecFiltre(tenantId, vendeurIdCible, eventName, data);
    }

    private void notifierAvecFiltre(final Long tenantId, final Long vendeurIdCible,
                                     final String eventName, final Object data) {
        final List<EmitterAvecUser> emitters = emittersParTenant.get(tenantId);
        if (emitters == null || emitters.isEmpty()) return;

        final List<EmitterAvecUser> morts = new ArrayList<>();
        for (final EmitterAvecUser wrapped : emitters) {
            // Filtre : si vendeurIdCible est defini, seuls les MANAGER et le VENDEUR
            // titulaire recoivent l'event. Les autres VENDEUR du meme tenant sont
            // exclus pour empecher la fuite intra-tenant du contenu des messages.
            if (vendeurIdCible != null && "VENDEUR".equals(wrapped.role)
                    && !vendeurIdCible.equals(wrapped.userId)) {
                continue;
            }
            try {
                wrapped.emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (final Exception e) {
                try {
                    wrapped.emitter.completeWithError(e);
                } catch (final Exception ignore) {
                    // emitter deja complete, on ignore
                }
                morts.add(wrapped);
            }
        }
        emitters.removeAll(morts);
    }

    /**
     * Envoie une notification SSE APRES le commit de la transaction en cours.
     * Garantit que les donnees sont visibles en base quand le frontend recoit l'evenement.
     */
    public void notifierApresCommit(final Long tenantId, final String eventName, final Object data) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifier(tenantId, eventName, data);
                }
            });
        } else {
            notifier(tenantId, eventName, data);
        }
    }

    /**
     * Variante apres-commit avec filtrage par vendeur cible.
     */
    public void notifierApresCommitPourVendeur(final Long tenantId, final Long vendeurIdCible,
                                                final String eventName, final Object data) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifierPourVendeur(tenantId, vendeurIdCible, eventName, data);
                }
            });
        } else {
            notifierPourVendeur(tenantId, vendeurIdCible, eventName, data);
        }
    }

    private void supprimerEmitter(final Long tenantId, final EmitterAvecUser wrapped) {
        final List<EmitterAvecUser> emitters = emittersParTenant.get(tenantId);
        if (emitters != null) {
            emitters.remove(wrapped);
        }
    }
}
