package service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Copie la base de donnees avant chaque demarrage et conserve les N dernieres
 * copies. Sans ce filet, une corruption ou une suppression accidentelle
 * entraine la perte definitive de tout l'historique de production.
 */
public class SauvegardeService {

    private static final Logger LOGGER = Logger.getLogger(SauvegardeService.class.getName());
    private static final DateTimeFormatter HORODATAGE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String PREFIXE = "sauvegarde_";
    private static final String EXTENSION = ".db";

    private final Path fichierBase;
    private final Path dossierSauvegardes;
    private final int nombreAConserver;

    public SauvegardeService(Path fichierBase, Path dossierSauvegardes, int nombreAConserver) {
        if (nombreAConserver < 1) {
            throw new IllegalArgumentException("Il faut conserver au moins une sauvegarde");
        }
        this.fichierBase = fichierBase;
        this.dossierSauvegardes = dossierSauvegardes;
        this.nombreAConserver = nombreAConserver;
    }

    /**
     * Sauvegarde la base si elle existe et n'est pas vide.
     *
     * @return le fichier cree, ou vide s'il n'y avait rien a sauvegarder
     */
    public Optional<Path> sauvegarder() throws IOException {
        if (!Files.exists(fichierBase) || Files.size(fichierBase) == 0) {
            LOGGER.info("Aucune base existante a sauvegarder : " + fichierBase);
            return Optional.empty();
        }

        Files.createDirectories(dossierSauvegardes);

        Path destination = dossierSauvegardes.resolve(
                PREFIXE + LocalDateTime.now().format(HORODATAGE) + EXTENSION);

        // Si deux demarrages tombent dans la meme seconde, on ne cherche pas a
        // creer deux copies identiques : la premiere fait foi.
        if (Files.exists(destination)) {
            LOGGER.info("Sauvegarde deja presente pour cet horodatage : " + destination);
            return Optional.of(destination);
        }

        Files.copy(fichierBase, destination, StandardCopyOption.COPY_ATTRIBUTES);
        LOGGER.info("Base sauvegardee : " + destination);

        purgerAnciennes();
        return Optional.of(destination);
    }

    /**
     * Supprime les sauvegardes les plus anciennes au-dela du quota.
     * Un echec de purge ne doit jamais empecher l'application de demarrer.
     */
    private void purgerAnciennes() {
        try {
            List<Path> sauvegardes = listerSauvegardes();
            if (sauvegardes.size() <= nombreAConserver) {
                return;
            }
            for (Path obsolete : sauvegardes.subList(nombreAConserver, sauvegardes.size())) {
                try {
                    Files.deleteIfExists(obsolete);
                    LOGGER.info("Ancienne sauvegarde supprimee : " + obsolete.getFileName());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Impossible de supprimer " + obsolete, e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Purge des sauvegardes impossible", e);
        }
    }

    /**
     * Sauvegardes existantes, de la plus recente a la plus ancienne.
     */
    public List<Path> listerSauvegardes() throws IOException {
        if (!Files.isDirectory(dossierSauvegardes)) {
            return new ArrayList<>();
        }
        try (Stream<Path> fichiers = Files.list(dossierSauvegardes)) {
            List<Path> trouvees = new ArrayList<>(fichiers
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(PREFIXE))
                    .filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .toList());
            // Le nom porte l'horodatage : l'ordre alphabetique inverse est
            // l'ordre chronologique inverse, et ne depend pas du systeme de fichiers.
            trouvees.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
            return trouvees;
        }
    }

    /**
     * Sauvegarde au demarrage, en isolant l'application de tout echec :
     * ne pas pouvoir sauvegarder ne doit pas empecher de travailler.
     */
    public static void sauvegarderAuDemarrage(String cheminBase) {
        try {
            Path base = Paths.get(cheminBase);
            Path dossier = base.toAbsolutePath().getParent().resolve("sauvegardes");
            new SauvegardeService(base, dossier, 10).sauvegarder();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Sauvegarde automatique impossible, demarrage poursuivi", e);
        }
    }
}
