package com.musicapp.controller;

import com.musicapp.database.DatabaseConnector;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    
    @FXML
    private void onLoginClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please fill in all fields");
            return;
        }
        
        Integer userId = DatabaseConnector.loginUser(username, password);
        
        if (userId != null) {
            openMainApp(userId, username);
        } else {
            errorLabel.setText("Invalid username or password");
        }
    }
    
    @FXML
    private void onRegisterClick() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please fill in all fields");
            return;
        }
        
        if (username.length() < 3) {
            errorLabel.setText("Username must be at least 3 characters");
            return;
        }
        
        if (password.length() < 4) {
            errorLabel.setText("Password must be at least 4 characters");
            return;
        }
        
        boolean success = DatabaseConnector.registerUser(username, password);
        
        if (success) {
            errorLabel.setStyle("-fx-text-fill: #4CAF50;");
            errorLabel.setText("Registration successful! You can now login.");
            passwordField.clear();
        } else {
            errorLabel.setStyle("-fx-text-fill: #FF6B6B;");
            errorLabel.setText("Username already exists");
        }
    }
    
    private void openMainApp(int userId, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/musicapp/view/main-view.fxml"));
            Parent root = loader.load();
            
            MainController mainController = loader.getController();
            mainController.setUser(userId, username);
            
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Music Player - " + username);
        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("Error loading application");
        }
    }
}
