package fr.leadrevive.common.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pousse un commentaire SSE toutes les 25s a toutes les connexions actives
 * pour eviter que les proxies (Railway, Cloudflare) ne coupent les
 * connexions idle (timeout typique : 30-60s sans trafic).
 */
@Component
@RequiredArgsConstructor
public class RealtimeHeartbeatJob {

    private final SseConnectionService sseConnectionService;

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        sseConnectionService.envoyerHeartbeat();
    }
}
