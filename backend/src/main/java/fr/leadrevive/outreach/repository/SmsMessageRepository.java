package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SmsMessageRepository extends JpaRepository<SmsMessage, Long> {

    List<SmsMessage> findByConversationIdOrderByOrdre(String conversationId);

    /**
     * Retourne l'ordre suivant a utiliser pour un nouvel SmsMessage. Calcul SQL
     * dans la meme transaction que l'INSERT a venir, ce qui reduit la fenetre
     * de race condition entre 2 ecritures concurrentes (webhook entrant + envoi
     * manuel par le vendeur, par exemple).
     *
     * <p>Note : pour une protection 100% sure, il faudrait une unique constraint
     * sur (conversation_id, ordre). Ici on minimise la fenetre.</p>
     */
    @Query("SELECT COALESCE(MAX(m.ordre), -1) + 1 FROM SmsMessage m WHERE m.conversationId = :conversationId")
    int findProchainOrdre(@Param("conversationId") String conversationId);

    /**
     * Charge en une seule requete le dernier message (max ordre) de chaque conversation.
     * Evite le N+1 query dans l'inbox quand on liste des dizaines/centaines de conversations.
     */
    @Query("""
            SELECT m FROM SmsMessage m
            WHERE m.conversationId IN :conversationIds
            AND m.ordre = (
                SELECT MAX(m2.ordre) FROM SmsMessage m2
                WHERE m2.conversationId = m.conversationId
            )
            """)
    List<SmsMessage> findDerniersMessages(@Param("conversationIds") List<String> conversationIds);

    Optional<SmsMessage> findByBrevoMessageId(String brevoMessageId);

    /**
     * Charge un message en le scopant à sa conversation ET au tenant. À utiliser
     * pour toute action sur un message identifié par son id (retry, etc.) : un
     * findById(messageId) nu permet un IDOR cross-tenant (renvoi du contenu d'un
     * message d'un autre tenant vers le prospect courant).
     */
    Optional<SmsMessage> findByIdAndConversationIdAndTenantId(Long id, String conversationId, Long tenantId);

    /**
     * Date du dernier message ENTRANT (prospect) d'une conversation. Sert à vérifier
     * la fenêtre WhatsApp de 24h avant un envoi manuel en message de session.
     */
    @Query("""
            SELECT MAX(m.creeLe) FROM SmsMessage m
            WHERE m.conversationId = :conversationId
            AND m.direction = fr.leadrevive.outreach.entity.DirectionSmsEnum.ENTRANT
            """)
    Optional<LocalDateTime> findDernierMessageEntrantLe(@Param("conversationId") String conversationId);

    List<SmsMessage> findByStatut(StatutSmsEnum statut);

    @Query("""
            SELECT m.direction, COUNT(m) FROM SmsMessage m
            WHERE m.tenantId = :tenantId
            AND m.creeLe >= :depuis
            GROUP BY m.direction
            """)
    List<Object[]> countByDirectionDepuis(@Param("tenantId") Long tenantId,
                                           @Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT m.direction, COUNT(m) FROM SmsMessage m
            WHERE m.creeLe >= :depuis
            GROUP BY m.direction
            """)
    List<Object[]> countGlobalByDirectionDepuis(@Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT COUNT(m) FROM SmsMessage m
            WHERE m.statut = fr.leadrevive.outreach.entity.StatutSmsEnum.ECHEC
            AND m.creeLe >= :depuis
            """)
    Long countGlobalEchecsDepuis(@Param("depuis") LocalDateTime depuis);

    /**
     * Agrege les messages en ECHEC par tenant depuis une date : pour chaque tenant,
     * le nombre d'echecs et la date du dernier. Utilise par la console admin WhatsApp
     * (rollup "messages en erreur") - calcul a la volee, pas de compteur denormalise.
     * Retourne des lignes {@code [tenantId(Long), count(Long), dernierEchec(LocalDateTime)]}.
     */
    @Query("""
            SELECT m.tenantId, COUNT(m), MAX(m.creeLe) FROM SmsMessage m
            WHERE m.statut = fr.leadrevive.outreach.entity.StatutSmsEnum.ECHEC
            AND m.creeLe >= :depuis
            GROUP BY m.tenantId
            """)
    List<Object[]> countEchecsParTenantDepuis(@Param("depuis") LocalDateTime depuis);

    /**
     * Detail des motifs d'echec par tenant depuis une date : pour chaque couple
     * (tenant, motif brut), le nombre d'occurrences. Permet a la console admin
     * d'expliquer POURQUOI les messages echouent (et pas seulement combien).
     * Retourne des lignes {@code [tenantId(Long), erreur(String), count(Long)]}.
     * Les messages en ECHEC sans motif (erreur null) sont exclus.
     */
    @Query("""
            SELECT m.tenantId, m.erreur, COUNT(m) FROM SmsMessage m
            WHERE m.statut = fr.leadrevive.outreach.entity.StatutSmsEnum.ECHEC
            AND m.creeLe >= :depuis
            AND m.erreur IS NOT NULL
            GROUP BY m.tenantId, m.erreur
            """)
    List<Object[]> countEchecsParTenantParMotifDepuis(@Param("depuis") LocalDateTime depuis);

    /**
     * Motifs (colonne erreur) des messages en ECHEC d'un tenant depuis une date.
     * Sert a detecter un numero/sender WhatsApp indisponible (signal "envois en echec"
     * affiche au manager) sans dependre d'un compteur denormalise.
     */
    @Query("""
            SELECT m.erreur FROM SmsMessage m
            WHERE m.tenantId = :tenantId
            AND m.statut = fr.leadrevive.outreach.entity.StatutSmsEnum.ECHEC
            AND m.creeLe >= :depuis
            AND m.erreur IS NOT NULL
            """)
    List<String> findErreursEchecDepuis(@Param("tenantId") Long tenantId,
                                        @Param("depuis") LocalDateTime depuis);

    // ─────────────────────────────────────────────────────────────────────────
    // Signal "numero indisponible" base sur l'ETAT COURANT (pas juste une fenetre
    // d'echecs) : un vieil echec ne doit plus alarmer si un envoi a reussi depuis.
    // On compare la date du dernier echec "numero indisponible" a celle du dernier
    // envoi reussi. Detection Java (codes en util), d'ou le renvoi de la date + motif.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Date du dernier envoi SORTANT reussi (ENVOYE / DELIVRE / LU) d'un tenant depuis
     * une date. Null si aucun. Un envoi reussi apres un echec prouve que le numero
     * refonctionne -> le signal "numero indisponible" doit s'eteindre.
     */
    @Query("""
            SELECT MAX(m.creeLe) FROM SmsMessage m
            WHERE m.tenantId = :tenantId
            AND m.direction = fr.leadrevive.outreach.entity.DirectionSmsEnum.SORTANT
            AND m.statut IN (fr.leadrevive.outreach.entity.StatutSmsEnum.ENVOYE,
                             fr.leadrevive.outreach.entity.StatutSmsEnum.DELIVRE,
                             fr.leadrevive.outreach.entity.StatutSmsEnum.LU)
            AND m.creeLe >= :depuis
            """)
    LocalDateTime findDerniereDateEnvoiReussi(@Param("tenantId") Long tenantId,
                                              @Param("depuis") LocalDateTime depuis);

    /**
     * Motifs + date des messages en ECHEC d'un tenant depuis une date.
     * Retourne des lignes {@code [erreur(String), creeLe(LocalDateTime)]} pour permettre
     * de dater le dernier echec "numero indisponible" (detection Java sur le motif).
     */
    @Query("""
            SELECT m.erreur, m.creeLe FROM SmsMessage m
            WHERE m.tenantId = :tenantId
            AND m.statut = fr.leadrevive.outreach.entity.StatutSmsEnum.ECHEC
            AND m.creeLe >= :depuis
            AND m.erreur IS NOT NULL
            """)
    List<Object[]> findEchecsAvecDateDepuis(@Param("tenantId") Long tenantId,
                                            @Param("depuis") LocalDateTime depuis);

    /**
     * Par tenant : date du dernier envoi SORTANT reussi (ENVOYE / DELIVRE / LU) depuis
     * une date. Retourne des lignes {@code [tenantId(Long), dernierEnvoiReussi(LocalDateTime)]}.
     * Utilise par la console admin pour n'alerter "numero indisponible" que si aucun
     * envoi n'a reussi depuis le dernier echec.
     */
    @Query("""
            SELECT m.tenantId, MAX(m.creeLe) FROM SmsMessage m
            WHERE m.direction = fr.leadrevive.outreach.entity.DirectionSmsEnum.SORTANT
            AND m.statut IN (fr.leadrevive.outreach.entity.StatutSmsEnum.ENVOYE,
                             fr.leadrevive.outreach.entity.StatutSmsEnum.DELIVRE,
                             fr.leadrevive.outreach.entity.StatutSmsEnum.LU)
            AND m.creeLe >= :depuis
            GROUP BY m.tenantId
            """)
    List<Object[]> findDerniereDateEnvoiReussiParTenant(@Param("depuis") LocalDateTime depuis);

    /**
     * Par (tenant, motif) : date du dernier ECHEC depuis une date.
     * Retourne des lignes {@code [tenantId(Long), erreur(String), dernierEchec(LocalDateTime)]}.
     * Permet a la console admin de dater le dernier echec "numero indisponible" (detection
     * Java sur le motif) et de le comparer au dernier envoi reussi.
     */
    @Query("""
            SELECT m.tenantId, m.erreur, MAX(m.creeLe) FROM SmsMessage m
            WHERE m.statut = fr.leadrevive.outreach.entity.StatutSmsEnum.ECHEC
            AND m.creeLe >= :depuis
            AND m.erreur IS NOT NULL
            GROUP BY m.tenantId, m.erreur
            """)
    List<Object[]> findEchecsParTenantParMotifAvecDerniereDate(@Param("depuis") LocalDateTime depuis);
}
