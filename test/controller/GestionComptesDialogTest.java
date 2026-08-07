package controller;

import dao.DatabaseManager;
import interfaces.BaseTestJavaFx;
import javafx.scene.control.*;
import model.UserRole;
import model.Utilisateur;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.AuthentificationService;
import service.ServiceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests des formulaires de gestion des comptes : les regles de saisie d'une
 * part, et le cablage reel des dialogues d'autre part (un champ renomme ou un
 * bouton mal branche fait echouer ces tests).
 */
class GestionComptesDialogTest extends BaseTestJavaFx {

    private AuthentificationService authentification;
    private GestionComptesDialog dialogues;

    @BeforeAll
    static void demarrer() throws InterruptedException {
        demarrerJavaFx();
    }

    @BeforeEach
    void preparerComptes() throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM utilisateurs");
        }
        authentification = new AuthentificationService();
        dialogues = new GestionComptesDialog(authentification);
    }

    private Utilisateur creer(String identifiant, String motDePasse, UserRole role)
            throws ServiceException {
        return authentification.creerCompte(identifiant, motDePasse.toCharArray(), role);
    }

    // ---------------------------------------------------------------- regles

    @Test
    @DisplayName("Créer un compte avec des mots de passe différents est refusé")
    void creationAvecConfirmationDifferente() {
        String refus = dialogues.creerCompte("operateur", "MotDePasse1", "MotDePasse2", UserRole.USER);

        assertNotNull(refus);
        assertTrue(refus.contains("correspondent pas"));
    }

    @Test
    @DisplayName("Créer un compte valide l'enregistre et permet de s'y connecter")
    void creationValide() throws ServiceException {
        assertNull(dialogues.creerCompte("operateur", "MotDePasse1", "MotDePasse1", UserRole.USER));

        Utilisateur connecte = authentification.authentifier("operateur", "MotDePasse1".toCharArray());
        assertNotNull(connecte);
        assertEquals(UserRole.USER, connecte.getRole());
    }

    @Test
    @DisplayName("Le formulaire refuse un mot de passe trop faible et un identifiant déjà pris")
    void creationRefuseeParLesReglesMetier() throws ServiceException {
        assertNotNull(dialogues.creerCompte("operateur", "court", "court", UserRole.USER));

        creer("existant", "MotDePasse1", UserRole.USER);
        String refus = dialogues.creerCompte("existant", "AutreMotDePasse1", "AutreMotDePasse1", UserRole.USER);

        assertNotNull(refus);
        assertTrue(refus.toLowerCase().contains("utilisé") || refus.toLowerCase().contains("utilise"));
    }

    @Test
    @DisplayName("Changer son mot de passe exige le mot de passe actuel")
    void changementExigeLAncienMotDePasse() throws ServiceException {
        creer("sofiane", "AncienMotDePasse1", UserRole.ADMIN);

        assertNotNull(dialogues.changerMotDePasse(
                "sofiane", "MauvaisAncien1", "NouveauMotDePasse1", "NouveauMotDePasse1"));
        assertNotNull(authentification.authentifier("sofiane", "AncienMotDePasse1".toCharArray()),
                "l'ancien mot de passe reste valable apres un echec");
    }

    @Test
    @DisplayName("Changer son mot de passe avec une confirmation erronée est refusé")
    void changementAvecConfirmationDifferente() throws ServiceException {
        creer("sofiane", "AncienMotDePasse1", UserRole.ADMIN);

        String refus = dialogues.changerMotDePasse(
                "sofiane", "AncienMotDePasse1", "NouveauMotDePasse1", "AutreChose1");

        assertNotNull(refus);
        assertTrue(refus.contains("correspondent pas"));
    }

    @Test
    @DisplayName("Un changement valide remplace bien le mot de passe")
    void changementValide() throws ServiceException {
        creer("sofiane", "AncienMotDePasse1", UserRole.ADMIN);

        assertNull(dialogues.changerMotDePasse(
                "sofiane", "AncienMotDePasse1", "NouveauMotDePasse1", "NouveauMotDePasse1"));

        assertNull(authentification.authentifier("sofiane", "AncienMotDePasse1".toCharArray()));
        assertNotNull(authentification.authentifier("sofiane", "NouveauMotDePasse1".toCharArray()));
    }

    @Test
    @DisplayName("Un administrateur réinitialise le mot de passe d'un opérateur")
    void reinitialisationParAdministrateur() throws ServiceException {
        Utilisateur operateur = creer("operateur", "AncienMotDePasse1", UserRole.USER);

        assertNotNull(dialogues.reinitialiserMotDePasse(operateur, "Nouveau1", "AutreChose1"),
                "confirmation differente");
        assertNull(dialogues.reinitialiserMotDePasse(operateur, "NouveauMotDePasse1", "NouveauMotDePasse1"));

        assertNotNull(authentification.authentifier("operateur", "NouveauMotDePasse1".toCharArray()));
    }

    @Test
    @DisplayName("Désactiver puis réactiver un compte depuis la liste")
    void basculeDActivation() throws ServiceException {
        creer("admin", "MotDePasse1", UserRole.ADMIN);
        Utilisateur operateur = creer("operateur", "MotDePasse1", UserRole.USER);

        assertNull(dialogues.basculerActivation(operateur, "admin"));
        assertNull(authentification.authentifier("operateur", "MotDePasse1".toCharArray()),
                "un compte desactive ne doit plus se connecter");

        Utilisateur desactive = authentification.listerComptes().stream()
                .filter(u -> u.getIdentifiant().equals("operateur"))
                .findFirst().orElseThrow();

        assertNull(dialogues.basculerActivation(desactive, "admin"));
        assertNotNull(authentification.authentifier("operateur", "MotDePasse1".toCharArray()));
    }

    @Test
    @DisplayName("Les garde-fous d'administration remontent jusqu'au formulaire")
    void gardeFousRemontesAlUtilisateur() throws ServiceException {
        Utilisateur admin = creer("admin", "MotDePasse1", UserRole.ADMIN);

        assertTrue(dialogues.basculerActivation(admin, "admin").contains("propre compte"));
        assertNotNull(dialogues.basculerActivation(null, "admin"),
                "aucun compte selectionne");
    }

    @Test
    @DisplayName("Retirer le dernier administrateur est refusé jusque dans le formulaire")
    void dernierAdministrateurProtege() throws ServiceException {
        Utilisateur admin = creer("admin", "MotDePasse1", UserRole.ADMIN);
        creer("operateur", "MotDePasse1", UserRole.USER);

        String refus = dialogues.basculerActivation(admin, "operateur");

        assertNotNull(refus);
        assertTrue(refus.contains("dernier administrateur"));
    }

    // -------------------------------------------------------------- cablage

    @Test
    @DisplayName("Le formulaire de création présente ses champs, mot de passe masqué")
    void formulaireDeCreationBienConstruit() throws Exception {
        Dialog<ButtonType> dialogue = surFilJavaFx(() -> dialogues.construireDialogueCreation());
        DialogPane panneau = dialogue.getDialogPane();

        assertInstanceOf(TextField.class, panneau.lookup("#champIdentifiant"));
        assertInstanceOf(PasswordField.class, panneau.lookup("#champMotDePasse"),
                "le mot de passe ne doit jamais s'afficher en clair");
        assertInstanceOf(PasswordField.class, panneau.lookup("#champConfirmation"));
        assertInstanceOf(ComboBox.class, panneau.lookup("#choixRole"));
        assertEquals(2, panneau.getButtonTypes().size(), "un bouton valider et un annuler");
    }

    @Test
    @DisplayName("Une saisie invalide affiche l'erreur sans fermer le formulaire")
    void saisieInvalideAfficheLErreurEtGardeLeFormulaireOuvert() throws Exception {
        Dialog<ButtonType> dialogue = surFilJavaFx(() -> dialogues.construireDialogueCreation());
        DialogPane panneau = dialogue.getDialogPane();

        surFilJavaFx(() -> {
            ((TextField) panneau.lookup("#champIdentifiant")).setText("operateur");
            ((PasswordField) panneau.lookup("#champMotDePasse")).setText("MotDePasse1");
            ((PasswordField) panneau.lookup("#champConfirmation")).setText("PasLeMeme1");
            ((Button) panneau.lookupButton(panneau.getButtonTypes().get(0))).fire();
        });

        Label message = (Label) panneau.lookup("#labelMessage");
        assertTrue(message.getText().contains("correspondent pas"));
        assertNull(dialogue.getResult(), "le formulaire ne doit pas s'etre valide");
    }

    @Test
    @DisplayName("Le formulaire de changement de mot de passe est correctement câblé")
    void formulaireDeChangementBienCable() throws Exception {
        creer("sofiane", "AncienMotDePasse1", UserRole.ADMIN);

        Dialog<ButtonType> dialogue =
                surFilJavaFx(() -> dialogues.construireDialogueChangementMotDePasse("sofiane"));
        DialogPane panneau = dialogue.getDialogPane();

        surFilJavaFx(() -> {
            ((PasswordField) panneau.lookup("#champActuel")).setText("MauvaisAncien1");
            ((PasswordField) panneau.lookup("#champNouveau")).setText("NouveauMotDePasse1");
            ((PasswordField) panneau.lookup("#champConfirmation")).setText("NouveauMotDePasse1");
            ((Button) panneau.lookupButton(panneau.getButtonTypes().get(0))).fire();
        });

        Label message = (Label) panneau.lookup("#labelMessage");
        assertFalse(message.getText().isBlank(), "le mot de passe actuel errone doit etre signale");
    }

    @Test
    @DisplayName("Le formulaire de réinitialisation est correctement câblé")
    void formulaireDeReinitialisationBienCable() throws Exception {
        Utilisateur operateur = creer("operateur", "MotDePasse1", UserRole.USER);

        Dialog<ButtonType> dialogue =
                surFilJavaFx(() -> dialogues.construireDialogueReinitialisation(operateur));
        DialogPane panneau = dialogue.getDialogPane();

        surFilJavaFx(() -> {
            ((PasswordField) panneau.lookup("#champNouveau")).setText("NouveauMotDePasse1");
            ((PasswordField) panneau.lookup("#champConfirmation")).setText("NouveauMotDePasse1");
            ((Button) panneau.lookupButton(panneau.getButtonTypes().get(0))).fire();
        });

        assertNotNull(authentification.authentifier("operateur", "NouveauMotDePasse1".toCharArray()),
                "le nouveau mot de passe doit etre actif");
    }
}
