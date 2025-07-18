package components;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;

import java.time.LocalTime;

public class HourMinuteField extends HBox {

    private final TextField h1 = createDigitField();
    private final TextField h2 = createDigitField();
    private final TextField m1 = createDigitField();
    private final TextField m2 = createDigitField();

    public HourMinuteField() {
        super(2);
        Label colon = new Label(":");
        colon.setMinWidth(5);
        this.getChildren().addAll(h1, h2, colon, m1, m2);
    }

    private TextField createDigitField() {
        TextField field = new TextField();
        field.setPrefColumnCount(1);
        field.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            return newText.matches("\\d?") ? change : null;
        }));
        return field;
    }

    public String getTime() {
        return h1.getText() + h2.getText() + ":" + m1.getText() + m2.getText();
    }

    public LocalTime getLocalTime() {
        try {
            // Utiliser getTime() au lieu de getText()
            String timeText = getTime();
            if (timeText == null || timeText.trim().isEmpty()) {
                return null;
            }

            // Vérifier que tous les champs sont remplis
            if (h1.getText().isEmpty() || h2.getText().isEmpty() ||
                    m1.getText().isEmpty() || m2.getText().isEmpty()) {
                return null;
            }

            // Créer l'heure au format "HH:mm"
            String formattedTime = String.format("%s%s:%s%s",
                    h1.getText(), h2.getText(), m1.getText(), m2.getText());

            return LocalTime.parse(formattedTime);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValidTime() {
        try {
            // Vérifier que tous les champs sont remplis
            if (h1.getText().isEmpty() || h2.getText().isEmpty() ||
                    m1.getText().isEmpty() || m2.getText().isEmpty()) {
                return false;
            }

            int hours = Integer.parseInt(h1.getText() + h2.getText());
            int minutes = Integer.parseInt(m1.getText() + m2.getText());
            return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Méthode utilitaire pour obtenir l'heure formatée (ex: "09:30")
    public String getFormattedTime() {
        if (!isValidTime()) {
            return "";
        }
        return String.format("%s%s:%s%s", h1.getText(), h2.getText(), m1.getText(), m2.getText());
    }

    // Méthode pour définir une heure (utile pour les tests ou la pré-saisie)
    public void setTime(int hours, int minutes) {
        if (hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59) {
            h1.setText(String.valueOf(hours / 10));
            h2.setText(String.valueOf(hours % 10));
            m1.setText(String.valueOf(minutes / 10));
            m2.setText(String.valueOf(minutes % 10));
        }
    }

    // Méthode pour définir une heure à partir d'un LocalTime
    public void setTime(LocalTime time) {
        if (time != null) {
            setTime(time.getHour(), time.getMinute());
        }
    }

    // Méthode pour effacer tous les champs
    public void clear() {
        h1.clear();
        h2.clear();
        m1.clear();
        m2.clear();
    }
}