package com.sofiane.performance;

/**
 * Point d'entree du jar distribuable.
 *
 * JavaFX refuse de demarrer lorsque la classe principale herite d'Application
 * et que les bibliotheques viennent du classpath plutot que du module-path.
 * Cette classe intermediaire, qui n'en herite pas, contourne ce controle.
 */
public class Lanceur {

    public static void main(String[] args) {
        Main.main(args);
    }
}
