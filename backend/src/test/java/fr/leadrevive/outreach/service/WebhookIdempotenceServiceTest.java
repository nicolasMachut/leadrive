package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.repository.WebhookIdempotenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de l'idempotence DB des webhooks externes.
 *
 * Critique en multi-instance : un retry Twilio peut atterrir sur une autre replica
 * Railway que celle qui a recu l'appel initial. Sans cette protection, double appel
 * IA + double reponse au prospect.
 */
@ExtendWith(MockitoExtension.class)
class WebhookIdempotenceServiceTest {

    @Mock private WebhookIdempotenceRepository repository;

    private WebhookIdempotenceService service;

    @BeforeEach
    void setUp() {
        service = new WebhookIdempotenceService(repository);
    }

    @Test
    void enregistrerSiNouveau_premierAppel_retourneTrue() {
        // GIVEN : INSERT reussit (1 ligne inseree).
        when(repository.insertSiNouveau(eq("SM_abc"), eq("twilio"), any(LocalDateTime.class))).thenReturn(1);

        // WHEN
        final boolean nouveau = service.enregistrerSiNouveau("SM_abc", "twilio");

        // THEN : le webhook doit etre traite.
        assertThat(nouveau).isTrue();
    }

    @Test
    void enregistrerSiNouveau_retryDejaTraite_retourneFalse() {
        // GIVEN : ON CONFLICT DO NOTHING => 0 ligne inseree (SID deja en BDD).
        when(repository.insertSiNouveau(eq("SM_abc"), eq("twilio"), any(LocalDateTime.class))).thenReturn(0);

        // WHEN
        final boolean nouveau = service.enregistrerSiNouveau("SM_abc", "twilio");

        // THEN : le webhook doit etre IGNORE (sinon double traitement).
        assertThat(nouveau).isFalse();
    }

    @Test
    void enregistrerSiNouveau_sidNull_retourneTrueSansInsert() {
        // GIVEN : pas de MessageSid (rare mais possible sur anciens webhooks).
        // Plutot que de bloquer, on laisse passer.

        // WHEN
        final boolean nouveau = service.enregistrerSiNouveau(null, "twilio");

        // THEN
        assertThat(nouveau).isTrue();
        verify(repository, never()).insertSiNouveau(any(), any(), any());
    }

    @Test
    void enregistrerSiNouveau_sidBlank_retourneTrueSansInsert() {
        final boolean nouveau = service.enregistrerSiNouveau("   ", "twilio");
        assertThat(nouveau).isTrue();
        verify(repository, never()).insertSiNouveau(any(), any(), any());
    }

    @Test
    void purger_appelleRepositoryAvecSeuil24h() {
        // GIVEN
        when(repository.purgerAvant(any(LocalDateTime.class))).thenReturn(42);

        // WHEN
        service.purger();

        // THEN : le seuil est ~24h avant maintenant.
        final org.mockito.ArgumentCaptor<LocalDateTime> captor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).purgerAvant(captor.capture());
        final LocalDateTime seuil = captor.getValue();
        final LocalDateTime maintenant = LocalDateTime.now();
        assertThat(seuil).isBefore(maintenant.minusHours(23));
        assertThat(seuil).isAfter(maintenant.minusHours(25));
    }
}
