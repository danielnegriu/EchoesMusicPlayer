package com.musicapp.model;

public class Song {
    private String path;
    private String title;
    private String artist;
    private String duration;
    
    public Song(String path, String title, String artist, String duration) {
        this.path = path;
        this.title = title;
        this.artist = artist;
        this.duration = duration;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getArtist() {
        return artist;
    }
    
    public String getDuration() {
        return duration;
    }
    
    @Override
    public String toString() {
        String displayTitle = title != null ? title : "Unknown";
        String displayArtist = artist != null ? artist : "Unknown Artist";
        
        if (duration != null && !duration.equals("--:--") && !duration.equals("??:??")) {
            return displayTitle + " - " + displayArtist + " [" + duration + "]";
        }
        return displayTitle + " - " + displayArtist;
    }
}
