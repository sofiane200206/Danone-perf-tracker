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
            createTables();
            LOGGER.info("Base de données initialisée avec succès");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation de la base de données", e);
            throw new RuntimeException("Impossible d'initialiser la base de données", e);
        }
    }

    private void createTables() throws SQLException {
        String[] createTableQueries = {
                // Table des matières premières
                """
            CREATE TABLE IF NOT EXISTS matieres_premieres (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nom TEXT NOT NULL UNIQUE,
                quantite_entree_ideale REAL NOT NULL,
                nombre_sorties INTEGER NOT NULL DEFAULT 2,
                date_creation DATETIME DEFAULT CURRENT_TIMESTAMP,
                actif BOOLEAN DEFAULT TRUE
            )
            """,

                // Table des sorties idéales (flexible pour N sorties)
                """
            CREATE TABLE IF NOT EXISTS sorties_ideales (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                matiere_premiere_id INTEGER NOT NULL,
                numero_sortie INTEGER NOT NULL,
                quantite_ideale REAL NOT NULL,
                nom_sortie TEXT,
                FOREIGN KEY (matiere_premiere_id) REFERENCES matieres_premieres(id),
                UNIQUE(matiere_premiere_id, numero_sortie)
            )
            """,

                // Table des productions
                """
            CREATE TABLE IF NOT EXISTS productions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                matiere_premiere_id INTEGER NOT NULL,
                date_production DATE NOT NULL,
                heure_production TIME NOT NULL,
                quantite_entree_reelle REAL NOT NULL,
                statut TEXT NOT NULL DEFAULT 'VALIDE',
                message_erreur TEXT,
                date_creation DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (matiere_premiere_id) REFERENCES matieres_premieres(id)
            )
            """,

                // Table des sorties réelles (flexible pour N sorties)
                """
            CREATE TABLE IF NOT EXISTS sorties_reelles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                production_id INTEGER NOT NULL,
                numero_sortie INTEGER NOT NULL,
                quantite_reelle REAL NOT NULL,
                FOREIGN KEY (production_id) REFERENCES productions(id),
                UNIQUE(production_id, numero_sortie)
            )
            """,

                // Index pour optimiser les requêtes
                "CREATE INDEX IF NOT EXISTS idx_production_date ON productions(date_production)",
                "CREATE INDEX IF NOT EXISTS idx_production_matiere ON productions(matiere_premiere_id)",
                "CREATE INDEX IF NOT EXISTS idx_sortie_production ON sorties_reelles(production_id)"
        };

        for (String query : createTableQueries) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(query);
            }
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