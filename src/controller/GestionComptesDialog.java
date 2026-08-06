package controller;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.UserRole;
import model.Utilisateur;
import service.AuthentificationService;
import service.MotDePasseService;
import service.ServiceException;

import java.util.List;

/**
 * Boites de dialogue de gestion des comptes, isolees du TrackerController
 * pour ne pas alourdir davantage un fichier deja tres long.
 */
public class GestionComptesDialog {

    private final AuthentificationService authentificationService;

    public GestionComptesDialog(AuthentificationService authentificationService) {
        this.authentificationService = authentificationService;
    }

    /** Ecran d'administration des comptes : liste, creation, activation, reinitialisation. */
    public void afficherGestion(String identifiantConnecte) {
        Dialog<Void> dialogue = new Dialog<>();
        dialogue.setTitle("Gestion des comptes");
        dialogue.setHeaderText("Comptes autorisés à utiliser l'application");
        dialogue.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogue.getDialogPane().setPrefWidth(560);

        ListView<Utilisateur> liste = new ListView<>();
        liste.setPrefHeight(240);
        liste.setCellFactory(vue -> new ListCell<>() {
            @Override
            protected void updateItem(Utilisateur utilisateur, boolean vide) {
                super.updateItem(utilisateur, vide);
                if (vide || utilisateur == null) {
                    setText(null);
                    return;
                }
                String etat = utilisateur.isActif() ? "" : "  —  désactivé";
                String moi = utilisateur.getIdentifiant().equals(identifiantConnecte) ? "  (vous)" : "";
                setText(utilisateur.getIdentifiant() + "  ·  "
                        + utilisateur.getRole().getDisplayName() + moi + etat);
            }
        });

        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #c0392b;");

        rafraichir(liste, message);

        Button btnNouveau = new Button("➕ Nouveau compte");
        Button btnActiver = new Button("Activer / Désactiver");
        Button btnReinitialiser = new Button("🔑 Réinitialiser le mot de passe");

        btnNouveau.setOnAction(e -> {
            if (afficherCreationCompte()) {
                rafraichir(liste, message);
                message.setStyle("-fx-text-fill: #27ae60;");
                message.setText("Compte créé.");
            }
        });

        btnActiver.setOnAction(e -> {
            Utilisateur choisi = liste.getSelectionModel().getSelectedItem();
            if (choisi == null) {
                afficher(message, "Sélectionnez d'abord un compte.");
                return;
            }
            try {
                if (choisi.isActif()) {
                    authentificationService.desactiverCompte(choisi.getId(), identifiantConnecte);
                } else {
                    authentificationService.reactiverCompte(choisi.getId());
                }
                rafraichir(liste, message);
            } catch (ServiceException ex) {
                afficher(message, ex.getMessage());
            }
        });

        btnReinitialiser.setOnAction(e -> {
            Utilisateur choisi = liste.getSelectionModel().getSelectedItem();
            if (choisi == null) {
                afficher(message, "Sélectionnez d'abord un compte.");
                return;
            }
            if (afficherReinitialisation(choisi)) {
                message.setStyle("-fx-text-fill: #27ae60;");
                message.setText("Mot de passe réinitialisé pour " + choisi.getIdentifiant() + ".");
            }
        });

        HBox actions = new HBox(10, btnNouveau, btnActiver, btnReinitialiser);
        VBox contenu = new VBox(12, liste, actions, message);
        contenu.setPadding(new Insets(10));
        VBox.setVgrow(liste, Priority.ALWAYS);

        dialogue.getDialogPane().setContent(contenu);
        dialogue.showAndWait();
    }

    /** Formulaire de creation d'un compte. Retourne vrai si un compte a ete cree. */
    public boolean afficherCreationCompte() {
        Dialog<ButtonType> dialogue = new Dialog<>();
        dialogue.setTitle("Nouveau compte");
        dialogue.setHeaderText("Créer un compte utilisateur");

        ButtonType valider = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(valider, ButtonType.CANCEL);

        TextField identifiant = new TextField();
        identifiant.setPromptText("Identifiant (3 caractères minimum)");
        PasswordField motDePasse = new PasswordField();
        motDePasse.setPromptText("Mot de passe");
        PasswordField confirmation = new PasswordField();
        confirmation.setPromptText("Confirmer le mot de passe");

        ComboBox<UserRole> role = new ComboBox<>();
        role.getItems().addAll(UserRole.values());
        role.setValue(UserRole.USER);
        role.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(UserRole r) { return r == null ? "" : r.getDisplayName(); }
            @Override public UserRole fromString(String s) { return null; }
        });

        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #c0392b;");

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(10);
        grille.setPadding(new Insets(15));
        grille.addRow(0, new Label("Identifiant :"), identifiant);
        grille.addRow(1, new Label("Mot de passe :"), motDePasse);
        grille.addRow(2, new Label("Confirmation :"), confirmation);
        grille.addRow(3, new Label("Rôle :"), role);
        grille.add(new Label("Au moins 8 caractères, mêlant lettres et chiffres."), 0, 4, 2, 1);
        grille.add(message, 0, 5, 2, 1);

        dialogue.getDialogPane().setContent(grille);

        // Empeche la fermeture tant que la saisie n'est pas valide
        Button boutonValider = (Button) dialogue.getDialogPane().lookupButton(valider);
        boutonValider.addEventFilter(javafx.event.ActionEvent.ACTION, evenement -> {
            message.setText("");
            if (!motDePasse.getText().equals(confirmation.getText())) {
                message.setText("Les deux mots de passe ne correspondent pas.");
                evenement.consume();
                return;
            }
            char[] caracteres = motDePasse.getText().toCharArray();
            try {
                authentificationService.creerCompte(identifiant.getText(), caracteres, role.getValue());
            } catch (ServiceException e) {
                message.setText(e.getMessage());
                evenement.consume();
            } finally {
                MotDePasseService.effacer(caracteres);
            }
        });

        return dialogue.showAndWait().filter(valider::equals).isPresent();
    }

    /** Changement de son propre mot de passe, ancien mot de passe exige. */
    public void afficherChangementMotDePasse(String identifiant) {
        Dialog<ButtonType> dialogue = new Dialog<>();
        dialogue.setTitle("Changer mon mot de passe");
        dialogue.setHeaderText("Compte : " + identifiant);

        ButtonType valider = new ButtonType("Changer", ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(valider, ButtonType.CANCEL);

        PasswordField actuel = new PasswordField();
        actuel.setPromptText("Mot de passe actuel");
        PasswordField nouveau = new PasswordField();
        nouveau.setPromptText("Nouveau mot de passe");
        PasswordField confirmation = new PasswordField();
        confirmation.setPromptText("Confirmer");

        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #c0392b;");

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(10);
        grille.setPadding(new Insets(15));
        grille.addRow(0, new Label("Actuel :"), actuel);
        grille.addRow(1, new Label("Nouveau :"), nouveau);
        grille.addRow(2, new Label("Confirmation :"), confirmation);
        grille.add(message, 0, 3, 2, 1);

        dialogue.getDialogPane().setContent(grille);

        Button boutonValider = (Button) dialogue.getDialogPane().lookupButton(valider);
        boutonValider.addEventFilter(javafx.event.ActionEvent.ACTION, evenement -> {
            message.setText("");
            if (!nouveau.getText().equals(confirmation.getText())) {
                message.setText("Les deux mots de passe ne correspondent pas.");
                evenement.consume();
                return;
            }
            char[] ancien = actuel.getText().toCharArray();
            char[] cible = nouveau.getText().toCharArray();
            try {
                authentificationService.changerMotDePasse(identifiant, ancien, cible);
            } catch (ServiceException e) {
                message.setText(e.getMessage());
                evenement.consume();
            } finally {
                MotDePasseService.effacer(ancien);
                MotDePasseService.effacer(cible);
            }
        });

        if (dialogue.showAndWait().filter(valider::equals).isPresent()) {
            Alert succes = new Alert(Alert.AlertType.INFORMATION, "Mot de passe modifié.");
            succes.setHeaderText(null);
            succes.showAndWait();
        }
    }

    private boolean afficherReinitialisation(Utilisateur cible) {
        Dialog<ButtonType> dialogue = new Dialog<>();
        dialogue.setTitle("Réinitialiser un mot de passe");
        dialogue.setHeaderText("Compte : " + cible.getIdentifiant());

        ButtonType valider = new ButtonType("Réinitialiser", ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(valider, ButtonType.CANCEL);

        PasswordField nouveau = new PasswordField();
        nouveau.setPromptText("Nouveau mot de passe");
        PasswordField confirmation = new PasswordField();
        confirmation.setPromptText("Confirmer");

        Label message = new Label();
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: #c0392b;");

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(10);
        grille.setPadding(new Insets(15));
        grille.addRow(0, new Label("Nouveau :"), nouveau);
        grille.addRow(1, new Label("Confirmation :"), confirmation);
        grille.add(new Label("Communiquez-le à la personne, qui pourra le changer ensuite."),
                0, 2, 2, 1);
        grille.add(message, 0, 3, 2, 1);

        dialogue.getDialogPane().setContent(grille);

        Button boutonValider = (Button) dialogue.getDialogPane().lookupButton(valider);
        boutonValider.addEventFilter(javafx.event.ActionEvent.ACTION, evenement -> {
            message.setText("");
            if (!nouveau.getText().equals(confirmation.getText())) {
                message.setText("Les deux mots de passe ne correspondent pas.");
                evenement.consume();
                return;
            }
            char[] caracteres = nouveau.getText().toCharArray();
            try {
                authentificationService.reinitialiserMotDePasse(cible.getId(), caracteres);
            } catch (ServiceException e) {
                message.setText(e.getMessage());
                evenement.consume();
            } finally {
                MotDePasseService.effacer(caracteres);
            }
        });

        return dialogue.showAndWait().filter(valider::equals).isPresent();
    }

    private void rafraichir(ListView<Utilisateur> liste, Label message) {
        try {
            List<Utilisateur> comptes = authentificationService.listerComptes();
            liste.getItems().setAll(comptes);
        } catch (ServiceException e) {
            afficher(message, e.getMessage());
        }
    }

    private void afficher(Label message, String texte) {
        message.setStyle("-fx-text-fill: #c0392b;");
        message.setText(texte);
    }
}
