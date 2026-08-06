package interfaces;

import controller.TrackerController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifie que l'ecran principal s'adapte a la taille de la fenetre : contenu
 * etire sur les grands ecrans, panneaux empiles et rien qui deborde sur les petits.
 */
class MiseEnPageResponsiveTest extends BaseTestJavaFx {

    @BeforeAll
    static void demarrer() throws InterruptedException {
        demarrerJavaFx();
    }

    /** Charge l'ecran principal dans une fenetre de la taille demandee. */
    private Parent afficher(double largeur, double hauteur) throws Exception {
        return surFilJavaFx(() -> {
            FXMLLoader chargeur = new FXMLLoader(
                    getClass().getClassLoader().getResource("resources/views/tracker.fxml"));
            Parent racine = chargeur.load();

            Stage fenetre = new Stage();
            fenetre.setScene(new Scene(racine, largeur, hauteur));
            fenetre.show();
            racine.applyCss();
            racine.layout();
            return racine;
        });
    }

    private Region region(Parent racine, String identifiant) {
        return (Region) racine.lookup("#" + identifiant);
    }

    @Test
    @DisplayName("Sur grand ecran, les deux panneaux se cotoient")
    void deuxColonnesSurGrandEcran() throws Exception {
        Parent racine = afficher(1400, 900);

        Region matiere = region(racine, "panneauMatiere");
        Region statistiques = region(racine, "panneauStatistiques");

        assertEquals(matiere.getLayoutY(), statistiques.getLayoutY(), 1.0,
                "les deux panneaux doivent etre sur la meme ligne");
        assertTrue(statistiques.getLayoutX() > matiere.getLayoutX(),
                "le panneau statistiques doit etre a droite");
    }

    @Test
    @DisplayName("Sur petit ecran, les panneaux s'empilent")
    void empilementSurPetitEcran() throws Exception {
        Parent racine = afficher(700, 700);

        Region matiere = region(racine, "panneauMatiere");
        Region statistiques = region(racine, "panneauStatistiques");

        assertTrue(statistiques.getLayoutY() > matiere.getLayoutY(),
                "le panneau statistiques doit passer sous le panneau matiere");
    }

    @Test
    @DisplayName("Le contenu s'etire sur toute la largeur disponible")
    void contenuEtireSurLaLargeur() throws Exception {
        Parent etroit = afficher(800, 700);
        Parent large = afficher(1500, 700);

        double largeurContenuEtroit = region(etroit, "panneauxSuperieurs").getWidth();
        double largeurContenuLarge = region(large, "panneauxSuperieurs").getWidth();

        assertTrue(largeurContenuLarge > largeurContenuEtroit + 400,
                "sans fitToWidth le contenu garderait la meme largeur : "
                        + largeurContenuEtroit + " puis " + largeurContenuLarge);
    }

    @Test
    @DisplayName("Aucun contenu ne deborde horizontalement de la fenetre")
    void pasDeDebordementHorizontal() throws Exception {
        for (double largeur : new double[]{640, 800, 1024, 1400}) {
            Parent racine = afficher(largeur, 700);

            Region panneaux = region(racine, "panneauxSuperieurs");
            assertTrue(panneaux.getWidth() <= largeur + 1,
                    "a " + largeur + " px, les panneaux debordent (" + panneaux.getWidth() + " px)");

            Region zone = region(racine, "zoneProductions");
            assertTrue(zone.getWidth() <= largeur + 1,
                    "a " + largeur + " px, la zone des productions deborde");
        }
    }

    @Test
    @DisplayName("Les rangees de boutons passent a la ligne au lieu d'etre coupees")
    void boutonsQuiPassentALaLigne() throws Exception {
        Parent racine = afficher(640, 700);

        // Toutes les rangees d'actions sont des FlowPane : elles replient leur
        // contenu au lieu de le tronquer comme le faisait une HBox.
        int rangeesRepliables = compterComposants(racine, FlowPane.class);

        assertTrue(rangeesRepliables >= 5,
                "les rangees d'actions doivent pouvoir se replier, trouvees : " + rangeesRepliables);
    }

    @Test
    @DisplayName("La hauteur de la liste des productions suit celle de la fenetre")
    void hauteurDeLaListeSuitLaFenetre() throws Exception {
        double hauteurPetite = region(afficher(1000, 600), "zoneProductions").getPrefHeight();
        double hauteurGrande = region(afficher(1000, 1000), "zoneProductions").getPrefHeight();

        assertTrue(hauteurGrande > hauteurPetite,
                "la zone doit s'agrandir avec la fenetre : "
                        + hauteurPetite + " puis " + hauteurGrande);
    }

    @Test
    @DisplayName("Le partage de largeur bascule au seuil des deux colonnes")
    void seuilDeBasculement() throws Exception {
        // Au-dessus du seuil expose par le controleur, on attend deux colonnes egales
        Parent racine = afficher(TrackerController.LARGEUR_MINIMALE_DEUX_COLONNES + 300, 700);

        Region matiere = region(racine, "panneauMatiere");
        Region statistiques = region(racine, "panneauStatistiques");
        assertEquals(matiere.getWidth(), statistiques.getWidth(), 2.0,
                "les deux panneaux doivent se partager la largeur a parts egales");
    }
}
