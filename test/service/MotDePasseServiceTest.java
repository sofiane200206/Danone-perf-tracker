package service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotDePasseServiceTest {

    @Test
    @DisplayName("Un mot de passe correct est reconnu")
    void motDePasseCorrectReconnu() {
        String sel = MotDePasseService.genererSel();
        String empreinte = MotDePasseService.hacher("MonMotDePasse1".toCharArray(), sel);

        assertTrue(MotDePasseService.verifier("MonMotDePasse1".toCharArray(), sel, empreinte));
    }

    @Test
    @DisplayName("Un mot de passe faux est rejete")
    void motDePasseFauxRejete() {
        String sel = MotDePasseService.genererSel();
        String empreinte = MotDePasseService.hacher("MonMotDePasse1".toCharArray(), sel);

        assertFalse(MotDePasseService.verifier("MonMotDePasse2".toCharArray(), sel, empreinte));
        assertFalse(MotDePasseService.verifier("monmotdepasse1".toCharArray(), sel, empreinte),
                "la casse compte");
    }

    @Test
    @DisplayName("Le mot de passe n'apparait jamais dans l'empreinte")
    void empreinteNeContientPasLeMotDePasse() {
        String sel = MotDePasseService.genererSel();
        String empreinte = MotDePasseService.hacher("MonMotDePasse1".toCharArray(), sel);

        assertFalse(empreinte.contains("MonMotDePasse1"));
        assertNotEquals("MonMotDePasse1", empreinte);
    }

    @Test
    @DisplayName("Deux comptes avec le meme mot de passe ont des empreintes differentes")
    void selRendLesEmpreintesUniques() {
        String sel1 = MotDePasseService.genererSel();
        String sel2 = MotDePasseService.genererSel();

        assertNotEquals(sel1, sel2, "chaque sel doit etre unique");
        assertNotEquals(
                MotDePasseService.hacher("MemeMotDePasse1".toCharArray(), sel1),
                MotDePasseService.hacher("MemeMotDePasse1".toCharArray(), sel2),
                "sans cela, une empreinte cassee compromettrait tous les comptes identiques");
    }

    @Test
    @DisplayName("Le meme couple mot de passe et sel donne toujours la meme empreinte")
    void hachageDeterministe() {
        String sel = MotDePasseService.genererSel();

        assertEquals(
                MotDePasseService.hacher("MonMotDePasse1".toCharArray(), sel),
                MotDePasseService.hacher("MonMotDePasse1".toCharArray(), sel));
    }

    @Test
    @DisplayName("Verifier avec des valeurs absentes renvoie faux sans exception")
    void verificationRobusteAuxValeursAbsentes() {
        String sel = MotDePasseService.genererSel();
        String empreinte = MotDePasseService.hacher("MonMotDePasse1".toCharArray(), sel);

        assertFalse(MotDePasseService.verifier(null, sel, empreinte));
        assertFalse(MotDePasseService.verifier(new char[0], sel, empreinte));
        assertFalse(MotDePasseService.verifier("MonMotDePasse1".toCharArray(), null, empreinte));
        assertFalse(MotDePasseService.verifier("MonMotDePasse1".toCharArray(), sel, null));
    }

    @Test
    @DisplayName("Hacher un mot de passe vide est refuse")
    void hachageMotDePasseVideRefuse() {
        String sel = MotDePasseService.genererSel();

        assertThrows(IllegalArgumentException.class,
                () -> MotDePasseService.hacher(new char[0], sel));
        assertThrows(IllegalArgumentException.class,
                () -> MotDePasseService.hacher(null, sel));
    }

    @Test
    @DisplayName("La politique de mot de passe refuse les cas faibles")
    void politiqueDeRobustesse() {
        assertNotNull(MotDePasseService.motifDeRefus(null));
        assertNotNull(MotDePasseService.motifDeRefus(""));
        assertNotNull(MotDePasseService.motifDeRefus("court1"), "moins de 8 caracteres");
        assertNotNull(MotDePasseService.motifDeRefus("quelettres"), "aucun chiffre");
        assertNotNull(MotDePasseService.motifDeRefus("12345678"), "aucune lettre");

        assertNull(MotDePasseService.motifDeRefus("MotDePasse1"));
    }

    @Test
    @DisplayName("Effacer neutralise le mot de passe en memoire")
    void effacementDuMotDePasse() {
        char[] motDePasse = "MonMotDePasse1".toCharArray();

        MotDePasseService.effacer(motDePasse);

        assertEquals(0, new String(motDePasse).replace("\0", "").length());
        assertDoesNotThrow(() -> MotDePasseService.effacer(null));
    }
}
