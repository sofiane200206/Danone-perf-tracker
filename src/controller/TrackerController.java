package controller;

import components.HourMinuteField;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import model.*;
import model.MatierePremiereModel.SortieIdeale;
import model.ProductionModel.SortieReelle;
import service.*;
import filter.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class TrackerController {
    private static final Logger LOGGER = Logger.getLogger(TrackerController.class.getName());

    // Composants FXML
    @FXML private TextField matierePremiereField;
    @FXML private TextField quantiteEntreeIdealeField;
    @FXML private VBox sortiesIdealesContainer;
    @FXML private Spinner<Integer> nombreSortiesSpinner;
    @FXML private Button btnCreerMatiere;
    @FXML private ComboBox<MatierePremiereModel> matierePremiereCombo;
    @FXML private Label labelMeilleurePerf;
    @FXML private Label labelPlusGrossePerte;
    @FXML private Label labelMoyenneGlobale;
    @FXML private VBox joursContainer;
    @FXML private DatePicker dateDebutFiltre;
    @FXML private DatePicker dateFinFiltre;
    @FXML private Button btnAppliquerFiltre;
    @FXML private Button btnVoirTout;
    @FXML private Label labelFiltrageActuel;
    @FXML private Label labelMatiereSelectionnee;
    private static Long compteurIdProduction = 1L;
    private static Long compteurIdMatiere = 1L;
    // Services
    private MatierePremiereService matiereService;
    private ProductionService productionService;
    private FiltrePeriode filtreActuel;
    private MatierePremiereModel matiereActuelle;
    private int compteurProductions = 1;
    private List<ProductionUI> listeProductionUI = new ArrayList<>();
    private List<TextField> champsSortiesIdeales = new ArrayList<>();

    // Composants UI pour les productions
    private class ProductionUI {
        ProductionModel production;
        Label label;
        DatePicker datePicker;
        HourMinuteField timeField;
        TextField entreeReelle;
        List<TextField> sortiesReellesFields;
        Label resultatLabel;
        VBox container;


        ProductionUI(ProductionModel production) {
            this.production = production;
            this.sortiesReellesFields = new ArrayList<>();
            initComponents();
        }

        private void initComponents() {
            label = new Label("Production " + compteurProductions);
            label.setStyle("-fx-font-weight: bold;");

            datePicker = new DatePicker(LocalDate.now());
            timeField = new HourMinuteField();

            entreeReelle = new TextField();
            entreeReelle.setPromptText("Quantité entrée réelle");

            resultatLabel = new Label("⏳ En attente de saisie...");
            resultatLabel.setWrapText(true);

            // Créer les champs de sorties selon la matière première
            creerChampsSorties();

            // Listeners
            setupListeners();
        }

        private void creerChampsSorties() {
            sortiesReellesFields.clear();

            if (matiereActuelle != null && matiereActuelle.getSortiesIdeales() != null) {
                for (SortieIdeale sortieIdeale : matiereActuelle.getSortiesIdeales()) {
                    TextField field = new TextField();
                    field.setPromptText("Sortie " + sortieIdeale.getNumeroSortie() +
                            " (" + sortieIdeale.getNomSortie() + ") - réelle");
                    sortiesReellesFields.add(field);

                    // Listener pour ce champ
                    field.textProperty().addListener((obs, old, val) -> mettreAJourProduction());
                }
            }
        }

        private void setupListeners() {
            datePicker.valueProperty().addListener((obs, old, val) -> {
                if (val != null) {
                    production.setDateProduction(val);
                    mettreAJourProduction();
                }
            });

            entreeReelle.textProperty().addListener((obs, old, val) -> mettreAJourProduction());
        }

        private void mettreAJourProduction() {
            try {
                // Mettre à jour la date
                if (datePicker.getValue() != null) {
                    production.setDateProduction(datePicker.getValue());
                }

                // Mettre à jour l'heure
                try {
                    LocalTime localTime = timeField.getLocalTime();
                    if (localTime != null) {
                        production.setHeureProduction(localTime);
                    } else {
                        production.setHeureProduction(LocalTime.now());
                    }
                } catch (Exception e) {
                    production.setHeureProduction(LocalTime.now());
                }

                // Mettre à jour l'entrée réelle
                if (!entreeReelle.getText().trim().isEmpty()) {
                    double entree = Double.parseDouble(entreeReelle.getText().trim());
                    production.setQuantiteEntreeReelle(entree);
                }

                // Mettre à jour les sorties réelles
                production.getSortiesReelles().clear();
                for (int i = 0; i < sortiesReellesFields.size(); i++) {
                    TextField field = sortiesReellesFields.get(i);
                    if (!field.getText().trim().isEmpty()) {
                        double quantite = Double.parseDouble(field.getText().trim());
                        int numeroSortie = i + 1;
                        production.ajouterSortieReelle(numeroSortie, quantite);
                    }
                }

                // Valider la production
                production.validerProduction();

                // Sauvegarder seulement si les données sont complètes
                if (production.isDonneeComplete()) {
                    try {
                        // Distinguer entre création et mise à jour
                        if (production.getId() == null) {
                            productionService.ajouterProduction(production);
                            resultatLabel.setText("✅ Production créée - ID: " + production.getId());
                        } else {
                            productionService.mettreAJourProduction(production); // ← FIX
                            resultatLabel.setText("✅ Production mise à jour - ID: " + production.getId());
                        }
                        resultatLabel.setTextFill(Color.GREEN);

                    } catch (ServiceException e) {
                        resultatLabel.setText("⚠️ Erreur sauvegarde: " + e.getMessage());
                        resultatLabel.setTextFill(Color.RED);
                        LOGGER.log(Level.WARNING, "Erreur lors de la sauvegarde", e);
                    }
                } else {
                    resultatLabel.setText("⏳ Production incomplète - Manque: " + getChampManquants());
                    resultatLabel.setTextFill(Color.ORANGE);
                }

            } catch (NumberFormatException e) {
                resultatLabel.setText("❌ Erreur : valeurs numériques invalides");
                resultatLabel.setTextFill(Color.RED);
            } catch (Exception e) {
                resultatLabel.setText("❌ Erreur inattendue : " + e.getMessage());
                resultatLabel.setTextFill(Color.RED);
                LOGGER.log(Level.SEVERE, "Erreur lors de la mise à jour de la production", e);
            }
        }

        // Méthode helper pour debug
        private String getChampManquants() {
            List<String> manquants = new ArrayList<>();
            if (production.getDateProduction() == null) manquants.add("date");
            if (production.getHeureProduction() == null) manquants.add("heure");
            if (production.getQuantiteEntreeReelle() <= 0) manquants.add("entrée");
            if (production.getSortiesReelles().isEmpty()) manquants.add("sorties");
            return String.join(", ", manquants);
        }

        VBox getContainer() {
            if (container == null) {
                container = new VBox(8);
                container.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-border-radius: 5; -fx-padding: 10; -fx-background-color: #f9f9f9;");

                HBox heureBox = new HBox(10);
                heureBox.getChildren().addAll(new Label("Heure :"), timeField);

                container.getChildren().addAll(label, datePicker, heureBox, entreeReelle);

                // Ajouter les champs de sorties
                for (TextField field : sortiesReellesFields) {
                    container.getChildren().add(field);
                }

                container.getChildren().add(resultatLabel);

                // Bouton de suppression
                Button btnSupprimer = new Button("🗑️ Supprimer");
                btnSupprimer.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white;");
                btnSupprimer.setOnAction(e -> supprimerProduction());
                container.getChildren().add(btnSupprimer);
            }
            return container;
        }

        private void supprimerProduction() {
            try {
                // 1. D'abord, vérifier si la production a un ID (donc existe en base)
                if (production.getId() != null) {
                    // Supprimer d'abord de la base de données
                    productionService.supprimerProduction(production);
                    LOGGER.info("Production ID " + production.getId() + " supprimée de la base de données");
                } else {
                    LOGGER.info("Production sans ID, suppression uniquement de l'interface");
                }

                // 2. Supprimer de la liste UI et de l'interface (dans tous les cas)
                listeProductionUI.remove(this);
                joursContainer.getChildren().remove(container);

                // 3. Recalculer les performances après suppression
                calculerPerformances();

                // 4. Mettre à jour le compteur si nécessaire
                compteurProductions = Math.max(1, listeProductionUI.size() + 1);

                // 5. Log de confirmation
                LOGGER.info("Production supprimée avec succès. Reste " + listeProductionUI.size() + " productions");

            } catch (ServiceException e) {
                LOGGER.log(Level.WARNING, "Erreur lors de la suppression en base : " + e.getMessage(), e);

                // Même en cas d'erreur de base, on supprime de l'interface
                listeProductionUI.remove(this);
                joursContainer.getChildren().remove(container);
                calculerPerformances();

                // Afficher l'erreur à l'utilisateur
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Avertissement");
                alert.setHeaderText("Suppression partielle");
                alert.setContentText("La production a été supprimée de l'interface mais une erreur s'est produite lors de la suppression en base : " + e.getMessage());
                alert.showAndWait();

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erreur inattendue lors de la suppression", e);

                // En cas d'erreur grave, quand même essayer de nettoyer l'interface
                try {
                    listeProductionUI.remove(this);
                    joursContainer.getChildren().remove(container);
                } catch (Exception cleanupError) {
                    LOGGER.log(Level.SEVERE, "Impossible de nettoyer l'interface", cleanupError);
                }

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Erreur");
                alert.setHeaderText("Erreur de suppression");
                alert.setContentText("Une erreur grave s'est produite : " + e.getMessage());
                alert.showAndWait();
            }
        }

        public void mettreAJourAffichageStatut() {
            if (production.getId() != null) {
                if (production.isValide()) {
                    resultatLabel.setText("✅ Production chargée - ID: " + production.getId() +
                            " (Statut: " + production.getStatut() + ")");
                    resultatLabel.setTextFill(Color.GREEN);
                } else {
                    resultatLabel.setText("⚠️ Production incomplète - ID: " + production.getId());
                    resultatLabel.setTextFill(Color.ORANGE);
                }
            } else {
                resultatLabel.setText("⏳ Nouvelle production - en attente de saisie...");
                resultatLabel.setTextFill(Color.GRAY);
            }
        }
        // Méthode de test temporaire

        public void actualiserAffichage() {
            // Recréer les champs si la matière première a changé
            if (container != null) {
                // Sauvegarder les valeurs actuelles
                String entreeText = entreeReelle.getText();
                List<String> sortiesTexts = new ArrayList<>();
                for (TextField field : sortiesReellesFields) {
                    sortiesTexts.add(field.getText());
                }

                // Recréer les champs
                creerChampsSorties();

                // Reconstruire le container
                container.getChildren().clear();

                Label titleLabel = new Label("Production " + (listeProductionUI.indexOf(this) + 1));
                titleLabel.setStyle("-fx-font-weight: bold;");

                HBox heureBox = new HBox(10);
                heureBox.getChildren().addAll(new Label("Heure :"), timeField);

                container.getChildren().addAll(titleLabel, datePicker, heureBox, entreeReelle);

                for (TextField field : sortiesReellesFields) {
                    container.getChildren().add(field);
                }

                container.getChildren().add(resultatLabel);

                Button btnSupprimer = new Button("🗑️ Supprimer");
                btnSupprimer.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white;");
                btnSupprimer.setOnAction(e -> supprimerProduction());
                container.getChildren().add(btnSupprimer);

                // Restaurer les valeurs
                entreeReelle.setText(entreeText);
                for (int i = 0; i < Math.min(sortiesTexts.size(), sortiesReellesFields.size()); i++) {
                    sortiesReellesFields.get(i).setText(sortiesTexts.get(i));
                }

                // Re-setup des listeners
                setupListeners();
            }
        }
    }

    @FXML
    public void initialize() {
        try {
            matiereService = new MatierePremiereService();
            productionService = new ProductionService();
            filtreActuel = FiltrePeriode.creerFiltreTout();

            // Configuration du spinner pour le nombre de sorties
            nombreSortiesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 2));
            nombreSortiesSpinner.valueProperty().addListener((obs, old, val) -> creerChampsSortiesIdeales(val));

            // Configuration des boutons
            btnCreerMatiere.setOnAction(e -> creerNouvelleMatiere());
            btnAppliquerFiltre.setOnAction(e -> appliquerFiltre());
            btnVoirTout.setOnAction(e -> voirTout());

            // Configuration du ComboBox des matières premières
            matierePremiereCombo.setConverter(new javafx.util.StringConverter<MatierePremiereModel>() {
                @Override
                public String toString(MatierePremiereModel matiere) {
                    return matiere != null ? matiere.getNom() : "";
                }

                @Override
                public MatierePremiereModel fromString(String string) {
                    return null; // Pas utilisé
                }
            });

            matierePremiereCombo.valueProperty().addListener((obs, old, val) -> {
                if (val != null) {
                    selectionnerMatiere(val);
                }
            });

            // Dates par défaut
            dateDebutFiltre.setValue(LocalDate.now().minusDays(7));
            dateFinFiltre.setValue(LocalDate.now());

            // Initialisation
            creerChampsSortiesIdeales(2);
            chargerMatieresPremieres();
            mettreAJourLabelFiltre();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation du contrôleur", e);
            afficherErreur("Erreur d'initialisation", "Impossible d'initialiser l'application : " + e.getMessage());
        }
    }

    private void creerChampsSortiesIdeales(int nombre) {
        sortiesIdealesContainer.getChildren().clear();
        champsSortiesIdeales.clear();

        for (int i = 1; i <= nombre; i++) {
            HBox hbox = new HBox(10);
            Label label = new Label("Sortie " + i + " idéale :");
            TextField field = new TextField();
            field.setPromptText("Quantité idéale sortie " + i);

            TextField nomField = new TextField();
            nomField.setPromptText("Nom sortie " + i);
            nomField.setText("Sortie " + i); // Nom par défaut

            champsSortiesIdeales.add(field);
            champsSortiesIdeales.add(nomField);

            hbox.getChildren().addAll(label, field, new Label("Nom:"), nomField);
            sortiesIdealesContainer.getChildren().add(hbox);
        }
    }

    @FXML
    public void creerNouvelleMatiere() {
        try {
            String nom = matierePremiereField.getText().trim();
            if (nom.isEmpty()) {
                afficherErreur("Nom manquant", "Veuillez saisir le nom de la matière première.");
                return;
            }

            String entreeText = quantiteEntreeIdealeField.getText().trim();
            if (entreeText.isEmpty()) {
                afficherErreur("Quantité manquante", "Veuillez saisir la quantité d'entrée idéale.");
                return;
            }

            double quantiteEntreeIdeale = Double.parseDouble(entreeText);
            int nombreSorties = nombreSortiesSpinner.getValue();

            // ✅ CORRECTION : Créer d'abord TOUTES les sorties idéales
            List<SortieIdeale> sortiesIdeales = new ArrayList<>();
            for (int i = 0; i < nombreSorties; i++) {
                String quantiteText = champsSortiesIdeales.get(i * 2).getText().trim();
                String nomSortie = champsSortiesIdeales.get(i * 2 + 1).getText().trim();

                if (quantiteText.isEmpty()) {
                    afficherErreur("Sortie manquante", "Veuillez saisir la quantité pour la sortie " + (i + 1));
                    return;
                }

                double quantite = Double.parseDouble(quantiteText);
                sortiesIdeales.add(new SortieIdeale(i + 1, quantite, nomSortie.isEmpty() ? "Sortie " + (i + 1) : nomSortie));
            }

            // ✅ Maintenant créer la matière première avec TOUTES les sorties
            MatierePremiereModel matiere = matiereService.creerMatierePremiereComplete(
                    nom, quantiteEntreeIdeale, nombreSorties, sortiesIdeales);

            // ✅ S'assurer que la matière a un ID
            if (matiere.getId() == null) {
                matiere.setId(compteurIdMatiere++);
            }

            // Actualiser la liste et sélectionner la nouvelle matière
            chargerMatieresPremieres();
            matierePremiereCombo.setValue(matiere);

            afficherInfo("Succès", "Matière première créée avec succès !");

            // Vider les champs
            viderChampsSaisie();

        } catch (NumberFormatException e) {
            afficherErreur("Erreur de saisie", "Veuillez entrer des valeurs numériques valides.");
        } catch (ServiceException e) {
            afficherErreur("Erreur de création", e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la création de la matière première", e);
            afficherErreur("Erreur", "Une erreur inattendue s'est produite : " + e.getMessage());
        }
    }

    private void chargerMatieresPremieres() {
        try {
            List<MatierePremiereModel> matieres = matiereService.listerMatieresActives();
            matierePremiereCombo.getItems().clear();
            matierePremiereCombo.getItems().addAll(matieres);
        } catch (ServiceException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des matières premières", e);
            afficherErreur("Erreur de chargement", "Impossible de charger les matières premières : " + e.getMessage());
        }
    }

    private void selectionnerMatiere(MatierePremiereModel matiere) {
        matiereActuelle = matiere;
        productionService.setMatierePremiereModel(matiere);

        labelMatiereSelectionnee.setText("📦 Matière sélectionnée : " + matiere.getNom());
        chargerProductionsExistantes();
        // Actualiser toutes les productions existantes
        for (ProductionUI productionUI : listeProductionUI) {
            productionUI.production.setMatierePremiereId(matiere.getId());
            productionUI.actualiserAffichage();
        }

        calculerPerformances();
    }
    private void chargerProductionsExistantes() {
        try {
            // VIDER TOUT D'ABORD (fix du bug principal)
            viderAffichageProductions();

            // Récupérer les productions de cette matière première
            List<ProductionModel> productionsBDD = productionService.getProductions();

            LOGGER.info("Chargement de " + productionsBDD.size() + " productions depuis la base");

            // Créer les UI pour chaque production existante
            for (ProductionModel production : productionsBDD) {
                creerUIProduction(production);
            }

            // Mettre à jour le compteur
            compteurProductions = listeProductionUI.size() + 1;

            // Ajouter le bouton calculer si nécessaire
            if (listeProductionUI.size() >= 1 && !boutonCalculerExiste()) {
                ajouterBoutonCalculer();
            }

            LOGGER.info("Interface mise à jour avec " + listeProductionUI.size() + " productions");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des productions existantes", e);
            afficherErreur("Erreur de chargement",
                    "Impossible de charger les productions existantes : " + e.getMessage());
        }
    }
    private void creerUIProduction(ProductionModel production) {
        ProductionUI productionUI = new ProductionUI(production);

        // Remplir les champs avec les données existantes
        if (production.getDateProduction() != null) {
            productionUI.datePicker.setValue(production.getDateProduction());
        }

        if (production.getHeureProduction() != null) {
            productionUI.timeField.setTime(production.getHeureProduction());
        }

        if (production.getQuantiteEntreeReelle() > 0) {
            productionUI.entreeReelle.setText(String.valueOf(production.getQuantiteEntreeReelle()));
        }

        // Remplir les sorties réelles
        if (production.getSortiesReelles() != null) {
            for (ProductionModel.SortieReelle sortieReelle : production.getSortiesReelles()) {
                int index = sortieReelle.getNumeroSortie() - 1;
                if (index >= 0 && index < productionUI.sortiesReellesFields.size()) {
                    productionUI.sortiesReellesFields.get(index)
                            .setText(String.valueOf(sortieReelle.getQuantiteReelle()));
                }
            }
        }

        // Mettre à jour le label avec le statut
        productionUI.mettreAJourAffichageStatut();

        // Ajouter à la liste et à l'interface
        listeProductionUI.add(productionUI);

        // Insérer avant le bouton d'ajout (qui est toujours en dernier)
        int indexInsertion = joursContainer.getChildren().size() - 1;
        if (indexInsertion < 0) indexInsertion = 0;

        joursContainer.getChildren().add(indexInsertion, productionUI.getContainer());
    }
    private void viderAffichageProductions() {
        // Conserver seulement le bouton d'ajout production qui doit rester en dernier
        if (!joursContainer.getChildren().isEmpty()) {
            // Sauvegarder le dernier élément (bouton d'ajout) et le bouton calculer si il existe
            int taille = joursContainer.getChildren().size();
            var dernierElement = joursContainer.getChildren().get(taille - 1);

            boolean avaitBoutonCalculer = boutonCalculerExiste();
            var boutonCalculer = avaitBoutonCalculer ?
                    joursContainer.getChildren().get(taille - 2) : null;

            // Vider tout
            joursContainer.getChildren().clear();
            listeProductionUI.clear();

            // Remettre les boutons
            if (avaitBoutonCalculer && boutonCalculer != null) {
                joursContainer.getChildren().add(boutonCalculer);
            }
            joursContainer.getChildren().add(dernierElement);
        }
    }

    @FXML
    public void ajouterProduction() {
        if (matiereActuelle == null) {
            afficherErreur("Matière non sélectionnée", "Veuillez d'abord sélectionner ou créer une matière première.");
            return;
        }

        try {
            ProductionModel production = new ProductionModel();
            // Ne pas assigner d'ID ici - il sera généré par la base de données
            production.setMatierePremiereId(matiereActuelle.getId());
            production.setDateProduction(LocalDate.now());

            ProductionUI productionUI = new ProductionUI(production);
            listeProductionUI.add(productionUI);

            // Insérer avant les boutons (ajout et calculer)
            int indexInsertion = joursContainer.getChildren().size() - 1;
            if (boutonCalculerExiste()) {
                indexInsertion = joursContainer.getChildren().size() - 2;
            }

            joursContainer.getChildren().add(indexInsertion, productionUI.getContainer());

            compteurProductions++;

            if (compteurProductions == 2 && !boutonCalculerExiste()) {
                ajouterBoutonCalculer();
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'ajout de production", e);
            afficherErreur("Erreur", "Impossible d'ajouter la production : " + e.getMessage());
        }
    }
    private boolean boutonCalculerExiste() {
        return joursContainer.getChildren().stream()
                .anyMatch(node -> node instanceof Button &&
                        ((Button) node).getText().contains("Calculer les performances"));
    }

    private void ajouterBoutonCalculer() {
        Button btnCalculer = new Button("🔄 Calculer les performances");
        btnCalculer.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnCalculer.setOnAction(e -> calculerPerformances());
        joursContainer.getChildren().add(btnCalculer);
    }

    @FXML
    public void appliquerFiltre() {
        LocalDate debut = dateDebutFiltre.getValue();
        LocalDate fin = dateFinFiltre.getValue();

        if (debut == null || fin == null) {
            afficherErreur("Dates manquantes", "Veuillez sélectionner une date de début et une date de fin.");
            return;
        }

        if (debut.isAfter(fin)) {
            afficherErreur("Dates invalides", "La date de début doit être antérieure à la date de fin.");
            return;
        }

        filtreActuel = FiltrePeriode.creerFiltrePersonnalise(debut, fin);
        mettreAJourLabelFiltre();
        calculerPerformances();
    }

    @FXML
    public void voirTout() {
        filtreActuel = FiltrePeriode.creerFiltreTout();
        mettreAJourLabelFiltre();
        calculerPerformances();
    }

    @FXML
    public void filtrer7DerniersJours() {
        filtreActuel = FiltrePeriode.creerFiltreSeptJours();
        dateDebutFiltre.setValue(filtreActuel.getDateDebut());
        dateFinFiltre.setValue(filtreActuel.getDateFin());
        mettreAJourLabelFiltre();
        calculerPerformances();
    }

    @FXML
    public void filtrerSemaineCourante() {
        filtreActuel = FiltrePeriode.creerFiltreSemaineCourante();
        dateDebutFiltre.setValue(filtreActuel.getDateDebut());
        dateFinFiltre.setValue(filtreActuel.getDateFin());
        mettreAJourLabelFiltre();
        calculerPerformances();
    }

    @FXML
    public void filtrerMoisCourant() {
        filtreActuel = FiltrePeriode.creerFiltreMoisCourant();
        dateDebutFiltre.setValue(filtreActuel.getDateDebut());
        dateFinFiltre.setValue(filtreActuel.getDateFin());
        mettreAJourLabelFiltre();
        calculerPerformances();
    }

    @FXML
    public void calculerPerformances() {
        if (matiereActuelle == null) {
            return;
        }

        try {
            // ✅ Debug : afficher les productions avant calcul
            List<ProductionModel> toutesProductions = productionService.getProductions();
            System.out.println("=== DEBUG PRODUCTIONS ===");
            System.out.println("Nombre total de productions : " + toutesProductions.size());

            for (ProductionModel prod : toutesProductions) {
                System.out.println("Production ID: " + prod.getId() +
                        ", Date: " + prod.getDateProduction() +
                        ", Entrée: " + prod.getQuantiteEntreeReelle() +
                        ", Statut: " + prod.getStatut() +
                        ", Valid: " + prod.isValide() +
                        ", Complete: " + prod.isDonneeComplete());
            }

            // Obtenir les productions filtrées
            List<ProductionModel> productionsFiltrees = productionService.getProductionsFiltrees(
                    filtreActuel.getDateDebut(),
                    filtreActuel.getDateFin()
            );

            System.out.println("Productions filtrées : " + productionsFiltrees.size());

            // Grouper par jour
            Map<LocalDate, JourneeProduction> journees = productionService.grouperParJour(productionsFiltrees);

            System.out.println("Journées créées : " + journees.size());
            for (Map.Entry<LocalDate, JourneeProduction> entry : journees.entrySet()) {
                JourneeProduction journee = entry.getValue();
                System.out.println("Journée " + entry.getKey() +
                        " - Entrée: " + journee.getTotalEntreeJour() +
                        " - Sorties: " + journee.getTotalSortiesJour());
            }

            // Calculer les statistiques
            StatistiquesService.StatistiquesResume stats = StatistiquesService.calculerStatistiques(journees, matiereActuelle);

            // Afficher les résultats
            afficherStatistiques(stats);
            mettreAJourAffichageProductions(journees);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du calcul des performances", e);
            afficherErreur("Erreur de calcul", "Erreur lors du calcul des performances : " + e.getMessage());
        }
    }

    private void afficherStatistiques(StatistiquesService.StatistiquesResume stats) {
        labelMeilleurePerf.setText(String.format("📈 Meilleur jour : %s → %.1f%%",
                stats.getJourMeilleur() != null ? stats.getJourMeilleur().toString() : "N/A",
                stats.getPerformanceMax()));

        labelPlusGrossePerte.setText(String.format("📉 Plus grosse perte : %s → %.1f%%",
                stats.getJourPire() != null ? stats.getJourPire().toString() : "N/A",
                stats.getPerformanceMin()));

        labelMoyenneGlobale.setText(String.format("📊 Période: %.1f%% | Moyenne: %.1f%% (%d jours)",
                stats.getPerformanceGlobalePeriode(), stats.getPerformanceMoyenne(), stats.getNombreJours()));
    }

    private void mettreAJourAffichageProductions(Map<LocalDate, JourneeProduction> journees) {
        for (ProductionUI productionUI : listeProductionUI) {
            if (productionUI.production != null && productionUI.production.getDateProduction() != null) {
                LocalDate dateProduction = productionUI.production.getDateProduction();

                if (journees.containsKey(dateProduction)) {
                    JourneeProduction journee = journees.get(dateProduction);

                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("📅 %s\n", dateProduction.toString()));
                    sb.append(String.format("🏭 Total entrée jour: %.2fkg\n", journee.getTotalEntreeJour()));
                    sb.append(String.format("📦 Total sorties jour: %.2fkg\n", journee.getTotalSortiesJour()));

                    // Afficher le détail par sortie
                    if (matiereActuelle != null && matiereActuelle.getSortiesIdeales() != null) {
                        for (SortieIdeale sortieIdeale : matiereActuelle.getSortiesIdeales()) {
                            double totalSortie = journee.getTotalSortieParNumero(sortieIdeale.getNumeroSortie());
                            sb.append(String.format("   %s: %.2fkg\n", sortieIdeale.getNomSortie(), totalSortie));
                        }
                    }

                    sb.append(String.format("⚡ Performance jour: %.1f%%", journee.getPerformanceJour()));

                    productionUI.resultatLabel.setText(sb.toString());

                    // Couleur selon la performance
                    double performance = journee.getPerformanceJour();
                    if (performance >= 100) {
                        productionUI.resultatLabel.setTextFill(Color.GREEN);
                    } else if (performance >= 80) {
                        productionUI.resultatLabel.setTextFill(Color.ORANGE);
                    } else {
                        productionUI.resultatLabel.setTextFill(Color.RED);
                    }

                } else {
                    productionUI.resultatLabel.setText("⛔ Pas de données pour cette date ou hors période filtrée");
                    productionUI.resultatLabel.setTextFill(Color.GRAY);
                }
            }
        }
    }
    @FXML
    public void supprimerMatiereActuelle() {
        if (matiereActuelle == null) {
            afficherErreur("Aucune sélection", "Veuillez d'abord sélectionner une matière première à supprimer.");
            return;
        }

        try {
            // Compter les productions associées
            int nbProductions = matiereService.compterProductionsAssociees(matiereActuelle.getId());

            // Créer le message de confirmation
            String message = "Êtes-vous sûr de vouloir supprimer la matière première '" +
                    matiereActuelle.getNom() + "' ?";

            if (nbProductions > 0) {
                message += "\n\n⚠️ ATTENTION : Cette action supprimera également " +
                        nbProductions + " production(s) associée(s) !";
            }

            message += "\n\nCette action est irréversible.";

            // Demander confirmation
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Confirmation de suppression");
            confirmation.setHeaderText("Supprimer la matière première");
            confirmation.setContentText(message);

            Optional<ButtonType> resultat = confirmation.showAndWait();

            if (resultat.isPresent() && resultat.get() == ButtonType.OK) {
                // Supprimer avec cascade
                matiereService.supprimerAvecConfirmation(matiereActuelle.getId(), true);

                // Nettoyer l'interface
                matiereActuelle = null;
                productionService.setMatierePremiereModel(null);
                labelMatiereSelectionnee.setText("📦 Aucune matière sélectionnée");

                // Vider l'affichage des productions
                viderAffichageProductions();
                listeProductionUI.clear();

                // Vider les statistiques
                labelMeilleurePerf.setText("📈 Meilleur jour : N/A");
                labelPlusGrossePerte.setText("📉 Plus grosse perte : N/A");
                labelMoyenneGlobale.setText("📊 Moyenne globale : N/A");

                // Recharger la liste des matières premières
                chargerMatieresPremieres();

                afficherInfo("Suppression réussie",
                        "La matière première '" + matiereActuelle + "' et ses " +
                                nbProductions + " production(s) ont été supprimées avec succès.");

            } else {
                LOGGER.info("Suppression annulée par l'utilisateur");
            }

        } catch (ServiceException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression de la matière première", e);
            afficherErreur("Erreur de suppression",
                    "Impossible de supprimer la matière première : " + e.getMessage());
        }
    }
    public void supprimerMatiere(MatierePremiereModel matiere) {
        if (matiere == null) {
            return;
        }

        // Si c'est la matière actuelle, utiliser la méthode principale
        if (matiere.equals(matiereActuelle)) {
            supprimerMatiereActuelle();
            return;
        }

        // Sinon, suppression directe
        try {
            int nbProductions = matiereService.compterProductionsAssociees(matiere.getId());

            String message = "Supprimer '" + matiere.getNom() + "' ?";
            if (nbProductions > 0) {
                message += "\n⚠️ " + nbProductions + " production(s) seront aussi supprimées !";
            }

            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setContentText(message);

            Optional<ButtonType> resultat = confirmation.showAndWait();
            if (resultat.isPresent() && resultat.get() == ButtonType.OK) {
                matiereService.supprimerDefinitivement(matiere.getId());
                chargerMatieresPremieres(); // Recharger la liste
                afficherInfo("Supprimé", "Matière première supprimée avec succès.");
            }

        } catch (ServiceException e) {
            afficherErreur("Erreur", "Impossible de supprimer : " + e.getMessage());
        }
    }

    private void mettreAJourLabelFiltre() {
        labelFiltrageActuel.setText("📅 " + filtreActuel.getNom());
    }

    private void viderChampsSaisie() {
        matierePremiereField.clear();
        quantiteEntreeIdealeField.clear();
        for (TextField field : champsSortiesIdeales) {
            field.clear();
        }
        // Remettre les noms par défaut
        for (int i = 1; i < champsSortiesIdeales.size(); i += 2) {
            champsSortiesIdeales.get(i).setText("Sortie " + ((i + 1) / 2));
        }
    }

    private void afficherErreur(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(titre);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void afficherInfo(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(titre);
        alert.setContentText(message);
        alert.showAndWait();
    }
}