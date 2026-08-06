package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.UserRole;
import model.Utilisateur;
import service.AuthentificationService;
import service.MotDePasseService;
import service.ServiceException;
import service.SessionManager;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private Label labelBienvenue;
    @FXML private Label labelSousTitre;
    @FXML private Label labelMessage;

    @FXML private VBox panneauConnexion;
    @FXML private TextField champIdentifiant;
    @FXML private PasswordField champMotDePasse;
    @FXML private Button btnConnexion;

    @FXML private VBox panneauPremiereUtilisation;
    @FXML private TextField champNouvelIdentifiant;
    @FXML private PasswordField champNouveauMotDePasse;
    @FXML private PasswordField champConfirmation;
    @FXML private Button btnCreerCompte;

    private final AuthentificationService authentificationService = new AuthentificationService();

    @FXML
    public void initialize() {
        btnConnexion.setOnAction(e -> connecter());
        btnCreerCompte.setOnAction(e -> creerCompteAdministrateur());

        // Entrée valide le formulaire affiché
        champMotDePasse.setOnAction(e -> connecter());
        champConfirmation.setOnAction(e -> creerCompteAdministrateur());

        try {
            if (authentificationService.aucunCompteExistant()) {
                afficherPremiereUtilisation();
            }
        } catch (ServiceException e) {
            LOGGER.log(Level.SEVERE, "Consultation des comptes impossible", e);
            afficherErreur("Base de données inaccessible. Vérifiez l'installation.");
            btnConnexion.setDisable(true);
        }
    }

    private void afficherPremiereUtilisation() {
        labelSousTitre.setText("Première utilisation");
        basculer(panneauConnexion, false);
        basculer(panneauPremiereUtilisation, true);
        Platform.runLater(() -> champNouvelIdentifiant.requestFocus());
    }

    private void basculer(VBox panneau, boolean visible) {
        panneau.setVisible(visible);
        panneau.setManaged(visible);
    }

    private void connecter() {
        effacerMessage();

        char[] motDePasse = champMotDePasse.getText().toCharArray();
        try {
            Utilisateur utilisateur =
                    authentificationService.authentifier(champIdentifiant.getText(), motDePasse);

            if (utilisateur == null) {
                // Un seul message, quelle que soit la cause : ne pas revéler
                // si l'identifiant existe.
                afficherErreur("Identifiant ou mot de passe incorrect.");
                champMotDePasse.clear();
                return;
            }

            SessionManager.getInstance().login(utilisateur);
            ouvrirTracker(utilisateur.getRole());

        } catch (ServiceException e) {
            LOGGER.log(Level.SEVERE, "Erreur d'authentification", e);
            afficherErreur("Connexion impossible. Réessayez.");
        } finally {
            MotDePasseService.effacer(motDePasse);
        }
    }

    private void creerCompteAdministrateur() {
        effacerMessage();

        String motDePasse = champNouveauMotDePasse.getText();
        if (!motDePasse.equals(champConfirmation.getText())) {
            afficherErreur("Les deux mots de passe ne correspondent pas.");
            return;
        }

        char[] caracteres = motDePasse.toCharArray();
        try {
            Utilisateur administrateur = authentificationService.creerCompte(
                    champNouvelIdentifiant.getText(), caracteres, UserRole.ADMIN);

            SessionManager.getInstance().login(administrateur);
            ouvrirTracker(administrateur.getRole());

        } catch (ServiceException e) {
            afficherErreur(e.getMessage());
        } finally {
            MotDePasseService.effacer(caracteres);
            champNouveauMotDePasse.clear();
            champConfirmation.clear();
        }
    }

    private void ouvrirTracker(UserRole role) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader()
                    .getResource("resources/views/tracker.fxml"));
            Parent root = loader.load();

            TrackerController trackerController = loader.getController();
            trackerController.configureForRole(role);

            Stage stage = (Stage) btnConnexion.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 700));
            stage.setTitle("Performance Tracker - " + role.getDisplayName());
            stage.setResizable(true);
            stage.show();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ouverture de l'écran principal impossible", e);
            afficherErreur("Impossible d'ouvrir l'application.");
        }
    }

    private void afficherErreur(String message) {
        labelMessage.setText(message);
    }

    private void effacerMessage() {
        labelMessage.setText("");
    }
}
