package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SmsConversationRepository extends JpaRepository<SmsConversation, Long> {

    Optional<SmsConversation> findByConversationId(String conversationId);

    /** Securise multi-tenant : ne retourne la conversation que si elle appartient au tenant. */
    Optional<SmsConversation> findByConversationIdAndTenantId(String conversationId, Long tenantId);

    /**
     * Met a jour UNIQUEMENT la date de derniere lecture (suivi de lecture inbox), via un
     * UPDATE cible qui ne charge pas l'entite et ne participe donc PAS au bump de {@code @Version}.
     *
     * <p><strong>Pourquoi</strong> : {@code derniere_lecture_le} est une donnee de confort UI,
     * pas metier. L'ancien load + setter + save incrementait la version optimiste : si un manager
     * ouvrait une conversation (= marquage lu) pendant qu'un webhook Twilio traitait la reponse du
     * prospect (appel IA ~10s tenant l'entite chargee), le save final du webhook levait
     * {@code ObjectOptimisticLockingFailureException} et le prospect ne recevait jamais de reponse.
     * Un UPDATE cible ne touche que cette colonne, ne collisionne plus avec le traitement IA.</p>
     *
     * @return le nombre de lignes mises a jour (0 si conversation introuvable pour ce tenant)
     */
    @Modifying
    @Query("""
            UPDATE SmsConversation c
            SET c.derniereLectureLe = :derniereLectureLe
            WHERE c.conversationId = :conversationId AND c.tenantId = :tenantId
            """)
    int updateDerniereLecture(@Param("conversationId") String conversationId,
                              @Param("tenantId") Long tenantId,
                              @Param("derniereLectureLe") LocalDateTime derniereLectureLe);

    /**
     * Bookkeeping post-envoi ({@code nombre_messages} + {@code dernier_message_le}) via UPDATE
     * cible qui ne bump PAS le {@code @Version}.
     *
     * <p><strong>Pourquoi</strong> : ces deux champs sont mis a jour APRES l'envoi effectif du
     * message au prospect (action externe irreversible). Un {@code save(conversation)} classique
     * comparerait le {@code @Version} ; s'il a bouge pendant la generation IA (3-8s : marquage lu
     * residuel, toggle mode, relance), il leverait {@code ObjectOptimisticLockingFailureException}
     * qui remonterait au webhook -> 500 -> retry Twilio -> le prospect recevrait une 2e reponse.
     * L'UPDATE cible ne peut pas echouer sur conflit optimiste, donc pas de double-envoi.</p>
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE SmsConversation c
            SET c.nombreMessages = :nombreMessages, c.dernierMessageLe = :dernierMessageLe
            WHERE c.conversationId = :conversationId
            """)
    int updateBookkeepingApresEnvoi(@Param("conversationId") String conversationId,
                                    @Param("nombreMessages") Integer nombreMessages,
                                    @Param("dernierMessageLe") LocalDateTime dernierMessageLe);

    /**
     * Variante du bookkeeping post-envoi qui met aussi a jour {@code mode_pilotage} (bascule
     * HUMAIN du fallback IA indisponible), via UPDATE cible sans bump de {@code @Version}.
     * Meme raison que {@link #updateBookkeepingApresEnvoi} : le message est deja parti au
     * prospect, l'ecriture ne doit pas pouvoir echouer sur conflit optimiste (-> double-envoi).
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE SmsConversation c
            SET c.modePilotage = :modePilotage, c.nombreMessages = :nombreMessages, c.dernierMessageLe = :dernierMessageLe
            WHERE c.conversationId = :conversationId
            """)
    int updateModeEtBookkeepingApresEnvoi(@Param("conversationId") String conversationId,
                                          @Param("modePilotage") fr.leadrevive.outreach.entity.ModePilotageEnum modePilotage,
                                          @Param("nombreMessages") Integer nombreMessages,
                                          @Param("dernierMessageLe") LocalDateTime dernierMessageLe);

    /**
     * Purge RGPD : conversations dont le statut est dans la liste donnee ET creees avant {@code seuil}.
     * Job cross-tenant : le filtre Hibernate doit etre desactive par l'appelant.
     * Remplace l'ancien findAll() + stream() qui chargeait toute la table.
     */
    @Query("""
            SELECT c FROM SmsConversation c
            WHERE c.statut IN :statuts
            AND c.creeLe IS NOT NULL
            AND c.creeLe < :seuil
            """)
    List<SmsConversation> findFermeesAvant(@Param("statuts") List<StatutConversationSmsEnum> statuts,
                                            @Param("seuil") LocalDateTime seuil);

    /** Retourne la conversation la plus recente pour ce numero + statut (gere les doublons). */
    @Query("""
            SELECT c FROM SmsConversation c
            WHERE c.numeroProspect = :numero AND c.statut = :statut
            ORDER BY c.dernierMessageLe DESC NULLS LAST
            LIMIT 1
            """)
    Optional<SmsConversation> findMostRecentByNumeroProspectAndStatut(@Param("numero") String numeroProspect,
                                                                      @Param("statut") StatutConversationSmsEnum statut);

    /** Retourne la conversation active la plus recente pour ce numero (tous statuts sauf STOP/TERMINEE). */
    @Query("""
            SELECT c FROM SmsConversation c
            WHERE c.numeroProspect = :numero
            AND c.statut NOT IN (fr.leadrevive.outreach.entity.StatutConversationSmsEnum.STOP_DEMANDE,
                                 fr.leadrevive.outreach.entity.StatutConversationSmsEnum.TERMINEE,
                                 fr.leadrevive.outreach.entity.StatutConversationSmsEnum.ERREUR)
            ORDER BY c.dernierMessageLe DESC NULLS LAST
            LIMIT 1
            """)
    Optional<SmsConversation> findMostRecentActiveByNumeroProspect(@Param("numero") String numeroProspect);

    /**
     * Retourne la conversation active dont le dernier message SORTANT est le plus recent.
     * Sert au routage des messages entrants : un prospect repond a celui qui lui a ecrit en dernier.
     * Plus fiable que findMostRecentActiveByNumeroProspect quand plusieurs conversations actives
     * existent pour le meme numero (meme tenant, leads distincts) — voir bug #16.
     */
    @Query("""
            SELECT c FROM SmsConversation c
            WHERE c.numeroProspect = :numero
            AND c.statut NOT IN (fr.leadrevive.outreach.entity.StatutConversationSmsEnum.STOP_DEMANDE,
                                 fr.leadrevive.outreach.entity.StatutConversationSmsEnum.TERMINEE,
                                 fr.leadrevive.outreach.entity.StatutConversationSmsEnum.ERREUR)
            AND EXISTS (
                SELECT 1 FROM SmsMessage m
                WHERE m.conversationId = c.conversationId
                AND m.direction = fr.leadrevive.outreach.entity.DirectionSmsEnum.SORTANT
            )
            ORDER BY (
                SELECT MAX(m.creeLe) FROM SmsMessage m
                WHERE m.conversationId = c.conversationId
                AND m.direction = fr.leadrevive.outreach.entity.DirectionSmsEnum.SORTANT
            ) DESC NULLS LAST
            LIMIT 1
            """)
    Optional<SmsConversation> findActiveByNumeroProspectOrderByLatestOutbound(@Param("numero") String numeroProspect);

    /** Securise multi-tenant : pour le webhook Twilio (cherche dans le bon tenant uniquement). */
    Optional<SmsConversation> findByNumeroProspectAndStatutAndTenantId(String numeroProspect, StatutConversationSmsEnum statut, Long tenantId);

    /**
     * Toutes les conversations (y compris fermees STOP/TERMINEE/ERREUR) pour ce
     * prospect + tenant. Utilisee par le webhook pour detecter le cas "le prospect
     * a optout (STOP) puis envoie un nouveau message" — il faut alerter le vendeur
     * (cas frequent : "Attendez, je me suis trompe" apres un stop accidentel).
     */
    List<SmsConversation> findAllByNumeroProspectAndTenantId(String numeroProspect, Long tenantId);

    /**
     * Variante multi-tenant de {@link #findActiveByNumeroProspectOrderByLatestOutbound(String)}.
     * Utilisee par le webhook (epic 23) pour router les reponses prospect vers la
     * conversation active du tenant proprietaire du numero To. Le filtre tenantFilter
     * Hibernate doit etre desactive par l'appelant (cross-tenant intentionnel).
     */
    @Query("""
            SELECT c FROM SmsConversation c
            WHERE c.numeroProspect = :numero
            AND c.tenantId = :tenantId
            AND c.statut NOT IN (fr.leadrevive.outreach.entity.StatutConversationSmsEnum.STOP_DEMANDE,
                                 fr.leadrevive.outreach.entity.StatutConversationSmsEnum.TERMINEE,
                                 fr.leadrevive.outreach.entity.StatutConversationSmsEnum.ERREUR)
            ORDER BY (
                SELECT MAX(m.creeLe) FROM SmsMessage m
                WHERE m.conversationId = c.conversationId
                AND m.direction = fr.leadrevive.outreach.entity.DirectionSmsEnum.SORTANT
            ) DESC NULLS LAST
            LIMIT 1
            """)
    Optional<SmsConversation> findActiveByNumeroProspectAndTenantId(@Param("numero") String numeroProspect,
                                                                     @Param("tenantId") Long tenantId);

    Optional<SmsConversation> findByLeadIdAndStatut(Long leadId, StatutConversationSmsEnum statut);

    List<SmsConversation> findByLeadIdAndTenantIdOrderByCreeLe(Long leadId, Long tenantId);

    List<SmsConversation> findByTenantIdOrderByDernierMessageLeDesc(Long tenantId);

    List<SmsConversation> findByTenantIdAndUserIdOrderByDernierMessageLeDesc(Long tenantId, Long userId);

    /**
     * Variantes paginees pour l'inbox : limitent le nombre de conversations chargees
     * en memoire (sinon une concession avec 5000 conversations sature la heap +
     * declenche un N+1 ailleurs). La pagination "full" (offset + total) suivra.
     */
    List<SmsConversation> findByTenantIdOrderByDernierMessageLeDesc(Long tenantId, org.springframework.data.domain.Pageable pageable);

    List<SmsConversation> findByTenantIdAndUserIdOrderByDernierMessageLeDesc(Long tenantId, Long userId, org.springframework.data.domain.Pageable pageable);

    /**
     * Compteurs inbox via 1 seule requete SQL (statut + modePilotage groupes), au
     * lieu de charger toutes les conversations + leads + users + derniers messages
     * pour les compter cote Java. Critique car {@code getCompteurs} est appele
     * frequemment (badges sidebar + polling 30s).
     *
     * <p>Retour : tableau d'objets {@code [statut, modePilotage, count]}.</p>
     */
    @Query("""
            SELECT c.statut, c.modePilotage, COUNT(c)
            FROM SmsConversation c
            WHERE c.tenantId = :tenantId
            AND (:vendeurId IS NULL OR c.userId = :vendeurId)
            GROUP BY c.statut, c.modePilotage
            """)
    List<Object[]> compterGroupeParStatutEtMode(@Param("tenantId") Long tenantId,
                                                 @Param("vendeurId") Long vendeurId);

    /**
     * Compte les conversations dont le dernier message est ENTRANT et plus recent
     * que {@code derniereLectureLe} : c'est-a-dire les conversations "non lues".
     * Evite de charger tous les messages cote Java.
     */
    @Query("""
            SELECT COUNT(c) FROM SmsConversation c
            WHERE c.tenantId = :tenantId
            AND (:vendeurId IS NULL OR c.userId = :vendeurId)
            AND EXISTS (
                SELECT 1 FROM SmsMessage m
                WHERE m.conversationId = c.conversationId
                AND m.direction = fr.leadrevive.outreach.entity.DirectionSmsEnum.ENTRANT
                AND m.ordre = (
                    SELECT MAX(m2.ordre) FROM SmsMessage m2
                    WHERE m2.conversationId = c.conversationId
                )
                AND (c.derniereLectureLe IS NULL OR m.creeLe > c.derniereLectureLe)
            )
            """)
    long compterNonLues(@Param("tenantId") Long tenantId, @Param("vendeurId") Long vendeurId);

    @Query("""
            SELECT COUNT(c) FROM SmsConversation c
            WHERE c.tenantId = :tenantId
            AND c.creeLe >= :depuis
            """)
    Long countByTenantIdDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT COUNT(c) FROM SmsConversation c
            WHERE c.tenantId = :tenantId
            AND c.statut = :statut
            AND c.creeLe >= :depuis
            """)
    Long countByTenantIdAndStatutDepuis(@Param("tenantId") Long tenantId,
                                         @Param("statut") StatutConversationSmsEnum statut,
                                         @Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT c.userId, COUNT(c),
                   SUM(CASE WHEN c.statut = fr.leadrevive.outreach.entity.StatutConversationSmsEnum.RDV_OBTENU THEN 1 ELSE 0 END)
            FROM SmsConversation c
            WHERE c.tenantId = :tenantId
            AND c.creeLe >= :depuis
            GROUP BY c.userId
            """)
    List<Object[]> statsParVendeurDepuis(@Param("tenantId") Long tenantId,
                                          @Param("depuis") LocalDateTime depuis);

    @Query(value = """
            SELECT DATE(c.cree_le) as jour, COUNT(*),
                   SUM(CASE WHEN c.statut = 'RDV_OBTENU' THEN 1 ELSE 0 END)
            FROM sms_conversation c
            WHERE c.tenant_id = :tenantId
            AND c.cree_le >= :depuis
            GROUP BY DATE(c.cree_le)
            ORDER BY jour
            """, nativeQuery = true)
    List<Object[]> statsParJourDepuis(@Param("tenantId") Long tenantId,
                                       @Param("depuis") LocalDateTime depuis);

    @Query(value = """
            SELECT TO_CHAR(c.cree_le, 'YYYY-MM') as mois,
                   SUM(CASE WHEN c.statut = 'RDV_OBTENU' THEN 1 ELSE 0 END)
            FROM sms_conversation c
            WHERE c.tenant_id = :tenantId
            AND c.cree_le >= :depuis
            GROUP BY TO_CHAR(c.cree_le, 'YYYY-MM')
            """, nativeQuery = true)
    List<Object[]> rdvParMoisDepuis(@Param("tenantId") Long tenantId, @Param("depuis") LocalDateTime depuis);

    /** Conversations eligibles pour une relance automatique. */
    @Query("""
            SELECT c FROM SmsConversation c
            WHERE c.statut = fr.leadrevive.outreach.entity.StatutConversationSmsEnum.EN_COURS
            AND c.modePilotage = fr.leadrevive.outreach.entity.ModePilotageEnum.IA_AUTO
            AND c.prochaineRelanceLe IS NOT NULL
            AND c.prochaineRelanceLe <= :maintenant
            ORDER BY c.prochaineRelanceLe ASC
            """)
    List<SmsConversation> findConversationsEligiblesRelance(@Param("maintenant") LocalDateTime maintenant);

    @Query("""
            SELECT COUNT(c) FROM SmsConversation c
            WHERE c.creeLe >= :depuis
            """)
    Long countGlobalDepuis(@Param("depuis") LocalDateTime depuis);

    @Query("""
            SELECT COUNT(c) FROM SmsConversation c
            WHERE c.statut = :statut
            AND c.creeLe >= :depuis
            """)
    Long countGlobalByStatutDepuis(@Param("statut") StatutConversationSmsEnum statut,
                                    @Param("depuis") LocalDateTime depuis);
}
