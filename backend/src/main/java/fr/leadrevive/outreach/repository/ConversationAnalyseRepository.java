package fr.leadrevive.outreach.repository;

import fr.leadrevive.outreach.entity.ConversationAnalyse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationAnalyseRepository extends JpaRepository<ConversationAnalyse, Long> {

    List<ConversationAnalyse> findByConversationIdOrderByCreeLe(String conversationId);

    @Query("""
            SELECT a FROM ConversationAnalyse a
            WHERE a.tenantId = :tenantId
            AND a.conversationId = :conversationId
            ORDER BY a.creeLe DESC
            LIMIT 1
            """)
    Optional<ConversationAnalyse> findDerniereByTenantIdAndConversationId(
            @Param("tenantId") Long tenantId,
            @Param("conversationId") String conversationId);
}
