package interfaces;

import dao.DatabaseManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.UserRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.AuthentificationService;
import service.SessionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifie que chaque vue se charge et que sa liaison avec le controleur est
 * correcte : un fx:id renomme ou un champ supprime fait echouer ces tests.
 */
class ChargementDesVuesTest extends BaseTestJavaFx {

    @BeforeAll
    static void demarrer() throws InterruptedException {
        demarrerJavaFx();
    }

    @BeforeEach
    void viderLesComptes() throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM utilisateurs");
        }
        SessionManager.getInstance().logout();
    }

    /**
     * Charge une vue et la place dans une scene : sans cela, les composants
     * situes dans une ScrollPane ne sont pas encore construits et restent
     * introuvables.
     */
    private Parent charger(String vue) throws Exception {
        return surFilJavaFx(() -> {
            Parent racine = FXMLLoader.load(
                    getClass().getClassLoader().getResource("resources/views/" + vue));
            Stage fenetre = new Stage();
            fenetre.setScene(new Scene(racine, 1000, 700));
            fenetre.show();
            racine.applyCss();
            racine.layout();
            return racine;
        });
    }

    @Test
    @DisplayName("L'ecran de connexion se charge avec ses champs")
    void ecranDeConnexionSeCharge() throws Exception {
        Parent racine = charger("login.fxml");

        assertNotNull(racine);
        assertNotNull(racine.lookup("#champIdentifiant"));
        assertNotNull(racine.lookup("#champMotDePasse"));
        assertNotNull(racine.lookup("#btnConnexion"));
        assertInstanceOf(PasswordField.class, racine.lookup("#champMotDePasse"),
                "le mot de passe ne doit jamais s'afficher en clair");
    }

    @Test
    @DisplayName("Sans compte, l'ecran propose la creation de l'administrateur")
    void premierDemarrageProposeLaCreationDuCompte() throws Exception {
        Parent racine = charger("login.fxml");

        assertTrue(racine.lookup("#panneauPremiereUtilisation").isVisible(),
                "le panneau de premiere utilisation doit etre affiche");
        assertFalse(racine.lookup("#panneauConnexion").isVisible(),
                "le formulaire de connexion doit etre masque");
    }

    @Test
    @DisplayName("Des qu'un compte existe, l'ecran demande identifiant et mot de passe")
    void avecUnCompteLeFormulaireDeConnexionEstAffiche() throws Exception {
        new AuthentificationService().creerCompte("admin", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        Parent racine = charger("login.fxml");

        assertTrue(racine.lookup("#panneauConnexion").isVisible());
        assertFalse(racine.lookup("#panneauPremiereUtilisation").isVisible());
    }

    @Test
    @DisplayName("Un mot de passe errone affiche une erreur sans ouvrir l'application")
    void mauvaisMotDePasseAfficheUneErreur() throws Exception {
        new AuthentificationService().creerCompte("admin", "MotDePasse1".toCharArray(), UserRole.ADMIN);

        Parent racine = charger("login.fxml");

        surFilJavaFx(() -> {
            ((TextField) racine.lookup("#champIdentifiant")).setText("admin");
            ((PasswordField) racine.lookup("#champMotDePasse")).setText("MauvaisMotDePasse1");
            ((Button) racine.lookup("#btnConnexion")).fire();
        });

        Label message = (Label) racine.lookup("#labelMessage");
        assertFalse(message.getText().isBlank(), "un message d'erreur doit apparaitre");
        assertFalse(message.getText().toLowerCase().contains("identifiant inconnu"),
                "le message ne doit pas revéler si l'identifiant existe");
        assertEquals("", ((PasswordField) racine.lookup("#champMotDePasse")).getText(),
                "le mot de passe saisi doit etre efface du champ");
    }

    @Test
    @DisplayName("L'ecran principal se charge avec ses composants")
    void ecranPrincipalSeCharge() throws Exception {
        Parent racine = charger("tracker.fxml");

        assertNotNull(racine);
        assertNotNull(racine.lookup("#matierePremiereCombo"));
        assertNotNull(racine.lookup("#joursContainer"));
        assertNotNull(racine.lookup("#labelMoyenneGlobale"));
        assertNotNull(racine.lookup("#panneauxSuperieurs"));
        assertNotNull(racine.lookup("#zoneProductions"));
        assertNotNull(racine.lookup("#btnGererComptes"));
    }
}
