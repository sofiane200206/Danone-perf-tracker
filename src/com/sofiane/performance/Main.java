package com.sofiane.performance;

import dao.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import service.SauvegardeService;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Copie de securite de la base AVANT toute ouverture
        SauvegardeService.sauvegarderAuDemarrage(DatabaseManager.getCheminFichierBase());

        // Charger l'écran de connexion au lieu du tracker directement
        Parent root = FXMLLoader.load(getClass().getClassLoader()
                .getResource("resources/views/login.fxml"));

        primaryStage.setTitle("Performance Tracker - Connexion");
        primaryStage.setScene(new Scene(root, 600, 560));
        primaryStage.setMinWidth(420);
        primaryStage.setMinHeight(480);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}