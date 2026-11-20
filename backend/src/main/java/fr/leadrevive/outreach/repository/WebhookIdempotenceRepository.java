package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.WebhookIdempotence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface WebhookIdempotenceRepository extends JpaRepository<WebhookIdempotence, String> {

    /**
     * Insert atomique avec {@code ON CONFLICT DO NOTHING} : si le messageSid existe deja,
     * retourne 0 (pas d'insertion) ; sinon retourne 1. Permet de detecter un retry Twilio
     * sans race condition entre plusieurs instances Railway.
     *
     * @return 1 si nouvelle entree, 0 si deja traite
     */
    @Modifying
    @Query(value = """
            INSERT INTO webhook_idempotence (message_sid, source, traite_le)
            VALUES (:messageSid, :source, :traiteLe)
            ON CONFLICT (message_sid) DO NOTHING
            """, nativeQuery = true)
    int insertSiNouveau(@Param("messageSid") String messageSid,
                         @Param("source") String source,
                         @Param("traiteLe") LocalDateTime traiteLe);

    /** Purge les entrees plus anciennes que {@code seuil}. Appele par un cron. */
    @Modifying
    @Query("DELETE FROM WebhookIdempotence w WHERE w.traiteLe < :seuil")
    int purgerAvant(@Param("seuil") LocalDateTime seuil);

    /**
     * Supprime le marqueur d'idempotence d'un SID. Appele UNIQUEMENT quand le traitement
     * metier du webhook a echoue : sans ca, le marqueur pose en amont (anti-concurrence)
     * bloquerait definitivement le retry Twilio et le prospect resterait sans reponse.
     */
    @Modifying
    @Query("DELETE FROM WebhookIdempotence w WHERE w.messageSid = :messageSid")
    int supprimerParSid(@Param("messageSid") String messageSid);
}
