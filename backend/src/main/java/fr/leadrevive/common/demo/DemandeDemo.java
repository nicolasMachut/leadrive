package fr.leadrevive.common.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "demande_demo")
@Getter
@Setter
public class DemandeDemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_demande_demo")
    private Long id;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "concession", nullable = false)
    private String concession;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "telephone")
    private String telephone;

    @Column(name = "volume_leads")
    private String volumeLeads;

    @Column(name = "statut", nullable = false)
    private String statut = "NOUVEAU";

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe = LocalDateTime.now();

    @Column(name = "traite_le")
    private LocalDateTime traiteLe;

    @Column(name = "traite_par")
    private String traitePar;
}
