package service;

import dao.DatabaseManager;
import dao.UtilisateurDAO;
import model.UserRole;
import model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AuthentificationServiceTest {

    private AuthentificationService service;

    @BeforeEach
    void viderLesComptes() throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM utilisateurs");
        }
        service = new AuthentificationService(new UtilisateurDAO());
    }

    @Test
    @DisplayName("Au depart aucun compte n'existe : il faut creer l'administrateur")
    void aucunCompteAuDepart() throws ServiceException {
        assertTrue(service.aucunCompteExistant());

        service.creerCompte("admin", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        assertFalse(service.aucunCompteExistant());
    }

    @Test
    @DisplayName("Un compte cree permet de se connecter")
    void connexionAvecLesBonsIdentifiants() throws ServiceException {
        service.creerCompte("sofiane", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        Utilisateur connecte = service.authentifier("sofiane", "MotDePasse1".toCharArray());

        assertNotNull(connecte);
        assertEquals("sofiane", connecte.getIdentifiant());
        assertEquals(UserRole.ADMIN, connecte.getRole());
    }

    @Test
    @DisplayName("Un mauvais mot de passe refuse la connexion")
    void mauvaisMotDePasseRefuse() throws ServiceException {
        service.creerCompte("sofiane", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        assertNull(service.authentifier("sofiane", "MauvaisMotDePasse1".toCharArray()));
    }

    @Test
    @DisplayName("Un identifiant inconnu refuse la connexion")
    void identifiantInconnuRefuse() throws ServiceException {
        service.creerCompte("sofiane", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        assertNull(service.authentifier("inconnu", "MotDePasse1".toCharArray()));
    }

    @Test
    @DisplayName("Les identifiants vides sont refuses sans consulter la base")
    void identifiantsVidesRefuses() throws ServiceException {
        service.creerCompte("sofiane", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        assertNull(service.authentifier(null, "MotDePasse1".toCharArray()));
        assertNull(service.authentifier("  ", "MotDePasse1".toCharArray()));
        assertNull(service.authentifier("sofiane", new char[0]));
        assertNull(service.authentifier("sofiane", null));
    }

    @Test
    @DisplayName("Le mot de passe stocke en base n'est jamais le mot de passe saisi")
    void motDePasseJamaisStockeEnClair() throws ServiceException, SQLException {
        service.creerCompte("sofiane", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        Utilisateur enBase = new UtilisateurDAO().trouverParIdentifiant("sofiane");

        assertNotEquals("MotDePasse1", enBase.getEmpreinteMotDePasse());
        assertFalse(enBase.getEmpreinteMotDePasse().contains("MotDePasse1"));
        assertNotNull(enBase.getSel());
    }

    @Test
    @DisplayName("Un identifiant deja pris est refuse")
    void identifiantDejaPrisRefuse() throws ServiceException {
        service.creerCompte("sofiane", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.creerCompte("sofiane", "AutreMotDePasse1".toCharArray(), UserRole.USER));
        assertTrue(e.getMessage().toLowerCase().contains("utilise"));
    }

    @Test
    @DisplayName("Un mot de passe trop faible est refuse a la creation")
    void motDePasseFaibleRefuse() {
        assertThrows(ServiceException.class,
                () -> service.creerCompte("sofiane", "court".toCharArray(), UserRole.ADMIN));
        assertThrows(ServiceException.class,
                () -> service.creerCompte("sofiane", "quedeslettres".toCharArray(), UserRole.ADMIN));
    }

    @Test
    @DisplayName("Un identifiant trop court est refuse")
    void identifiantTropCourtRefuse() {
        assertThrows(ServiceException.class,
                () -> service.creerCompte("ab", "MotDePasse1".toCharArray(), UserRole.ADMIN));
    }

    @Test
    @DisplayName("Un compte desactive ne peut plus se connecter")
    void compteDesactiveRefuse() throws ServiceException, SQLException {
        Utilisateur compte = service.creerCompte("temporaire", "MotDePasse1".toCharArray(), UserRole.USER);

        new UtilisateurDAO().desactiver(compte.getId());

        assertNull(service.authentifier("temporaire", "MotDePasse1".toCharArray()));
    }

    @Test
    @DisplayName("Le changement de mot de passe exige l'ancien et applique le nouveau")
    void changementDeMotDePasse() throws ServiceException {
        service.creerCompte("sofiane", "AncienMotDePasse1".toCharArray(), UserRole.ADMIN);

        assertThrows(ServiceException.class, () -> service.changerMotDePasse(
                "sofiane", "MauvaisAncien1".toCharArray(), "NouveauMotDePasse1".toCharArray()));

        service.changerMotDePasse("sofiane",
                "AncienMotDePasse1".toCharArray(), "NouveauMotDePasse1".toCharArray());

        assertNull(service.authentifier("sofiane", "AncienMotDePasse1".toCharArray()));
        assertNotNull(service.authentifier("sofiane", "NouveauMotDePasse1".toCharArray()));
    }

    @Test
    @DisplayName("Le role est conserve d'une session a l'autre")
    void roleConserve() throws ServiceException {
        service.creerCompte("operateur", "MotDePasse1".toCharArray(), UserRole.USER);

        assertEquals(UserRole.USER,
                service.authentifier("operateur", "MotDePasse1".toCharArray()).getRole());
    }
}
