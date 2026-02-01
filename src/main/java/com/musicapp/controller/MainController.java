package com.musicapp.controller;

import com.musicapp.database.DatabaseConnector;
import com.musicapp.service.SpotifyService;
import com.musicapp.service.MoodClassifier;
import com.musicapp.model.Song;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.*;
import javafx.stage.FileChooser;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class MainController {
    @FXML
    private ListView<Song> songListView;
    @FXML
    private ListView<DatabaseConnector.Playlist> playlistListView;
    @FXML
    private Label currentSongLabel;
    @FXML
    private Label artistLabel;
    @FXML
    private Label usernameLabel;
    @FXML
    private Label currentPlaylistLabel;
    @FXML
    private Slider progressSlider;
    @FXML
    private Label currentTimeLabel;
    @FXML
    private Label totalTimeLabel;
    @FXML
    private ImageView albumArtworkView;

    private MediaPlayer mediaPlayer;
    private File currentSong;
    private boolean isPaused = false;
    private SpotifyService spotifyService;
    private int currentUserId;
    private String currentUsername;
    private DatabaseConnector.Playlist currentPlaylist = null;

    @FXML
    public void initialize() {
        // Inițializează serviciul Spotify
        spotifyService = new SpotifyService();
        spotifyService.authenticate();
        
        // Configurează listener-ul slider-ului de progres pentru derulare
        progressSlider.setOnMousePressed(event -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
            }
        });
        
        progressSlider.setOnMouseDragged(event -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
            }
        });
        
        // Setează imaginea implicită a albumului
        setDefaultAlbumArt();
        
        // Adaugă listener pentru selecția playlistului
        playlistListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadPlaylistSongs(newVal);
            }
        });
    }
    
    public void setUser(int userId, String username) {
        this.currentUserId = userId;
        this.currentUsername = username;
        this.usernameLabel.setText("Welcome, " + username + "!");
        
        // Încarcă melodiile salvate ale utilizatorului
        List<String> savedSongPaths = DatabaseConnector.getSongsForUser(userId);
        for (String path : savedSongPaths) {
            songListView.getItems().add(createSongFromPath(path));
        }
        
        // Încarcă playlisturile utilizatorului
        loadPlaylists();
    }

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
                String songPath = file.getAbsolutePath();
                DatabaseConnector.addSongForUser(currentUserId, songPath);
                // Adaugă la vizualizare doar dacă suntem în modul "Toate melodiile"
                if (currentPlaylist == null) {
                    songListView.getItems().add(createSongFromPath(songPath));
                }
            }
        }
    }

    @FXML
    private void onPlayClick(){
        if (isPaused && mediaPlayer != null) {
            // Reia redarea
            mediaPlayer.play();
            isPaused = false;
            currentSongLabel.setText("Playing: " + currentSong.getName());
        } else {
            // Redă melodia selectată
            Song selected = songListView.getSelectionModel().getSelectedItem();
            if(selected != null){
                playSong(new File(selected.getPath()));
            }
        }
    }

    private void playSong(File file){
        if(mediaPlayer != null){
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        isPaused = false;
        currentSong = file;
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        
        // Configurează actualizarea barei de progres
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!progressSlider.isValueChanging()) {
                progressSlider.setValue(newTime.toSeconds());
            }
            currentTimeLabel.setText(formatTime(newTime));
        });
        
        // Setează durata totală când este gata
        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getTotalDuration();
            progressSlider.setMax(total.toSeconds());
            totalTimeLabel.setText(formatTime(total));
        });
        
        mediaPlayer.play();
        
        // Metadate Spotify
        new Thread(() -> {
            SpotifyService.TrackInfo trackInfo = spotifyService.searchTrack(file.getName());
            Platform.runLater(() -> {
                if (trackInfo != null) {
                    currentSongLabel.setText("Playing: " + trackInfo.getTitle());
                    artistLabel.setText(trackInfo.getArtist());
                    
                    // Încarcă coperta albumului
                    if (trackInfo.getAlbumArtUrl() != null) {
                        try {
                            Image albumArt = new Image(trackInfo.getAlbumArtUrl(), true);
                            albumArtworkView.setImage(albumArt);
                        } catch (Exception e) {
                            setDefaultAlbumArt();
                        }
                    } else {
                        setDefaultAlbumArt();
                    }
                } else {
                    currentSongLabel.setText("Playing: " + file.getName());
                    artistLabel.setText("Artist information not available");
                    setDefaultAlbumArt();
                }
            });
        }).start();
    }

    @FXML
    private void onPauseClick(){
        if(mediaPlayer != null){
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                isPaused = true;
                currentSongLabel.setText("Paused: " + currentSong.getName());
            }
        }
    }

    @FXML
    private void onStopClick(){
        if(mediaPlayer != null){
            mediaPlayer.stop();
            isPaused = false;
            currentSongLabel.setText("Stopped");
            artistLabel.setText("");
            progressSlider.setValue(0);
            currentTimeLabel.setText("0:00");
            setDefaultAlbumArt();
        }
    }
    
    @FXML
    private void onNextClick() {
        if (songListView.getItems().isEmpty()) {
            return;
        }
        
        int currentIndex = songListView.getSelectionModel().getSelectedIndex();
        int nextIndex;
        
        // Dacă nicio melodie nu este selectată sau suntem la ultima melodie, mergi la prima melodie
        if (currentIndex == -1 || currentIndex >= songListView.getItems().size() - 1) {
            nextIndex = 0;
        } else {
            nextIndex = currentIndex + 1;
        }
        
        songListView.getSelectionModel().select(nextIndex);
        Song nextSong = songListView.getItems().get(nextIndex);
        playSong(new File(nextSong.getPath()));
    }
    
    @FXML
    private void onPreviousClick() {
        if (songListView.getItems().isEmpty()) {
            return;
        }
        
        int currentIndex = songListView.getSelectionModel().getSelectedIndex();
        int previousIndex;
        
        // Dacă nicio melodie nu este selectată sau suntem la prima melodie, mergi la ultima melodie
        if (currentIndex <= 0) {
            previousIndex = songListView.getItems().size() - 1;
        } else {
            previousIndex = currentIndex - 1;
        }
        
        songListView.getSelectionModel().select(previousIndex);
        Song previousSong = songListView.getItems().get(previousIndex);
        playSong(new File(previousSong.getPath()));
    }
    
    private String formatTime(Duration duration) {
        int minutes = (int) duration.toMinutes();
        int seconds = (int) duration.toSeconds() % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    private void setDefaultAlbumArt() {
        // Creează o imagine placeholder (poți înlocui aceasta cu o imagine implicită reală)
        albumArtworkView.setImage(null);
    }
    
    private Song createSongFromPath(String path) {
        // Extrage informații din numele fișierului - nu apela Spotify la încărcare (prea lent)
        String fileName = extractFileName(path);
        String title = fileName;
        String artist = "Unknown Artist";
        
        // Încearcă să parsezi formatul "Artist - Titlu" din numele fișierului
        if (path.contains(" - ")) {
            String baseName = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
            baseName = baseName.replaceAll("\\.(mp3|wav|ogg)$", "");
            if (baseName.contains(" - ")) {
                String[] parts = baseName.split(" - ", 2);
                artist = parts[0].trim();
                title = parts[1].trim();
            }
        }
        
        return new Song(path, title, artist, null);
    }
    
    private String extractFileName(String path) {
        String fileName = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
        fileName = fileName.replaceAll("\\.(mp3|wav|ogg)$", "");
        if (fileName.contains(" - ")) {
            return fileName.split(" - ", 2)[1].trim();
        }
        return fileName;
    }
    
    private void loadPlaylists() {
        playlistListView.getItems().clear();
        List<DatabaseConnector.Playlist> playlists = DatabaseConnector.getPlaylistsForUser(currentUserId);
        playlistListView.getItems().addAll(playlists);
    }
    
    private void loadPlaylistSongs(DatabaseConnector.Playlist playlist) {
        currentPlaylist = playlist;
        currentPlaylistLabel.setText(playlist.getName());
        songListView.getItems().clear();
        List<String> songPaths = DatabaseConnector.getSongsInPlaylist(playlist.getId());
        for (String path : songPaths) {
            songListView.getItems().add(createSongFromPath(path));
        }
    }
    
    @FXML
    private void onCreatePlaylistClick() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Playlist");
        dialog.setHeaderText("Enter playlist name:");
        dialog.setContentText("Name:");
        
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                DatabaseConnector.createPlaylist(currentUserId, name);
                loadPlaylists();
            }
        });
    }
    
    @FXML
    private void onDeletePlaylistClick() {
        DatabaseConnector.Playlist selected = playlistListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Playlist");
            alert.setHeaderText("Delete " + selected.getName() + "?");
            alert.setContentText("This action cannot be undone.");
            
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    DatabaseConnector.deletePlaylist(selected.getId());
                    loadPlaylists();
                    // Resetează la vizualizarea tuturor melodiilor
                    currentPlaylist = null;
                    currentPlaylistLabel.setText("All Songs");
                    songListView.getItems().clear();
                    List<String> songPaths = DatabaseConnector.getSongsForUser(currentUserId);
                    for (String path : songPaths) {
                        songListView.getItems().add(createSongFromPath(path));
                    }
                }
            });
        }
    }
    
    @FXML
    private void onAddToPlaylistClick() {
        Song selectedSong = songListView.getSelectionModel().getSelectedItem();
        if (selectedSong == null) {
            showAlert("No Song Selected", "Please select a song to add to a playlist.");
            return;
        }
        
        List<DatabaseConnector.Playlist> playlists = DatabaseConnector.getPlaylistsForUser(currentUserId);
        if (playlists.isEmpty()) {
            showAlert("No Playlists", "Please create a playlist first.");
            return;
        }
        
        ChoiceDialog<DatabaseConnector.Playlist> dialog = new ChoiceDialog<>(playlists.get(0), playlists);
        dialog.setTitle("Add to Playlist");
        dialog.setHeaderText("Select a playlist:");
        dialog.setContentText("Playlist:");
        
        dialog.showAndWait().ifPresent(playlist -> {
            DatabaseConnector.addSongToPlaylist(playlist.getId(), selectedSong.getPath());
            // Reîmprospătează dacă vizualizăm acest playlist
            if (currentPlaylist != null && currentPlaylist.getId() == playlist.getId()) {
                loadPlaylistSongs(currentPlaylist);
            }
        });
    }
    
    @FXML
    private void onRemoveFromPlaylistClick() {
        if (currentPlaylist == null) {
            showAlert("Not in Playlist View", "You can only remove songs when viewing a playlist.");
            return;
        }
        
        Song selectedSong = songListView.getSelectionModel().getSelectedItem();
        if (selectedSong != null) {
            DatabaseConnector.removeSongFromPlaylist(currentPlaylist.getId(), selectedSong.getPath());
            loadPlaylistSongs(currentPlaylist);
        }
    }
    
    @FXML
    private void onViewAllSongsClick() {
        currentPlaylist = null;
        currentPlaylistLabel.setText("All Songs");
        songListView.getItems().clear();
        List<String> songPaths = DatabaseConnector.getSongsForUser(currentUserId);
        for (String path : songPaths) {
            songListView.getItems().add(createSongFromPath(path));
        }
        playlistListView.getSelectionModel().clearSelection();
    }
    
    @FXML
    private void onGenerateMoodPlaylistsClick() {
        List<String> allSongs = DatabaseConnector.getSongsForUser(currentUserId);
        
        if (allSongs.isEmpty()) {
            showAlert("No Songs", "Please add some songs before generating mood playlists.");
            return;
        }
        
        // Afișează dialogul de progres
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Analyzing Songs");
        progressAlert.setHeaderText("Fetching audio features from Spotify...");
        progressAlert.setContentText("This may take a moment. Please wait.");
        progressAlert.show();
        
        // Analizează melodiile într-un fir de execuție în fundal
        new Thread(() -> {
            try {
                // Re-autentifică pentru a obține un token proaspăt
                spotifyService.authenticate();
                
                List<SpotifyService.AudioFeaturesData> audioFeaturesList = new ArrayList<>();
                
                // Obține caracteristicile audio pentru fiecare melodie - acum returnează întotdeauna o valoare
                for (String songPath : allSongs) {
                    SpotifyService.AudioFeaturesData features = spotifyService.getAudioFeatures(songPath);
                    audioFeaturesList.add(features); // Nu este nevoie de verificare null
                }
                
                if (audioFeaturesList.isEmpty()) {
                    Platform.runLater(() -> {
                        progressAlert.close();
                        showAlert("Analysis Failed", "Could not fetch audio features from Spotify. Make sure you have valid songs and an internet connection.");
                    });
                    return;
                }
                
                System.out.println("Starting mood classification with " + audioFeaturesList.size() + " songs...");
                
                // Afișează progresul clustering-ului pe firul UI
                Platform.runLater(() -> {
                    progressAlert.setHeaderText("Classifying songs by mood...");
                    progressAlert.setContentText("Using machine learning to analyze your music.");
                });
                
                // Clasifică melodiile pe stări folosind ML (păstrează în firul de fundal!)
                Map<MoodClassifier.Mood, List<String>> moodPlaylists = null;
                try {
                    moodPlaylists = MoodClassifier.classifySongsByMood(audioFeaturesList);
                    System.out.println("Mood classification completed successfully!");
                } catch (Exception mlError) {
                    System.err.println("Error during mood classification: " + mlError.getMessage());
                    mlError.printStackTrace();
                    Platform.runLater(() -> {
                        progressAlert.close();
                        showAlert("Classification Error", "Error during mood classification: " + mlError.getMessage());
                    });
                    return;
                }
                
                final Map<MoodClassifier.Mood, List<String>> finalMoodPlaylists = moodPlaylists;
                
                // Acum actualizează UI-ul cu rezultatele
                Platform.runLater(() -> {
                    progressAlert.close();
                    
                    // Creează playlisturi în baza de date
                    int playlistsCreated = 0;
                    StringBuilder summary = new StringBuilder("Created playlists:\n\n");
                    
                    for (MoodClassifier.Mood mood : MoodClassifier.Mood.values()) {
                        List<String> songs = finalMoodPlaylists.get(mood);
                        if (songs != null && !songs.isEmpty()) {
                            String playlistName = "🤖 " + mood.getDisplayName();
                            int playlistId = DatabaseConnector.createPlaylist(currentUserId, playlistName);
                            
                            for (String song : songs) {
                                DatabaseConnector.addSongToPlaylist(playlistId, song);
                            }
                            
                            playlistsCreated++;
                            summary.append("• ").append(playlistName)
                                   .append(": ").append(songs.size()).append(" songs\n");
                        }
                    }
                    
                    // Reîmprospătează vizualizarea playlisturilor
                    loadPlaylists();
                    
                    if (playlistsCreated > 0) {
                        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                        successAlert.setTitle("Success!");
                        successAlert.setHeaderText("Mood playlists generated using ML!");
                        successAlert.setContentText(summary.toString());
                        successAlert.showAndWait();
                    } else {
                        showAlert("No Playlists Created", "Could not classify songs into mood categories.");
                    }
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    progressAlert.close();
                    showAlert("Error", "An error occurred while generating mood playlists: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }
    
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
