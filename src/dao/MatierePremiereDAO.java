package dao;

import model.MatierePremiereModel;
import model.MatierePremiereModel.SortieIdeale;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

public class MatierePremiereDAO {
    private static final Logger LOGGER = Logger.getLogger(MatierePremiereDAO.class.getName());
    private final DatabaseManager dbManager;

    public MatierePremiereDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public Long creer(MatierePremiereModel matiere) throws SQLException {
        String queryMatiere = """
            INSERT INTO matieres_premieres (nom, quantite_entree_ideale, nombre_sorties, date_creation, actif)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryMatiere, Statement.RETURN_GENERATED_KEYS)) {

            conn.setAutoCommit(false);

            try {
                // Insérer la matière première
                stmt.setString(1, matiere.getNom());
                stmt.setDouble(2, matiere.getQuantiteEntreeIdeale());
                stmt.setInt(3, matiere.getNombreSorties());
                stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setBoolean(5, matiere.isActif());

                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("Échec de la création de la matière première");
                }

                // Récupérer l'ID généré
                Long matiereId;
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        matiereId = generatedKeys.getLong(1);
                        matiere.setId(matiereId);
                    } else {
                        throw new SQLException("Échec de la récupération de l'ID généré");
                    }
                }

                // Insérer les sorties idéales
                creerSortiesIdeales(conn, matiereId, matiere.getSortiesIdeales());

                conn.commit();
                LOGGER.info("Matière première créée avec succès : " + matiere.getNom());
                return matiereId;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void creerSortiesIdeales(Connection conn, Long matiereId, List<SortieIdeale> sorties) throws SQLException {
        String querySortie = """
            INSERT INTO sorties_ideales (matiere_premiere_id, numero_sortie, quantite_ideale, nom_sortie)
            VALUES (?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(querySortie)) {
            for (SortieIdeale sortie : sorties) {
                stmt.setLong(1, matiereId);
                stmt.setInt(2, sortie.getNumeroSortie());
                stmt.setDouble(3, sortie.getQuantiteIdeale());
                stmt.setString(4, sortie.getNomSortie());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public List<MatierePremiereModel> listerActives() throws SQLException {
        String query = """
            SELECT m.id, m.nom, m.quantite_entree_ideale, m.nombre_sorties, 
                   m.date_creation, m.actif
            FROM matieres_premieres m
            WHERE m.actif = true
            ORDER BY m.nom
            """;

        List<MatierePremiereModel> matieres = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                MatierePremiereModel matiere = mapResultSetToMatiere(rs);
                // Charger les sorties idéales
                matiere.setSortiesIdeales(chargerSortiesIdeales(conn, matiere.getId()));
                matieres.add(matiere);
            }
        }

        return matieres;
    }

    public MatierePremiereModel trouverParId(Long id) throws SQLException {
        String query = """
            SELECT m.id, m.nom, m.quantite_entree_ideale, m.nombre_sorties, 
                   m.date_creation, m.actif
            FROM matieres_premieres m
            WHERE m.id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    MatierePremiereModel matiere = mapResultSetToMatiere(rs);
                    matiere.setSortiesIdeales(chargerSortiesIdeales(conn, id));
                    return matiere;
                }
            }
        }
        return null;
    }

    private List<SortieIdeale> chargerSortiesIdeales(Connection conn, Long matiereId) throws SQLException {
        String query = """
            SELECT numero_sortie, quantite_ideale, nom_sortie
            FROM sorties_ideales
            WHERE matiere_premiere_id = ?
            ORDER BY numero_sortie
            """;

        List<SortieIdeale> sorties = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, matiereId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sorties.add(new SortieIdeale(
                            rs.getInt("numero_sortie"),
                            rs.getDouble("quantite_ideale"),
                            rs.getString("nom_sortie")
                    ));
                }
            }
        }

        return sorties;
    }

    public void mettreAJour(MatierePremiereModel matiere) throws SQLException {
        String queryMatiere = """
            UPDATE matieres_premieres 
            SET nom = ?, quantite_entree_ideale = ?, nombre_sorties = ?, actif = ?
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(queryMatiere)) {

            conn.setAutoCommit(false);

            try {
                // Mettre à jour la matière première
                stmt.setString(1, matiere.getNom());
                stmt.setDouble(2, matiere.getQuantiteEntreeIdeale());
                stmt.setInt(3, matiere.getNombreSorties());
                stmt.setBoolean(4, matiere.isActif());
                stmt.setLong(5, matiere.getId());

                stmt.executeUpdate();

                // Supprimer les anciennes sorties
                supprimerSortiesIdeales(conn, matiere.getId());

                // Insérer les nouvelles sorties
                creerSortiesIdeales(conn, matiere.getId(), matiere.getSortiesIdeales());

                conn.commit();
                LOGGER.info("Matière première mise à jour : " + matiere.getNom());

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void supprimerSortiesIdeales(Connection conn, Long matiereId) throws SQLException {
        String query = "DELETE FROM sorties_ideales WHERE matiere_premiere_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, matiereId);
            stmt.executeUpdate();
        }
    }

    public void desactiver(Long id) throws SQLException {
        String query = "UPDATE matieres_premieres SET actif = false WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, id);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                LOGGER.info("Matière première désactivée : ID " + id);
            }
        }
    }

    private MatierePremiereModel mapResultSetToMatiere(ResultSet rs) throws SQLException {
        MatierePremiereModel matiere = new MatierePremiereModel();
        matiere.setId(rs.getLong("id"));
        matiere.setNom(rs.getString("nom"));
        matiere.setQuantiteEntreeIdeale(rs.getDouble("quantite_entree_ideale"));
        matiere.setNombreSorties(rs.getInt("nombre_sorties"));
        matiere.setActif(rs.getBoolean("actif"));

        Timestamp timestamp = rs.getTimestamp("date_creation");
        if (timestamp != null) {
            matiere.setDateCreation(timestamp.toLocalDateTime());
        }

        return matiere;
    }
}