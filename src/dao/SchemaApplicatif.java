package dao;

import java.util.List;

/**
 * Historique des versions du schema de la base.
 *
 * Pour faire evoluer le modele de donnees, ne modifiez JAMAIS une migration
 * deja livree : les bases des utilisateurs l'ont deja appliquee et ne la
 * rejoueront pas. Ajoutez une nouvelle migration a la suite, par exemple :
 *
 * <pre>
 * new Migration(2, "Ajout du commentaire de production",
 *         "ALTER TABLE productions ADD COLUMN commentaire TEXT")
 * </pre>
 *
 * Elle sera appliquee automatiquement au prochain demarrage, apres la
 * sauvegarde de la base.
 */
public final class SchemaApplicatif {

    private SchemaApplicatif() {
    }

    /**
     * Version 1 : schema initial.
     *
     * Toutes les instructions sont en IF NOT EXISTS afin qu'une base creee
     * avant la mise en place des migrations soit adoptee telle quelle, sans
     * rien recreer ni perdre.
     */
    private static final Migration SCHEMA_INITIAL = new Migration(1, "Schéma initial", List.of(
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

            """
            CREATE TABLE IF NOT EXISTS utilisateurs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                identifiant TEXT NOT NULL UNIQUE,
                empreinte_mot_de_passe TEXT NOT NULL,
                sel TEXT NOT NULL,
                role TEXT NOT NULL,
                date_creation DATETIME DEFAULT CURRENT_TIMESTAMP,
                actif BOOLEAN DEFAULT TRUE
            )
            """,

            "CREATE INDEX IF NOT EXISTS idx_production_date ON productions(date_production)",
            "CREATE INDEX IF NOT EXISTS idx_production_matiere ON productions(matiere_premiere_id)",
            "CREATE INDEX IF NOT EXISTS idx_sortie_production ON sorties_reelles(production_id)"
    ));

    /**
     * Version 2 : trace du compte ayant saisi chaque production.
     *
     * Sur un poste partage entre plusieurs operateurs, savoir qui a enregistre
     * une valeur permet de lever un doute sans avoir a interroger tout le monde.
     * Les productions anterieures gardent une valeur vide : elles datent d'avant
     * cette trace, et le pretendre serait une information fausse.
     */
    private static final Migration TRACE_DE_SAISIE = new Migration(2,
            "Trace du compte ayant saisi la production",
            "ALTER TABLE productions ADD COLUMN saisi_par TEXT");

    /** Toutes les migrations connues, dans l'ordre. */
    public static List<Migration> migrations() {
        return List.of(SCHEMA_INITIAL, TRACE_DE_SAISIE);
    }

    /** Version attendue par cette version de l'application. */
    public static int versionAttendue() {
        return migrations().stream().mapToInt(Migration::getVersion).max().orElse(0);
    }
}
