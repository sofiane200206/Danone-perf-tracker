package model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


public class JourneeProduction {
    private LocalDate date;
    private List<ProductionModel> productions;
    private double performanceJour;
    private double totalEntreeJour;
    private double totalSortiesJour; // Remplace totalSortie1Jour et totalSortie2Jour

    public JourneeProduction(LocalDate date) {
        this.date = date;
        this.productions = new ArrayList<>();
    }

    public void ajouterProduction(ProductionModel production) {
        productions.add(production);
        recalculerTotaux();
    }

    private void recalculerTotaux() {
        totalEntreeJour = productions.stream()
                .filter(ProductionModel::isValide)
                .mapToDouble(ProductionModel::getQuantiteEntreeReelle)
                .sum();

        totalSortiesJour = productions.stream()
                .filter(ProductionModel::isValide)
                .mapToDouble(ProductionModel::getTotalSortiesReelles)
                .sum();
    }

    public void calculerPerformance(MatierePremiereModel matiere) {
        if (matiere == null || !matiere.isValide() || totalEntreeJour == 0) {
            performanceJour = 0;
            return;
        }

        // Calculer la production idéale totale pour la journée
        double ratioProduction = totalEntreeJour / matiere.getQuantiteEntreeIdeale();
        double totalSortiesIdealesJour = ratioProduction * matiere.getTotalSortiesIdeales();

        if (totalSortiesIdealesJour > 0) {
            performanceJour = (totalSortiesJour / totalSortiesIdealesJour) * 100;
        } else {
            performanceJour = 0;
        }
    }

    // Méthodes de compatibilité pour l'ancien système (si besoin)


    // Nouvelle méthode pour obtenir le total d'une sortie spécifique
    public double getTotalSortieParNumero(int numeroSortie) {
        return productions.stream()
                .filter(ProductionModel::isValide)
                .mapToDouble(p -> {
                    ProductionModel.SortieReelle sortie = p.getSortieReelle(numeroSortie);
                    return sortie != null ? sortie.getQuantiteReelle() : 0;
                })
                .sum();
    }

    // Getters
    public LocalDate getDate() {
        return date;
    }

    public List<ProductionModel> getProductions() {
        return new ArrayList<>(productions);
    }

    public double getPerformanceJour() {
        return performanceJour;
    }

    public double getTotalEntreeJour() {
        return totalEntreeJour;
    }

    public double getTotalSortiesJour() {
        return totalSortiesJour;
    }
}