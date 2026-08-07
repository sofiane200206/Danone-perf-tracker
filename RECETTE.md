# Recette avant déploiement

Check-list à dérouler avant de laisser des opérateurs saisir des données réelles.
Coche au fur et à mesure. Les points marqués **BLOQUANT** doivent tous passer :
si l'un échoue, ne déploie pas.

---

## A. Préparation (sur ton poste)

- [ ] `mvn test` : les 166 tests passent, `BUILD SUCCESS`
- [ ] `construire-application.bat` se termine sans erreur
- [ ] Le dossier `target/application/PerformanceTracker/` contient bien
      `PerformanceTracker.exe`, `app/` et `runtime/`

---

## B. Validation métier — **BLOQUANT**

C'est le seul test que personne ne peut faire à ta place : vérifier que le
pourcentage affiché correspond à ce que **tu** attends de ta ligne de production.

- [ ] Prends une journée réelle dont tu connais déjà le rendement
- [ ] Crée la matière avec ses quantités idéales réelles
- [ ] Saisis les productions de cette journée
- [ ] **Le pourcentage affiché correspond-il à ton calcul ?**

Rappel de la formule appliquée :

```
ratio             = entrée réelle / entrée idéale
sorties attendues = ratio × total des sorties idéales
performance       = sorties réelles / sorties attendues × 100
```

- [ ] Vérifie un cas simple à la main : entrée idéale 100 kg → 80 kg de sortie.
      Si tu engages 50 kg et produis 40 kg, l'application doit afficher **100 %**
- [ ] Si le résultat ne correspond pas à ta réalité (pertes de démarrage,
      matière recyclée, rendement variable selon le lot), **arrête-toi ici** :
      c'est le modèle de calcul qu'il faut revoir, pas le déploiement

---

## C. Installation sur le poste cible

- [ ] Copie le dossier `PerformanceTracker/` sur le poste
- [ ] **BLOQUANT** — il n'est **pas** dans `C:\Program Files` (protégé en écriture)
- [ ] Double-clic sur `PerformanceTracker.exe` : l'application s'ouvre
- [ ] **BLOQUANT** — elle démarre sur un poste **sans Java installé**
- [ ] `production_tracker.db` apparaît à côté de l'exécutable après le premier lancement
- [ ] Ferme et rouvre : un dossier `sauvegardes/` est créé avec une copie horodatée

---

## D. Comptes et permissions

- [ ] Au tout premier lancement, l'application demande la création du compte
      administrateur (et non un écran de connexion)
- [ ] Un mot de passe trop court ou sans chiffre est refusé avec un message clair
- [ ] Deux mots de passe différents dans les deux champs sont refusés
- [ ] Une fois le compte créé, fermer et rouvrir affiche l'écran de **connexion**
- [ ] **BLOQUANT** — un mauvais mot de passe est refusé
- [ ] **BLOQUANT** — un identifiant inexistant donne le **même** message qu'un
      mauvais mot de passe (il ne faut pas pouvoir deviner quels comptes existent)
- [ ] Bouton « 👥 Comptes » : crée un compte opérateur avec le rôle Utilisateur
- [ ] Déconnecte-toi, reconnecte-toi avec ce compte opérateur

Avec le compte **opérateur** connecté :

- [ ] **BLOQUANT** — le bouton « Reset BDD » n'est **pas** visible
- [ ] **BLOQUANT** — le bouton « Supprimer » une matière n'est **pas** visible
- [ ] Le formulaire de création de matière est grisé, marqué « Réservé Admin »
- [ ] Le bouton « 👥 Comptes » n'est pas visible
- [ ] Le bouton « 🔑 Mot de passe » est visible et permet de changer le sien
- [ ] Change le mot de passe de l'opérateur, déconnecte-toi, reconnecte-toi avec
      le nouveau : ça fonctionne, l'ancien ne fonctionne plus

De retour en **administrateur** :

- [ ] Réinitialise le mot de passe de l'opérateur depuis « 👥 Comptes »
- [ ] Désactive le compte opérateur : il ne peut plus se connecter
- [ ] Réactive-le : il se reconnecte
- [ ] **BLOQUANT** — tu ne peux pas désactiver ton propre compte
- [ ] **BLOQUANT** — tu ne peux pas désactiver le dernier administrateur actif

---

## E. Saisie quotidienne

- [ ] Crée une matière première avec 2 sorties, puis une avec 3 sorties
- [ ] Sélectionne une matière : le formulaire se remplit avec ses valeurs
- [ ] « ➕ Ajouter une Production » : une ligne de saisie apparaît avec un champ
      par sortie
- [ ] Remplis date, heure, entrée et sorties, puis « 💾 Sauvegarder » :
      message vert de confirmation
- [ ] Ferme et rouvre l'application : la production est toujours là
- [ ] Modifie une production existante et sauvegarde : la nouvelle valeur est
      conservée après redémarrage
- [ ] Supprime une production : elle disparaît et ne revient pas au redémarrage

---

## F. Contrôles de saisie

Chacun doit afficher un message d'erreur **et** empêcher l'enregistrement.

- [ ] Entrée vide
- [ ] Entrée à `0` ou négative
- [ ] Une lettre dans un champ numérique
- [ ] Une sortie laissée vide
- [ ] Une sortie négative
- [ ] **BLOQUANT** — une sortie seule supérieure à l'entrée
      (ex. entrée 100, sortie 1 = 150)
- [ ] **BLOQUANT** — le **total** des sorties supérieur à l'entrée
      (ex. entrée 100, sorties 60 et 60 → doit être refusé)
- [ ] Une date dans le futur
- [ ] Une date de plus d'un an
- [ ] La virgule décimale fonctionne : saisir `100,5` est accepté
- [ ] Un rendement parfait sans perte (entrée 100, sorties 70 + 30) est **accepté**

---

## G. Mesures douteuses, statistiques et export

- [ ] Sur une production enregistrée, clique « ⚠️ Douteuse », saisis un motif
- [ ] La production reste visible mais **sort** des statistiques
      (moyenne, meilleur jour, pire jour changent)
- [ ] Le bouton devient « ✅ Rétablir » ; clique-le : elle revient dans les calculs
- [ ] Les filtres de période (7 jours, semaine, mois, dates personnalisées)
      modifient bien les statistiques affichées
- [ ] « 📊 Exporter vers Excel » produit un fichier qui s'ouvre sans avertissement
- [ ] **BLOQUANT** — dans le fichier, les valeurs sont sous les bons en-têtes,
      et la colonne « Saisi par » contient le nom du compte qui a saisi
- [ ] Une production marquée douteuse **n'apparaît pas** dans l'export

---

## H. Sécurité des données — **BLOQUANT**

- [ ] Après plusieurs lancements, `sauvegardes/` contient plusieurs copies horodatées
- [ ] **Test de restauration** : ferme l'application, renomme `production_tracker.db`
      en `.old`, copie une sauvegarde à sa place en la renommant
      `production_tracker.db`, relance → tes données sont bien là
- [ ] Copie le dossier `sauvegardes/` ailleurs (serveur, disque externe) et vérifie
      que la procédure est claire pour la personne qui s'en occupera
- [ ] **Test de mise à jour** : garde une copie de la base, remplace le dossier de
      l'application par une nouvelle version en conservant `production_tracker.db`
      et `sauvegardes/`, relance → toutes les données sont conservées

---

## I. Robustesse

- [ ] Redimensionne la fenêtre en petit (environ 800 px de large) : rien n'est
      coupé, les panneaux s'empilent, les boutons passent à la ligne
- [ ] Agrandis en plein écran : le contenu occupe toute la largeur
- [ ] Lance l'application deux fois en même temps : n'utilise **qu'une seule**
      instance à la fois pour éviter les incohérences
- [ ] Saisis une dizaine de productions d'affilée : pas de ralentissement
- [ ] Laisse l'application ouverte plusieurs heures, puis saisis : ça répond encore

---

## J. Décision

- [ ] Tous les points **BLOQUANT** sont passés
- [ ] Tu as expliqué à chaque opérateur : son identifiant, comment saisir,
      et à quoi sert « ⚠️ Douteuse »
- [ ] Quelqu'un est désigné pour copier `sauvegardes/` régulièrement
- [ ] Tu as noté où se trouve la base, au cas où quelqu'un doive la récupérer

**Rappel important :** une installation = une base. Plusieurs personnes partagent
l'application en partageant le **poste**, chacune avec son compte. Ne place jamais
`production_tracker.db` sur un lecteur réseau ou un dossier synchronisé
(OneDrive, SharePoint) pour l'ouvrir depuis plusieurs machines : le fichier
finirait par se corrompre.
