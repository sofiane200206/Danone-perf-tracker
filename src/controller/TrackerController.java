package controller;

import components.HourMinuteField;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackerController {

    @FXML
    private TextField matierePremiereField;
    @FXML
    private TextField quantiteEntreeIdealeField;
    @FXML
    private TextField sortie1IdealeField;
    @FXML
    private TextField sortie2IdealeField;

    @FXML
    private Label labelMeilleurePerf;
    @FXML
    private Label labelPlusGrossePerte;
    @FXML
    private Label labelMoyenneGlobale;

    @FXML
    private VBox joursContainer;

    private int productionCounter = 1;

    private static class ProductionFields {
        Label label;
        DatePicker datePicker;
        HourMinuteField timeField;
        TextField entreeReelle;
        TextField sortie1Reelle;
        TextField sortie2Reelle;
        Label resultatLabel;

        VBox getVBox() {
            VBox vbox = new VBox(5);
            vbox.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-padding: 5;");

            HBox heureBox = new HBox(5);
            heureBox.getChildren().addAll(new Label("Heure :"), timeField);

            vbox.getChildren().addAll(label, datePicker, heureBox, entreeReelle, sortie1Reelle, sortie2Reelle, resultatLabel);
            return vbox;
        }
    }

    // Classe pour stocker les données d'une journée complète
    private static class JourComplet {
        LocalDate date;
        List<ProductionFields> productions;
        double perfGlobaleJour;
        double totalEntreeJour;
        double totalSortie1Jour;
        double totalSortie2Jour;

        JourComplet(LocalDate date) {
            this.date = date;
            this.productions = new ArrayList<>();
        }
    }

    private final List<ProductionFields> productions = new ArrayList<>();

    @FXML
    public void ajouterJour() {
        ProductionFields production = new ProductionFields();
        production.datePicker = new DatePicker();
        production.label = new Label("Production " + productionCounter);
        production.timeField = new HourMinuteField();
        production.entreeReelle = new TextField();
        production.entreeReelle.setPromptText("Quantité entrée réelle");
        production.sortie1Reelle = new TextField();
        production.sortie1Reelle.setPromptText("Sortie 1 réelle");
        production.sortie2Reelle = new TextField();
        production.sortie2Reelle.setPromptText("Sortie 2 réelle");
        production.resultatLabel = new Label();

        productions.add(production);
        joursContainer.getChildren().add(production.getVBox());

        productionCounter++;

        if (productions.size() == 1) {
            Button btnCalculer = new Button("Calculer les performances");
            btnCalculer.setOnAction(e -> calculerToutesPerformances());
            joursContainer.getChildren().add(btnCalculer);
        }
    }

    public void calculerToutesPerformances() {
        try {
            double qteIdeale = Double.parseDouble(quantiteEntreeIdealeField.getText());
            double sortie1Ideale = Double.parseDouble(sortie1IdealeField.getText());
            double sortie2Ideale = Double.parseDouble(sortie2IdealeField.getText());

            // Regrouper les productions par date
            Map<LocalDate, JourComplet> joursMap = new HashMap<>();

            for (ProductionFields production : productions) {
                LocalDate date = production.datePicker.getValue();
                if (date == null) {
                    production.resultatLabel.setText("⛔ Date manquante");
                    production.resultatLabel.setTextFill(Color.RED);
                    continue;
                }

                if (!production.timeField.isValidTime()) {
                    production.resultatLabel.setText("⛔ Heure invalide (ex: 14:30)");
                    production.resultatLabel.setTextFill(Color.RED);
                    continue;
                }

                try {
                    double qteReelle = Double.parseDouble(production.entreeReelle.getText());
                    double sortie1Reelle = Double.parseDouble(production.sortie1Reelle.getText());
                    double sortie2Reelle = Double.parseDouble(production.sortie2Reelle.getText());

                    // Ajouter cette production au jour correspondant
                    JourComplet jour = joursMap.computeIfAbsent(date, JourComplet::new);
                    jour.productions.add(production);
                    jour.totalEntreeJour += qteReelle;
                    jour.totalSortie1Jour += sortie1Reelle;
                    jour.totalSortie2Jour += sortie2Reelle;

                } catch (NumberFormatException e) {
                    production.resultatLabel.setText("⛔ Valeurs numériques invalides");
                    production.resultatLabel.setTextFill(Color.RED);
                }
            }

            // Calculer les performances pour chaque jour
            double sommePerfJours = 0;
            double maxPerfJour = Double.MIN_VALUE;
            double minPerfJour = Double.MAX_VALUE;
            LocalDate jourMax = null;
            LocalDate jourMin = null;
            int joursValides = 0;

            for (JourComplet jour : joursMap.values()) {
                if (jour.productions.isEmpty()) continue;

                // Calculer la performance idéale pour ce jour
                double sortie1IdealeJour = (jour.totalEntreeJour * sortie1Ideale) / qteIdeale;
                double sortie2IdealeJour = (jour.totalEntreeJour * sortie2Ideale) / qteIdeale;
                double totalIdealeJour = sortie1IdealeJour + sortie2IdealeJour;
                double totalReelJour = jour.totalSortie1Jour + jour.totalSortie2Jour;

                jour.perfGlobaleJour = (totalReelJour / totalIdealeJour) * 100;

                // Mettre à jour les labels de chaque production de ce jour
                for (ProductionFields production : jour.productions) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("📅 %s\n", jour.date.toString()));
                    sb.append(String.format("🏭 Total jour: %.2fkg entrée\n", jour.totalEntreeJour));
                    sb.append(String.format("📦 S1: %.2fkg | S2: %.2fkg\n", jour.totalSortie1Jour, jour.totalSortie2Jour));
                    sb.append(String.format("⚡ Performance jour: %.1f%%", jour.perfGlobaleJour));

                    production.resultatLabel.setText(sb.toString());
                    production.resultatLabel.setTextFill(jour.perfGlobaleJour < 100 ? Color.RED :
                            (jour.perfGlobaleJour > 100 ? Color.GREEN : Color.GRAY));
                }

                // Statistiques globales
                sommePerfJours += jour.perfGlobaleJour;
                joursValides++;

                if (jour.perfGlobaleJour > maxPerfJour) {
                    maxPerfJour = jour.perfGlobaleJour;
                    jourMax = jour.date;
                }
                if (jour.perfGlobaleJour < minPerfJour) {
                    minPerfJour = jour.perfGlobaleJour;
                    jourMin = jour.date;
                }
            }

            if (joursValides > 0) {
                double moyenneJours = sommePerfJours / joursValides;

                labelMeilleurePerf.setText(String.format("📈 Meilleur jour : %s → %.1f%%",
                        jourMax != null ? jourMax.toString() : "N/A", maxPerfJour));
                labelPlusGrossePerte.setText(String.format("📉 Plus grosse perte : %s → %.1f%%",
                        jourMin != null ? jourMin.toString() : "N/A", minPerfJour));
                labelMoyenneGlobale.setText(String.format("📊 Moyenne performance : %.1f%% (%d jours)",
                        moyenneJours, joursValides));
            }

        } catch (NumberFormatException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Erreur de saisie");
            alert.setContentText("Veuillez entrer uniquement des nombres valides dans les champs idéaux.");
            alert.show();
        }
    }

    // Méthode pour ajouter un separateur visuel entre les jours
    private void ajouterSeparateurJour() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: blue; -fx-border-width: 2;");
        joursContainer.getChildren().add(sep);
    }
}