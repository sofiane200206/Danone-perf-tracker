package service;

import dao.DatabaseManager;
import model.MatierePremiereModel;
import model.ProductionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Le reset efface l'historique de production : c'est l'operation la plus
 * destructrice de l'application, elle merite d'etre cernee precisement.
 */
class DatabaseResetServiceTest {

    private static final LocalDate LE_5_JANVIER = LocalDate.of(2026, 1, 5);

    private MatierePremiereModel matiere;
    private ProductionService productionService;
    private DatabaseResetService resetService;

    @BeforeEach
    void preparerDonnees() throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM sorties_reelles");
            stmt.executeUpdate("DELETE FROM productions");
            stmt.executeUpdate("DELETE FROM sorties_ideales");
            stmt.executeUpdate("DELETE FROM matieres_premieres");
        }

        matiere = new MatierePremiereService()
                .creerMatierePremiereSimple("Lait de test", 100.0, 60.0, 20.0);

        productionService = new ProductionService();
        productionService.setMatierePremiereModel(matiere);
        resetService = new DatabaseResetService(new ExcelExportService(productionService));
    }

    private void ajouterProduction(LocalDate date, double entree) throws ServiceException {
        ProductionModel production =
                new ProductionModel(matiere.getId(), date, LocalTime.of(8, 30), entree);
        production.ajouterSortieReelle(1, 55.0);
        production.ajouterSortieReelle(2, 18.0);
        productionService.ajouterProduction(production);
    }

    private int compter(String table) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    @Test
    @DisplayName("Le reset supprime les productions et leurs sorties")
    void resetSupprimeProductionsEtSorties() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0);
        ajouterProduction(LE_5_JANVIER.plusDays(1), 90.0);
        assertEquals(4, compter("sorties_reelles"));

        int supprimees = resetService.resetProductionsSansExport();

        assertEquals(2, supprimees);
        assertEquals(0, compter("productions"));
        assertEquals(0, compter("sorties_reelles"),
                "aucune sortie ne doit survivre a ses productions");
    }

    @Test
    @DisplayName("Le reset épargne les matières premières et leur paramétrage")
    void resetEpargneLesMatieres() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0);

        resetService.resetProductionsSansExport();

        assertEquals(1, compter("matieres_premieres"),
                "le paramétrage doit survivre : seules les mesures sont effacées");
        assertEquals(2, compter("sorties_ideales"));
        assertNotNull(new MatierePremiereService().trouverParId(matiere.getId()));
    }

    @Test
    @DisplayName("Le reset épargne les comptes utilisateurs")
    void resetEpargneLesComptes() throws Exception {
        new AuthentificationService()
                .creerCompte("operateur_reset", "MotDePasse1".toCharArray(), model.UserRole.USER);
        ajouterProduction(LE_5_JANVIER, 100.0);

        resetService.resetProductionsSansExport();

        assertNotNull(new AuthentificationService()
                        .authentifier("operateur_reset", "MotDePasse1".toCharArray()),
                "personne ne doit se retrouver enfermé dehors après un reset");
    }

    @Test
    @DisplayName("Un reset sur une base déjà vide ne fait rien et n'échoue pas")
    void resetSurBaseVide() throws Exception {
        assertEquals(0, resetService.resetProductionsSansExport());
        assertEquals(0, compter("productions"));
    }

    @Test
    @DisplayName("Le comptage reflète l'état réel avant et après reset")
    void comptageAvantEtApres() throws Exception {
        assertEquals(0, resetService.compterProductionsExistantes());
        assertFalse(resetService.baseDonneesContientProductions());

        ajouterProduction(LE_5_JANVIER, 100.0);

        assertEquals(1, resetService.compterProductionsExistantes());
        assertTrue(resetService.baseDonneesContientProductions());

        resetService.resetProductionsSansExport();

        assertEquals(0, resetService.compterProductionsExistantes());
        assertFalse(resetService.baseDonneesContientProductions());
    }

    @Test
    @DisplayName("Les productions signalées douteuses sont aussi effacées")
    void resetEffaceAussiLesProductionsSignalees() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0);
        ProductionModel douteuse = productionService.getProductions().get(0);
        productionService.marquerEnErreur(douteuse, "Mesure suspecte");

        resetService.resetProductionsSansExport();

        assertEquals(0, compter("productions"),
                "le reset efface tout l'historique, quel que soit le statut");
    }

    @Test
    @DisplayName("Après reset, une nouvelle saisie repart d'un identifiant propre")
    void identifiantsRepartentAZero() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0);
        resetService.resetProductionsSansExport();

        ajouterProduction(LE_5_JANVIER, 80.0);

        assertEquals(1, productionService.getProductions().get(0).getId(),
                "le compteur SQLite doit avoir été remis à zéro");
    }
}
