package model;

import java.time.LocalDateTime;

/**
 * Compte permettant de se connecter a l'application.
 * Le mot de passe n'est jamais stocke : seul son empreinte et le sel le sont.
 */
public class Utilisateur {

    private Long id;
    private String identifiant;
    private String empreinteMotDePasse;
    private String sel;
    private UserRole role;
    private LocalDateTime dateCreation;
    private boolean actif = true;

    public Utilisateur() {
    }

    public Utilisateur(String identifiant, String empreinteMotDePasse, String sel, UserRole role) {
        this.identifiant = identifiant;
        this.empreinteMotDePasse = empreinteMotDePasse;
        this.sel = sel;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdentifiant() { return identifiant; }
    public void setIdentifiant(String identifiant) { this.identifiant = identifiant; }

    public String getEmpreinteMotDePasse() { return empreinteMotDePasse; }
    public void setEmpreinteMotDePasse(String empreinte) { this.empreinteMotDePasse = empreinte; }

    public String getSel() { return sel; }
    public void setSel(String sel) { this.sel = sel; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    @Override
    public String toString() {
        // Volontairement sans empreinte ni sel : ces valeurs n'ont rien a faire dans un log
        return identifiant + " (" + (role != null ? role.getDisplayName() : "sans role") + ")";
    }
}
