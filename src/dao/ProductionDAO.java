package dao;

import model.Production;
import model.ProductionModel;
import model.ProductionModel.SortieReelle;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ProductionDAO {
    private static final Logger LOGGER = Logger.getLogger(ProductionDAO.class.getName());
    private final DatabaseManager dbManager;

    public ProductionDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Crée une nouvelle production avec ses sorties
     */
    public Long creer(ProductionModel production) throws SQLException {
        String queryProduction = """
            INSERT INTO productions (matiere_premiere_id, date_production, heure_production, 
                                   quantite_entree_reelle, statut, message_erreur)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryProduction, Statement.RETURN_GENERATED_KEYS)) {

            conn.setAutoCommit(false);

            try {
                // Insérer la production
                stmt.setLong(1, production.getMatierePremiereId());
                stmt.setDate(2, Date.valueOf(production.getDateProduction()));
                stmt.setTime(3, Time.valueOf(production.getHeureProduction()));
                stmt.setDouble(4, production.getQuantiteEntreeReelle());
                stmt.setString(5, production.getStatut());
                stmt.setString(6, production.getMessageErreur());

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("Échec de la création de la production");
                }

                // Récupérer l'ID généré
                Long productionId;
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        productionId = generatedKeys.getLong(1);
                        production.setId(productionId);
                    } else {
                        throw new SQLException("Échec de la récupération de l'ID généré");
                    }
                }

                // Insérer les sorties réelles
                creerSortiesReelles(conn, productionId, production.getSortiesReelles());

                conn.commit();
                LOGGER.info("Production créée avec succès : ID " + productionId);
                return productionId;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void creerSortiesReelles(Connection conn, Long productionId, List<SortieReelle> sorties) throws SQLException {
        String querySortie = """
            INSERT INTO sorties_reelles (production_id, numero_sortie, quantite_reelle)
            VALUES (?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(querySortie)) {
            for (SortieReelle sortie : sorties) {
                stmt.setLong(1, productionId);
                stmt.setInt(2, sortie.getNumeroSortie());
                stmt.setDouble(3, sortie.getQuantiteReelle());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Récupère toutes les productions pour une matière première donnée
     */
    public List<ProductionModel> listerParMatiere(Long matierePremiereId) throws SQLException {
        String query = """
            SELECT p.id, p.matiere_premiere_id, p.date_production, p.heure_production,
                   p.quantite_entree_reelle, p.statut, p.message_erreur, p.date_creation
            FROM productions p
            WHERE p.matiere_premiere_id = ?
            ORDER BY p.date_production DESC, p.heure_production DESC
            """;

        List<ProductionModel> productions = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, matierePremiereId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ProductionModel production = mapResultSetToProduction(rs);
                    production.setSortiesReelles(chargerSortiesReelles(conn, production.getId()));
                    productions.add(production);
                }
            }
        }

        return productions;
    }

    /**
     * Récupère les productions filtrées par date
     */
    public List<ProductionModel> listerParMatierePeriode(Long matierePremiereId,
                                                         LocalDate dateDebut,
                                                         LocalDate dateFin) throws SQLException {
        String query = """
            SELECT p.id, p.matiere_premiere_id, p.date_production, p.heure_production,
                   p.quantite_entree_reelle, p.statut, p.message_erreur, p.date_creation
            FROM productions p
            WHERE p.matiere_premiere_id = ? 
              AND p.date_production >= ? 
              AND p.date_production <= ?
            ORDER BY p.date_production DESC, p.heure_production DESC
            """;

        List<ProductionModel> productions = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, matierePremiereId);
            stmt.setDate(2, Date.valueOf(dateDebut));
            stmt.setDate(3, Date.valueOf(dateFin));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ProductionModel production = mapResultSetToProduction(rs);
                    production.setSortiesReelles(chargerSortiesReelles(conn, production.getId()));
                    productions.add(production);
                }
            }
        }

        return productions;
    }

    /**
     * Trouve une production par son ID
     */
    public ProductionModel trouverParId(Long id) throws SQLException {
        String query = """
            SELECT p.id, p.matiere_premiere_id, p.date_production, p.heure_production,
                   p.quantite_entree_reelle, p.statut, p.message_erreur, p.date_creation
            FROM productions p
            WHERE p.id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ProductionModel production = mapResultSetToProduction(rs);
                    production.setSortiesReelles(chargerSortiesReelles(conn, id));
                    return production;
                }
            }
        }
        return null;
    }

    private List<SortieReelle> chargerSortiesReelles(Connection conn, Long productionId) throws SQLException {
        String query = """
            SELECT numero_sortie, quantite_reelle
            FROM sorties_reelles
            WHERE production_id = ?
            ORDER BY numero_sortie
            """;

        List<SortieReelle> sorties = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, productionId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sorties.add(new SortieReelle(
                            rs.getInt("numero_sortie"),
                            rs.getDouble("quantite_reelle")
                    ));
                }
            }
        }

        return sorties;
    }

    /**
     * Met à jour une production existante
     */
    public void mettreAJour(ProductionModel production) throws SQLException {
        String queryProduction = """
            UPDATE productions 
            SET date_production = ?, heure_production = ?, quantite_entree_reelle = ?,
                statut = ?, message_erreur = ?
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryProduction)) {

            conn.setAutoCommit(false);

            try {
                // Mettre à jour la production
                stmt.setDate(1, Date.valueOf(production.getDateProduction()));
                stmt.setTime(2, Time.valueOf(production.getHeureProduction()));
                stmt.setDouble(3, production.getQuantiteEntreeReelle());
                stmt.setString(4, production.getStatut());
                stmt.setString(5, production.getMessageErreur());
                stmt.setLong(6, production.getId());

                stmt.executeUpdate();

                // Supprimer les anciennes sorties
                supprimerSortiesReelles(conn, production.getId());

                // Insérer les nouvelles sorties
                creerSortiesReelles(conn, production.getId(), production.getSortiesReelles());

                conn.commit();
                LOGGER.info("Production mise à jour : ID " + production.getId());

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void supprimerSortiesReelles(Connection conn, Long productionId) throws SQLException {
        String query = "DELETE FROM sorties_reelles WHERE production_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, productionId);
            stmt.executeUpdate();
        }
    }

    /**
     * Supprime une production et ses sorties
     */
    public void supprimer(Long id) throws SQLException {
        String query = "DELETE FROM productions WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            // Les sorties seront supprimées automatiquement grâce à la clé étrangère
            stmt.setLong(1, id);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                LOGGER.info("Production supprimée : ID " + id);
            }
        }
    }
    public int compterParMatiereId(Long matiereId) throws SQLException {
        String query = "SELECT COUNT(*) FROM productions WHERE matiere_premiere_id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, matiereId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private ProductionModel mapResultSetToProduction(ResultSet rs) throws SQLException {
        ProductionModel production = new ProductionModel();
        production.setId(rs.getLong("id"));
        production.setMatierePremiereId(rs.getLong("matiere_premiere_id"));
        production.setDateProduction(rs.getDate("date_production").toLocalDate());
        production.setHeureProduction(rs.getTime("heure_production").toLocalTime());
        production.setQuantiteEntreeReelle(rs.getDouble("quantite_entree_reelle"));
        production.setStatut(rs.getString("statut"));
        production.setMessageErreur(rs.getString("message_erreur"));

        Timestamp timestamp = rs.getTimestamp("date_creation");
        if (timestamp != null) {
            production.setDateCreation(timestamp.toLocalDateTime());
        }

        return production;
    }
}