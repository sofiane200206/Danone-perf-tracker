package dao;

import model.UserRole;
import model.Utilisateur;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UtilisateurDAO {

    private static final Logger LOGGER = Logger.getLogger(UtilisateurDAO.class.getName());
    private final DatabaseManager dbManager;

    public UtilisateurDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public Long creer(Utilisateur utilisateur) throws SQLException {
        String query = """
            INSERT INTO utilisateurs (identifiant, empreinte_mot_de_passe, sel, role, date_creation, actif)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, utilisateur.getIdentifiant());
            stmt.setString(2, utilisateur.getEmpreinteMotDePasse());
            stmt.setString(3, utilisateur.getSel());
            stmt.setString(4, utilisateur.getRole().name());
            stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setBoolean(6, utilisateur.isActif());

            stmt.executeUpdate();

            try (PreparedStatement dernierId = conn.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet rs = dernierId.executeQuery()) {
                if (rs.next()) {
                    Long id = rs.getLong(1);
                    utilisateur.setId(id);
                    // Volontairement sans detail du compte dans le journal
                    LOGGER.info("Compte utilisateur cree : " + utilisateur.getIdentifiant());
                    return id;
                }
            }
            throw new SQLException("Echec de la recuperation de l'identifiant genere");
        }
    }

    public Utilisateur trouverParIdentifiant(String identifiant) throws SQLException {
        String query = """
            SELECT id, identifiant, empreinte_mot_de_passe, sel, role, date_creation, actif
            FROM utilisateurs
            WHERE identifiant = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, identifiant);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapper(rs) : null;
            }
        }
    }

    /** Tous les comptes, actifs et desactives, par ordre alphabetique. */
    public List<Utilisateur> listerTous() throws SQLException {
        String query = """
            SELECT id, identifiant, empreinte_mot_de_passe, sel, role, date_creation, actif
            FROM utilisateurs
            ORDER BY identifiant
            """;

        List<Utilisateur> utilisateurs = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Utilisateur utilisateur = mapper(rs);
                if (utilisateur != null) {
                    utilisateurs.add(utilisateur);
                }
            }
        }
        return utilisateurs;
    }

    /** Nombre d'administrateurs encore actifs : sert a ne pas tous les desactiver. */
    public int compterAdministrateursActifs() throws SQLException {
        String query = "SELECT COUNT(*) FROM utilisateurs WHERE role = ? AND actif = true";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, UserRole.ADMIN.name());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void reactiver(Long id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE utilisateurs SET actif = true WHERE id = ?")) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    public int compter() throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM utilisateurs");
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public boolean identifiantExiste(String identifiant) throws SQLException {
        return trouverParIdentifiant(identifiant) != null;
    }

    public void mettreAJourMotDePasse(Long id, String empreinte, String sel) throws SQLException {
        String query = "UPDATE utilisateurs SET empreinte_mot_de_passe = ?, sel = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, empreinte);
            stmt.setString(2, sel);
            stmt.setLong(3, id);

            if (stmt.executeUpdate() == 0) {
                throw new SQLException("Aucun compte ne correspond a l'identifiant " + id);
            }
            LOGGER.info("Mot de passe modifie pour le compte " + id);
        }
    }

    public void desactiver(Long id) throws SQLException {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE utilisateurs SET actif = false WHERE id = ?")) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private Utilisateur mapper(ResultSet rs) throws SQLException {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(rs.getLong("id"));
        utilisateur.setIdentifiant(rs.getString("identifiant"));
        utilisateur.setEmpreinteMotDePasse(rs.getString("empreinte_mot_de_passe"));
        utilisateur.setSel(rs.getString("sel"));
        utilisateur.setActif(rs.getBoolean("actif"));

        try {
            utilisateur.setRole(UserRole.valueOf(rs.getString("role")));
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Role inconnu en base pour le compte "
                    + utilisateur.getIdentifiant() + ", compte ignore", e);
            return null;
        }

        Timestamp creation = rs.getTimestamp("date_creation");
        if (creation != null) {
            utilisateur.setDateCreation(creation.toLocalDateTime());
        }
        return utilisateur;
    }
}
