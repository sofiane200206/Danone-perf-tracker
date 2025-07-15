package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

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

    private int jourCounter = 1;

    private static class JourFields {
        Label label;
        TextField entreeReelle;
        TextField sortie1Reelle;
        TextField sortie2Reelle;
        Label resultatLabel;

        VBox getVBox() {
            VBox vbox = new VBox(5);
            vbox.getChildren().addAll(label, entreeReelle, sortie1Reelle, sortie2Reelle, resultatLabel);
            return vbox;
        }
    }

    private final List<JourFields> jours = new ArrayList<>();

    @FXML
    public void ajouterJour() {
        JourFields jour = new JourFields();
        jour.label = new Label("Jour " + jourCounter);
        jour.entreeReelle = new TextField();
        jour.entreeReelle.setPromptText("Quantité entrée réelle");

        jour.sortie1Reelle = new TextField();
        jour.sortie1Reelle.setPromptText("Sortie 1 réelle");

        jour.sortie2Reelle = new TextField();
        jour.sortie2Reelle.setPromptText("Sortie 2 réelle");

        jour.resultatLabel = new Label();

        jours.add(jour);
        joursContainer.getChildren().add(jour.getVBox());

        jourCounter++;

        // Ajouter bouton de calcul après le 1er jour
        if (jours.size() == 1) {
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

            double somme = 0;
            double maxPerf = Double.MIN_VALUE;
            double minPerf = Double.MAX_VALUE;
            int jourMax = -1;
            int jourMin = -1;

            for (int i = 0; i < jours.size(); i++) {
                JourFields jour = jours.get(i);

                double qteReelle = Double.parseDouble(jour.entreeReelle.getText());
                double sortie1Reelle = Double.parseDouble(jour.sortie1Reelle.getText());
                double sortie2Reelle = Double.parseDouble(jour.sortie2Reelle.getText());

                double sortie1IdealeReelle = (qteReelle * sortie1Ideale) / qteIdeale;
                double sortie2IdealeReelle = (qteReelle * sortie2Ideale) / qteIdeale;
                double totalIdealeReelle = sortie1IdealeReelle + sortie2IdealeReelle;
                double totalReel = sortie1Reelle + sortie2Reelle;

                double diff1 = sortie1Reelle - sortie1IdealeReelle;
                double diff2 = sortie2Reelle - sortie2IdealeReelle;

                double perf1 = (sortie1Reelle / sortie1IdealeReelle) * 100;
                double perf2 = (sortie2Reelle / sortie2IdealeReelle) * 100;
                double perfGlobale = (totalReel / totalIdealeReelle) * 100;

                // Résumé texte
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("S1: %.2fkg (%+,.2fkg) - %.1f%%\n", sortie1Reelle, diff1, perf1));
                sb.append(String.format("S2: %.2fkg (%+,.2fkg) - %.1f%%\n", sortie2Reelle, diff2, perf2));
                sb.append(String.format("Perf globale: %.1f%%", perfGlobale));

                jour.resultatLabel.setText(sb.toString());
                jour.resultatLabel.setTextFill(perfGlobale < 100 ? Color.RED :
                        (perfGlobale > 100 ? Color.GREEN : Color.GRAY));

                // Pour les stats globales
                somme += perfGlobale;
                if (perfGlobale > maxPerf) {
                    maxPerf = perfGlobale;
                    jourMax = i + 1;
                }
                if (perfGlobale < minPerf) {
                    minPerf = perfGlobale;
                    jourMin = i + 1;
                }
            }

            double moyenne = somme / jours.size();

            labelMeilleurePerf.setText(String.format("📈 Meilleur jour : Jour %d → %.1f%%", jourMax, maxPerf));
            labelPlusGrossePerte.setText(String.format("📉 Plus grosse perte : Jour %d → %.1f%%", jourMin, minPerf));
            labelMoyenneGlobale.setText(String.format("📊 Moyenne performance : %.1f%%", moyenne));

        } catch (NumberFormatException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Erreur de saisie");
            alert.setContentText("Veuillez entrer uniquement des nombres valides.");
            alert.show();
        }
    }

}
