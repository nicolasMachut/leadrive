package fr.leadrevive.lead.service;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.VenteConfirmee;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.lead.repository.VenteConfirmeeRepository;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Genere un export CSV des ventes confirmees pour une periode donnee.
 * Format adapte a Excel (separateur ;, BOM UTF-8 pour les accents).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VenteExportService {

    private static final String SEPARATEUR = ";";
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final VenteConfirmeeRepository venteConfirmeeRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public String exporterVentes(final Long tenantId, final LocalDate debut, final LocalDate fin) {
        log.info("Export CSV ventes - tenantId: {}, periode: {} -> {}", tenantId, debut, fin);

        final List<VenteConfirmee> ventes = venteConfirmeeRepository
                .findByTenantIdAndDateVenteBetweenOrderByDateVenteDesc(tenantId, debut, fin);

        // Pre-charger les leads et users pour eviter le N+1
        final Map<Long, Lead> leadsById = leadRepository.findAllById(
                ventes.stream().map(VenteConfirmee::getLeadId).distinct().toList()
        ).stream().collect(Collectors.toMap(Lead::getId, Function.identity()));

        final Map<Long, User> usersById = userRepository.findAllById(
                ventes.stream().map(VenteConfirmee::getUserId).distinct().toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        final StringBuilder csv = new StringBuilder();
        // BOM UTF-8 pour qu'Excel detecte l'encodage
        csv.append('\uFEFF');

        // Header
        csv.append(String.join(SEPARATEUR,
                "Date vente",
                "Prospect",
                "Telephone",
                "Email",
                "Marque",
                "Modele",
                "Vendeur",
                "Montant EUR",
                "Source",
                "Commentaire"
        )).append("\n");

        // Lignes
        for (final VenteConfirmee v : ventes) {
            final Lead lead = leadsById.get(v.getLeadId());
            final User vendeur = usersById.get(v.getUserId());
            csv.append(String.join(SEPARATEUR,
                    v.getDateVente() != null ? v.getDateVente().format(DATE_FR) : "",
                    escape(lead != null ? lead.getNom() : ""),
                    escape(lead != null ? lead.getTelephone() : ""),
                    escape(lead != null ? lead.getEmail() : ""),
                    escape(lead != null ? lead.getMarque() : ""),
                    escape(lead != null ? lead.getModele() : ""),
                    escape(vendeur != null ? (vendeur.getPrenom() + " " + vendeur.getNom()).trim() : ""),
                    v.getMontant() != null ? v.getMontant().toPlainString().replace(".", ",") : "",
                    v.getSource() != null ? v.getSource().name() : "",
                    escape(v.getCommentaire())
            )).append("\n");
        }

        log.info("Export CSV genere - {} ventes", ventes.size());
        return csv.toString();
    }

    /**
     * Echappe une valeur pour CSV : double les guillemets, encadre de guillemets si la valeur
     * contient le separateur, des guillemets ou des sauts de ligne.
     */
    private String escape(final String value) {
        if (value == null || value.isEmpty()) return "";
        final boolean needsQuoting = value.contains(SEPARATEUR) || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
