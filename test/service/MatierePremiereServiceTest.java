package service;

import dao.DatabaseManager;
import model.MatierePremiereModel;
import model.MatierePremiereModel.SortieIdeale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatierePremiereServiceTest {

    private MatierePremiereService service;

    @BeforeEach
    void preparerBaseVierge() throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM sorties_reelles");
            stmt.executeUpdate("DELETE FROM productions");
            stmt.executeUpdate("DELETE FROM sorties_ideales");
            stmt.executeUpdate("DELETE FROM matieres_premieres");
        }
        service = new MatierePremiereService();
    }

    @Test
    @DisplayName("Une matiere creee est relue avec ses sorties ideales")
    void creationPuisRelecture() throws ServiceException {
        MatierePremiereModel creee =
                service.creerMatierePremiereSimple("Lait", 100.0, 60.0, 20.0);

        assertNotNull(creee.getId());

        MatierePremiereModel relue = service.trouverParId(creee.getId());
        assertEquals("Lait", relue.getNom());
        assertEquals(100.0, relue.getQuantiteEntreeIdeale());
        assertEquals(2, relue.getNombreSorties());
        assertEquals(80.0, relue.getTotalSortiesIdeales());
    }

    @Test
    @DisplayName("Un nombre variable de sorties est pris en charge")
    void creationAvecTroisSorties() throws ServiceException {
        List<SortieIdeale> sorties = List.of(
                new SortieIdeale(1, 50.0, "Yaourt"),
                new SortieIdeale(2, 20.0, "Creme"),
                new SortieIdeale(3, 10.0, "Beurre"));

        MatierePremiereModel creee =
                service.creerMatierePremiereComplete("Lait entier", 100.0, 3, sorties);

        assertEquals(3, service.trouverParId(creee.getId()).getNombreSorties());
        assertEquals(80.0, service.trouverParId(creee.getId()).getTotalSortiesIdeales());
    }

    @Test
    @DisplayName("Un nom deja utilise est refuse")
    void nomDejaUtiliseRefuse() throws ServiceException {
        service.creerMatierePremiereSimple("Lait", 100.0, 60.0, 20.0);

        assertThrows(ServiceException.class,
                () -> service.creerMatierePremiereSimple("Lait", 200.0, 100.0, 50.0));
    }

    @Test
    @DisplayName("Une quantite d'entree nulle ou negative est refusee")
    void quantiteEntreeInvalideRefusee() {
        assertThrows(ServiceException.class,
                () -> service.creerMatierePremiereSimple("Lait", 0.0, 60.0, 20.0));
        assertThrows(ServiceException.class,
                () -> service.creerMatierePremiereSimple("Lait", -10.0, 60.0, 20.0));
    }

    @Test
    @DisplayName("Un nom vide est refuse")
    void nomVideRefuse() {
        assertThrows(ServiceException.class,
                () -> service.creerMatierePremiereSimple("   ", 100.0, 60.0, 20.0));
    }

    @Test
    @DisplayName("Seules les matieres actives sont listees")
    void listageDesMatieresActives() throws ServiceException {
        MatierePremiereModel gardee = service.creerMatierePremiereSimple("Lait", 100.0, 60.0, 20.0);
        MatierePremiereModel retiree = service.creerMatierePremiereSimple("Creme", 100.0, 60.0, 20.0);

        service.desactiver(retiree.getId());

        List<MatierePremiereModel> actives = service.listerMatieresActives();
        assertEquals(1, actives.size());
        assertEquals(gardee.getId(), actives.get(0).getId());
        assertEquals(1, service.compterMatieresActives());
    }

    @Test
    @DisplayName("Une matiere reactivee reapparait dans la liste")
    void reactivation() throws ServiceException {
        MatierePremiereModel matiere = service.creerMatierePremiereSimple("Lait", 100.0, 60.0, 20.0);

        service.desactiver(matiere.getId());
        assertEquals(0, service.compterMatieresActives());

        service.reactiver(matiere.getId());
        assertEquals(1, service.compterMatieresActives());
    }

    @Test
    @DisplayName("La recherche par nom ignore la casse")
    void rechercheParNomIgnoreLaCasse() throws ServiceException {
        service.creerMatierePremiereSimple("Lait", 100.0, 60.0, 20.0);

        assertNotNull(service.trouverParNom("lait"));
        assertTrue(service.nomExiste("LAIT"));
        assertFalse(service.nomExiste("Beurre"));
    }

    @Test
    @DisplayName("Chercher une matiere inexistante : exception par identifiant, null par nom")
    void rechercheInexistante() throws ServiceException {
        // Les deux methodes se comportent differemment : a uniformiser un jour,
        // le test fige le comportement actuel pour que tout changement soit visible.
        assertThrows(ServiceException.class, () -> service.trouverParId(999_999L));
        assertNull(service.trouverParNom("inexistante"));
    }
}
