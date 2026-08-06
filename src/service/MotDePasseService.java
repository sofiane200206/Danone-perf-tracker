package service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Hachage et verification des mots de passe.
 *
 * Un mot de passe n'est jamais conserve ni chiffre : on en stocke une empreinte
 * PBKDF2 accompagnee d'un sel aleatoire propre a chaque compte. Deux comptes
 * partageant le meme mot de passe produisent donc des empreintes differentes,
 * et retrouver le mot de passe a partir de l'empreinte est impraticable.
 */
public final class MotDePasseService {

    private static final String ALGORITHME = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int LONGUEUR_CLE_BITS = 256;
    private static final int LONGUEUR_SEL_OCTETS = 16;
    private static final int LONGUEUR_MINIMALE = 8;

    private static final SecureRandom ALEA = new SecureRandom();

    private MotDePasseService() {
    }

    /** Sel aleatoire encode en Base64, a stocker aux cotes de l'empreinte. */
    public static String genererSel() {
        byte[] sel = new byte[LONGUEUR_SEL_OCTETS];
        ALEA.nextBytes(sel);
        return Base64.getEncoder().encodeToString(sel);
    }

    /** Empreinte PBKDF2 du mot de passe, encodee en Base64. */
    public static String hacher(char[] motDePasse, String selBase64) {
        if (motDePasse == null || motDePasse.length == 0) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas etre vide");
        }
        byte[] sel = Base64.getDecoder().decode(selBase64);
        KeySpec spec = new PBEKeySpec(motDePasse, sel, ITERATIONS, LONGUEUR_CLE_BITS);
        try {
            SecretKeyFactory fabrique = SecretKeyFactory.getInstance(ALGORITHME);
            byte[] empreinte = fabrique.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(empreinte);
        } catch (Exception e) {
            throw new IllegalStateException("Hachage du mot de passe impossible", e);
        } finally {
            ((PBEKeySpec) spec).clearPassword();
        }
    }

    /**
     * Compare un mot de passe saisi a une empreinte connue.
     * La comparaison est a temps constant pour ne rien laisser deviner.
     */
    public static boolean verifier(char[] motDePasse, String selBase64, String empreinteAttendue) {
        if (motDePasse == null || motDePasse.length == 0
                || selBase64 == null || empreinteAttendue == null) {
            return false;
        }
        try {
            String calculee = hacher(motDePasse, selBase64);
            return MessageDigest.isEqual(
                    calculee.getBytes(StandardCharsets.UTF_8),
                    empreinteAttendue.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Controle de robustesse minimal.
     *
     * @return le motif de refus, ou null si le mot de passe convient
     */
    public static String motifDeRefus(String motDePasse) {
        if (motDePasse == null || motDePasse.isEmpty()) {
            return "Le mot de passe est obligatoire.";
        }
        if (motDePasse.length() < LONGUEUR_MINIMALE) {
            return "Le mot de passe doit contenir au moins " + LONGUEUR_MINIMALE + " caracteres.";
        }
        boolean lettre = motDePasse.chars().anyMatch(Character::isLetter);
        boolean chiffre = motDePasse.chars().anyMatch(Character::isDigit);
        if (!lettre || !chiffre) {
            return "Le mot de passe doit melanger lettres et chiffres.";
        }
        return null;
    }

    /** Efface un mot de passe de la memoire des qu'il n'est plus utile. */
    public static void effacer(char[] motDePasse) {
        if (motDePasse != null) {
            Arrays.fill(motDePasse, '\0');
        }
    }
}
