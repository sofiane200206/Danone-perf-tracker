package service;

import model.MatierePremiereModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidateurProductionTest {

    private static final LocalDate AUJOURD_HUI = LocalDate.now();
    private static final LocalTime HUIT_HEURES = LocalTime.of(8, 0);

    private MatierePremiereModel matiere() {
        MatierePremiereModel matiere = new MatierePremiereModel("Lait", 100.0, 2);
        matiere.ajouterSortieIdeale(1, 60.0, "Yaourt");
        matiere.ajouterSortieIdeale(2, 20.0, "Crème");
        return matiere;
    }

    private ValidateurProduction.Resultat valider(String entree, String... sorties) {
        return ValidateurProduction.valider(
                AUJOURD_HUI, HUIT_HEURES, entree, List.of(sorties), matiere());
    }

    // ------------------------------------------------ conservation de la matiere

    @Test
    @DisplayName("Le total des sorties ne peut pas dépasser l'entrée")
    void totalDesSortiesLimiteParLEntree() {
        // Chaque sortie passe le controle individuel, mais leur somme est impossible
        ValidateurProduction.Resultat resultat = valider("100", "60", "60");

        assertFalse(resultat.estAcceptee());
        assertTrue(resultat.getRefus().contains("total"), resultat.getRefus());
        assertTrue(resultat.getRefus().contains("120"), "le total fautif doit etre indique");
    }

    @Test
    @DisplayName("Des pertes de transformation restent acceptées")
    void pertesAcceptees() {
        ValidateurProduction.Resultat resultat = valider("100", "60", "20");

        assertTrue(resultat.estAcceptee(), resultat.getRefus());
        assertEquals(80.0, resultat.getTotalSorties());
    }

    @Test
    @DisplayName("Un rendement parfait, sans aucune perte, est accepté")
    void rendementIntegralAccepte() {
        assertTrue(valider("100", "70", "30").estAcceptee());
    }

    @Test
    @DisplayName("Une sortie seule ne peut pas dépasser l'entrée")
    void sortieIndividuelleLimitee() {
        ValidateurProduction.Resultat resultat = valider("100", "150", "0");

        assertFalse(resultat.estAcceptee());
        assertTrue(resultat.getRefus().contains("sortie 1"), resultat.getRefus());
    }

    // ---------------------------------------------------------------- quantites

    @Test
    @DisplayName("L'entrée doit être un nombre strictement positif")
    void entreeObligatoirementPositive() {
        assertFalse(valider("", "50", "20").estAcceptee());
        assertFalse(valider("0", "0", "0").estAcceptee());
        assertFalse(valider("-10", "0", "0").estAcceptee());
        assertFalse(valider("beaucoup", "50", "20").estAcceptee());
    }

    @Test
    @DisplayName("Une sortie négative ou non numérique est refusée")
    void sortiesInvalidesRefusees() {
        assertFalse(valider("100", "-5", "20").estAcceptee());
        assertFalse(valider("100", "cinquante", "20").estAcceptee());
    }

    @Test
    @DisplayName("Toutes les sorties doivent être renseignées")
    void sortiesToutesObligatoires() {
        ValidateurProduction.Resultat resultat = valider("100", "60", "");

        assertFalse(resultat.estAcceptee());
        assertTrue(resultat.getRefus().contains("sortie 2"), resultat.getRefus());
    }

    @Test
    @DisplayName("La virgule décimale du clavier français est acceptée")
    void virguleDecimaleAcceptee() {
        ValidateurProduction.Resultat resultat = valider("100,5", "60,25", "20");

        assertTrue(resultat.estAcceptee(), resultat.getRefus());
        assertEquals(100.5, resultat.getEntree());
        assertEquals(80.25, resultat.getTotalSorties());
    }

    // -------------------------------------------------------------------- dates

    @Test
    @DisplayName("Une date dans le futur est refusée")
    void dateFutureRefusee() {
        ValidateurProduction.Resultat resultat = ValidateurProduction.valider(
                AUJOURD_HUI.plusDays(1), HUIT_HEURES, "100", List.of("60", "20"), matiere());

        assertFalse(resultat.estAcceptee());
        assertTrue(resultat.getRefus().contains("futur"));
    }

    @Test
    @DisplayName("Une date de plus d'un an est refusée")
    void dateTropAncienneRefusee() {
        ValidateurProduction.Resultat resultat = ValidateurProduction.valider(
                AUJOURD_HUI.minusYears(1).minusDays(1), HUIT_HEURES, "100",
                List.of("60", "20"), matiere());

        assertFalse(resultat.estAcceptee());
        assertTrue(resultat.getRefus().contains("ancienne"));
    }

    @Test
    @DisplayName("La date et l'heure sont obligatoires")
    void dateEtHeureObligatoires() {
        assertFalse(ValidateurProduction.valider(
                null, HUIT_HEURES, "100", List.of("60", "20"), matiere()).estAcceptee());
        assertFalse(ValidateurProduction.valider(
                AUJOURD_HUI, null, "100", List.of("60", "20"), matiere()).estAcceptee());
    }

    // ----------------------------------------------------------- avertissements

    @Test
    @DisplayName("Une entrée très supérieure à l'idéal avertit sans bloquer")
    void entreeEloigneeDeLIdealAvertitSansBloquer() {
        // Ideal a 100 kg, saisie a 300 kg
        ValidateurProduction.Resultat resultat = valider("300", "150", "100");

        assertTrue(resultat.estAcceptee(), "l'avertissement ne doit pas empecher la saisie");
        assertFalse(resultat.getAvertissements().isEmpty());
        assertTrue(resultat.getAvertissements().get(0).contains("éloignée"));
    }

    @Test
    @DisplayName("Une production sans aucune sortie avertit sans bloquer")
    void aucuneSortieAvertitSansBloquer() {
        ValidateurProduction.Resultat resultat = valider("100", "0", "0");

        assertTrue(resultat.estAcceptee());
        assertTrue(resultat.getAvertissements().stream()
                .anyMatch(message -> message.contains("Aucune sortie")));
    }

    @Test
    @DisplayName("Une saisie normale ne génère aucun avertissement")
    void saisieNormaleSansAvertissement() {
        assertTrue(valider("100", "60", "20").getAvertissements().isEmpty());
    }

    @Test
    @DisplayName("Sans matière de référence, la validation reste possible")
    void sansMatiereDeReference() {
        ValidateurProduction.Resultat resultat = ValidateurProduction.valider(
                AUJOURD_HUI, HUIT_HEURES, "100", List.of("60", "20"), null);

        assertTrue(resultat.estAcceptee(), resultat.getRefus());
        assertTrue(resultat.getAvertissements().isEmpty());
    }

    @Test
    @DisplayName("Les arrondis ne font pas échouer une saisie juste")
    void toleranceAuxArrondis() {
        // 33.333 x 3 = 99.999, et un total a l'euro pres ne doit pas etre refuse
        assertTrue(valider("100", "33.334", "66.667").estAcceptee(),
                "un depassement de 1 gramme ne doit pas bloquer la saisie");
    }
}
