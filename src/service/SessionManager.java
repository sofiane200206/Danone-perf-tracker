package service;

import model.UserRole;

/**
 * Gestionnaire de session pour stocker le rôle de l'utilisateur actuel
 * Utilise le pattern Singleton
 */
public class SessionManager {
    private static SessionManager instance;
    private UserRole currentRole;
    private String identifiantConnecte;

    private SessionManager() {
        // Constructeur privé pour le pattern Singleton
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void login(UserRole role) {
        this.currentRole = role;
    }

    /** Ouvre la session pour un compte authentifié. */
    public void login(model.Utilisateur utilisateur) {
        this.currentRole = utilisateur.getRole();
        this.identifiantConnecte = utilisateur.getIdentifiant();
    }

    public String getIdentifiantConnecte() {
        return identifiantConnecte;
    }

    public void logout() {
        this.currentRole = null;
        this.identifiantConnecte = null;
    }

    public UserRole getCurrentRole() {
        return currentRole;
    }

    public boolean isAdmin() {
        return currentRole == UserRole.ADMIN;
    }

    public boolean isUser() {
        return currentRole == UserRole.USER;
    }

    public boolean isLoggedIn() {
        return currentRole != null;
    }
}