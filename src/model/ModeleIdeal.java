package model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// Modèle pour les paramètres idéaux
public class ModeleIdeal {
    private String matierePremiereNom;
    private double quantiteEntreeIdeale;
    private double sortie1Ideale;
    private double sortie2Ideale;

    // Constructeurs, getters, setters
    public ModeleIdeal() {}

    public ModeleIdeal(String nom, double entree, double sortie1, double sortie2) {
        this.matierePremiereNom = nom;
        this.quantiteEntreeIdeale = entree;
        this.sortie1Ideale = sortie1;
        this.sortie2Ideale = sortie2;
    }

    // Getters et setters
    public String getMatierePremiereNom() { return matierePremiereNom; }
    public void setMatierePremiereNom(String nom) { this.matierePremiereNom = nom; }

    public double getQuantiteEntreeIdeale() { return quantiteEntreeIdeale; }
    public void setQuantiteEntreeIdeale(double quantite) { this.quantiteEntreeIdeale = quantite; }

    public double getSortie1Ideale() { return sortie1Ideale; }
    public void setSortie1Ideale(double sortie1) { this.sortie1Ideale = sortie1; }

    public double getSortie2Ideale() { return sortie2Ideale; }
    public void setSortie2Ideale(double sortie2) { this.sortie2Ideale = sortie2; }

    public boolean isValide() {
        return matierePremiereNom != null && !matierePremiereNom.trim().isEmpty()
                && quantiteEntreeIdeale > 0 && sortie1Ideale >= 0 && sortie2Ideale >= 0;
    }
}