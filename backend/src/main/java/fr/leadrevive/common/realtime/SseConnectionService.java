package fr.leadrevive.common.realtime;

import fr.leadrevive.common.realtime.dto.RealtimeEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registre central des connexions SSE actives, organisees par tenant.
 *
 * <p>Multi-tenant : chaque event est diffuse UNIQUEMENT aux emitters du
 * meme tenantId. Pas de risque de fuite cross-tenant.</p>
 *
 * <p>Cleanup automatique des emitters morts (timeout / erreur / fermeture
 * client) via les callbacks {@link SseEmitter#onCompletion}, {@link SseEmitter#onTimeout},
 * {@link SseEmitter#onError}.</p>
 */
@Service
@Slf4j
public class SseConnectionService {

    /**
     * Timeout 1h. Au-dela, le browser EventSource se reconnecte tout seul.
     * Pas de risque de zombie : on retire l'emitter de la map a chaque close.
     */
    private static final long TIMEOUT_MS = 60L * 60L * 1000L;

    private final Map<Long, List<SseEmitter>> emittersByTenant = new ConcurrentHashMap<>();

    /**
     * Cree un nouvel emitter pour le tenant donne, l'enregistre dans la map,
     * et le retourne pour que le controller le rende.
     */
    public SseEmitter creerEmitter(final Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId requis pour creer un SSE emitter");
        }
        final SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        final List<SseEmitter> liste = emittersByTenant.computeIfAbsent(
                tenantId, k -> new CopyOnWriteArrayList<>());
        liste.add(emitter);

        emitter.onCompletion(() -> retirerEmitter(tenantId, emitter, "completion"));
        emitter.onTimeout(() -> retirerEmitter(tenantId, emitter, "timeout"));
        emitter.onError(e -> retirerEmitter(tenantId, emitter, "erreur: " + e.getMessage()));

        log.info("SSE emitter cree - tenantId: {}, total emitters tenant: {}", tenantId, liste.size());

        // Premier message "ready" pour confirmer cote client que la connexion est ouverte.
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (final IOException e) {
            log.warn("Echec envoi event ready - tenantId: {}, cause: {}", tenantId, e.getMessage());
        }

        return emitter;
    }

    /**
     * Pousse un event a tous les emitters du tenant. Idempotent : si la liste
     * est vide (personne connecte), no-op silencieux.
     *
     * <p>Les erreurs d'envoi (client deconnecte entre temps, IO timeout) sont
     * loggees et l'emitter est retire — pas de propagation a l'appelant pour
     * eviter de casser le flot metier (creation de lead) a cause d'une
     * notification ratee.</p>
     */
    public void broadcast(final Long tenantId, final RealtimeEventDto event) {
        if (tenantId == null || event == null) return;
        final List<SseEmitter> liste = emittersByTenant.get(tenantId);
        if (liste == null || liste.isEmpty()) {
            return;
        }
        final List<SseEmitter> aRetirer = new ArrayList<>();
        for (final SseEmitter emitter : liste) {
            try {
                emitter.send(SseEmitter.event().name(event.type()).data(event));
            } catch (final Exception e) {
                log.warn("Echec broadcast SSE - tenantId: {}, event: {}, cause: {}",
                        tenantId, event.type(), e.getMessage());
                aRetirer.add(emitter);
            }
        }
        aRetirer.forEach(e -> retirerEmitter(tenantId, e, "envoi echoue"));
        log.info("Broadcast SSE - tenantId: {}, event: {}, emitters notifies: {}",
                tenantId, event.type(), liste.size() - aRetirer.size());
    }

    /**
     * Heartbeat : envoie un commentaire SSE a tous les emitters de tous les
     * tenants pour eviter que les proxies (Railway, Cloudflare) coupent les
     * connexions idle. Appele toutes les 25s par {@link RealtimeHeartbeatJob}.
     */
    public void envoyerHeartbeat() {
        for (final Map.Entry<Long, List<SseEmitter>> entry : emittersByTenant.entrySet()) {
            final Long tenantId = entry.getKey();
            final List<SseEmitter> aRetirer = new ArrayList<>();
            for (final SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (final Exception e) {
                    aRetirer.add(emitter);
                }
            }
            aRetirer.forEach(e -> retirerEmitter(tenantId, e, "heartbeat echoue"));
        }
    }

    /**
     * Nombre total d'emitters actifs (tous tenants confondus) — utile pour
     * debug / metriques Actuator si besoin futur.
     */
    public int totalEmitters() {
        return emittersByTenant.values().stream().mapToInt(List::size).sum();
    }

    private void retirerEmitter(final Long tenantId, final SseEmitter emitter, final String raison) {
        final List<SseEmitter> liste = emittersByTenant.get(tenantId);
        if (liste != null) {
            liste.remove(emitter);
            if (liste.isEmpty()) {
                emittersByTenant.remove(tenantId);
            }
        }
        log.info("SSE emitter retire - tenantId: {}, raison: {}", tenantId, raison);
    }
}
