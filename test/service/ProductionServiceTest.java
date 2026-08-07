package service;

import dao.DatabaseManager;
import dao.ProductionDAO;
import model.JourneeProduction;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductionServiceTest {

    private static final LocalDate LE_5_JANVIER = LocalDate.of(2026, 1, 5);

    private ProductionService service;
    private MatierePremiereModel matiere;

    @BeforeEach
    void preparerBaseVierge() throws SQLException, ServiceException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM sorties_reelles");
            stmt.executeUpdate("DELETE FROM productions");
            stmt.executeUpdate("DELETE FROM sorties_ideales");
            stmt.executeUpdate("DELETE FROM matieres_premieres");
        }

        matiere = new MatierePremiereService()
                .creerMatierePremiereSimple("Lait de test", 100.0, 60.0, 20.0);

        service = new ProductionService();
        service.setMatierePremiereModel(matiere);
    }

    private ProductionModel production(LocalDate date, double entree, double sortie1, double sortie2) {
        ProductionModel p = new ProductionModel(matiere.getId(), date, LocalTime.of(8, 30), entree);
        p.ajouterSortieReelle(1, sortie1);
        p.ajouterSortieReelle(2, sortie2);
        return p;
    }

    @Test
    @DisplayName("Ajouter une production la sauvegarde et lui attribue un identifiant")
    void ajoutSauvegardeEtAttribueUnId() throws ServiceException {
        ProductionModel p = production(LE_5_JANVIER, 100.0, 55.0, 18.0);

        service.ajouterProduction(p);

        assertNotNull(p.getId());
        assertEquals("VALIDE", p.getStatut(), "l'ajout valide la production");
        assertEquals(1, service.getProductions().size());
    }

    @Test
    @DisplayName("Une production sans sortie est refusee")
    void productionIncompleteRefusee() {
        ProductionModel sansSortie =
                new ProductionModel(matiere.getId(), LE_5_JANVIER, LocalTime.of(8, 30), 100.0);

        assertThrows(ServiceException.class, () -> service.ajouterProduction(sansSortie));
    }

    @Test
    @DisplayName("Ajouter une production nulle est refuse")
    void productionNulleRefusee() {
        assertThrows(ServiceException.class, () -> service.ajouterProduction(null));
    }

    @Test
    @DisplayName("Le filtrage par periode ne retient que les dates demandees")
    void filtrageParPeriode() throws ServiceException {
        service.ajouterProduction(production(LE_5_JANVIER, 100.0, 55.0, 18.0));
        service.ajouterProduction(production(LE_5_JANVIER.plusMonths(3), 100.0, 55.0, 18.0));

        List<ProductionModel> filtrees = service.getProductionsFiltrees(
                LE_5_JANVIER.minusDays(2), LE_5_JANVIER.plusDays(2));

        assertEquals(1, filtrees.size());
        assertEquals(LE_5_JANVIER, filtrees.get(0).getDateProduction());
    }

    @Test
    @DisplayName("Le regroupement par jour rassemble les productions et calcule la performance")
    void regroupementParJour() throws ServiceException {
        // Deux productions le meme jour : 200 kg engages, 160 kg produits => 100 %
        service.ajouterProduction(production(LE_5_JANVIER, 100.0, 60.0, 20.0));
        service.ajouterProduction(production(LE_5_JANVIER, 100.0, 60.0, 20.0));
        service.ajouterProduction(production(LE_5_JANVIER.plusDays(1), 100.0, 30.0, 10.0));

        Map<LocalDate, JourneeProduction> journees = service.grouperParJour(service.getProductions());

        assertEquals(2, journees.size());
        assertEquals(200.0, journees.get(LE_5_JANVIER).getTotalEntreeJour());
        assertEquals(100.0, journees.get(LE_5_JANVIER).getPerformanceJour());
        assertEquals(50.0, journees.get(LE_5_JANVIER.plusDays(1)).getPerformanceJour());
    }

    @Test
    @DisplayName("Sans matiere selectionnee, aucune production n'est renvoyee")
    void sansMatiereSelectionneeAucuneProduction() throws ServiceException {
        service.ajouterProduction(production(LE_5_JANVIER, 100.0, 55.0, 18.0));

        ProductionService sansMatiere = new ProductionService();

        assertTrue(sansMatiere.getProductions().isEmpty());
        assertTrue(sansMatiere.getProductionsFiltrees(LE_5_JANVIER, LE_5_JANVIER).isEmpty());
    }

    @Test
    @DisplayName("Supprimer une production la retire de la base")
    void suppressionRetireDeLaBase() throws ServiceException {
        ProductionModel p = production(LE_5_JANVIER, 100.0, 55.0, 18.0);
        service.ajouterProduction(p);

        service.supprimerProduction(p);

        assertTrue(service.getProductions().isEmpty());
    }

    @Test
    @DisplayName("Supprimer une production sans identifiant est refuse")
    void suppressionSansIdRefusee() {
        assertThrows(ServiceException.class,
                () -> service.supprimerProduction(production(LE_5_JANVIER, 100.0, 55.0, 18.0)));
        assertThrows(ServiceException.class, () -> service.supprimerProduction(null));
    }

    @Test
    @DisplayName("La suppression en cascade vide les productions de la matiere sans laisser d'orphelines")
    void suppressionCascadeSansOrphelines() throws ServiceException, SQLException {
        service.ajouterProduction(production(LE_5_JANVIER, 100.0, 55.0, 18.0));
        service.ajouterProduction(production(LE_5_JANVIER.plusDays(1), 90.0, 50.0, 15.0));

        int supprimees = service.supprimerToutesProductionsMatiere(matiere.getId());

        assertEquals(2, supprimees);
        assertEquals(0, service.compterProductionsParMatiere(matiere.getId()));
        assertEquals(0, compterToutesLesSortiesReelles(),
                "aucune sortie ne doit survivre a la suppression de sa production");
    }

    @Test
    @DisplayName("La mise a jour remplace les valeurs sans dupliquer les sorties")
    void miseAJourRemplaceLesValeurs() throws ServiceException, SQLException {
        ProductionModel p = production(LE_5_JANVIER, 100.0, 55.0, 18.0);
        service.ajouterProduction(p);

        p.setQuantiteEntreeReelle(150.0);
        p.ajouterSortieReelle(1, 90.0);
        service.mettreAJourProduction(p);

        ProductionModel relue = new ProductionDAO().trouverParId(p.getId());
        assertEquals(150.0, relue.getQuantiteEntreeReelle());
        assertEquals(2, relue.getNombreSortiesReelles());
        assertEquals(108.0, relue.getTotalSortiesReelles());
    }

    @Test
    @DisplayName("La production retient le compte connecte qui l'a saisie")
    void auteurRenseigneDepuisLaSession() throws ServiceException {
        SessionManager.getInstance().logout();
        model.Utilisateur operateur = new model.Utilisateur();
        operateur.setIdentifiant("operateur_jour");
        operateur.setRole(model.UserRole.USER);
        SessionManager.getInstance().login(operateur);

        try {
            ProductionModel p = production(LE_5_JANVIER, 100.0, 55.0, 18.0);
            service.ajouterProduction(p);

            assertEquals("operateur_jour", p.getSaisiPar());
            assertEquals("operateur_jour", service.getProductions().get(0).getSaisiPar());
        } finally {
            SessionManager.getInstance().logout();
        }
    }

    @Test
    @DisplayName("Un auteur deja renseigne n'est pas ecrase par la session")
    void auteurExistantConserve() throws ServiceException {
        model.Utilisateur autre = new model.Utilisateur();
        autre.setIdentifiant("quelquun_dautre");
        autre.setRole(model.UserRole.USER);
        SessionManager.getInstance().login(autre);

        try {
            ProductionModel p = production(LE_5_JANVIER, 100.0, 55.0, 18.0);
            p.setSaisiPar("auteur_dorigine");
            service.ajouterProduction(p);

            assertEquals("auteur_dorigine", p.getSaisiPar());
        } finally {
            SessionManager.getInstance().logout();
        }
    }

    @Test
    @DisplayName("Sans session ouverte, la saisie reste possible sans auteur")
    void saisieSansSessionRestePossible() throws ServiceException {
        SessionManager.getInstance().logout();

        ProductionModel p = production(LE_5_JANVIER, 100.0, 55.0, 18.0);
        service.ajouterProduction(p);

        assertNotNull(p.getId(), "l'absence d'auteur ne doit pas bloquer la saisie");
        assertNull(p.getSaisiPar());
    }

    @Test
    @DisplayName("Le comptage par matiere suit les ajouts")
    void comptageParMatiere() throws ServiceException {
        assertEquals(0, service.compterProductionsParMatiere(matiere.getId()));

        service.ajouterProduction(production(LE_5_JANVIER, 100.0, 55.0, 18.0));

        assertEquals(1, service.compterProductionsParMatiere(matiere.getId()));
        assertEquals(0, service.compterProductionsParMatiere(null));
    }

    private int compterToutesLesSortiesReelles() throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sorties_reelles")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}
