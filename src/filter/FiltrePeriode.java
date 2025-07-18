package filter;

import java.time.LocalDate;

public class FiltrePeriode {
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String nom;
    private TypeFiltre type;

    public enum TypeFiltre {
        PERSONNALISE, SEPT_DERNIERS_JOURS, SEMAINE_COURANTE, MOIS_COURANT, TOUT
    }

    public FiltrePeriode(LocalDate debut, LocalDate fin, String nom, TypeFiltre type) {
        this.dateDebut = debut;
        this.dateFin = fin;
        this.nom = nom;
        this.type = type;
    }

    // Factory methods pour les filtres prédéfinis
    public static FiltrePeriode creerFiltreSeptJours() {
        LocalDate fin = LocalDate.now();
        LocalDate debut = fin.minusDays(7);
        return new FiltrePeriode(debut, fin, "7 derniers jours", TypeFiltre.SEPT_DERNIERS_JOURS);
    }

    public static FiltrePeriode creerFiltreSemaineCourante() {
        LocalDate now = LocalDate.now();
        LocalDate debut = now.minusDays(now.getDayOfWeek().getValue() - 1);
        LocalDate fin = now.plusDays(7 - now.getDayOfWeek().getValue());
        return new FiltrePeriode(debut, fin, "Semaine courante", TypeFiltre.SEMAINE_COURANTE);
    }

    public static FiltrePeriode creerFiltreMoisCourant() {
        LocalDate now = LocalDate.now();
        LocalDate debut = now.withDayOfMonth(1);
        LocalDate fin = now.withDayOfMonth(now.lengthOfMonth());
        return new FiltrePeriode(debut, fin, "Mois courant", TypeFiltre.MOIS_COURANT);
    }

    public static FiltrePeriode creerFiltreTout() {
        return new FiltrePeriode(null, null, "Toutes les productions", TypeFiltre.TOUT);
    }

    public static FiltrePeriode creerFiltrePersonnalise(LocalDate debut, LocalDate fin) {
        String nom = String.format("Du %s au %s", debut.toString(), fin.toString());
        return new FiltrePeriode(debut, fin, nom, TypeFiltre.PERSONNALISE);
    }

    public boolean estDansPeriode(LocalDate date) {
        if (type == TypeFiltre.TOUT) return true;
        if (date == null) return false;

        boolean apresDebut = (dateDebut == null || !date.isBefore(dateDebut));
        boolean avantFin = (dateFin == null || !date.isAfter(dateFin));

        return apresDebut && avantFin;
    }

    // Getters
    public LocalDate getDateDebut() { return dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public String getNom() { return nom; }
    public TypeFiltre getType() { return type; }
}

