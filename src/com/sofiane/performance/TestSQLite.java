package com.sofiane.performance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TestSQLite {
    public static void main(String[] args) {
        try {
            // 🔧 Charger manuellement le driver
            Class.forName("org.sqlite.JDBC");

            // Chemin vers la BDD SQLite
            String url = "jdbc:sqlite:ma_base_de_donnee.db";
            Connection conn = DriverManager.getConnection(url);
            System.out.println("✅ Connexion SQLite établie !");
            conn.close();
        } catch (ClassNotFoundException e) {
            System.out.println("❌ Driver SQLite non trouvé : " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("❌ Erreur de connexion : " + e.getMessage());
        }
    }
}
