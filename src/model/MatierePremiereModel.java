package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MatierePremiereModel {
    private Long id;
    private String nom;
    private double quantiteEntreeIdeale;
    private int nombreSorties;
    private List<SortieIdeale> sortiesIdeales;
    private LocalDateTime dateCreation;
    private boolean actif;

    // Représente une matière première utilisée dans la production. Contient :
    //
    //id, nom, active
    //
    //Une liste de sorties idéales (quantités que cette matière devrait produire en théorie)
    //
    //
    // Constructeurs
    public MatierePremiereModel() {
        this.sortiesIdeales = new ArrayList<>();
        this.actif = true;
        this.nombreSorties = 2; // Par défaut 2 sorties
    }

    public MatierePremiereModel(String nom, double quantiteEntreeIdeale, int nombreSorties) {
        this();
        this.nom = nom;
        this.quantiteEntreeIdeale = quantiteEntreeIdeale;
        this.nombreSorties = nombreSorties;

        // Initialiser les sorties avec des valeurs par défaut
        for (int i = 1; i <= nombreSorties; i++) {
            sortiesIdeales.add(new SortieIdeale(i, 0.0, "Sortie " + i));
        }
    }

    // Méthodes métier
    public void ajouterSortieIdeale(int numeroSortie, double quantiteIdeale, String nomSortie) {
        // Vérifier si la sortie existe déjà
        SortieIdeale sortieExistante = sortiesIdeales.stream()
                .filter(s -> s.getNumeroSortie() == numeroSortie)
                .findFirst()
                .orElse(null);

        if (sortieExistante != null) {
            // Mettre à jour la sortie existante
            sortieExistante.setQuantiteIdeale(quantiteIdeale);
            sortieExistante.setNomSortie(nomSortie);
        } else {
            // Ajouter une nouvelle sortie
            sortiesIdeales.add(new SortieIdeale(numeroSortie, quantiteIdeale, nomSortie));
        }
    }

    public SortieIdeale getSortieIdeale(int numeroSortie) {
        return sortiesIdeales.stream()
                .filter(s -> s.getNumeroSortie() == numeroSortie)
                .findFirst()
                .orElse(null);
    }

    public double getTotalSortiesIdeales() {
        return sortiesIdeales.stream()
                .mapToDouble(SortieIdeale::getQuantiteIdeale)
                .sum();
    }

    public boolean isValide() {
        return nom != null && !nom.trim().isEmpty()
                && quantiteEntreeIdeale > 0
                && nombreSorties > 0
                && sortiesIdeales.size() == nombreSorties
                && sortiesIdeales.stream().allMatch(s -> s.getQuantiteIdeale() >= 0);
    }

    // Conversion vers l'ancien ModeleIdeal pour compatibilité
    public ModeleIdeal toModeleIdeal() {
        if (nombreSorties == 2) {
            SortieIdeale sortie1 = getSortieIdeale(1);
            SortieIdeale sortie2 = getSortieIdeale(2);

            return new ModeleIdeal(
                    nom,
                    quantiteEntreeIdeale,
                    sortie1 != null ? sortie1.getQuantiteIdeale() : 0,
                    sortie2 != null ? sortie2.getQuantiteIdeale() : 0
            );
        }
        throw new UnsupportedOperationException("Conversion impossible pour " + nombreSorties + " sorties");
    }

    // Getters et setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public double getQuantiteEntreeIdeale() { return quantiteEntreeIdeale; }
    public void setQuantiteEntreeIdeale(double quantiteEntreeIdeale) {
        this.quantiteEntreeIdeale = quantiteEntreeIdeale;
    }

    public int getNombreSorties() { return nombreSorties; }
    public void setNombreSorties(int nombreSorties) {
        this.nombreSorties = nombreSorties;
    }

    public List<SortieIdeale> getSortiesIdeales() { return new ArrayList<>(sortiesIdeales); }
    public void setSortiesIdeales(List<SortieIdeale> sortiesIdeales) {
        this.sortiesIdeales = new ArrayList<>(sortiesIdeales);
    }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    // Classe interne pour les sorties idéales
    public static class SortieIdeale {
        private int numeroSortie;
        private double quantiteIdeale;
        private String nomSortie;

        public SortieIdeale(int numeroSortie, double quantiteIdeale, String nomSortie) {
            this.numeroSortie = numeroSortie;
            this.quantiteIdeale = quantiteIdeale;
            this.nomSortie = nomSortie;
        }

        // Getters et setters
        public int getNumeroSortie() { return numeroSortie; }
        public void setNumeroSortie(int numeroSortie) { this.numeroSortie = numeroSortie; }

        public double getQuantiteIdeale() { return quantiteIdeale; }
        public void setQuantiteIdeale(double quantiteIdeale) { this.quantiteIdeale = quantiteIdeale; }

        public String getNomSortie() { return nomSortie; }
        public void setNomSortie(String nomSortie) { this.nomSortie = nomSortie; }
    }

    @Override
    public String toString() {
        return nom + " (" + nombreSorties + " sorties)";
    }
}