package fr.leadrevive.outreach.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests des garde-fous du client Meta Graph (sans réseau). On vérifie surtout
 * que l'absence de credentials ou de paramètres renvoie {@code null} sans lever
 * d'exception (comportement non-fatal attendu par le flow d'activation).
 */
class MetaGraphApiClientTest {

    private final MetaGraphApiClient client = new MetaGraphApiClient(JsonMapper.builder().build());

    @Test
    void nonConfigure_retourneNull() {
        // app-id / app-secret non injectés -> estConfigure() == false
        assertThat(client.estConfigure()).isFalse();
        assertThat(client.recupererInfosNumero("code", "WABA")).isNull();
    }

    @Test
    void configureMaisParametresManquants_retourneNull() {
        ReflectionTestUtils.setField(client, "appId", "APP_ID");
        ReflectionTestUtils.setField(client, "appSecret", "SECRET");

        assertThat(client.estConfigure()).isTrue();
        assertThat(client.recupererInfosNumero(null, "WABA")).isNull();
        assertThat(client.recupererInfosNumero("code", null)).isNull();
        assertThat(client.recupererInfosNumero("  ", "  ")).isNull();
    }
}
