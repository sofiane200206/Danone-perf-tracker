package interfaces;

import components.HourMinuteField;
import controller.TrackerController;
import dao.DatabaseManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.MatierePremiereModel;
import model.ProductionModel;
import model.UserRole;
import model.Utilisateur;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.MatierePremiereService;
import service.ProductionService;
import service.SessionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parcours complets depuis l'interface : selectionner une matiere, ajouter une
 * production, la remplir et l'enregistrer.
 *
 * Les tests precedents chargeaient les vues sans jamais les parcourir, ce qui
 * avait laisse passer un ClassCastException au premier ajout de production.
 */
class ParcoursSaisieTest extends BaseTestJavaFx {

    private MatierePremiereModel matiere;

    @BeforeAll
    static void demarrer() throws InterruptedException {
        demarrerJavaFx();
    }

    @BeforeEach
    void preparerBaseEtSession() throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM sorties_reelles");
            stmt.executeUpdate("DELETE FROM productions");
            stmt.executeUpdate("DELETE FROM sorties_ideales");
            stmt.executeUpdate("DELETE FROM matieres_premieres");
        }

        matiere = new MatierePremiereService()
                .creerMatierePremiereSimple("Lait de test", 100.0, 60.0, 20.0);

        Utilisateur operateur = new Utilisateur();
        operateur.setIdentifiant("operateur_test");
        operateur.setRole(UserRole.USER);
        SessionManager.getInstance().login(operateur);

        oublierLesErreursInterface();
    }

    @org.junit.jupiter.api.AfterEach
    void aucunPlantageDurantLeParcours() {
        // Un plantage au clic n'interrompt pas JavaFX : il faut le reclamer
        verifierAucuneErreurInterface();
    }

    /** Ecran principal charge, affiche, avec sa matiere selectionnee. */
    private Parent ouvrirEcranAvecMatiereSelectionnee() throws Exception {
        return surFilJavaFx(() -> {
            FXMLLoader chargeur = new FXMLLoader(
                    getClass().getClassLoader().getResource("resources/views/tracker.fxml"));
            Parent racine = chargeur.load();

            Stage fenetre = new Stage();
            fenetre.setScene(new Scene(racine, 1200, 800));
            fenetre.show();
            racine.applyCss();
            racine.layout();

            TrackerController controleur = chargeur.getController();
            controleur.configureForRole(UserRole.ADMIN);

            @SuppressWarnings("unchecked")
            ComboBox<MatierePremiereModel> combo =
                    (ComboBox<MatierePremiereModel>) racine.lookup("#matierePremiereCombo");
            combo.setValue(combo.getItems().stream()
                    .filter(m -> m.getId().equals(matiere.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("matiere absente de la liste")));

            controleur.ajouterProduction();
            racine.layout();
            return racine;
        });
    }

    // --------------------------------------------------------------- outillage

    private List<Node> tousLesNoeuds(Node racine) {
        List<Node> noeuds = new ArrayList<>();
        noeuds.add(racine);
        if (racine instanceof Parent parent) {
            for (Node enfant : parent.getChildrenUnmodifiable()) {
                noeuds.addAll(tousLesNoeuds(enfant));
            }
        }
        return noeuds;
    }

    /** Champs de saisie de la ligne de production, reperes par leur texte d'invite. */
    private List<TextField> champsDeSaisie(Parent racine, String debutInvite) {
        VBox conteneur = (VBox) racine.lookup("#joursContainer");
        return tousLesNoeuds(conteneur).stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(champ -> champ.getPromptText() != null
                        && champ.getPromptText().startsWith(debutInvite))
                .toList();
    }

    private HourMinuteField champHeure(Parent racine) {
        VBox conteneur = (VBox) racine.lookup("#joursContainer");
        return tousLesNoeuds(conteneur).stream()
                .filter(HourMinuteField.class::isInstance)
                .map(HourMinuteField.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("champ heure introuvable"));
    }

    private Button bouton(Parent racine, String texteContenu) {
        VBox conteneur = (VBox) racine.lookup("#joursContainer");
        return tousLesNoeuds(conteneur).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(b -> b.getText() != null && b.getText().contains(texteContenu))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("bouton introuvable : " + texteContenu));
    }

    private Label messageDeResultat(Parent racine) {
        VBox conteneur = (VBox) racine.lookup("#joursContainer");
        return tousLesNoeuds(conteneur).stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .filter(l -> l.getText() != null
                        && (l.getText().contains("⏳") || l.getText().contains("⚠")
                            || l.getText().contains("✅") || l.getText().contains("❌")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("message de resultat introuvable"));
    }

    private void saisir(Parent racine, String entree, String... sorties) throws Exception {
        surFilJavaFx(() -> {
            champsDeSaisie(racine, "Quantité entrée").get(0).setText(entree);
            List<TextField> champsSorties = champsDeSaisie(racine, "Sortie ");
            for (int i = 0; i < sorties.length && i < champsSorties.size(); i++) {
                champsSorties.get(i).setText(sorties[i]);
            }
            champHeure(racine).setTime(LocalTime.of(8, 30));
        });
    }

    private void enregistrer(Parent racine) throws Exception {
        surFilJavaFx(() -> bouton(racine, "Sauvegarder").fire());
    }

    private List<ProductionModel> productionsEnBase() {
        ProductionService service = new ProductionService();
        service.setMatierePremiereModel(matiere);
        return service.getProductions();
    }

    // ----------------------------------------------------------------- parcours

    @Test
    @DisplayName("Ajouter une production fait apparaître une ligne de saisie")
    void ajoutDUneLigneDeSaisie() throws Exception {
        // C'est ici que le passage de HBox a FlowPane avait provoque un plantage
        Parent racine = ouvrirEcranAvecMatiereSelectionnee();

        assertEquals(1, champsDeSaisie(racine, "Quantité entrée").size());
        assertEquals(2, champsDeSaisie(racine, "Sortie ").size(),
                "une ligne par sortie de la matière");
        assertNotNull(bouton(racine, "Sauvegarder"));
    }

    @Test
    @DisplayName("Une saisie complète est enregistrée avec ses valeurs et son auteur")
    void saisieCompleteEnregistree() throws Exception {
        Parent racine = ouvrirEcranAvecMatiereSelectionnee();

        saisir(racine, "100", "55", "18");
        enregistrer(racine);

        List<ProductionModel> enBase = productionsEnBase();
        assertEquals(1, enBase.size(), "la production doit être en base");

        ProductionModel enregistree = enBase.get(0);
        assertEquals(100.0, enregistree.getQuantiteEntreeReelle());
        assertEquals(73.0, enregistree.getTotalSortiesReelles());
        assertEquals(LocalTime.of(8, 30), enregistree.getHeureProduction());
        assertEquals("operateur_test", enregistree.getSaisiPar(),
                "l'auteur doit être tracé jusqu'en base");
    }

    @Test
    @DisplayName("Un total de sorties supérieur à l'entrée est refusé et rien n'est enregistré")
    void totalDesSortiesImpossibleRefuse() throws Exception {
        Parent racine = ouvrirEcranAvecMatiereSelectionnee();

        // Chaque sortie passe le controle individuel, mais leur somme est impossible
        saisir(racine, "100", "60", "60");
        enregistrer(racine);

        assertTrue(productionsEnBase().isEmpty(), "rien ne doit être enregistré");
        assertTrue(messageDeResultat(racine).getText().contains("total"),
                "l'opérateur doit voir pourquoi : " + messageDeResultat(racine).getText());
    }

    @Test
    @DisplayName("Une saisie non numérique est refusée sans enregistrement")
    void saisieNonNumeriqueRefusee() throws Exception {
        Parent racine = ouvrirEcranAvecMatiereSelectionnee();

        saisir(racine, "beaucoup", "55", "18");
        enregistrer(racine);

        assertTrue(productionsEnBase().isEmpty());
        assertTrue(messageDeResultat(racine).getText().contains("nombre"),
                messageDeResultat(racine).getText());
    }

    @Test
    @DisplayName("La virgule décimale est acceptée jusqu'en base")
    void virguleDecimaleJusquEnBase() throws Exception {
        Parent racine = ouvrirEcranAvecMatiereSelectionnee();

        saisir(racine, "100,5", "60,25", "20");
        enregistrer(racine);

        List<ProductionModel> enBase = productionsEnBase();
        assertEquals(1, enBase.size(), "la virgule ne doit pas faire échouer la saisie");
        assertEquals(100.5, enBase.get(0).getQuantiteEntreeReelle());
        assertEquals(80.25, enBase.get(0).getTotalSortiesReelles());
    }

    @Test
    @DisplayName("Les productions déjà en base sont rechargées à la sélection de la matière")
    void productionsExistantesRechargees() throws Exception {
        Parent racine = ouvrirEcranAvecMatiereSelectionnee();
        saisir(racine, "100", "55", "18");
        enregistrer(racine);

        // Nouvel ecran : la production enregistree doit reapparaitre
        Parent nouvelEcran = ouvrirEcranAvecMatiereSelectionnee();

        List<TextField> entrees = champsDeSaisie(nouvelEcran, "Quantité entrée");
        assertEquals(2, entrees.size(),
                "la production existante plus la nouvelle ligne vide");
        assertTrue(entrees.stream().anyMatch(champ -> champ.getText().contains("100")),
                "la valeur enregistrée doit être réaffichée");
    }
}
