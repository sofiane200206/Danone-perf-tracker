package service;

import model.JourneeProduction;
import model.MatierePremiereModel;
import model.ProductionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agregation des performances sur une periode : moyenne, meilleur et pire jour,
 * performance globale.
 */
class StatistiquesServiceTest {

    private static final LocalDate JOUR_1 = LocalDate.of(2026, 1, 5);
    private static final LocalDate JOUR_2 = LocalDate.of(2026, 1, 6);

    private MatierePremiereModel matiere;

    @BeforeEach
    void preparerMatiere() {
        matiere = new MatierePremiereModel("Lait", 100.0, 1);
        matiere.ajouterSortieIdeale(1, 80.0, "Yaourt");
    }

    private JourneeProduction journee(LocalDate date, double entree, double sortie) {
        ProductionModel p = new ProductionModel(1L, date, LocalTime.of(8, 0), entree);
        p.setId(1L);
        p.ajouterSortieReelle(1, sortie);
        p.setStatut("VALIDE");

        JourneeProduction journee = new JourneeProduction(date);
        journee.ajouterProduction(p);
        return journee;
    }

    private Map<LocalDate, JourneeProduction> periode(JourneeProduction... journees) {
        Map<LocalDate, JourneeProduction> map = new HashMap<>();
        for (JourneeProduction j : journees) {
            map.put(j.getDate(), j);
        }
        return map;
    }

    @Test
    @DisplayName("Moyenne, meilleur et pire jour sur deux journees")
    void moyenneEtExtremes() {
        StatistiquesService.StatistiquesResume stats = StatistiquesService.calculerStatistiques(
                periode(journee(JOUR_1, 100.0, 80.0),   // 100 %
                        journee(JOUR_2, 100.0, 40.0)),  //  50 %
                matiere);

        assertEquals(2, stats.getNombreJours());
        assertEquals(75.0, stats.getPerformanceMoyenne());
        assertEquals(100.0, stats.getPerformanceMax());
        assertEquals(50.0, stats.getPerformanceMin());
        assertEquals(JOUR_1, stats.getJourMeilleur());
        assertEquals(JOUR_2, stats.getJourPire());
    }

    @Test
    @DisplayName("La performance globale rapporte les totaux de la periode a l'ideal")
    void performanceGlobaleDeLaPeriode() {
        StatistiquesService.StatistiquesResume stats = StatistiquesService.calculerStatistiques(
                periode(journee(JOUR_1, 100.0, 80.0),
                        journee(JOUR_2, 100.0, 40.0)),
                matiere);

        // 200 kg engages => 160 kg attendus ; 120 kg produits => 75 %
        assertEquals(75.0, stats.getPerformanceGlobalePeriode());
    }

    @Test
    @DisplayName("Une periode sans donnees ne fait pas planter le calcul")
    void periodeVide() {
        StatistiquesService.StatistiquesResume stats =
                StatistiquesService.calculerStatistiques(new HashMap<>(), matiere);

        assertEquals(0, stats.getNombreJours());
        assertEquals(0.0, stats.getPerformanceMoyenne());
    }

    @Test
    @DisplayName("Sans matiere de reference, aucune statistique n'est calculee")
    void sansMatiereReference() {
        StatistiquesService.StatistiquesResume stats = StatistiquesService.calculerStatistiques(
                periode(journee(JOUR_1, 100.0, 80.0)), null);

        assertEquals(0, stats.getNombreJours());
    }

    @Test
    @DisplayName("Meme quand tous les jours sont a 0%, un meilleur jour est designe")
    void meilleurJourDesigneMemeAZeroPourCent() {
        StatistiquesService.StatistiquesResume stats = StatistiquesService.calculerStatistiques(
                periode(journee(JOUR_1, 100.0, 0.0),
                        journee(JOUR_2, 100.0, 0.0)),
                matiere);

        assertEquals(0.0, stats.getPerformanceMax());
        assertNotNull(stats.getJourMeilleur(),
                "sans cela l'interface affiche 'N/A' alors que des journees existent");
    }
}
