package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Calcul de performance d'une journee : la production reelle est comparee au
 * modele ideal, mis a l'echelle de la quantite reellement engagee.
 */
class JourneeProductionTest {

    private static final LocalDate LE_5_JANVIER = LocalDate.of(2026, 1, 5);

    private MatierePremiereModel matiere;

    @BeforeEach
    void preparerMatiere() {
        // Modele ideal : 100 kg en entree doivent produire 80 kg en sortie
        matiere = new MatierePremiereModel("Lait", 100.0, 1);
        matiere.ajouterSortieIdeale(1, 80.0, "Yaourt");
    }

    private ProductionModel production(long id, String statut, double entree, double sortie) {
        ProductionModel p = new ProductionModel(1L, LE_5_JANVIER, LocalTime.of(8, 0), entree);
        p.setId(id);
        p.ajouterSortieReelle(1, sortie);
        p.setStatut(statut);
        return p;
    }

    @Test
    @DisplayName("Une production conforme au modele donne 100%")
    void productionConformeDonneCentPourCent() {
        JourneeProduction journee = new JourneeProduction(LE_5_JANVIER);
        journee.ajouterProduction(production(1L, "VALIDE", 100.0, 80.0));
        journee.calculerPerformance(matiere);

        assertEquals(100.0, journee.getPerformanceJour());
    }

    @Test
    @DisplayName("Un rendement de moitie donne 50%")
    void rendementDeMoitieDonneCinquantePourCent() {
        JourneeProduction journee = new JourneeProduction(LE_5_JANVIER);
        journee.ajouterProduction(production(1L, "VALIDE", 100.0, 40.0));
        journee.calculerPerformance(matiere);

        assertEquals(50.0, journee.getPerformanceJour());
    }

    @Test
    @DisplayName("L'ideal est mis a l'echelle de l'entree reelle")
    void idealMisALEchelleDeLEntree() {
        // 50 kg engages au lieu de 100 : on attend 40 kg de sortie, pas 80
        JourneeProduction journee = new JourneeProduction(LE_5_JANVIER);
        journee.ajouterProduction(production(1L, "VALIDE", 50.0, 40.0));
        journee.calculerPerformance(matiere);

        assertEquals(100.0, journee.getPerformanceJour(),
                "produire 40 kg avec 50 kg engages respecte exactement le modele");
    }

    @Test
    @DisplayName("Les productions en ERREUR sont exclues des totaux")
    void productionsEnErreurExclues() {
        JourneeProduction journee = new JourneeProduction(LE_5_JANVIER);
        journee.ajouterProduction(production(1L, "VALIDE", 100.0, 80.0));
        journee.ajouterProduction(production(2L, "INCOMPLETE", 100.0, 80.0));
        journee.ajouterProduction(production(3L, "ERREUR", 100.0, 80.0));
        journee.calculerPerformance(matiere);

        assertEquals(200.0, journee.getTotalEntreeJour(), "seules les deux premieres comptent");
        assertEquals(160.0, journee.getTotalSortiesJour());
        assertEquals(100.0, journee.getPerformanceJour());
    }

    @Test
    @DisplayName("Une journee sans entree affiche 0% au lieu de diviser par zero")
    void journeeSansEntreeNeDivisePasParZero() {
        JourneeProduction journee = new JourneeProduction(LE_5_JANVIER);
        journee.calculerPerformance(matiere);

        assertEquals(0.0, journee.getPerformanceJour());
    }

    @Test
    @DisplayName("Le total d'une sortie donnee ignore les productions en erreur")
    void totalParNumeroDeSortie() {
        JourneeProduction journee = new JourneeProduction(LE_5_JANVIER);
        journee.ajouterProduction(production(1L, "VALIDE", 100.0, 80.0));
        journee.ajouterProduction(production(2L, "ERREUR", 100.0, 25.0));

        assertEquals(80.0, journee.getTotalSortieParNumero(1));
        assertEquals(0.0, journee.getTotalSortieParNumero(2), "cette sortie n'existe pas");
    }

    @Test
    @DisplayName("Sans matiere de reference, la performance reste a 0")
    void sansMatiereReferencePerformanceNulle() {
        JourneeProduction journee = new JourneeProduction(LE_5_JANVIER);
        journee.ajouterProduction(production(1L, "VALIDE", 100.0, 80.0));
        journee.calculerPerformance(null);

        assertEquals(0.0, journee.getPerformanceJour());
    }
}
