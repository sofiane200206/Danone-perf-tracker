package dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chaque test travaille sur sa propre base jetable : les migrations touchent
 * au schema, il ne faut jamais les essayer sur une base partagee.
 */
class MigrationServiceTest {

    @TempDir
    Path dossier;

    private String urlBase;

    @BeforeEach
    void preparerBase() {
        urlBase = "jdbc:sqlite:" + dossier.resolve("migration-test.db");
    }

    private Connection ouvrir() throws SQLException {
        return DriverManager.getConnection(urlBase);
    }

    private boolean tableExiste(Connection conn, String nom) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name = ?")) {
            stmt.setString(1, nom);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private boolean colonneExiste(Connection conn, String table, String colonne) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (colonne.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    @Test
    @DisplayName("Une base vierge recoit tout le schema applicatif")
    void baseViergeRecoitLeSchema() throws SQLException {
        try (Connection conn = ouvrir()) {
            MigrationService service = new MigrationService(SchemaApplicatif.migrations());

            assertEquals(0, service.versionActuelle(conn), "base vierge");
            int appliquees = service.appliquer(conn);

            assertEquals(SchemaApplicatif.migrations().size(), appliquees);
            assertEquals(SchemaApplicatif.versionAttendue(), service.versionActuelle(conn));

            for (String table : List.of("matieres_premieres", "sorties_ideales",
                    "productions", "sorties_reelles", "utilisateurs")) {
                assertTrue(tableExiste(conn, table), "table manquante : " + table);
            }
        }
    }

    @Test
    @DisplayName("Relancer l'application ne rejoue aucune migration")
    void secondDemarrageNeRejoueRien() throws SQLException {
        try (Connection conn = ouvrir()) {
            MigrationService service = new MigrationService(SchemaApplicatif.migrations());

            service.appliquer(conn);
            assertEquals(0, service.appliquer(conn), "aucune migration ne doit etre rejouee");
            assertTrue(service.migrationsEnAttente(conn).isEmpty());
        }
    }

    @Test
    @DisplayName("Seules les nouvelles migrations sont appliquees a une base existante")
    void migrationSupplementaireSurBaseExistante() throws SQLException {
        try (Connection conn = ouvrir()) {
            // L'utilisateur tourne avec la version livree
            new MigrationService(SchemaApplicatif.migrations()).appliquer(conn);

            // Une mise a jour ajoute une colonne. Le numero est calcule a partir
            // du schema livre pour que ce test resiste a l'ajout de vraies migrations.
            int prochaine = SchemaApplicatif.versionAttendue() + 1;
            List<Migration> versionSuivante = new java.util.ArrayList<>(SchemaApplicatif.migrations());
            versionSuivante.add(new Migration(prochaine, "Ajout du commentaire de production",
                    "ALTER TABLE productions ADD COLUMN commentaire TEXT"));

            MigrationService service = new MigrationService(versionSuivante);
            assertEquals(1, service.migrationsEnAttente(conn).size());
            assertEquals(1, service.appliquer(conn), "seule la nouvelle migration doit etre jouee");

            assertEquals(prochaine, service.versionActuelle(conn));
            assertTrue(colonneExiste(conn, "productions", "commentaire"));
        }
    }

    @Test
    @DisplayName("Les donnees existantes survivent a une migration")
    void donneesConserveesApresMigration() throws SQLException {
        try (Connection conn = ouvrir()) {
            new MigrationService(SchemaApplicatif.migrations()).appliquer(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO matieres_premieres (nom, quantite_entree_ideale, nombre_sorties) "
                        + "VALUES ('Lait', 100.0, 2)");
            }

            List<Migration> versionSuivante = new java.util.ArrayList<>(SchemaApplicatif.migrations());
            versionSuivante.add(new Migration(SchemaApplicatif.versionAttendue() + 1, "Ajout d'une colonne",
                    "ALTER TABLE matieres_premieres ADD COLUMN fournisseur TEXT"));
            new MigrationService(versionSuivante).appliquer(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT nom, quantite_entree_ideale FROM matieres_premieres")) {
                assertTrue(rs.next(), "la matiere doit toujours exister");
                assertEquals("Lait", rs.getString("nom"));
                assertEquals(100.0, rs.getDouble("quantite_entree_ideale"));
            }
        }
    }

    @Test
    @DisplayName("Une migration qui echoue est annulee et n'est pas enregistree")
    void migrationEchoueeEstAnnulee() throws SQLException {
        try (Connection conn = ouvrir()) {
            new MigrationService(SchemaApplicatif.migrations()).appliquer(conn);

            List<Migration> avecErreur = new java.util.ArrayList<>(SchemaApplicatif.migrations());
            avecErreur.add(new Migration(SchemaApplicatif.versionAttendue() + 1, "Migration cassee",
                    "ALTER TABLE productions ADD COLUMN valide TEXT",
                    "CETTE INSTRUCTION N'EST PAS DU SQL"));

            MigrationService service = new MigrationService(avecErreur);

            assertThrows(SQLException.class, () -> service.appliquer(conn));

            assertEquals(SchemaApplicatif.versionAttendue(), service.versionActuelle(conn),
                    "la base doit rester a la version livree");
            assertFalse(colonneExiste(conn, "productions", "valide"),
                    "la premiere instruction doit avoir ete annulee avec le reste");
        }
    }

    @Test
    @DisplayName("Apres un echec, les migrations suivantes ne sont pas tentees")
    void migrationsSuivantesNonTenteesApresEchec() throws SQLException {
        try (Connection conn = ouvrir()) {
            new MigrationService(SchemaApplicatif.migrations()).appliquer(conn);

            int prochaine = SchemaApplicatif.versionAttendue() + 1;
            List<Migration> suite = new java.util.ArrayList<>(SchemaApplicatif.migrations());
            suite.add(new Migration(prochaine, "Migration cassee", "PAS DU SQL"));
            suite.add(new Migration(prochaine + 1, "Migration valable",
                    "ALTER TABLE productions ADD COLUMN operateur TEXT"));

            assertThrows(SQLException.class, () -> new MigrationService(suite).appliquer(conn));

            assertFalse(colonneExiste(conn, "productions", "operateur"),
                    "une base a moitie migree serait pire que pas migree du tout");
        }
    }

    @Test
    @DisplayName("Les migrations sont appliquees dans l'ordre des versions")
    void ordreDApplication() throws SQLException {
        try (Connection conn = ouvrir()) {
            // Volontairement declarees dans le desordre
            List<Migration> desordre = List.of(
                    new Migration(3, "Troisieme", "CREATE TABLE t3 (id INTEGER)"),
                    new Migration(1, "Premiere", "CREATE TABLE t1 (id INTEGER)"),
                    new Migration(2, "Deuxieme", "CREATE TABLE t2 (id INTEGER)"));

            new MigrationService(desordre).appliquer(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT version, description FROM schema_migrations ORDER BY version")) {
                assertTrue(rs.next());
                assertEquals("Premiere", rs.getString("description"));
                assertTrue(rs.next());
                assertEquals("Deuxieme", rs.getString("description"));
                assertTrue(rs.next());
                assertEquals("Troisieme", rs.getString("description"));
            }
        }
    }

    @Test
    @DisplayName("Deux migrations portant le meme numero sont refusees")
    void versionsDupliqueesRefusees() {
        assertThrows(IllegalArgumentException.class, () -> new MigrationService(List.of(
                new Migration(1, "Une", "CREATE TABLE a (id INTEGER)"),
                new Migration(1, "Deux", "CREATE TABLE b (id INTEGER)"))));
    }

    @Test
    @DisplayName("Une migration mal formee est refusee des sa declaration")
    void migrationMalFormeeRefusee() {
        assertThrows(IllegalArgumentException.class,
                () -> new Migration(0, "Version invalide", "CREATE TABLE a (id INTEGER)"));
        assertThrows(IllegalArgumentException.class,
                () -> new Migration(1, "  ", "CREATE TABLE a (id INTEGER)"));
        assertThrows(IllegalArgumentException.class,
                () -> new Migration(1, "Sans instruction", List.of()));
    }

    @Test
    @DisplayName("Une base creee avant les migrations est adoptee sans etre recreee")
    void baseAncienneAdopteeSansPerte() throws SQLException {
        try (Connection conn = ouvrir()) {
            // Base d'un utilisateur ayant installe l'application avant ce mecanisme
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE matieres_premieres (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        nom TEXT NOT NULL UNIQUE,
                        quantite_entree_ideale REAL NOT NULL,
                        nombre_sorties INTEGER NOT NULL DEFAULT 2,
                        date_creation DATETIME DEFAULT CURRENT_TIMESTAMP,
                        actif BOOLEAN DEFAULT TRUE
                    )
                    """);
                stmt.executeUpdate("INSERT INTO matieres_premieres (nom, quantite_entree_ideale, nombre_sorties) "
                        + "VALUES ('Lait historique', 250.0, 3)");
            }

            new MigrationService(SchemaApplicatif.migrations()).appliquer(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT nom, quantite_entree_ideale FROM matieres_premieres")) {
                assertTrue(rs.next(), "les donnees d'origine doivent etre intactes");
                assertEquals("Lait historique", rs.getString("nom"));
                assertEquals(250.0, rs.getDouble("quantite_entree_ideale"));
            }
            assertTrue(tableExiste(conn, "utilisateurs"),
                    "les tables manquantes doivent avoir ete ajoutees");
        }
    }
}
