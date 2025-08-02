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

import javafx.stage.FileChooser;
import java.io.File;
import java.time.format.DateTimeFormatter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

public class TrackerController {
    private static final Logger LOGGER = Logger.getLogger(TrackerController.class.getName());

    // Composants FXML
    @FXML private Button btnExporterExcel;
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
    // Services
    private boolean modeModification = false;
    private ExcelExportService excelExportService;
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

        }

        private void creerChampsSorties() {
            sortiesReellesFields.clear();

            if (matiereActuelle != null && matiereActuelle.getSortiesIdeales() != null) {
                for (SortieIdeale sortieIdeale : matiereActuelle.getSortiesIdeales()) {
                    TextField field = new TextField();
                    field.setPromptText("Sortie " + sortieIdeale.getNumeroSortie() +
                            " (" + sortieIdeale.getNomSortie() + ") - réelle");

                    // ✅ CORRECTION CRITIQUE
                    sortiesReellesFields.add(field);

                    field.textProperty().addListener((obs, old, val) -> mettreAJourAffichageStatut());
                }
            }
        }


        public void sauvegarderProduction() {
            try {
                // ✅ NOUVEAU : Validation de la date
                LocalDate dateProduction = datePicker.getValue();
                if (dateProduction == null) {
                    resultatLabel.setText("⚠️ Veuillez sélectionner une date");
                    resultatLabel.setTextFill(Color.RED);
                    return;
                }

                // ✅ NOUVEAU : Validation date cohérente
                if (dateProduction.isAfter(LocalDate.now())) {
                    resultatLabel.setText("⚠️ La date ne peut pas être dans le futur");
                    resultatLabel.setTextFill(Color.RED);
                    return;
                }

                if (dateProduction.isBefore(LocalDate.now().minusYears(1))) {
                    resultatLabel.setText("⚠️ La date ne peut pas être antérieure à 1 an");
                    resultatLabel.setTextFill(Color.RED);
                    return;
                }

                production.setDateProduction(dateProduction);

                // ✅ NOUVEAU : Validation de l'heure
                LocalTime heureProduction = timeField.getLocalTime();
                if (heureProduction == null) {
                    resultatLabel.setText("⚠️ Veuillez saisir une heure valide");
                    resultatLabel.setTextFill(Color.RED);
                    return;
                }

                production.setHeureProduction(heureProduction);

                // ✅ VALIDATION AMÉLIORÉE DE L'ENTRÉE RÉELLE
                String entreeText = entreeReelle.getText().trim();
                if (entreeText.isEmpty()) {
                    resultatLabel.setText("⚠️ Veuillez saisir la quantité d'entrée réelle");
                    resultatLabel.setTextFill(Color.RED);
                    return;
                }

                double quantiteEntree;
                try {
                    quantiteEntree = Double.parseDouble(entreeText);
                } catch (NumberFormatException e) {
                    resultatLabel.setText("⚠️ La quantité d'entrée doit être un nombre valide");
                    resultatLabel.setTextFill(Color.RED);
                    return;
                }

                if (quantiteEntree <= 0) {
                    resultatLabel.setText("⚠️ La quantité d'entrée doit être positive");
                    resultatLabel.setTextFill(Color.RED);
                    return;
                }

                // ✅ NOUVEAU : Validation par rapport à l'idéal
                if (matiereActuelle != null) {
                    double entreeIdeale = matiereActuelle.getQuantiteEntreeIdeale();
                    if (quantiteEntree > entreeIdeale * 2) {
                        resultatLabel.setText("⚠️ Entrée très éloignée de l'idéal (" + entreeIdeale + "kg). Confirmez la valeur.");
                        resultatLabel.setTextFill(Color.ORANGE);
                        // On ne return pas, on laisse continuer avec un avertissement
                    }
                }

                production.setQuantiteEntreeReelle(quantiteEntree);

                // ✅ VALIDATION AMÉLIORÉE DES SORTIES
                production.getSortiesReelles().clear();
                double totalSorties = 0;

                for (int i = 0; i < sortiesReellesFields.size(); i++) {
                    String sortieText = sortiesReellesFields.get(i).getText().trim();

                    if (sortieText.isEmpty()) {
                        resultatLabel.setText("⚠️ Veuillez saisir toutes les sorties réelles");
                        resultatLabel.setTextFill(Color.RED);
                        return;
                    }

                    double quantiteSortie;
                    try {
                        quantiteSortie = Double.parseDouble(sortieText);
                    } catch (NumberFormatException e) {
                        resultatLabel.setText("⚠️ La sortie " + (i + 1) + " doit être un nombre valide");
                        resultatLabel.setTextFill(Color.RED);
                        return;
                    }

                    if (quantiteSortie < 0) {
                        resultatLabel.setText("⚠️ La sortie " + (i + 1) + " ne peut pas être négative");
                        resultatLabel.setTextFill(Color.RED);
                        return;
                    }

                    if (quantiteSortie > quantiteEntree) {
                        resultatLabel.setText("⚠️ La sortie " + (i + 1) + " (" + quantiteSortie + "kg) ne peut pas dépasser l'entrée (" + quantiteEntree + "kg)");
                        resultatLabel.setTextFill(Color.RED);
                        return;
                    }

                    totalSorties += quantiteSortie;
                    production.ajouterSortieReelle(i + 1, quantiteSortie);
                }

                // ✅ NOUVEAU : Validation cohérence globale des sorties


                // ✅ VALIDATION FINALE (reste identique)
                if (!production.isDonneeComplete()) {
                    resultatLabel.setText("⚠️ Impossible de sauvegarder - Manque: " + getChampManquants());
                    resultatLabel.setTextFill(Color.RED);
                    return;
                }

                // Valider et sauvegarder (reste identique)
                production.validerProduction();

                if (production.getId() == null) {
                    productionService.ajouterProduction(production);
                    resultatLabel.setText("✅ Production créée - ID: " + production.getId());
                    LOGGER.info("Nouvelle production créée avec ID: " + production.getId());
                } else {
                    productionService.mettreAJourProduction(production);
                    resultatLabel.setText("✅ Production mise à jour - ID: " + production.getId());
                    LOGGER.info("Production mise à jour - ID: " + production.getId());
                }

                resultatLabel.setTextFill(Color.GREEN);
                System.out.println("Avant désactivation - Nombre de champs de sorties : " + sortiesReellesFields.size());
                desactiverChampsSaisie();
                calculerPerformances();

            } catch (ServiceException e) {
                resultatLabel.setText("❌ Erreur sauvegarde: " + e.getMessage());
                resultatLabel.setTextFill(Color.RED);
                LOGGER.log(Level.WARNING, "Erreur lors de la sauvegarde", e);
            } catch (Exception e) {
                resultatLabel.setText("❌ Erreur inattendue : " + e.getMessage());
                resultatLabel.setTextFill(Color.RED);
                LOGGER.log(Level.SEVERE, "Erreur lors de la sauvegarde de la production", e);
            }
        }


        private void desactiverChampsSaisie() {
            datePicker.setDisable(true);
            timeField.setDisable(true);
            entreeReelle.setDisable(true);

            for (TextField field : sortiesReellesFields) {
                field.setDisable(true);
            }
            System.out.println("Nombre de champs de sorties désactivés : " + sortiesReellesFields.size());
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

                // NOUVELLE LIGNE : Boutons d'action
                HBox boutons = new HBox(10);

                // Bouton Sauvegarder
                Button btnSauvegarder = new Button("💾 Sauvegarder");
                btnSauvegarder.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                btnSauvegarder.setOnAction(e -> sauvegarderProduction());
                // Bouton Modifier
                Button btnModifier = new Button("✏️ Modifier");
                btnModifier.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
                btnModifier.setOnAction(e -> activerModeModification());
                // Bouton Supprimer
                Button btnSupprimer = new Button("🗑️ Supprimer");
                btnSupprimer.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white;");
                btnSupprimer.setOnAction(e -> supprimerProduction());

                boutons.getChildren().addAll(btnSauvegarder, btnSupprimer);
                container.getChildren().add(boutons);
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
            try {
                // Mettre à jour la date si saisie
                LocalDate dateProduction = datePicker.getValue();
                if (dateProduction != null) {
                    production.setDateProduction(dateProduction);
                }

                // Mettre à jour l'heure si saisie
                LocalTime localTime = timeField.getLocalTime();
                if (localTime != null) {
                    production.setHeureProduction(localTime);
                }

                // NOUVEAU : Mettre à jour l'entrée réelle
                String entreeText = entreeReelle.getText().trim();
                if (!entreeText.isEmpty()) {
                    try {
                        double quantiteEntree = Double.parseDouble(entreeText);
                        if (quantiteEntree > 0) {
                            production.setQuantiteEntreeReelle(quantiteEntree);
                        }
                    } catch (NumberFormatException e) {
                        // Valeur invalide, on ne met pas à jour
                    }
                }

                // NOUVEAU : Mettre à jour les sorties réelles
                production.getSortiesReelles().clear();
                boolean toutesLesRortiesRemplies = true;

                for (int i = 0; i < sortiesReellesFields.size(); i++) {
                    String sortieText = sortiesReellesFields.get(i).getText().trim();

                    if (!sortieText.isEmpty()) {
                        try {
                            double quantiteSortie = Double.parseDouble(sortieText);
                            if (quantiteSortie >= 0) {
                                production.ajouterSortieReelle(i + 1, quantiteSortie);
                            }
                        } catch (NumberFormatException e) {
                            // Valeur invalide, on ignore cette sortie
                            toutesLesRortiesRemplies = false;
                        }
                    } else {
                        toutesLesRortiesRemplies = false;
                    }
                }

            } catch (Exception e) {
                // En cas d'erreur, on continue avec l'affichage
            }

            // Affichage du statut sans sauvegarde
            if (production.getId() != null) {
                // Production existante
                if (production.isDonneeComplete()) {
                    resultatLabel.setText("✏️ Production modifiée - ID: " + production.getId() +
                            " (cliquez 'Sauvegarder' pour confirmer)");
                    resultatLabel.setTextFill(Color.ORANGE);
                } else {
                    resultatLabel.setText("⚠️ Production incomplète - ID: " + production.getId() +
                            " - Manque: " + getChampManquants());
                    resultatLabel.setTextFill(Color.ORANGE);
                }
            } else {
                // Nouvelle production
                if (production.isDonneeComplete()) {
                    resultatLabel.setText("📝 Prêt à sauvegarder - Cliquez 'Sauvegarder'");
                    resultatLabel.setTextFill(Color.BLUE);
                } else {
                    resultatLabel.setText("⏳ Nouvelle production - Manque: " + getChampManquants());
                    resultatLabel.setTextFill(Color.GRAY);
                }
            }
        }
        private void activerModeModification() {
            // Réactiver tous les champs de saisie
            datePicker.setDisable(false);
            timeField.setDisable(false);
            entreeReelle.setDisable(false);

            for (TextField field : sortiesReellesFields) {
                field.setDisable(false);
            }

            // Mettre à jour le message
            resultatLabel.setText("✏️ Mode modification activé - ID: " + production.getId() +
                    " (modifiez les valeurs puis cliquez 'Sauvegarder')");
            resultatLabel.setTextFill(Color.BLUE);

            LOGGER.info("Mode modification activé pour la production ID: " + production.getId());
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

                // Mémoriser si la production était déjà sauvegardée
                boolean etaitSauvegardee = (production.getId() != null);

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

                // Boutons d'action (MODIFIÉ pour inclure le bouton Modifier)
                HBox boutons = new HBox(10);

                // Bouton Sauvegarder
                Button btnSauvegarder = new Button("💾 Sauvegarder");
                btnSauvegarder.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                btnSauvegarder.setOnAction(e -> sauvegarderProduction());

                // Bouton Modifier (NOUVEAU)
                Button btnModifier = new Button("✏️ Modifier");
                btnModifier.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
                btnModifier.setOnAction(e -> activerModeModification());

                // Bouton Supprimer
                Button btnSupprimer = new Button("🗑️ Supprimer");
                btnSupprimer.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white;");
                btnSupprimer.setOnAction(e -> supprimerProduction());

                boutons.getChildren().addAll(btnSauvegarder, btnModifier, btnSupprimer);
                container.getChildren().add(boutons);

                // Restaurer les valeurs
                entreeReelle.setText(entreeText);
                for (int i = 0; i < Math.min(sortiesTexts.size(), sortiesReellesFields.size()); i++) {
                    sortiesReellesFields.get(i).setText(sortiesTexts.get(i));
                }

                // Redésactiver les champs si la production était sauvegardée
                if (etaitSauvegardee) {
                    desactiverChampsSaisie();
                }

                // Re-setup des listeners
                mettreAJourAffichageStatut();
            }

        }
    }

    @FXML
    public void initialize() {
        try {
            matiereService = new MatierePremiereService();
            productionService = new ProductionService();
            excelExportService = new ExcelExportService(productionService);
            btnExporterExcel.setOnAction(e -> exporterVersExcel());

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
            // ✅ VALIDATION DU NOM
            String nom = matierePremiereField.getText().trim();
            if (nom.isEmpty()) {
                afficherErreur("Nom manquant", "Veuillez saisir le nom de la matière première.");
                return;
            }

            // ✅ NOUVEAU : Validation longueur et caractères
            if (nom.length() < 2) {
                afficherErreur("Nom trop court", "Le nom doit contenir au moins 2 caractères.");
                return;
            }

            if (nom.length() > 50) {
                afficherErreur("Nom trop long", "Le nom ne peut pas dépasser 50 caractères.");
                return;
            }

            // ✅ NOUVEAU : Vérifier les doublons


            // ✅ VALIDATION DE LA QUANTITÉ D'ENTRÉE
            String entreeText = quantiteEntreeIdealeField.getText().trim();
            if (entreeText.isEmpty()) {
                afficherErreur("Quantité manquante", "Veuillez saisir la quantité d'entrée idéale.");
                return;
            }
            // ✅ Vérifier les doublons
            if (!modeModification || !nom.equals(matiereActuelle.getNom())) {
                if (matiereService.nomExiste(nom)) {
                    afficherErreur("Nom déjà utilisé",
                            "Une matière première avec ce nom existe déjà. Veuillez choisir un autre nom.");
                    return;
                }
            }
            double quantiteEntreeIdeale;
            try {
                quantiteEntreeIdeale = Double.parseDouble(entreeText);
            } catch (NumberFormatException e) {
                afficherErreur("Valeur invalide", "La quantité d'entrée doit être un nombre valide.");
                return;
            }

            // ✅ NOUVEAU : Validation des valeurs
            if (quantiteEntreeIdeale <= 0) {
                afficherErreur("Valeur invalide", "La quantité d'entrée doit être positive.");
                return;
            }

            if (quantiteEntreeIdeale > 10000) {
                afficherErreur("Valeur trop élevée", "La quantité d'entrée ne peut pas dépasser 10 000 kg.");
                return;
            }

            int nombreSorties = nombreSortiesSpinner.getValue();

            // ✅ VALIDATION DES SORTIES AVEC DÉTAILS
            List<SortieIdeale> sortiesIdeales = new ArrayList<>();
            double totalSorties = 0;

            for (int i = 0; i < nombreSorties; i++) {
                String quantiteText = champsSortiesIdeales.get(i * 2).getText().trim();
                String nomSortie = champsSortiesIdeales.get(i * 2 + 1).getText().trim();

                // Validation quantité sortie
                if (quantiteText.isEmpty()) {
                    afficherErreur("Sortie manquante",
                            "Veuillez saisir la quantité pour la sortie " + (i + 1));
                    return;
                }

                double quantite;
                try {
                    quantite = Double.parseDouble(quantiteText);
                } catch (NumberFormatException e) {
                    afficherErreur("Valeur invalide",
                            "La quantité de la sortie " + (i + 1) + " doit être un nombre valide.");
                    return;
                }

                if (quantite <= 0) {
                    afficherErreur("Valeur invalide",
                            "La quantité de la sortie " + (i + 1) + " doit être positive.");
                    return;
                }

                if (quantite > quantiteEntreeIdeale) {
                    afficherErreur("Valeur incohérente",
                            "La sortie " + (i + 1) + " (" + quantite + "kg) ne peut pas être supérieure à l'entrée (" + quantiteEntreeIdeale + "kg).");
                    return;
                }

                // Validation nom sortie
                if (nomSortie.isEmpty()) {
                    nomSortie = "Sortie " + (i + 1); // Nom par défaut
                } else if (nomSortie.length() > 30) {
                    afficherErreur("Nom trop long",
                            "Le nom de la sortie " + (i + 1) + " ne peut pas dépasser 30 caractères.");
                    return;
                }

                // ✅ NOUVEAU : Vérifier les doublons de noms de sorties
                for (SortieIdeale sortieExistante : sortiesIdeales) {
                    if (sortieExistante.getNomSortie().equalsIgnoreCase(nomSortie)) {
                        afficherErreur("Noms de sorties dupliqués",
                                "Le nom '" + nomSortie + "' est utilisé plusieurs fois. Chaque sortie doit avoir un nom unique.");
                        return;
                    }
                }

                totalSorties += quantite;
                sortiesIdeales.add(new SortieIdeale(i + 1, quantite, nomSortie));
            }

            // ✅ NOUVEAU : Validation cohérence globale
            if (totalSorties > quantiteEntreeIdeale * 1.1) { // Tolérance de 10%
                afficherErreur("Incohérence des quantités",
                        String.format("Le total des sorties (%.2fkg) dépasse largement l'entrée (%.2fkg). " +
                                "Vérifiez vos valeurs.", totalSorties, quantiteEntreeIdeale));
                return;
            }

            // ✅ Création de la matière première (reste identique)
            if (modeModification) {
                // MODE MODIFICATION
                matiereActuelle.setNom(nom);
                matiereActuelle.setQuantiteEntreeIdeale(quantiteEntreeIdeale);
                matiereActuelle.setSortiesIdeales(sortiesIdeales);

                matiereService.mettreAJour(matiereActuelle); // ✅ Méthode qui existe déjà

                // Actualiser la liste
                chargerMatieresPremieres();
                matierePremiereCombo.getSelectionModel().select(matiereActuelle);

                afficherInfo("Succès", "Matière première modifiée avec succès !");

            } else {
                // MODE CRÉATION (code existant)
                MatierePremiereModel matiere = matiereService.creerMatierePremiereComplete(
                        nom, quantiteEntreeIdeale, nombreSorties, sortiesIdeales);

                // Actualiser la liste et sélectionner la nouvelle matière
                chargerMatieresPremieres();
                matierePremiereCombo.getSelectionModel().select(matiere);
                matiereActuelle = matiere;
                productionService.setMatierePremiereModel(matiere);
                labelMatiereSelectionnee.setText("📦 Matière sélectionnée : " + matiere.getNom());

                afficherInfo("Succès", "Matière première créée avec succès !");
            }

            viderFormulaire(); // Vider après création/modification

        } catch (NumberFormatException e) {
            afficherErreur("Erreur de saisie", "Veuillez entrer des valeurs numériques valides.");
        } catch (ServiceException e) {
            afficherErreur("Erreur de " + (modeModification ? "modification" : "création"), e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la " + (modeModification ? "modification" : "création") + " de la matière première", e);
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

        // NOUVEAU : Remplir le formulaire avec les données de la matière sélectionnée
        remplirFormulaireAvecMatiere(matiere);

        chargerProductionsExistantes();
        // Actualiser toutes les productions existantes
        for (ProductionUI productionUI : listeProductionUI) {
            productionUI.production.setMatierePremiereId(matiere.getId());
            productionUI.actualiserAffichage();
        }

        calculerPerformances();
    }
    private void remplirFormulaireAvecMatiere(MatierePremiereModel matiere) {
        // Remplir les champs
        matierePremiereField.setText(matiere.getNom());
        quantiteEntreeIdealeField.setText(String.valueOf(matiere.getQuantiteEntreeIdeale()));

        // Ajuster le nombre de sorties
        int nbSorties = matiere.getSortiesIdeales().size();
        nombreSortiesSpinner.getValueFactory().setValue(nbSorties);

        // Remplir les champs de sorties
        for (int i = 0; i < matiere.getSortiesIdeales().size(); i++) {
            SortieIdeale sortie = matiere.getSortiesIdeales().get(i);
            if (i * 2 < champsSortiesIdeales.size()) {
                champsSortiesIdeales.get(i * 2).setText(String.valueOf(sortie.getQuantiteIdeale()));
                champsSortiesIdeales.get(i * 2 + 1).setText(sortie.getNomSortie());
            }
        }

        // Changer le texte du bouton pour indiquer la modification
        btnCreerMatiere.setText("✏️ Modifier Matière");
        modeModification = true;

        // NOUVEAU : Afficher le bouton Annuler s'il n'existe pas déjà
        ajouterBoutonAnnulerSiNecessaire();
    }

    // NOUVELLE méthode pour gérer le bouton Annuler
    private void ajouterBoutonAnnulerSiNecessaire() {
        // Vérifier si le bouton Annuler existe déjà
        HBox parentBox = (HBox) btnCreerMatiere.getParent();
        boolean boutonAnnulerExiste = parentBox.getChildren().stream()
                .anyMatch(node -> node instanceof Button &&
                        ((Button) node).getText().contains("Annuler"));

        if (!boutonAnnulerExiste) {
            Button btnAnnuler = new Button("❌ Annuler");
            btnAnnuler.setStyle("-fx-background-color: #757575; -fx-text-fill: white;");
            btnAnnuler.setOnAction(e -> annulerModification());
            parentBox.getChildren().add(btnAnnuler);
        }
    }
    @FXML
    public void annulerModification() {
        viderFormulaire();

        // Supprimer le bouton Annuler
        HBox parentBox = (HBox) btnCreerMatiere.getParent();
        parentBox.getChildren().removeIf(node ->
                node instanceof Button && ((Button) node).getText().contains("Annuler"));
    }
    private void viderFormulaire() {
        matierePremiereField.clear();
        quantiteEntreeIdealeField.clear();
        for (TextField field : champsSortiesIdeales) {
            field.clear();
        }
        // Remettre les noms par défaut
        for (int i = 1; i < champsSortiesIdeales.size(); i += 2) {
            champsSortiesIdeales.get(i).setText("Sortie " + ((i + 1) / 2));
        }

        btnCreerMatiere.setText("➕ Créer Matière");
        modeModification = false;

        // NOUVEAU : Supprimer le bouton Annuler s'il existe
        HBox parentBox = (HBox) btnCreerMatiere.getParent();
        if (parentBox != null) {
            parentBox.getChildren().removeIf(node ->
                    node instanceof Button && ((Button) node).getText().contains("Annuler"));
        }
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

        // CHANGEMENT : Appeler la nouvelle méthode d'affichage
        productionUI.mettreAJourAffichageStatut();
        if (production.getId() != null) {
            productionUI.desactiverChampsSaisie();
        }
        // Ajouter à la liste et à l'interface
        listeProductionUI.add(productionUI);

        // Insérer avant le bouton d'ajout
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
            afficherErreur("Matière non sélectionnée",
                    "Veuillez d'abord sélectionner ou créer une matière première.");
            return;
        }

        try {
            ProductionModel production = new ProductionModel();
            production.setMatierePremiereId(matiereActuelle.getId());
            production.setDateProduction(LocalDate.now());

            ProductionUI productionUI = new ProductionUI(production);
            listeProductionUI.add(productionUI);
            // ALTERNATIVE SIMPLE : Insérer juste avant les boutons (toujours à la fin)
            int indexInsertion = joursContainer.getChildren().size();

            // Parcourir depuis la fin pour trouver où insérer
            while (indexInsertion > 0) {
                var element = joursContainer.getChildren().get(indexInsertion - 1);
                if (element instanceof Button) {
                    indexInsertion--; // Insérer avant ce bouton
                } else {
                    break; // On a trouvé un élément qui n'est pas un bouton
                }
            }

            joursContainer.getChildren().add(indexInsertion, productionUI.getContainer());
            compteurProductions++;

            // Ajouter le bouton calculer si c'est la première fois qu'on a 2 productions
            if (listeProductionUI.size() == 2 && !boutonCalculerExiste()) {
                ajouterBoutonCalculer();
            }

            LOGGER.info("Production ajoutée à l'index " + indexInsertion +
                    " - Total productions: " + listeProductionUI.size());

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
            String nomMatiereASupprimer = matiereActuelle.getNom();
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
                        "La matière première '" + nomMatiereASupprimer + "' et ses " +
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
    @FXML
    public void exporterVersExcel() {
        if (matiereActuelle == null) {
            afficherErreur("Aucune matière sélectionnée",
                    "Veuillez d'abord sélectionner une matière première à exporter.");
            return;
        }

        try {
            // Créer le sélecteur de fichier
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Exporter les productions vers Excel");

            // Configuration des filtres d'extension
            FileChooser.ExtensionFilter extFilter =
                    new FileChooser.ExtensionFilter("Fichiers Excel (*.xlsx)", "*.xlsx");
            fileChooser.getExtensionFilters().add(extFilter);

            // Nom de fichier par défaut
            String nomFichierDefaut = String.format("Production_%s_%s.xlsx",
                    matiereActuelle.getNom().replaceAll("[^a-zA-Z0-9]", "_"),
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            fileChooser.setInitialFileName(nomFichierDefaut);

            // Répertoire initial (Documents de l'utilisateur)
            String userHome = System.getProperty("user.home");
            File documentsDir = new File(userHome, "Documents");
            if (documentsDir.exists()) {
                fileChooser.setInitialDirectory(documentsDir);
            }

            // Afficher la boîte de dialogue
            File fichier = fileChooser.showSaveDialog(btnExporterExcel.getScene().getWindow());

            if (fichier != null) {
                // S'assurer que l'extension .xlsx est présente
                String cheminFichier = fichier.getAbsolutePath();
                if (!cheminFichier.toLowerCase().endsWith(".xlsx")) {
                    cheminFichier += ".xlsx";
                }

                // Effectuer l'export
                LOGGER.info("Début de l'export vers: " + cheminFichier);
                excelExportService.exporterProductions(matiereActuelle, cheminFichier);

                // Confirmation de succès
                afficherInfo("Export réussi",
                        String.format("Les données de '%s' ont été exportées avec succès vers:\n%s\n\n" +
                                        "Le fichier contient:\n" +
                                        "• Résumé de la matière première\n" +
                                        "• Détail de toutes les productions\n" +
                                        "• Statistiques complètes",
                                matiereActuelle.getNom(), cheminFichier));

                LOGGER.info("Export terminé avec succès");

            } else {
                LOGGER.info("Export annulé par l'utilisateur");
            }

        } catch (ServiceException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export Excel", e);
            afficherErreur("Erreur d'export",
                    "Impossible d'exporter les données vers Excel:\n" + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur inattendue lors de l'export", e);
            afficherErreur("Erreur inattendue",
                    "Une erreur inattendue s'est produite lors de l'export:\n" + e.getMessage());
        }
    }

    private void mettreAJourLabelFiltre() {
        labelFiltrageActuel.setText("📅 " + filtreActuel.getNom());
    }

    private void viderChampsSaisie() {
        viderFormulaire();
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