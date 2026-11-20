package fr.leadrevive.lead.service;

import fr.leadrevive.lead.dto.RepVenteDto;
import fr.leadrevive.lead.dto.ReqVenteDto;
import fr.leadrevive.lead.dto.ReqVenteRapideDto;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.entity.SourceVenteEnum;
import fr.leadrevive.lead.entity.VenteConfirmee;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.lead.repository.VenteConfirmeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VenteService {

    private final VenteConfirmeeRepository venteConfirmeeRepository;
    private final LeadRepository leadRepository;

    @CacheEvict(value = {"dashboardStats", "dashboardVendeurs", "dashboardEvolution"}, allEntries = true)
    @Transactional
    public RepVenteDto confirmerVente(final Long leadId, final Long tenantId, final Long userId,
                                       final ReqVenteDto request) {
        log.info("Confirmation vente - leadId: {}, tenantId: {}", leadId, tenantId);

        // SECURITE : findByIdAndTenantId pour empecher un caller cross-tenant
        // (admin, scheduler) de creer une vente sur un lead d'un autre tenant.
        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        venteConfirmeeRepository.findByLeadId(leadId).ifPresent(v -> {
            throw new IllegalStateException("Une vente est deja confirmee pour ce lead");
        });

        final VenteConfirmee vente = new VenteConfirmee();
        vente.setTenantId(tenantId);
        vente.setLeadId(leadId);
        vente.setUserId(userId);
        vente.setMontant(request.getMontant());
        vente.setDateVente(request.getDateVente());
        vente.setSource(SourceVenteEnum.valueOf(request.getSource()));
        vente.setCommentaire(request.getCommentaire());

        final VenteConfirmee saved = venteConfirmeeRepository.save(vente);

        // Un lead avec une vente confirmee est GAGNE : on le passe en TERMINE (sinon
        // il reste dans une colonne active du Kanban alors que le badge "Vente" est
        // affiche = pipeline incoherent). Symetrique de supprimerVente qui remet
        // EN_COURS. On ne trace PAS d'historique source "IA" (non facturable).
        if (lead.getStatut() != LeadStatutEnum.TERMINE) {
            lead.setStatut(LeadStatutEnum.TERMINE);
            lead.setStatutModifieLe(LocalDateTime.now());
            lead.setStatutModifiePar(userId != null ? "USER" : "IA");
            leadRepository.save(lead);
            log.info("Lead passe TERMINE apres confirmation vente - leadId: {}", leadId);
        }

        log.info("Vente confirmee - id: {}, leadId: {}, montant: {}", saved.getId(), leadId, request.getMontant());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<RepVenteDto> listerVentes(final Long tenantId, final YearMonth mois) {
        log.info("Liste ventes - tenantId: {}, mois: {}", tenantId, mois);
        final LocalDate debut = mois.atDay(1);
        final LocalDate fin = mois.atEndOfMonth();
        return venteConfirmeeRepository.findByTenantIdAndDateVenteBetweenOrderByDateVenteDesc(tenantId, debut, fin)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RepVenteDto getVenteLead(final Long leadId, final Long tenantId) {
        // Verifier que le lead appartient au tenant avant d'exposer les donnees de vente
        leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));
        return venteConfirmeeRepository.findByLeadId(leadId).map(this::toDto).orElse(null);
    }

    @CacheEvict(value = {"dashboardStats", "dashboardVendeurs", "dashboardEvolution"}, allEntries = true)
    @Transactional
    public void supprimerVente(final Long venteId, final Long tenantId) {
        log.info("Suppression vente - id: {}, tenantId: {}", venteId, tenantId);
        // SECURITE : 404 anonyme dans tous les cas d'acces refuse pour ne pas
        // confirmer l'existence d'une vente d'un autre tenant (oracle d'enumeration).
        final VenteConfirmee vente = venteConfirmeeRepository.findById(venteId)
                .filter(v -> tenantId != null && tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Vente introuvable"));
        // Restaure le statut du lead : sans cela, le lead restait TERMINE alors qu'il
        // n'a plus de vente associee => incoherence metier (manager voit "termine" mais
        // 0 vente). On le remet a EN_COURS pour qu'il puisse etre repris.
        leadRepository.findByIdAndTenantId(vente.getLeadId(), tenantId).ifPresent(lead -> {
            if (lead.getStatut() == LeadStatutEnum.TERMINE) {
                lead.setStatut(LeadStatutEnum.EN_COURS);
                leadRepository.save(lead);
                log.info("Lead remis EN_COURS apres suppression vente - leadId: {}", lead.getId());
            }
        });
        venteConfirmeeRepository.delete(vente);
        log.info("Vente supprimee - id: {}", venteId);
    }

    @CacheEvict(value = {"dashboardStats", "dashboardVendeurs", "dashboardEvolution"}, allEntries = true)
    @Transactional
    public RepVenteDto confirmerRapide(final ReqVenteRapideDto request, final Long tenantId, final Long userId) {
        log.info("Confirmation rapide - leadId: {}, vendu: {}", request.getLeadId(), request.getVendu());
        // SECURITE : findByIdAndTenantId pour empecher un SUPER_ADMIN d'ecraser le statut
        // d'un lead d'un autre tenant. tenantId null = caller cross-tenant interdit ici.
        if (tenantId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Action interdite cross-tenant");
        }
        final Lead lead = leadRepository.findByIdAndTenantId(request.getLeadId(), tenantId)
                .orElseThrow(() -> new LeadNotFoundException(request.getLeadId()));

        if (Boolean.TRUE.equals(request.getVendu())) {
            final ReqVenteDto req = new ReqVenteDto(
                    request.getMontant(),
                    LocalDate.now(),
                    "WHATSAPP_IA",
                    request.getCommentaire());
            return confirmerVente(request.getLeadId(), tenantId, userId, req);
        } else {
            // Marquer le lead comme TERMINE
            lead.setStatut(LeadStatutEnum.TERMINE);
            leadRepository.save(lead);
            log.info("Lead marque TERMINE - leadId: {}", request.getLeadId());
            return null;
        }
    }

    private RepVenteDto toDto(final VenteConfirmee vente) {
        return RepVenteDto.builder()
                .id(vente.getId())
                .leadId(vente.getLeadId())
                .montant(vente.getMontant())
                .dateVente(vente.getDateVente())
                .source(vente.getSource().name())
                .commentaire(vente.getCommentaire())
                .creeLe(vente.getCreeLe())
                .build();
    }
}
