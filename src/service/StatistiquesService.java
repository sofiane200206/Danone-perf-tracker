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

    public static StatistiquesResume calculerStatistiques(Map<LocalDate, JourneeProduction> journees, ModeleIdeal modele) {
        StatistiquesResume stats = new StatistiquesResume();

        if (journees.isEmpty() || modele == null || !modele.isValide()) {
            return stats;
        }

        double sommePerformances = 0;
        double performanceMax = Double.MIN_VALUE;
        double performanceMin = Double.MAX_VALUE;
        LocalDate jourMax = null;
        LocalDate jourMin = null;

        // Totaux pour la période
        double totalEntreePeriode = 0;
        double totalSortie1Periode = 0;
        double totalSortie2Periode = 0;

        for (JourneeProduction journee : journees.values()) {
            double perf = journee.getPerformanceJour();
            sommePerformances += perf;

            totalEntreePeriode += journee.getTotalEntreeJour();
            totalSortie1Periode += journee.getTotalSortie1Jour();
            totalSortie2Periode += journee.getTotalSortie2Jour();

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

        // Calcul performance globale de la période
        double sortie1IdealePeriode = (totalEntreePeriode * modele.getSortie1Ideale()) / modele.getQuantiteEntreeIdeale();
        double sortie2IdealePeriode = (totalEntreePeriode * modele.getSortie2Ideale()) / modele.getQuantiteEntreeIdeale();
        double totalIdealePeriode = sortie1IdealePeriode + sortie2IdealePeriode;
        double performanceGlobale = totalIdealePeriode > 0 ?
                ((totalSortie1Periode + totalSortie2Periode) / totalIdealePeriode) * 100 : 0;

        stats.setPerformanceMoyenne(sommePerformances / nombreJours);
        stats.setPerformanceMax(performanceMax);
        stats.setPerformanceMin(performanceMin);
        stats.setJourMeilleur(jourMax);
        stats.setJourPire(jourMin);
        stats.setNombreJours(nombreJours);
        stats.setPerformanceGlobalePeriode(performanceGlobale);

        return stats;
    }
}

