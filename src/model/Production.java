package model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Production {
    private LocalDate date;
    private LocalTime heure;
    private double quantiteEntreeReelle;
    private double sortie1Reelle;
    private double sortie2Reelle;
    private String statut; // "VALIDE", "ERREUR", "INCOMPLETE"
    private String messageErreur;

    // Constructeurs
    public Production() {
        this.statut = "INCOMPLETE";
    }

    public Production(LocalDate date, LocalTime heure, double entree, double sortie1, double sortie2) {
        this.date = date;
        this.heure = heure;
        this.quantiteEntreeReelle = entree;
        this.sortie1Reelle = sortie1;
        this.sortie2Reelle = sortie2;
        this.statut = "VALIDE";
    }

    // Getters et setters
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getHeure() { return heure; }
    public void setHeure(LocalTime heure) { this.heure = heure; }

    public double getQuantiteEntreeReelle() { return quantiteEntreeReelle; }
    public void setQuantiteEntreeReelle(double quantite) { this.quantiteEntreeReelle = quantite; }

    public double getSortie1Reelle() { return sortie1Reelle; }
    public void setSortie1Reelle(double sortie1) { this.sortie1Reelle = sortie1; }

    public double getSortie2Reelle() { return sortie2Reelle; }
    public void setSortie2Reelle(double sortie2) { this.sortie2Reelle = sortie2; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getMessageErreur() { return messageErreur; }
    public void setMessageErreur(String message) { this.messageErreur = message; }

    public double getTotalSortieReelle() {
        return sortie1Reelle + sortie2Reelle;
    }

    public boolean isValide() {
        return "VALIDE".equals(statut) && date != null && heure != null
                && quantiteEntreeReelle >= 0 && sortie1Reelle >= 0 && sortie2Reelle >= 0;
    }
}