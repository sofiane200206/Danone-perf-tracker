package interfaces;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Demarre le moteur graphique JavaFX une seule fois pour tous les tests
 * d'interface, et fournit de quoi executer du code sur le fil d'affichage
 * (toute manipulation de composant doit s'y faire).
 */
public abstract class BaseTestJavaFx {

    private static boolean demarre = false;

    protected static synchronized void demarrerJavaFx() throws InterruptedException {
        if (demarre) {
            return;
        }
        CountDownLatch attente = new CountDownLatch(1);
        try {
            Platform.startup(attente::countDown);
        } catch (IllegalStateException dejaDemarre) {
            // Un autre test a deja lance le moteur
            attente.countDown();
        }
        if (!attente.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Le moteur JavaFX n'a pas demarre");
        }
        Platform.setImplicitExit(false);
        demarre = true;
    }

    /** Execute une action sur le fil JavaFX et en renvoie le resultat. */
    protected static <T> T surFilJavaFx(Callable<T> action) throws Exception {
        FutureTask<T> tache = new FutureTask<>(action);
        if (Platform.isFxApplicationThread()) {
            tache.run();
        } else {
            Platform.runLater(tache);
        }
        return tache.get(30, TimeUnit.SECONDS);
    }

    protected static void surFilJavaFx(Runnable action) throws Exception {
        surFilJavaFx(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Compte les composants d'un type donne dans toute la vue.
     *
     * On parcourt l'arbre a la main plutot que par selecteur CSS : les
     * conteneurs de disposition (FlowPane, VBox...) n'ont pas de classe de
     * style par defaut, contrairement aux controles.
     */
    protected static int compterComposants(javafx.scene.Node racine, Class<?> type) {
        int total = type.isInstance(racine) ? 1 : 0;

        if (racine instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node enfant : parent.getChildrenUnmodifiable()) {
                total += compterComposants(enfant, type);
            }
        }
        return total;
    }
}
