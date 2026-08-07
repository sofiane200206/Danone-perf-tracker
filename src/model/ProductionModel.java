package model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Représente une journée de production :
 *
 * Liée à une MatierePremiereModel
 *
 * Contient une liste de sorties réelles
 *
 * A un statut (INCOMPLETE, ERREUR, VALIDE)
 *
 * Peut calculer sa performance comparée au modèle idéal
 * Modèle de production pour la persistance en base de données
 * Permet de gérer un nombre variable de sorties
 */
public class ProductionModel {
    private Long id;
    private Long matierePremiereId;
    private LocalDate dateProduction;
    private LocalTime heureProduction;
    private double quantiteEntreeReelle;
    private List<SortieReelle> sortiesReelles;
    private String statut; // "VALIDE", "ERREUR", "INCOMPLETE"
    private String messageErreur;
    private LocalDateTime dateCreation;
    /** Identifiant du compte ayant enregistre la saisie. Vide pour les productions anterieures. */
    private String saisiPar;

    // Constructeurs
    public ProductionModel() {
        this.sortiesReelles = new ArrayList<>();
        this.statut = "INCOMPLETE";
        this.dateCreation = LocalDateTime.now();
    }

    public ProductionModel(Long matierePremiereId, LocalDate dateProduction, LocalTime heureProduction,
                           double quantiteEntreeReelle) {
        this();
        this.matierePremiereId = matierePremiereId;
        this.dateProduction = dateProduction;
        this.heureProduction = heureProduction;
        this.quantiteEntreeReelle = quantiteEntreeReelle;
    }

    // Méthodes métier
    public void ajouterSortieReelle(int numeroSortie, double quantiteReelle) {
        if (numeroSortie <= 0) {
            throw new IllegalArgumentException("Le numéro de sortie doit être positif");
        }
        if (quantiteReelle < 0) {
            throw new IllegalArgumentException("La quantité réelle ne peut pas être négative");
        }

        // Vérifier si la sortie existe déjà
        SortieReelle sortieExistante = sortiesReelles.stream()
                .filter(s -> s.getNumeroSortie() == numeroSortie)
                .findFirst()
                .orElse(null);

        if (sortieExistante != null) {
            // Mettre à jour la sortie existante
            sortieExistante.setQuantiteReelle(quantiteReelle);
        } else {
            // Ajouter une nouvelle sortie
            sortiesReelles.add(new SortieReelle(numeroSortie, quantiteReelle));
        }
    }

    public void ajouterSortiesReelles(List<SortieReelle> sorties) {
        for (SortieReelle sortie : sorties) {
            ajouterSortieReelle(sortie.getNumeroSortie(), sortie.getQuantiteReelle());
        }
    }

    public SortieReelle getSortieReelle(int numeroSortie) {
        return sortiesReelles.stream()
                .filter(s -> s.getNumeroSortie() == numeroSortie)
                .findFirst()
                .orElse(null);
    }

    public double getTotalSortiesReelles() {
        return sortiesReelles.stream()
                .mapToDouble(SortieReelle::getQuantiteReelle)
                .sum();
    }

    public int getNombreSortiesReelles() {
        return sortiesReelles.size();
    }

    public boolean isValide() {
        return id != null && matierePremiereId != null && dateProduction != null && heureProduction != null
                && quantiteEntreeReelle >= 0 && "VALIDE".equals(statut)
                && sortiesReelles.stream().allMatch(s -> s.getQuantiteReelle() >= 0);
    }

    public boolean isDonneeComplete() {
        return dateProduction != null && heureProduction != null && quantiteEntreeReelle >= 0
                && !sortiesReelles.isEmpty()
                && sortiesReelles.stream().allMatch(s -> s.getQuantiteReelle() >= 0);
    }

    /**
     * Met a jour le statut selon la completude des donnees.
     *
     * Une production signalee en ERREUR est laissee telle quelle : ce statut
     * traduit un jugement humain sur la fiabilite de la mesure, qu'un controle
     * automatique de completude n'a pas a effacer. Utiliser {@link #leverErreur()}
     * pour le retirer.
     */
    public void validerProduction() {
        if (hasErreur()) {
            return;
        }
        if (isDonneeComplete()) {
            this.statut = "VALIDE";
            this.messageErreur = null;
        } else {
            this.statut = "INCOMPLETE";
            this.messageErreur = "Données incomplètes ou invalides";
        }
    }

    /** Signale une mesure jugee douteuse : elle sera exclue des calculs. */
    public void marquerErreur(String messageErreur) {
        this.statut = "ERREUR";
        this.messageErreur = messageErreur;
    }

    /** Retire le signalement d'erreur et reevalue le statut. */
    public void leverErreur() {
        this.statut = "INCOMPLETE";
        this.messageErreur = null;
        validerProduction();
    }

    public boolean hasErreur() {
        return "ERREUR".equals(statut);
    }

    /**
     * Indique si cette production doit être comptée dans les totaux de performance.
     * Règle métier : les données doivent être complètes (peu importe l'id en base
     * ou le statut administratif), sauf si la production est marquée en ERREUR
     * (mesure connue comme douteuse).
     */
    public boolean isComptabilisable() {
        return isDonneeComplete() && !hasErreur();
    }

    public boolean isComplete() {
        return "VALIDE".equals(statut);
    }

    // Méthodes de calcul de performance
    public double calculerPerformanceEntree(MatierePremiereModel matiereReference) {
        if (matiereReference == null || matiereReference.getQuantiteEntreeIdeale() == 0) {
            return 0.0;
        }
        return (this.quantiteEntreeReelle / matiereReference.getQuantiteEntreeIdeale()) * 100;
    }

    public double calculerPerformanceSortie(MatierePremiereModel matiereReference, int numeroSortie) {
        if (matiereReference == null) {
            return 0.0;
        }

        MatierePremiereModel.SortieIdeale sortieIdeale = matiereReference.getSortieIdeale(numeroSortie);
        SortieReelle sortieReelle = this.getSortieReelle(numeroSortie);

        if (sortieIdeale == null || sortieReelle == null || sortieIdeale.getQuantiteIdeale() == 0) {
            return 0.0;
        }

        return (sortieReelle.getQuantiteReelle() / sortieIdeale.getQuantiteIdeale()) * 100;
    }

    public double calculerPerformanceGlobale(MatierePremiereModel matiereReference) {
        if (matiereReference == null) {
            return 0.0;
        }

        double totalSortiesReelles = getTotalSortiesReelles();
        double totalSortiesIdeales = matiereReference.getTotalSortiesIdeales();

        if (totalSortiesIdeales == 0) {
            return 0.0;
        }

        return (totalSortiesReelles / totalSortiesIdeales) * 100;
    }

    // Conversion vers l'ancien modèle Production pour compatibilité


    // Création à partir de l'ancien modèle Production


    // Méthodes utilitaires
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductionModel that = (ProductionModel) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(matierePremiereId, that.matierePremiereId) &&
                Objects.equals(dateProduction, that.dateProduction) &&
                Objects.equals(heureProduction, that.heureProduction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, matierePremiereId, dateProduction, heureProduction);
    }

    @Override
    public String toString() {
        return String.format("Production[id=%d, matiere=%d, date=%s, heure=%s, entree=%.2f, sorties=%d, statut=%s]",
                id, matierePremiereId, dateProduction, heureProduction, quantiteEntreeReelle, sortiesReelles.size(), statut);
    }

    // Getters et setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMatierePremiereId() { return matierePremiereId; }
    public void setMatierePremiereId(Long matierePremiereId) { this.matierePremiereId = matierePremiereId; }

    public LocalDate getDateProduction() { return dateProduction; }
    public void setDateProduction(LocalDate dateProduction) { this.dateProduction = dateProduction; }

    public LocalTime getHeureProduction() { return heureProduction; }
    public void setHeureProduction(LocalTime heureProduction) { this.heureProduction = heureProduction; }

    public double getQuantiteEntreeReelle() { return quantiteEntreeReelle; }
    public void setQuantiteEntreeReelle(double quantiteEntreeReelle) {
        if (quantiteEntreeReelle < 0) {
            throw new IllegalArgumentException("La quantité d'entrée ne peut pas être négative");
        }
        this.quantiteEntreeReelle = quantiteEntreeReelle;
    }

    public List<SortieReelle> getSortiesReelles() { return new ArrayList<>(sortiesReelles); }
    public void setSortiesReelles(List<SortieReelle> sortiesReelles) {
        this.sortiesReelles = new ArrayList<>(sortiesReelles);
    }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getMessageErreur() { return messageErreur; }
    public void setMessageErreur(String messageErreur) { this.messageErreur = messageErreur; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public String getSaisiPar() { return saisiPar; }
    public void setSaisiPar(String saisiPar) { this.saisiPar = saisiPar; }

    // Classe interne pour les sorties réelles
    public static class SortieReelle {
        private int numeroSortie;
        private double quantiteReelle;

        public SortieReelle(int numeroSortie, double quantiteReelle) {
            if (numeroSortie <= 0) {
                throw new IllegalArgumentException("Le numéro de sortie doit être positif");
            }
            if (quantiteReelle < 0) {
                throw new IllegalArgumentException("La quantité réelle ne peut pas être négative");
            }
            this.numeroSortie = numeroSortie;
            this.quantiteReelle = quantiteReelle;
        }

        // Getters et setters
        public int getNumeroSortie() { return numeroSortie; }
        public void setNumeroSortie(int numeroSortie) {
            if (numeroSortie <= 0) {
                throw new IllegalArgumentException("Le numéro de sortie doit être positif");
            }
            this.numeroSortie = numeroSortie;
        }

        public double getQuantiteReelle() { return quantiteReelle; }
        public void setQuantiteReelle(double quantiteReelle) {
            if (quantiteReelle < 0) {
                throw new IllegalArgumentException("La quantité réelle ne peut pas être négative");
            }
            this.quantiteReelle = quantiteReelle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SortieReelle that = (SortieReelle) o;
            return numeroSortie == that.numeroSortie &&
                    Double.compare(that.quantiteReelle, quantiteReelle) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(numeroSortie, quantiteReelle);
        }

        @Override
        public String toString() {
            return String.format("Sortie[%d: %.2fkg]", numeroSortie, quantiteReelle);
        }
    }
}