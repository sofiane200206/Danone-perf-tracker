package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

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
    private TextField quantiteEntreeReelleField;
    @FXML
    private TextField sortie1ReelleField;
    @FXML
    private TextField sortie2ReelleField;

    @FXML
    private Label resultatLabel;

    @FXML
    public void calculerPerformance() {
        try {
            double qteIdeale = Double.parseDouble(quantiteEntreeIdealeField.getText());
            double sortie1Ideale = Double.parseDouble(sortie1IdealeField.getText());
            double sortie2Ideale = Double.parseDouble(sortie2IdealeField.getText());

            double qteReelle = Double.parseDouble(quantiteEntreeReelleField.getText());
            double sortie1Reelle = Double.parseDouble(sortie1ReelleField.getText());
            double sortie2Reelle = Double.parseDouble(sortie2ReelleField.getText());

            // Proportion idéale
            double sortie1IdealeReelle = (qteReelle * sortie1Ideale) / qteIdeale;
            double sortie2IdealeReelle = (qteReelle * sortie2Ideale) / qteIdeale;
            double totalIdealeReelle = sortie1IdealeReelle + sortie2IdealeReelle;

            // Totaux réels
            double totalReel = sortie1Reelle + sortie2Reelle;

            // Pertes ou gains
            double diff1 = sortie1Reelle - sortie1IdealeReelle;
            double diff2 = sortie2Reelle - sortie2IdealeReelle;
            double performance = (totalReel / totalIdealeReelle) * 100;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Sortie 1 : %.2f kg (%s%.2f kg)\n", sortie1Reelle,
                    diff1 >= 0 ? "+" : "", diff1));
            sb.append(String.format("Sortie 2 : %.2f kg (%s%.2f kg)\n", sortie2Reelle,
                    diff2 >= 0 ? "+" : "", diff2));
            sb.append(String.format("Performance globale : %.2f%%", performance));

            resultatLabel.setText(sb.toString());

            // Couleur selon performance
            if (performance < 100) {
                resultatLabel.setTextFill(Color.RED);
            } else if (performance > 100) {
                resultatLabel.setTextFill(Color.GREEN);
            } else if (performance == 100) {resultatLabel.setTextFill(Color.GRAY);}

        } catch (NumberFormatException e) {
            resultatLabel.setText("Veuillez remplir tous les champs avec des nombres valides.");
            resultatLabel.setTextFill(Color.ORANGE);
        }
    }
}
