package fr.leadrevive.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void getCurrentTenantId_etatInitial_retourneNull() {
        // GIVEN / WHEN / THEN
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void getCurrentTenantId_apresSet_retourneLaValeur() {
        // GIVEN
        TenantContext.setCurrentTenantId(42L);

        // WHEN / THEN
        assertEquals(42L, TenantContext.getCurrentTenantId());
    }

    @Test
    void clear_apresSet_retourneNull() {
        // GIVEN
        TenantContext.setCurrentTenantId(42L);

        // WHEN
        TenantContext.clear();

        // THEN
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void setCurrentTenantId_appelMultiple_ecraseLaValeur() {
        // GIVEN
        TenantContext.setCurrentTenantId(1L);

        // WHEN
        TenantContext.setCurrentTenantId(2L);

        // THEN
        assertEquals(2L, TenantContext.getCurrentTenantId());
    }

    @Test
    void setCurrentTenantId_threadSepare_isolationEntreThreads() throws InterruptedException {
        // GIVEN
        TenantContext.setCurrentTenantId(1L);
        final AtomicReference<Long> autreThreadValue = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        // WHEN
        final Thread autreThread = new Thread(() -> {
            // Avant set : doit etre null (pas de partage)
            autreThreadValue.set(TenantContext.getCurrentTenantId());
            TenantContext.setCurrentTenantId(2L);
            latch.countDown();
            TenantContext.clear();
        });
        autreThread.start();
        latch.await();
        autreThread.join();

        // THEN
        assertAll(
                // Le thread principal n'est PAS impacte par les modifs du thread B
                () -> assertEquals(1L, TenantContext.getCurrentTenantId()),
                // Le thread B avait null avant son set
                () -> assertNull(autreThreadValue.get())
        );
    }

    @Test
    void clear_tenantJamaisSet_neLanceAucuneException() {
        // GIVEN / WHEN
        TenantContext.clear();

        // THEN
        assertNull(TenantContext.getCurrentTenantId());
    }

    @Test
    void setCurrentTenantId_avecNull_effaceLeTenant() {
        // GIVEN
        TenantContext.setCurrentTenantId(99L);

        // WHEN
        TenantContext.setCurrentTenantId(null);

        // THEN
        assertNull(TenantContext.getCurrentTenantId());
    }
}
