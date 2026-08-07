package dao;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Amene une base de donnees a la version attendue par l'application.
 *
 * Les migrations deja appliquees sont enregistrees dans la table
 * schema_migrations : au demarrage suivant, seules les nouvelles sont jouees.
 * Chaque migration s'execute dans sa propre transaction ; si l'une echoue, elle
 * est annulee entierement et les suivantes ne sont pas tentees, pour ne jamais
 * laisser la base a moitie migree.
 */
public class MigrationService {

    private static final Logger LOGGER = Logger.getLogger(MigrationService.class.getName());

    private static final String CREATION_TABLE_SUIVI = """
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version INTEGER PRIMARY KEY,
            description TEXT NOT NULL,
            appliquee_le DATETIME NOT NULL
        )
        """;

    private final List<Migration> migrations;

    public MigrationService(List<Migration> migrations) {
        List<Migration> triees = new ArrayList<>(migrations);
        triees.sort(Comparator.comparingInt(Migration::getVersion));

        Set<Integer> versions = new HashSet<>();
        for (Migration migration : triees) {
            if (!versions.add(migration.getVersion())) {
                throw new IllegalArgumentException(
                        "Deux migrations portent le numero " + migration.getVersion());
            }
        }
        this.migrations = List.copyOf(triees);
    }

    /**
     * Applique les migrations manquantes.
     *
     * @return le nombre de migrations effectivement appliquees
     */
    public int appliquer(Connection conn) throws SQLException {
        creerTableDeSuivi(conn);

        Set<Integer> dejaAppliquees = versionsAppliquees(conn);
        List<Migration> enAttente = migrations.stream()
                .filter(migration -> !dejaAppliquees.contains(migration.getVersion()))
                .toList();

        if (enAttente.isEmpty()) {
            LOGGER.info("Base a jour (version " + versionActuelle(conn) + ")");
            return 0;
        }

        LOGGER.info(enAttente.size() + " migration(s) a appliquer");

        for (Migration migration : enAttente) {
            appliquerUne(conn, migration);
        }

        LOGGER.info("Base migree en version " + versionActuelle(conn));
        return enAttente.size();
    }

    private void appliquerUne(Connection conn, Migration migration) throws SQLException {
        boolean autoCommitInitial = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            try (Statement stmt = conn.createStatement()) {
                for (String instruction : migration.getInstructions()) {
                    stmt.execute(instruction);
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO schema_migrations (version, description, appliquee_le) VALUES (?, ?, ?)")) {
                stmt.setInt(1, migration.getVersion());
                stmt.setString(2, migration.getDescription());
                stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                stmt.executeUpdate();
            }

            conn.commit();
            LOGGER.info("Migration appliquee : " + migration);

        } catch (SQLException e) {
            conn.rollback();
            LOGGER.log(Level.SEVERE, "Migration echouee, base laissee intacte : " + migration, e);
            throw new SQLException(
                    "Migration " + migration + " impossible : " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(autoCommitInitial);
        }
    }

    /** Version courante de la base : 0 si aucune migration n'a encore ete appliquee. */
    public int versionActuelle(Connection conn) throws SQLException {
        creerTableDeSuivi(conn);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Migrations qui restent a appliquer sur cette base. */
    public List<Migration> migrationsEnAttente(Connection conn) throws SQLException {
        Set<Integer> dejaAppliquees = versionsAppliquees(conn);
        return migrations.stream()
                .filter(migration -> !dejaAppliquees.contains(migration.getVersion()))
                .toList();
    }

    private Set<Integer> versionsAppliquees(Connection conn) throws SQLException {
        creerTableDeSuivi(conn);

        Set<Integer> versions = new HashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_migrations")) {
            while (rs.next()) {
                versions.add(rs.getInt(1));
            }
        }
        return versions;
    }

    private void creerTableDeSuivi(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATION_TABLE_SUIVI);
        }
    }
}
