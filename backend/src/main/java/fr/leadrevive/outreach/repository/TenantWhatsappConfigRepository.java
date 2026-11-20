package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository de la configuration WhatsApp par tenant.
 *
 * <p><strong>Attention au filtre Hibernate `tenantFilter`</strong> : l'entite
 * {@link TenantWhatsappConfig} est filtree par defaut. Les methodes ci-dessous
 * sont impactees :
 * <ul>
 *   <li>{@link #findByTenantId(Long)} : OK si le tenant courant correspond.</li>
 *   <li>{@link #findByWhatsappFrom(String)} : <strong>cross-tenant</strong>,
 *       utilisee depuis le webhook (sans contexte tenant) → l'appelant doit
 *       desactiver le filtre via {@code session.disableFilter("tenantFilter")}
 *       avant l'appel.</li>
 *   <li>{@link #findByStatutIn(List)} : <strong>cross-tenant</strong>, utilisee
 *       par le job de polling Meta @Scheduled (sans contexte tenant) → idem.</li>
 * </ul>
 * </p>
 */
@Repository
public interface TenantWhatsappConfigRepository extends JpaRepository<TenantWhatsappConfig, Long> {

    /** Lookup classique par tenant courant. */
    Optional<TenantWhatsappConfig> findByTenantId(Long tenantId);

    /**
     * Lookup du tenant possedant un numero WhatsApp donne. Utilise par le
     * webhook pour router le message entrant (parametre Twilio `To`).
     *
     * <p><strong>Cross-tenant</strong> : le filtre Hibernate doit etre
     * desactive par l'appelant. Voir le pattern dans
     * {@code WhatsAppConversationService.traiterReponseProspect}.</p>
     */
    Optional<TenantWhatsappConfig> findByWhatsappFrom(String whatsappFrom);

    /**
     * Liste les configs ayant un des statuts demandes. Utilise par le job
     * de polling Meta @Scheduled.
     *
     * <p><strong>Cross-tenant</strong> : meme contrainte que ci-dessus.</p>
     */
    List<TenantWhatsappConfig> findByStatutIn(List<StatutWhatsappConfigEnum> statuts);

    /** Compteur pour monitoring admin (combien de tenants par statut). */
    long countByStatut(StatutWhatsappConfigEnum statut);

    /**
     * Compte les configs d'AUTRES tenants utilisant ce WABA / ce numéro. Requêtes
     * NATIVES → bypass volontaire du filtre Hibernate tenant : on veut détecter une
     * collision cross-tenant AVANT de rattacher un WABA/numéro déjà pris ailleurs
     * (sinon routage de webhook ambigu via {@link #findByWhatsappFrom(String)}).
     */
    @Query(value = "SELECT COUNT(*) FROM tenant_whatsapp_config WHERE waba_id = :wabaId AND tenant_id <> :tenantId",
            nativeQuery = true)
    long countAutresTenantsAvecWabaId(@Param("wabaId") String wabaId, @Param("tenantId") Long tenantId);

    @Query(value = "SELECT COUNT(*) FROM tenant_whatsapp_config WHERE whatsapp_from = :numero AND tenant_id <> :tenantId",
            nativeQuery = true)
    long countAutresTenantsAvecNumero(@Param("numero") String numero, @Param("tenantId") Long tenantId);
}
