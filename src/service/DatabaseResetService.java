package service;

import dao.DatabaseManager;
import dao.ProductionDAO;
import model.ProductionModel;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DatabaseResetService {
    private static final Logger LOGGER = Logger.getLogger(DatabaseResetService.class.getName());

    private final ProductionDAO productionDAO;
    private final ExcelExportService excelExportService;
    private final DatabaseManager dbManager;

    public DatabaseResetService(ExcelExportService excelExportService) {
        this.productionDAO = new ProductionDAO();
        this.excelExportService = excelExportService;
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Reset complet des productions avec export automatique vers le dossier Documents
     * @return Chemin du fichier d'export créé
     */
    public String resetProductionsAvecExport() throws ServiceException {
        try {
            LOGGER.info("Début du reset des productions avec export préalable");

            // 1. Vérifier s'il y a des productions à exporter
            List<ProductionModel> toutesProductions = productionDAO.listerToutes();

            String cheminFichierExport = null;

            if (!toutesProductions.isEmpty()) {
                // 2. Créer le nom du fichier d'export avec timestamp
                String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                        "_" + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH-mm"));
                String nomFichier = String.format("BACKUP_Productions_Avant_Reset_%s.xlsx", dateStr);

                // 3. Utiliser le dossier Documents par défaut
                String userHome = System.getProperty("user.home");
                File documentsDir = new File(userHome, "Documents");

                // Créer le dossier Documents s'il n'existe pas
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs();
                }

                cheminFichierExport = documentsDir.getAbsolutePath() + File.separator + nomFichier;

                // 4. Faire l'export de toutes les productions existantes
                LOGGER.info(String.format("Export de %d productions vers: %s",
                        toutesProductions.size(), cheminFichierExport));

                // Déterminer la période complète des productions
                LocalDate dateMin = toutesProductions.stream()
                        .filter(p -> p.getDateProduction() != null)
                        .map(ProductionModel::getDateProduction)
                        .min(LocalDate::compareTo)
                        .orElse(LocalDate.now().minusYears(1));

                LocalDate dateMax = toutesProductions.stream()
                        .filter(p -> p.getDateProduction() != null)
                        .map(ProductionModel::getDateProduction)
                        .max(LocalDate::compareTo)
                        .orElse(LocalDate.now());

                // Utiliser la méthode d'export existante
                excelExportService.exporterToutesProductions(dateMin, dateMax, cheminFichierExport);

                LOGGER.info("Export terminé avec succès");
            } else {
                LOGGER.info("Aucune production à exporter");
            }

            // 5. Supprimer toutes les productions de la base de données
            int nbProductionsSupprimees = supprimerToutesProductions();

            LOGGER.info(String.format("Reset terminé: %d productions supprimées", nbProductionsSupprimees));

            return cheminFichierExport;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du reset avec export", e);
            throw new ServiceException("Erreur lors du reset des productions: " + e.getMessage());
        }
    }

    /**
     * Reset simple sans export (pour les cas où on ne veut pas d'export)
     */
    public int resetProductionsSansExport() throws ServiceException {
        try {
            LOGGER.info("Début du reset des productions sans export");
            int nbSupprimees = supprimerToutesProductions();
            LOGGER.info(String.format("Reset terminé: %d productions supprimées", nbSupprimees));
            return nbSupprimees;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du reset sans export", e);
            throw new ServiceException("Erreur lors du reset des productions: " + e.getMessage());
        }
    }

    /**
     * Méthode privée pour supprimer toutes les productions
     */
    private int supprimerToutesProductions() throws SQLException {
        String querySortiesReelles = "DELETE FROM sorties_reelles";
        String queryProductions = "DELETE FROM productions";

        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Supprimer d'abord toutes les sorties réelles (clés étrangères)
                int nbSortiesSupprimees = 0;
                try (PreparedStatement stmt = conn.prepareStatement(querySortiesReelles)) {
                    nbSortiesSupprimees = stmt.executeUpdate();
                    LOGGER.info(String.format("Sorties réelles supprimées: %d", nbSortiesSupprimees));
                }

                // 2. Supprimer toutes les productions
                int nbProductionsSupprimees = 0;
                try (PreparedStatement stmt = conn.prepareStatement(queryProductions)) {
                    nbProductionsSupprimees = stmt.executeUpdate();
                    LOGGER.info(String.format("Productions supprimées: %d", nbProductionsSupprimees));
                }

                // 3. Remettre les compteurs à zéro (SQLite)
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM sqlite_sequence WHERE name='productions'")) {
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM sqlite_sequence WHERE name='sorties_reelles'")) {
                    stmt.executeUpdate();
                }

                conn.commit();
                LOGGER.info("Reset des productions terminé avec succès");
                return nbProductionsSupprimees;

            } catch (Exception e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Erreur lors de la suppression, rollback effectué", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Vérifier le nombre de productions avant reset
     */
    public int compterProductionsExistantes() throws ServiceException {
        try {
            List<ProductionModel> productions = productionDAO.listerToutes();
            return productions.size();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors du comptage des productions", e);
            throw new ServiceException("Erreur lors du comptage des productions: " + e.getMessage());
        }
    }

    /**
     * Vérifier si la base contient des données
     */
    public boolean baseDonneesContientProductions() throws ServiceException {
        return compterProductionsExistantes() > 0;
    }
}