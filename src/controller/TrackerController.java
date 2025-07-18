package controller;

import components.HourMinuteField;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import model.*;
import service.*;
import filter.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class TrackerController {

    // Composants FXML
    @FXML private TextField matierePremiereField;
    @FXML private TextField quantiteEntreeIdealeField;
    @FXML private TextField sortie1IdealeField;
    @FXML private TextField sortie2IdealeField;
    @FXML private Label labelMeilleurePerf;
    @FXML private Label labelPlusGrossePerte;
    @FXML private Label labelMoyenneGlobale;
    @FXML private VBox joursContainer;
    @FXML private DatePicker dateDebutFiltre;
    @FXML private DatePicker dateFinFiltre;
    @FXML private Button btnAppliquerFiltre;
    @FXML private Button btnVoirTout;
    @FXML private Label labelFiltrageActuel;

    // Services
    private ProductionService productionService;
    private FiltrePeriode filtreActuel;
    private int compteurProductions = 1;
    private List<ProductionUI> listeProductionUI = new java.util.ArrayList<>();

    // Composants UI pour les productions
    private class ProductionUI {
        Production production;
        Label label;
        DatePicker datePicker;
        HourMinuteField timeField;
        TextField entreeReelle;
        TextField sortie1Reelle;
        TextField sortie2Reelle;
        Label resultatLabel;

        ProductionUI(Production production) {
            this.production = production;
            initComponents();
        }



        private void mettreAJourProduction() {
            try {
                // Validation et mise à jour du modèle
                if (timeField.isValidTime()) {
                    LocalTime localTime = timeField.getLocalTime();
                    if (localTime != null) {
                        production.setHeure(localTime);
                    }
                }

                if (!entreeReelle.getText().isEmpty()) {
                    production.setQuantiteEntreeReelle(Double.parseDouble(entreeReelle.getText()));
                }

                if (!sortie1Reelle.getText().isEmpty()) {
                    production.setSortie1Reelle(Double.parseDouble(sortie1Reelle.getText()));
                }

                if (!sortie2Reelle.getText().isEmpty()) {
                    production.setSortie2Reelle(Double.parseDouble(sortie2Reelle.getText()));
                }

                production.setStatut("VALIDE");

            } catch (NumberFormatException e) {
                production.setStatut("ERREUR");
                production.setMessageErreur("Valeurs numériques invalides");
            } catch (Exception e) {
                production.setStatut("ERREUR");
                production.setMessageErreur("Erreur lors de la mise à jour: " + e.getMessage());
            }
        }

// Et aussi mettre à jour les listeners dans initComponents() :

        private void initComponents() {
            label = new Label("Production " + compteurProductions);
            datePicker = new DatePicker();
            timeField = new HourMinuteField();
            entreeReelle = new TextField();
            entreeReelle.setPromptText("Quantité entrée réelle");
            sortie1Reelle = new TextField();
            sortie1Reelle.setPromptText("Sortie 1 réelle");
            sortie2Reelle = new TextField();
            sortie2Reelle.setPromptText("Sortie 2 réelle");
            resultatLabel = new Label();

            // Listeners pour mettre à jour automatiquement le modèle
            datePicker.valueProperty().addListener((obs, old, val) -> {
                production.setDate(val);
                mettreAJourProduction();
            });

            // Ajouter des listeners pour les champs de temps
            // Vous pouvez ajouter des listeners sur les champs individuels de timeField si nécessaire

            // Listeners pour les champs de texte
            entreeReelle.textProperty().addListener((obs, old, val) -> mettreAJourProduction());
            sortie1Reelle.textProperty().addListener((obs, old, val) -> mettreAJourProduction());
            sortie2Reelle.textProperty().addListener((obs, old, val) -> mettreAJourProduction());
        }

        VBox getVBox() {
            VBox vbox = new VBox(5);
            vbox.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-padding: 5;");

            HBox heureBox = new HBox(5);
            heureBox.getChildren().addAll(new Label("Heure :"), timeField);

            vbox.getChildren().addAll(label, datePicker, heureBox, entreeReelle, sortie1Reelle, sortie2Reelle, resultatLabel);
            return vbox;
        }
    }

    @FXML
    public void initialize() {
        productionService = new ProductionService();
        filtreActuel = FiltrePeriode.creerFiltreTout();

        // Configuration des filtres
        btnAppliquerFiltre.setOnAction(e -> appliquerFiltre());
        btnVoirTout.setOnAction(e -> voirTout());

        // Dates par défaut
        dateDebutFiltre.setValue(LocalDate.now().minusDays(7));
        dateFinFiltre.setValue(LocalDate.now());

        mettreAJourLabelFiltre();
    }

    @FXML
    public void ajouterJour() {
        Production production = new Production();
        ProductionUI productionUI = new ProductionUI(production);
        listeProductionUI.add(productionUI);
        productionService.ajouterProduction(production);
        joursContainer.getChildren().add(productionUI.getVBox());

        compteurProductions++;

        // Ajouter le bouton de calcul si c'est la première production
        if (compteurProductions == 2) {
            ajouterBoutonCalculer();
        }
    }

    private void ajouterBoutonCalculer() {
        Button btnCalculer = new Button("Calculer les performances");
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
        mettreAJourLabelFiltre();
        calculerPerformances();
    }

    @FXML
    public void filtrerSemaineCourante() {
        filtreActuel = FiltrePeriode.creerFiltreSemaineCourante();
        mettreAJourLabelFiltre();
        calculerPerformances();
    }

    @FXML
    public void filtrerMoisCourant() {
        filtreActuel = FiltrePeriode.creerFiltreMoisCourant();
        mettreAJourLabelFiltre();
        calculerPerformances();
    }

    private void calculerPerformances() {
        // Créer le modèle idéal
        ModeleIdeal modele = creerModeleIdeal();
        if (modele == null) return;

        productionService.setModeleIdeal(modele);

        // Obtenir les productions filtrées
        List<Production> productionsFiltrees = productionService.getProductionsFiltrees(
                filtreActuel.getDateDebut(),
                filtreActuel.getDateFin()
        );

        // Grouper par jour
        Map<LocalDate, JourneeProduction> journees = productionService.grouperParJour(productionsFiltrees);

        // Calculer les statistiques
        StatistiquesService.StatistiquesResume stats = StatistiquesService.calculerStatistiques(journees, modele);

        // Afficher les résultats
        afficherStatistiques(stats);
        mettreAJourAffichageProductions(journees);
    }

    private ModeleIdeal creerModeleIdeal() {
        try {
            String nom = matierePremiereField.getText();
            double entree = Double.parseDouble(quantiteEntreeIdealeField.getText());
            double sortie1 = Double.parseDouble(sortie1IdealeField.getText());
            double sortie2 = Double.parseDouble(sortie2IdealeField.getText());

            return new ModeleIdeal(nom, entree, sortie1, sortie2);
        } catch (NumberFormatException e) {
            afficherErreur("Erreur de saisie", "Veuillez entrer des valeurs numériques valides pour le modèle idéal.");
            return null;
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
        // Parcourir tous les ProductionUI
        for (ProductionUI productionUI : listeProductionUI) {
            if (productionUI.production != null) {
                LocalDate dateProduction = productionUI.production.getDate();

                if (dateProduction != null && journees.containsKey(dateProduction)) {
                    JourneeProduction journee = journees.get(dateProduction);

                    // Créer le texte d'affichage
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("📅 %s\n", dateProduction.toString()));
                    sb.append(String.format("🏭 Total jour: %.2fkg entrée\n", journee.getTotalEntreeJour()));
                    sb.append(String.format("📦 S1: %.2fkg | S2: %.2fkg\n",
                            journee.getTotalSortie1Jour(), journee.getTotalSortie2Jour()));
                    sb.append(String.format("⚡ Performance jour: %.1f%%", journee.getPerformanceJour()));

                    productionUI.resultatLabel.setText(sb.toString());

                    // Couleur selon la performance
                    double performance = journee.getPerformanceJour();
                    Color couleur = performance < 100 ? Color.RED :
                            (performance > 100 ? Color.GREEN : Color.GRAY);
                    productionUI.resultatLabel.setTextFill(couleur);

                } else {
                    // Si pas de données pour cette date
                    productionUI.resultatLabel.setText("⛔ Pas de données pour cette date");
                    productionUI.resultatLabel.setTextFill(Color.RED);
                }
            }
        }
    }

    private void mettreAJourLabelFiltre() {
        labelFiltrageActuel.setText("📅 " + filtreActuel.getNom());
    }

    private void afficherErreur(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(titre);
        alert.setContentText(message);
        alert.show();
    }
}