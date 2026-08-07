package dao;

import java.util.List;

/**
 * Une evolution du schema de la base, identifiee par un numero de version.
 *
 * Une migration deja appliquee ne l'est jamais deux fois : c'est ce qui permet
 * de mettre a jour une base existante chez un utilisateur sans perdre ses
 * donnees ni rejouer les etapes precedentes.
 */
public class Migration {

    private final int version;
    private final String description;
    private final List<String> instructions;

    public Migration(int version, String description, List<String> instructions) {
        if (version <= 0) {
            throw new IllegalArgumentException("Le numero de version doit etre positif");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Une migration doit etre decrite");
        }
        if (instructions == null || instructions.isEmpty()) {
            throw new IllegalArgumentException("Une migration doit contenir au moins une instruction");
        }
        this.version = version;
        this.description = description;
        this.instructions = List.copyOf(instructions);
    }

    public Migration(int version, String description, String... instructions) {
        this(version, description, List.of(instructions));
    }

    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getInstructions() {
        return instructions;
    }

    @Override
    public String toString() {
        return "v" + version + " — " + description;
    }
}
