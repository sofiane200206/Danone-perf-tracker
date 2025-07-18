package model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


public class JourneeProduction {
    private LocalDate date;
    private List<Production> productions;
    private double performanceJour;
    private double totalEntreeJour;
    private double totalSortie1Jour;
    private double totalSortie2Jour;

    public JourneeProduction(LocalDate date) {
        this.date = date;
        this.productions = new ArrayList<>();
    }

    public void ajouterProduction(Production production) {
        productions.add(production);
        recalculerTotaux();
    }

    private void recalculerTotaux() {
        totalEntreeJour = productions.stream()
                .filter(Production::isValide)
                .mapToDouble(Production::getQuantiteEntreeReelle)
                .sum();

        totalSortie1Jour = productions.stream()
                .filter(Production::isValide)
                .mapToDouble(Production::getSortie1Reelle)
                .sum();

        totalSortie2Jour = productions.stream()
                .filter(Production::isValide)
                .mapToDouble(Production::getSortie2Reelle)
                .sum();
    }

    public void calculerPerformance(ModeleIdeal modele) {
        if (modele == null || !modele.isValide() || totalEntreeJour == 0) {
            performanceJour = 0;
            return;
        }

        double sortie1IdealeJour = (totalEntreeJour * modele.getSortie1Ideale()) / modele.getQuantiteEntreeIdeale();
        double sortie2IdealeJour = (totalEntreeJour * modele.getSortie2Ideale()) / modele.getQuantiteEntreeIdeale();
        double totalIdealeJour = sortie1IdealeJour + sortie2IdealeJour;

        if (totalIdealeJour > 0) {
            performanceJour = ((totalSortie1Jour + totalSortie2Jour) / totalIdealeJour) * 100;
        } else {
            performanceJour = 0;
        }
    }

    // Getters
    public LocalDate getDate() { return date; }
    public List<Production> getProductions() { return new ArrayList<>(productions); }
    public double getPerformanceJour() { return performanceJour; }
    public double getTotalEntreeJour() { return totalEntreeJour; }
    public double getTotalSortie1Jour() { return totalSortie1Jour; }
    public double getTotalSortie2Jour() { return totalSortie2Jour; }
}