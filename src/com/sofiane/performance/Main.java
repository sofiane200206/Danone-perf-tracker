package com.sofiane.performance;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Charger l'écran de connexion au lieu du tracker directement
        Parent root = FXMLLoader.load(getClass().getClassLoader()
                .getResource("resources/views/login.fxml"));

        primaryStage.setTitle("Performance Tracker - Connexion");
        primaryStage.setScene(new Scene(root, 600, 500));
        primaryStage.setResizable(false); // Optionnel : bloquer le redimensionnement
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}