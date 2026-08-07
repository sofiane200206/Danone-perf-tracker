package service;

import model.MatierePremiereModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Regles de saisie d'une production.
 *
 * Elles vivaient dans l'interface, donc dupliquees et invérifiables. Les
 * regrouper ici permet de les tester et garantit que la creation et la
 * modification appliquent exactement les memes controles.
 */
public final class ValidateurProduction {

    /** Au-dela, la date est jugee trop ancienne pour une saisie de production. */
    public static final int ANCIENNETE_MAXIMALE_ANNEES = 1;

    /** Au-dela de ce multiple de l'entree ideale, on avertit sans bloquer. */
    public static final double FACTEUR_ALERTE_ENTREE = 2.0;

    /** Tolerance sur la conservation de la matiere, pour absorber les arrondis. */
    private static final double TOLERANCE_KG = 0.001;

    private ValidateurProduction() {
    }

    /**
     * Issue d'une validation : soit un refus motive, soit les valeurs
     * exploitables accompagnees d'eventuels avertissements.
     */
    public static final class Resultat {

        private final String refus;
        private final double entree;
        private final List<Double> sorties;
        private final List<String> avertissements;

        private Resultat(String refus, double entree, List<Double> sorties, List<String> avertissements) {
            this.refus = refus;
            this.entree = entree;
            this.sorties = sorties == null ? List.of() : List.copyOf(sorties);
            this.avertissements = avertissements == null ? List.of() : List.copyOf(avertissements);
        }

        static Resultat refuse(String motif) {
            return new Resultat(motif, 0, List.of(), List.of());
        }

        static Resultat accepte(double entree, List<Double> sorties, List<String> avertissements) {
            return new Resultat(null, entree, sorties, avertissements);
        }

        public boolean estAcceptee() {
            return refus == null;
        }

        /** Motif du refus, ou null si la saisie est acceptable. */
        public String getRefus() {
            return refus;
        }

        public double getEntree() {
            return entree;
        }

        public List<Double> getSorties() {
            return sorties;
        }

        public double getTotalSorties() {
            return sorties.stream().mapToDouble(Double::doubleValue).sum();
        }

        /** Messages a afficher sans empecher l'enregistrement. */
        public List<String> getAvertissements() {
            return avertissements;
        }
    }

    /**
     * Valide une saisie complete telle qu'elle arrive de l'interface.
     *
     * @param matiere matiere de reference, facultative : sert seulement aux avertissements
     */
    public static Resultat valider(LocalDate date, LocalTime heure, String entreeTexte,
                                   List<String> sortiesTextes, MatierePremiereModel matiere) {

        if (date == null) {
            return Resultat.refuse("La date de production est obligatoire.");
        }
        if (date.isAfter(LocalDate.now())) {
            return Resultat.refuse("La date de production ne peut pas être dans le futur.");
        }
        if (date.isBefore(LocalDate.now().minusYears(ANCIENNETE_MAXIMALE_ANNEES))) {
            return Resultat.refuse("La date de production est trop ancienne (plus d'un an).");
        }
        if (heure == null) {
            return Resultat.refuse("L'heure de production est obligatoire.");
        }

        if (estVide(entreeTexte)) {
            return Resultat.refuse("La quantité d'entrée est obligatoire.");
        }
        Double entree = enNombre(entreeTexte);
        if (entree == null) {
            return Resultat.refuse("La quantité d'entrée doit être un nombre valide.");
        }
        if (entree <= 0) {
            return Resultat.refuse("La quantité d'entrée doit être positive.");
        }

        if (sortiesTextes == null || sortiesTextes.isEmpty()) {
            return Resultat.refuse("Aucune sortie à saisir : sélectionnez une matière première.");
        }

        List<Double> sorties = new ArrayList<>();
        for (int i = 0; i < sortiesTextes.size(); i++) {
            int numero = i + 1;
            String texte = sortiesTextes.get(i);

            if (estVide(texte)) {
                return Resultat.refuse("La sortie " + numero + " est obligatoire.");
            }
            Double sortie = enNombre(texte);
            if (sortie == null) {
                return Resultat.refuse("La sortie " + numero + " doit être un nombre valide.");
            }
            if (sortie < 0) {
                return Resultat.refuse("La sortie " + numero + " ne peut pas être négative.");
            }
            if (sortie > entree + TOLERANCE_KG) {
                return Resultat.refuse(String.format(
                        "La sortie %d (%.2f kg) ne peut pas dépasser l'entrée (%.2f kg).",
                        numero, sortie, entree));
            }
            sorties.add(sortie);
        }

        // Conservation de la matiere : on ne produit pas plus qu'on n'engage.
        double total = sorties.stream().mapToDouble(Double::doubleValue).sum();
        if (total > entree + TOLERANCE_KG) {
            return Resultat.refuse(String.format(
                    "Le total des sorties (%.2f kg) dépasse l'entrée (%.2f kg) : "
                            + "on ne peut pas produire plus de matière qu'il n'en entre.",
                    total, entree));
        }

        return Resultat.accepte(entree, sorties, avertissements(entree, total, matiere));
    }

    private static List<String> avertissements(double entree, double totalSorties,
                                               MatierePremiereModel matiere) {
        List<String> messages = new ArrayList<>();

        if (matiere != null && matiere.getQuantiteEntreeIdeale() > 0
                && entree > matiere.getQuantiteEntreeIdeale() * FACTEUR_ALERTE_ENTREE) {
            messages.add(String.format(
                    "Entrée très éloignée de l'idéal (%.2f kg attendus). Vérifiez la valeur.",
                    matiere.getQuantiteEntreeIdeale()));
        }

        if (totalSorties == 0) {
            messages.add("Aucune sortie produite pour cette entrée : la performance sera de 0 %.");
        }

        return messages;
    }

    private static boolean estVide(String texte) {
        return texte == null || texte.trim().isEmpty();
    }

    /**
     * Convertit une saisie en nombre, ou null si ce n'en est pas un.
     * La virgule est acceptee : c'est le separateur decimal du clavier francais.
     */
    private static Double enNombre(String texte) {
        try {
            return Double.parseDouble(texte.trim().replace(',', '.'));
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
