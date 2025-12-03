package com.musicapp.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnector {
    private static final String URL = "jdbc:sqlite:musicapp.db";

    public static Connection connect(){
        try {
            Connection conn = DriverManager.getConnection(URL);
            System.out.println("Connected to SQLite database!");
            return conn;
        } catch(SQLException e){
            System.out.println("Eroare conexiune DB: " + e.getMessage());
            return null;
        }
    }
}
