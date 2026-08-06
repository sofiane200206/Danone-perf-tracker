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
- **Comptes protégés par mot de passe**, avec deux rôles : **Administrateur** (accès
  complet) et **Utilisateur** (accès restreint)
- **Sauvegarde automatique** de la base à chaque démarrage (10 copies conservées)
- Persistance locale **SQLite** (base créée automatiquement au premier lancement)

## Sécurité et données

Les mots de passe ne sont jamais stockés : seule une empreinte PBKDF2-HMAC-SHA256
(210 000 itérations) est conservée, avec un sel aléatoire propre à chaque compte.
La vérification se fait en temps constant, et un échec de connexion renvoie le même
message que l'identifiant existe ou non.

Aucun compte par défaut n'est livré. Au premier démarrage, l'application demande la
création du compte administrateur : un mot de passe livré en dur serait connu de tous
et rarement changé.

Avant chaque ouverture de la base, une copie horodatée est déposée dans `sauvegardes/`
et les 10 plus récentes sont conservées. Un échec de sauvegarde est journalisé mais
n'empêche jamais l'application de démarrer.

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

## Distribuer l'application

```bash
construire-application.bat
```

Le script compile, lance les tests, puis produit deux livrables dans `target/` :

| Livrable | Pour qui | Prérequis |
|----------|----------|-----------|
| `application/PerformanceTracker/` | **Postes de production** | aucun — Java est embarqué |
| `PerformanceTracker-1.0-SNAPSHOT-autonome.jar` | Dépannage, test rapide | Java 17+ installé |

Pour déployer : copier le dossier `PerformanceTracker/` sur le poste et lancer
`PerformanceTracker.exe`. Au premier démarrage, l'application demande la création
du compte administrateur, qui pourra ensuite créer les comptes des opérateurs.

> **Où placer le dossier :** la base de données et les sauvegardes sont créées à
> côté de l'exécutable. Installez-le dans un emplacement où l'utilisateur a le droit
> d'écrire (`C:\PerformanceTracker`, un disque partagé…) et **non** dans
> `C:\Program Files`, qui est protégé en écriture. Pour choisir un autre
> emplacement, passer `-Dperformancetracker.db.url=jdbc:sqlite:CHEMIN` au lancement.

> **Sauvegardes :** l'application conserve les 10 dernières copies dans
> `sauvegardes/`, à côté de la base. Ces copies vivent sur le même disque : pour une
> vraie protection, sauvegardez ce dossier ailleurs (serveur, disque externe).

## Lancer le projet

**Prérequis :** JDK 17+ (développé avec le JDK 21).

### Avec Maven (le plus simple)

Le `pom.xml` déclare toutes les dépendances (JavaFX, sqlite-jdbc, Apache POI) et
le plugin `javafx-maven-plugin` est déjà configuré sur la classe principale :

```bash
mvn clean javafx:run
```

### Lancer les tests

```bash
mvn test
```

74 tests JUnit 5 dans `test/` couvrent le cœur métier (règle de comptabilisation,
calcul de performance, agrégation statistique), la persistance, la couche service
(productions et matières premières), l'authentification et la sauvegarde
automatique.

Les tests de persistance tournent sur une base SQLite jetable
(`target/test-tracker.db`), jamais sur `production_tracker.db`. Le chemin est
surchargeable via la propriété système `performancetracker.db.url`.

### Avec IntelliJ IDEA

1. Ouvrir le projet, vérifier le chemin du SDK JavaFX dans
   *File → Project Structure → Libraries*
2. Ajouter les options VM au lancement de `Main` :
   ```
   --module-path "chemin/vers/javafx-sdk-21/lib" --add-modules javafx.controls,javafx.fxml
   ```
3. Lancer `com.sofiane.performance.Main`

Dans les deux cas, la base `production_tracker.db` est créée automatiquement à la
racine du projet au premier lancement.

> **Note :** le projet a été développé avec les bibliothèques déclarées dans IntelliJ,
> en parallèle du `pom.xml`. Les deux configurations coexistent et les versions ne sont
> pas parfaitement alignées (ex. `sqlite-jdbc` 3.43 dans le pom, 3.45 côté IntelliJ) —
> unifier sur Maven fait partie des améliorations prévues.

## Pistes d'amélioration

- Étendre la couverture de tests à l'interface et aux services restants
- Gestion des comptes depuis l'interface (créer un opérateur, changer son mot de passe)
- Unifier la gestion des dépendances sur Maven (aujourd'hui dupliquée avec IntelliJ)
- Nettoyage du code hérité de la première version (`ModeleIdeal`, `Production`)
