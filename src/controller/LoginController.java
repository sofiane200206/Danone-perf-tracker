package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import model.UserRole;
import service.SessionManager;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML
    private Button btnAdmin;

    @FXML
    private Button btnUser;

    @FXML
    private Label labelBienvenue;

    @FXML
    public void initialize() {
        // Configuration des boutons
        btnAdmin.setOnAction(e -> connecterCommeAdmin());
        btnUser.setOnAction(e -> connecterCommeUser());
    }

    @FXML
    private void connecterCommeAdmin() {
        connecter(UserRole.ADMIN);
    }

    @FXML
    private void connecterCommeUser() {
        connecter(UserRole.USER);
    }

    private void connecter(UserRole role) {
        try {
            // Enregistrer le rôle dans la session
            SessionManager.getInstance().login(role);

            LOGGER.info("Connexion réussie en tant que : " + role.getDisplayName());

            // Charger l'écran principal (tracker)
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader()
                    .getResource("resources/views/tracker.fxml"));
            Parent root = loader.load();

            // Récupérer le contrôleur pour lui notifier le rôle
            TrackerController trackerController = loader.getController();
            trackerController.configureForRole(role);

            // Changer de scène
            Stage stage = (Stage) btnAdmin.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 700));
            stage.setTitle("Performance Tracker - " + role.getDisplayName());
            stage.show();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la connexion", e);
        }
    }
}