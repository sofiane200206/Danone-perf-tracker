package dao;

import model.MatierePremiereModel;
import model.ProductionModel;
import model.ProductionModel.SortieReelle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de persistance sur une base SQLite jetable (target/test-tracker.db,
 * definie par la propriete performancetracker.db.url dans le pom).
 */
class ProductionDAOTest {

    private static final LocalDate LE_5_JANVIER = LocalDate.of(2026, 1, 5);

    private ProductionDAO productionDAO;
    private Long matiereId;

    @BeforeEach
    void preparerBaseVierge() throws SQLException {
        viderTables();

        productionDAO = new ProductionDAO();

        MatierePremiereModel matiere = new MatierePremiereModel("Lait de test", 100.0, 2);
        matiere.ajouterSortieIdeale(1, 60.0, "Yaourt");
        matiere.ajouterSortieIdeale(2, 20.0, "Creme");
        matiereId = new MatierePremiereDAO().creer(matiere);
    }

    private void viderTables() throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM sorties_reelles");
            stmt.executeUpdate("DELETE FROM productions");
            stmt.executeUpdate("DELETE FROM sorties_ideales");
            stmt.executeUpdate("DELETE FROM matieres_premieres");
        }
    }

    private int compterSortiesReellesDe(Long productionId) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM sorties_reelles WHERE production_id = " + productionId)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private ProductionModel nouvelleProduction(LocalDate date, double entree, double sortie1, double sortie2) {
        ProductionModel p = new ProductionModel(matiereId, date, LocalTime.of(8, 30), entree);
        p.ajouterSortieReelle(1, sortie1);
        p.ajouterSortieReelle(2, sortie2);
        p.setStatut("VALIDE");
        return p;
    }

    @Test
    @DisplayName("Une production creee est relue a l'identique, sorties comprises")
    void creerPuisRelire() throws SQLException {
        Long id = productionDAO.creer(nouvelleProduction(LE_5_JANVIER, 100.0, 55.0, 18.0));
        assertNotNull(id);

        ProductionModel relue = productionDAO.trouverParId(id);

        assertNotNull(relue);
        assertEquals(LE_5_JANVIER, relue.getDateProduction());
        assertEquals(LocalTime.of(8, 30), relue.getHeureProduction());
        assertEquals(100.0, relue.getQuantiteEntreeReelle());
        assertEquals("VALIDE", relue.getStatut());
        assertEquals(2, relue.getNombreSortiesReelles());
        assertEquals(73.0, relue.getTotalSortiesReelles());
    }

    @Test
    @DisplayName("Les productions sont retrouvees par matiere premiere")
    void listerParMatiere() throws SQLException {
        productionDAO.creer(nouvelleProduction(LE_5_JANVIER, 100.0, 55.0, 18.0));
        productionDAO.creer(nouvelleProduction(LE_5_JANVIER.plusDays(1), 90.0, 50.0, 15.0));

        List<ProductionModel> productions = productionDAO.listerParMatiere(matiereId);

        assertEquals(2, productions.size());
        assertTrue(productions.stream().allMatch(p -> p.getNombreSortiesReelles() == 2),
                "les sorties doivent etre chargees avec chaque production");
    }

    @Test
    @DisplayName("Le filtrage par periode exclut les dates hors bornes")
    void listerParPeriode() throws SQLException {
        productionDAO.creer(nouvelleProduction(LE_5_JANVIER, 100.0, 55.0, 18.0));
        productionDAO.creer(nouvelleProduction(LE_5_JANVIER.plusMonths(2), 100.0, 55.0, 18.0));

        List<ProductionModel> dansLaPeriode = productionDAO.listerParMatierePeriode(
                matiereId, LE_5_JANVIER.minusDays(1), LE_5_JANVIER.plusDays(1));

        assertEquals(1, dansLaPeriode.size());
        assertEquals(LE_5_JANVIER, dansLaPeriode.get(0).getDateProduction());
    }

    @Test
    @DisplayName("La mise a jour remplace les sorties sans les accumuler")
    void mettreAJourRemplaceLesSorties() throws SQLException {
        Long id = productionDAO.creer(nouvelleProduction(LE_5_JANVIER, 100.0, 55.0, 18.0));

        ProductionModel modifiee = productionDAO.trouverParId(id);
        modifiee.setQuantiteEntreeReelle(120.0);
        modifiee.setSortiesReelles(List.of(new SortieReelle(1, 70.0), new SortieReelle(2, 25.0)));
        productionDAO.mettreAJour(modifiee);

        ProductionModel relue = productionDAO.trouverParId(id);
        assertEquals(120.0, relue.getQuantiteEntreeReelle());
        assertEquals(2, relue.getNombreSortiesReelles(), "pas de doublon apres mise a jour");
        assertEquals(95.0, relue.getTotalSortiesReelles());
        assertEquals(2, compterSortiesReellesDe(id));
    }

    @Test
    @DisplayName("Supprimer une production ne laisse aucune sortie orpheline")
    void supprimerNeLaissePasDOrphelines() throws SQLException {
        Long id = productionDAO.creer(nouvelleProduction(LE_5_JANVIER, 100.0, 55.0, 18.0));
        assertEquals(2, compterSortiesReellesDe(id));

        productionDAO.supprimer(id);

        assertNull(productionDAO.trouverParId(id));
        assertEquals(0, compterSortiesReellesDe(id),
                "les sorties doivent disparaitre avec la production (SQLite n'applique pas les FK)");
    }

    @Test
    @DisplayName("Le comptage par matiere reflete les creations et suppressions")
    void compterParMatiere() throws SQLException {
        assertEquals(0, productionDAO.compterParMatiereId(matiereId));

        Long id = productionDAO.creer(nouvelleProduction(LE_5_JANVIER, 100.0, 55.0, 18.0));
        productionDAO.creer(nouvelleProduction(LE_5_JANVIER.plusDays(1), 90.0, 50.0, 15.0));
        assertEquals(2, productionDAO.compterParMatiereId(matiereId));

        productionDAO.supprimer(id);
        assertEquals(1, productionDAO.compterParMatiereId(matiereId));
    }

    @Test
    @DisplayName("Chercher un identifiant inexistant renvoie null")
    void trouverParIdInexistant() throws SQLException {
        assertNull(productionDAO.trouverParId(999_999L));
    }
}
