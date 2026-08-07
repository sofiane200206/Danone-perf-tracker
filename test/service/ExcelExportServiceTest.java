package service;

import dao.DatabaseManager;
import model.MatierePremiereModel;
import model.ProductionModel;
import model.UserRole;
import model.Utilisateur;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L'export est le document que liront les collegues : on genere un vrai fichier
 * et on le relit, plutot que de se fier au fait que le code compile.
 */
class ExcelExportServiceTest {

    private static final LocalDate LE_5_JANVIER = LocalDate.of(2026, 1, 5);

    @TempDir
    Path dossier;

    private MatierePremiereModel matiere;
    private ProductionService productionService;
    private ExcelExportService exportService;

    @BeforeEach
    void preparerDonnees() throws Exception {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM sorties_reelles");
            stmt.executeUpdate("DELETE FROM productions");
            stmt.executeUpdate("DELETE FROM sorties_ideales");
            stmt.executeUpdate("DELETE FROM matieres_premieres");
        }

        Utilisateur operateur = new Utilisateur();
        operateur.setIdentifiant("operateur_test");
        operateur.setRole(UserRole.USER);
        SessionManager.getInstance().login(operateur);

        matiere = new MatierePremiereService()
                .creerMatierePremiereSimple("Lait de test", 100.0, 60.0, 20.0);

        productionService = new ProductionService();
        productionService.setMatierePremiereModel(matiere);
        exportService = new ExcelExportService(productionService);
    }

    private ProductionModel ajouterProduction(LocalDate date, double entree,
                                              double sortie1, double sortie2) throws ServiceException {
        ProductionModel production =
                new ProductionModel(matiere.getId(), date, LocalTime.of(8, 30), entree);
        production.ajouterSortieReelle(1, sortie1);
        production.ajouterSortieReelle(2, sortie2);
        productionService.ajouterProduction(production);
        return production;
    }

    private String cheminFichier(String nom) {
        return dossier.resolve(nom).toString();
    }

    // ------------------------------------------------------------- outillage

    private Row ligneDEntete(Sheet feuille, String premiereColonne) {
        for (Row ligne : feuille) {
            Cell premiere = ligne.getCell(0);
            if (premiere != null && premiere.getCellType() == CellType.STRING
                    && premiereColonne.equals(premiere.getStringCellValue())) {
                return ligne;
            }
        }
        throw new IllegalStateException("En-tête introuvable : " + premiereColonne);
    }

    private int colonne(Row entete, String intitule) {
        for (Cell cellule : entete) {
            if (cellule.getCellType() == CellType.STRING
                    && intitule.equals(cellule.getStringCellValue())) {
                return cellule.getColumnIndex();
            }
        }
        throw new IllegalStateException("Colonne introuvable : " + intitule);
    }

    private List<Row> lignesDeDonnees(Sheet feuille, Row entete) {
        List<Row> lignes = new ArrayList<>();
        for (int i = entete.getRowNum() + 1; i <= feuille.getLastRowNum(); i++) {
            Row ligne = feuille.getRow(i);
            if (ligne != null && ligne.getCell(0) != null) {
                lignes.add(ligne);
            }
        }
        return lignes;
    }

    private String texte(Row ligne, int colonne) {
        Cell cellule = ligne.getCell(colonne);
        return cellule == null ? "" : cellule.getStringCellValue();
    }

    // ---------------------------------------------------------------- export

    @Test
    @DisplayName("L'export produit un fichier lisible avec ses trois feuilles")
    void fichierProduitAvecSesFeuilles() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0, 55.0, 18.0);
        String chemin = cheminFichier("export.xlsx");

        exportService.exporterProductions(matiere, chemin);

        assertTrue(Files.exists(Path.of(chemin)));
        assertTrue(Files.size(Path.of(chemin)) > 0);

        try (Workbook classeur = WorkbookFactory.create(new File(chemin))) {
            assertNotNull(classeur.getSheet("Résumé Matière"));
            assertNotNull(classeur.getSheet("Productions Détaillées"));
            assertNotNull(classeur.getSheet("Statistiques"));
        }
    }

    @Test
    @DisplayName("Chaque production exportée porte ses valeurs sur la bonne colonne")
    void valeursAlignéesSurLesColonnes() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0, 55.0, 18.0);
        String chemin = cheminFichier("export.xlsx");

        exportService.exporterProductions(matiere, chemin);

        try (Workbook classeur = WorkbookFactory.create(new File(chemin))) {
            Sheet feuille = classeur.getSheet("Productions Détaillées");
            Row entete = ligneDEntete(feuille, "ID Production");
            List<Row> donnees = lignesDeDonnees(feuille, entete);

            assertEquals(1, donnees.size(), "une ligne par production");
            Row ligne = donnees.get(0);

            assertEquals(100.0, ligne.getCell(colonne(entete, "Entrée Réelle (kg)")).getNumericCellValue());
            assertEquals(55.0, ligne.getCell(colonne(entete, "Sortie 1 Réelle (kg)")).getNumericCellValue());
            assertEquals(60.0, ligne.getCell(colonne(entete, "Sortie 1 Idéale (kg)")).getNumericCellValue());
            assertEquals(18.0, ligne.getCell(colonne(entete, "Sortie 2 Réelle (kg)")).getNumericCellValue());
            assertEquals("VALIDE", texte(ligne, colonne(entete, "Statut")));
        }
    }

    @Test
    @DisplayName("La colonne Saisi par contient bien l'auteur de la saisie")
    void colonneSaisiParRenseignee() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0, 55.0, 18.0);
        String chemin = cheminFichier("export.xlsx");

        exportService.exporterProductions(matiere, chemin);

        try (Workbook classeur = WorkbookFactory.create(new File(chemin))) {
            Sheet feuille = classeur.getSheet("Productions Détaillées");
            Row entete = ligneDEntete(feuille, "ID Production");
            Row ligne = lignesDeDonnees(feuille, entete).get(0);

            assertEquals("operateur_test", texte(ligne, colonne(entete, "Saisi par")),
                    "la colonne doit être alignée avec son en-tête");
        }
    }

    @Test
    @DisplayName("Une production en erreur n'apparaît pas dans l'export")
    void productionEnErreurExclue() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0, 55.0, 18.0);

        ProductionModel douteuse = ajouterProduction(LE_5_JANVIER, 90.0, 50.0, 15.0);
        douteuse.marquerErreur("Balance déréglée");
        // Passage par le DAO : ProductionService.mettreAJourProduction appelle
        // validerProduction(), qui remet le statut a VALIDE et perdrait l'erreur.
        new dao.ProductionDAO().mettreAJour(douteuse);

        String chemin = cheminFichier("export.xlsx");
        exportService.exporterProductions(matiere, chemin);

        try (Workbook classeur = WorkbookFactory.create(new File(chemin))) {
            Sheet feuille = classeur.getSheet("Productions Détaillées");
            Row entete = ligneDEntete(feuille, "ID Production");

            assertEquals(1, lignesDeDonnees(feuille, entete).size(),
                    "une mesure jugée douteuse ne doit pas alimenter le rapport");
        }
    }

    @Test
    @DisplayName("Exporter sans matière sélectionnée est refusé")
    void exportSansMatiereRefuse() {
        assertThrows(ServiceException.class,
                () -> exportService.exporterProductions(null, cheminFichier("export.xlsx")));
    }

    // --------------------------------------------------- export toutes matieres

    @Test
    @DisplayName("L'export global ne retient que la période demandée")
    void exportGlobalFiltreLaPeriode() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0, 55.0, 18.0);
        ajouterProduction(LE_5_JANVIER.plusMonths(3), 100.0, 50.0, 20.0);

        String chemin = cheminFichier("global.xlsx");
        exportService.exporterToutesProductions(
                LE_5_JANVIER.minusDays(1), LE_5_JANVIER.plusDays(1), chemin);

        try (Workbook classeur = WorkbookFactory.create(new File(chemin))) {
            Sheet feuille = classeur.getSheet("Toutes Productions");
            assertNotNull(feuille);

            Row entete = ligneDEntete(feuille, "ID Production");
            assertEquals(1, lignesDeDonnees(feuille, entete).size(),
                    "la production hors période doit être écartée");
            assertEquals("operateur_test",
                    texte(lignesDeDonnees(feuille, entete).get(0), colonne(entete, "Saisi par")));
        }
    }

    @Test
    @DisplayName("Un export global sans production dans la période est refusé")
    void exportGlobalSansProductionRefuse() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0, 55.0, 18.0);

        assertThrows(ServiceException.class, () -> exportService.exporterToutesProductions(
                LE_5_JANVIER.plusYears(1), LE_5_JANVIER.plusYears(1).plusDays(5),
                cheminFichier("vide.xlsx")));
    }

    @Test
    @DisplayName("Des dates incohérentes sont refusées")
    void datesIncoherentesRefusees() {
        assertThrows(ServiceException.class, () -> exportService.exporterToutesProductions(
                LE_5_JANVIER.plusDays(10), LE_5_JANVIER, cheminFichier("export.xlsx")));
        assertThrows(ServiceException.class, () -> exportService.exporterToutesProductions(
                null, LE_5_JANVIER, cheminFichier("export.xlsx")));
    }

    @Test
    @DisplayName("Le fichier produit s'ouvre sans avertissement de corruption")
    void fichierNonCorrompu() throws Exception {
        ajouterProduction(LE_5_JANVIER, 100.0, 55.0, 18.0);
        String chemin = cheminFichier("export.xlsx");

        exportService.exporterProductions(matiere, chemin);

        // WorkbookFactory echoue si l'archive xlsx est incomplete ou mal fermee
        assertDoesNotThrow(() -> {
            try (Workbook classeur = WorkbookFactory.create(new File(chemin))) {
                assertTrue(classeur.getNumberOfSheets() >= 3);
            }
        });
    }
}
