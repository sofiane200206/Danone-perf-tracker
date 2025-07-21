package service;

import model.*;
import java.time.LocalDate;
import java.util.*;

public class StatistiquesService {

    public static class StatistiquesResume {
        private double performanceMoyenne;
        private double performanceMax;
        private double performanceMin;
        private LocalDate jourMeilleur;
        private LocalDate jourPire;
        private int nombreJours;
        private double performanceGlobalePeriode;

        // Constructeurs, getters, setters
        public StatistiquesResume() {}

        // Getters et setters
        public double getPerformanceMoyenne() { return performanceMoyenne; }
        public void setPerformanceMoyenne(double performance) { this.performanceMoyenne = performance; }

        public double getPerformanceMax() { return performanceMax; }
        public void setPerformanceMax(double performance) { this.performanceMax = performance; }

        public double getPerformanceMin() { return performanceMin; }
        public void setPerformanceMin(double performance) { this.performanceMin = performance; }

        public LocalDate getJourMeilleur() { return jourMeilleur; }
        public void setJourMeilleur(LocalDate jour) { this.jourMeilleur = jour; }

        public LocalDate getJourPire() { return jourPire; }
        public void setJourPire(LocalDate jour) { this.jourPire = jour; }

        public int getNombreJours() { return nombreJours; }
        public void setNombreJours(int nombre) { this.nombreJours = nombre; }

        public double getPerformanceGlobalePeriode() { return performanceGlobalePeriode; }
        public void setPerformanceGlobalePeriode(double performance) { this.performanceGlobalePeriode = performance; }
    }

    /**
     * Calcule les statistiques basées sur le nouveau système avec MatierePremiereModel
     */
    public static StatistiquesResume calculerStatistiques(Map<LocalDate, JourneeProduction> journees, MatierePremiereModel matiere) {

        StatistiquesResume stats = new StatistiquesResume();


        if (journees.isEmpty() || matiere == null || !matiere.isValide()) {
            return stats;
        }

        double sommePerformances = 0;
        double performanceMax = Double.MIN_VALUE;
        double performanceMin = Double.MAX_VALUE;
        LocalDate jourMax = null;
        LocalDate jourMin = null;

        // Totaux pour la période
        double totalEntreePeriode = 0;
        double totalSortiesPeriode = 0;

        for (JourneeProduction journee : journees.values()) {
            journee.calculerPerformance(matiere);
            double perf = journee.getPerformanceJour();
            sommePerformances += perf;

            totalEntreePeriode += journee.getTotalEntreeJour();
            totalSortiesPeriode += journee.getTotalSortiesJour();

            if (perf > performanceMax) {
                performanceMax = perf;
                jourMax = journee.getDate();
            }
            if (perf < performanceMin) {
                performanceMin = perf;
                jourMin = journee.getDate();
            }
        }

        int nombreJours = journees.size();

        // Calcul performance globale de la période avec le nouveau système
        double ratioProduction = totalEntreePeriode / matiere.getQuantiteEntreeIdeale();
        double totalSortiesIdealesPeriode = ratioProduction * matiere.getTotalSortiesIdeales();

        double performanceGlobale = totalSortiesIdealesPeriode > 0 ?
                (totalSortiesPeriode / totalSortiesIdealesPeriode) * 100 : 0;

        stats.setPerformanceMoyenne(sommePerformances / nombreJours);
        stats.setPerformanceMax(performanceMax);
        stats.setPerformanceMin(performanceMin);
        stats.setJourMeilleur(jourMax);
        stats.setJourPire(jourMin);
        stats.setNombreJours(nombreJours);
        stats.setPerformanceGlobalePeriode(performanceGlobale);

        return stats;
    }

    /**
     * Version de compatibilité avec l'ancien système ModeleIdeal (deprecated)
     * Utilise les méthodes dépréciées de JourneeProduction pour maintenir la compatibilité
     */


    /**
     * Méthode utilitaire pour calculer des statistiques par sortie spécifique
     */
    public static Map<Integer, Double> calculerTotauxParSortie(Map<LocalDate, JourneeProduction> journees, List<Integer> numerosSorties) {
        Map<Integer, Double> totaux = new HashMap<>();

        for (Integer numeroSortie : numerosSorties) {
            double total = journees.values().stream()
                    .mapToDouble(journee -> journee.getTotalSortieParNumero(numeroSortie))
                    .sum();
            totaux.put(numeroSortie, total);
        }

        return totaux;
    }
}