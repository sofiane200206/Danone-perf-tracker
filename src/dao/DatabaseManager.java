package dao;

import java.sql.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DB_URL_DEFAUT = "jdbc:sqlite:production_tracker.db";
    private static DatabaseManager instance;
    private final String dbUrl;
    private Connection connection;

    private DatabaseManager() {
        // Surchargeable pour que les tests travaillent sur une base jetable
        this.dbUrl = System.getProperty("performancetracker.db.url", DB_URL_DEFAUT);
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Chemin du fichier SQLite utilise, extrait de l'URL JDBC.
     * Sert notamment a le sauvegarder avant ouverture.
     */
    public static String getCheminFichierBase() {
        String url = System.getProperty("performancetracker.db.url", DB_URL_DEFAUT);
        return url.startsWith("jdbc:sqlite:") ? url.substring("jdbc:sqlite:".length()) : url;
    }

    private void initializeDatabase() {
        try {
            connection = DriverManager.getConnection(dbUrl);

            // Le schema est amene a la version attendue par des migrations :
            // une base existante est mise a jour sans perdre ses donnees.
            MigrationService migrations = new MigrationService(SchemaApplicatif.migrations());
            migrations.appliquer(connection);

            LOGGER.info("Base de donnees prete (schema version "
                    + migrations.versionActuelle(connection) + ")");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation de la base de données", e);
            throw new RuntimeException("Impossible d'initialiser la base de données", e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(dbUrl);
            }
            return connection;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de la connexion", e);
            throw new RuntimeException("Impossible d'obtenir la connexion à la base de données", e);
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la fermeture de la connexion", e);
        }
    }

    // Méthode pour exécuter des requêtes de mise à jour
    public int executeUpdate(String query, Object... params) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            return stmt.executeUpdate();
        }
    }

    // Méthode pour exécuter des requêtes de sélection
    public ResultSet executeQuery(String query, Object... params) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(query);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
        return stmt.executeQuery();
    }
}