package model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regle metier centrale : quelles productions comptent dans les totaux de
 * performance (a l'ecran comme dans les exports Excel).
 */
class ProductionModelTest {

    private ProductionModel production(Long id, String statut, double entree, double sortie) {
        ProductionModel p = new ProductionModel(1L, LocalDate.of(2026, 1, 5), LocalTime.of(8, 0), entree);
        p.setId(id);
        p.ajouterSortieReelle(1, sortie);
        p.setStatut(statut);
        return p;
    }

    @Test
    @DisplayName("Une production valide et complete est comptabilisee")
    void productionValideEstComptabilisee() {
        assertTrue(production(1L, "VALIDE", 100.0, 80.0).isComptabilisable());
    }

    @Test
    @DisplayName("Les kg comptent meme si le statut est reste INCOMPLETE")
    void statutIncompleteNEmpechePasLeComptage() {
        assertTrue(production(2L, "INCOMPLETE", 100.0, 80.0).isComptabilisable(),
                "des donnees completes doivent compter, le statut administratif ne change pas les kg produits");
    }

    @Test
    @DisplayName("Une production marquee ERREUR est exclue (mesure douteuse)")
    void statutErreurExclut() {
        assertFalse(production(3L, "ERREUR", 100.0, 80.0).isComptabilisable());
    }

    @Test
    @DisplayName("Une production pas encore sauvegardee (id null) compte quand meme")
    void absenceDIdNEmpechePasLeComptage() {
        assertTrue(production(null, "VALIDE", 100.0, 80.0).isComptabilisable(),
                "le fait d'etre enregistre en base ne change pas les kg sortis de la machine");
    }

    @Test
    @DisplayName("Sans aucune sortie saisie, la production n'est pas comptabilisee")
    void sansSortieNonComptabilisee() {
        ProductionModel p = new ProductionModel(1L, LocalDate.of(2026, 1, 5), LocalTime.of(8, 0), 100.0);
        p.setId(9L);
        p.setStatut("VALIDE");
        assertFalse(p.isComptabilisable());
    }

    @Test
    @DisplayName("Une quantite negative est refusee des la saisie")
    void quantiteNegativeRefusee() {
        ProductionModel p = new ProductionModel();
        assertThrows(IllegalArgumentException.class, () -> p.setQuantiteEntreeReelle(-1.0));
        assertThrows(IllegalArgumentException.class, () -> p.ajouterSortieReelle(1, -5.0));
    }

    @Test
    @DisplayName("Ressaisir la meme sortie met a jour au lieu de dupliquer")
    void sortieDupliqueeEstMiseAJour() {
        ProductionModel p = production(1L, "VALIDE", 100.0, 80.0);
        p.ajouterSortieReelle(1, 50.0);

        assertEquals(1, p.getNombreSortiesReelles());
        assertEquals(50.0, p.getTotalSortiesReelles());
    }
}
