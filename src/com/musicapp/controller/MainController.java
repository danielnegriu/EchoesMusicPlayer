package com.musicapp.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.media.*;
import javafx.stage.FileChooser;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import java.util.List;

public class MainController {
    @FXML
    private ListView<String> songListView;
    @FXML
    private Label currentSongLabel;

    private MediaPlayer mediaPlayer;
    private File currentSong;

    @FXML
    private void onAddSongClick(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Audio File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.ogg")
        );
        List<File> files = fileChooser.showOpenMultipleDialog(null);
        if(files != null){
            for(File file : files){
                songListView.getItems().add(file.getAbsolutePath());
            }
        }
    }

    @FXML
    private void onPlayClick(){
        String selected = songListView.getSelectionModel().getSelectedItem();
        if(selected != null){
            playSong(new File(selected));
        }
    }

    private void playSong(File file){
        if(mediaPlayer != null){
            mediaPlayer.stop();
        }
        currentSong = file;
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.play();
        currentSongLabel.setText("Playing: " + file.getName());
    }

    @FXML
    private void onPauseClick(){
        if(mediaPlayer != null){
            mediaPlayer.pause();
            currentSongLabel.setText("Paused: " + currentSong.getName());
        }
    }

    @FXML
    private void onStopClick(){
        if(mediaPlayer != null){
            mediaPlayer.stop();
            currentSongLabel.setText("Stopped");
        }
    }
}
