# Performance Tracker

Application de bureau (JavaFX) de suivi de performance d'une ligne de production industrielle.
Elle compare la production réelle d'une usine à un **modèle idéal** défini par matière première,
et calcule les gains ou pertes de rendement, jour par jour et sur une période.

## Le principe

Pour chaque matière première, on définit un modèle idéal : « avec X kg en entrée,
la ligne devrait produire Y kg en sortie ». L'application met cet idéal à l'échelle
de la quantité réellement engagée, puis mesure l'écart :

```
ratio              = entrée réelle / entrée idéale
sorties attendues  = ratio × total des sorties idéales
performance (%)    = sorties réelles / sorties attendues × 100
```

- **100 %** : rendement conforme au modèle
- **< 100 %** : perte (gaspillage, pertes de transformation…)
- **> 100 %** : rendement supérieur au modèle

## Fonctionnalités

- Création de matières premières avec un **nombre variable de sorties** (produits finis)
- Saisie des productions réelles (date, heure, entrée, sorties)
- Performance calculée **par production, par journée et par période**
- Statistiques : moyenne, meilleur jour, pire jour, performance globale
- Filtrage par période (dates de début/fin)
- **Export Excel** des productions et des statistiques (Apache POI)
- Deux rôles à la connexion : **Administrateur** (accès complet) et **Utilisateur** (accès restreint)
- Persistance locale **SQLite** (base créée automatiquement au premier lancement)

## Stack technique

| Couche | Technologie |
|--------|-------------|
| Interface | JavaFX 21 + FXML |
| Persistance | SQLite (driver `sqlite-jdbc`), requêtes préparées, transactions |
| Export | Apache POI (xlsx) |
| Logs | `java.util.logging` + SLF4J |

## Architecture

Le code suit une architecture en couches (MVC + DAO + services) :

```
src/
├── model/        Objets métier : MatierePremiereModel, ProductionModel,
│                 JourneeProduction (calcul de performance)
├── dao/          Accès SQLite : DatabaseManager, ProductionDAO, MatierePremiereDAO
├── service/      Logique applicative : ProductionService, StatistiquesService,
│                 ExcelExportService, SessionManager
├── controller/   Contrôleurs JavaFX : LoginController, TrackerController
├── components/   Composants UI réutilisables (HourMinuteField)
└── resources/    Vues FXML (login.fxml, tracker.fxml)
```

## Lancer le projet

Projet IntelliJ IDEA (pas de Maven/Gradle).

**Prérequis :**
- JDK 17+ (développé avec le JDK 21)
- [JavaFX SDK 21](https://gluonhq.com/products/javafx/) installé localement
- Jars fournis/attendus dans les bibliothèques du projet : `sqlite-jdbc`, `slf4j-api`,
  `slf4j-simple`, Apache POI

**Étapes :**
1. Ouvrir le projet dans IntelliJ IDEA
2. Vérifier le chemin du SDK JavaFX dans *File → Project Structure → Libraries*
3. Ajouter les options VM au lancement de `Main` :
   ```
   --module-path "chemin/vers/javafx-sdk-21/lib" --add-modules javafx.controls,javafx.fxml
   ```
4. Lancer `com.sofiane.performance.Main` — la base `production_tracker.db` est créée
   automatiquement à la racine du projet

## Pistes d'amélioration

- Tests unitaires sur le calcul de performance (cœur métier)
- Authentification réelle (le login actuel est un sélecteur de rôle)
- Migration vers Maven/Gradle pour la gestion des dépendances
- Nettoyage du code hérité de la première version (`ModeleIdeal`, `Production`)
