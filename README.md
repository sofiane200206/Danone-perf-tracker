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
  complet) et **Utilisateur** (saisie, consultation et export uniquement — la
  création de matières, leur suppression et la réinitialisation de la base lui sont
  refusées)
- **Traçabilité des saisies** : chaque production retient le compte qui l'a
  enregistrée, y compris dans les exports Excel
- **Signalement des mesures douteuses** : une production peut être marquée comme
  telle avec un motif ; elle reste consultable mais sort des statistiques et des
  exports jusqu'à ce que le signalement soit levé
- **Sauvegarde automatique** de la base à chaque démarrage (10 copies conservées)
- **Interface adaptative** : les panneaux se côtoient sur grand écran et s'empilent
  sur petit, les rangées de boutons passent à la ligne, rien n'est tronqué jusqu'à
  640 px de large
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

> **Une installation = une base.** Plusieurs personnes partagent l'application en
> partageant le **poste**, chacune avec son propre compte : leurs saisies vont dans
> le même fichier `production_tracker.db`. Ne placez jamais ce fichier sur un
> lecteur réseau ou un dossier synchronisé (OneDrive, SharePoint) pour l'ouvrir
> depuis plusieurs machines : SQLite a besoin d'un verrouillage fiable que ces
> supports ne garantissent pas, et le fichier finirait par se corrompre. Pour un
> usage réellement multi-postes en simultané, il faudrait une base serveur.

> **Où placer le dossier :** la base de données et les sauvegardes sont créées à
> côté de l'exécutable. Installez-le dans un emplacement où l'utilisateur a le droit
> d'écrire (`C:\PerformanceTracker`, un disque partagé…) et **non** dans
> `C:\Program Files`, qui est protégé en écriture. Pour choisir un autre
> emplacement, passer `-Dperformancetracker.db.url=jdbc:sqlite:CHEMIN` au lancement.

> **Sauvegardes :** l'application conserve les 10 dernières copies dans
> `sauvegardes/`, à côté de la base. Ces copies vivent sur le même disque : pour une
> vraie protection, sauvegardez ce dossier ailleurs (serveur, disque externe).

### Mettre à jour une installation existante

Remplacez le dossier de l'application en **conservant `production_tracker.db` et
`sauvegardes/`**. Au démarrage, la base est sauvegardée puis mise au niveau du
nouveau schéma automatiquement : les données déjà saisies sont conservées.

Pour faire évoluer le schéma, ajoutez une migration dans
[`SchemaApplicatif`](src/dao/SchemaApplicatif.java) — **sans jamais modifier une
migration déjà livrée**, puisque les bases des utilisateurs l'ont déjà appliquée :

```java
new Migration(2, "Ajout du commentaire de production",
        "ALTER TABLE productions ADD COLUMN commentaire TEXT")
```

Chaque migration s'exécute dans sa propre transaction. Si l'une échoue, elle est
annulée entièrement, les suivantes ne sont pas tentées et le démarrage s'interrompt
avec l'erreur : une base à moitié migrée serait pire qu'une base non migrée.

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

166 tests JUnit 5 dans `test/` couvrent le cœur métier (règle de comptabilisation,
calcul de performance, agrégation statistique), la persistance, les migrations de
schéma, la couche service (productions et matières premières), l'authentification,
la sauvegarde automatique, l'export Excel, l'interface (chargement des vues, liaison FXML, mise en
page adaptative) et les formulaires de gestion des comptes.

Les tests d'interface démarrent le moteur JavaFX et inspectent la disposition
réelle à différentes tailles de fenêtre : ils échouent si un `fx:id` disparaît ou
si le contenu cesse de s'adapter.

Les tests de parcours vont plus loin : ils sélectionnent une matière, ajoutent une
production, remplissent les champs et déclenchent l'enregistrement, puis vérifient
la base. Toute exception non gérée survenue sur le fil d'affichage pendant le
parcours fait échouer le test — JavaFX se contenterait sinon de l'afficher dans la
console.

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
