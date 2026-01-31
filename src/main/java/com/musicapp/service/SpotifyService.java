package com.musicapp.service;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.AudioFeatures;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import se.michaelthelin.spotify.requests.data.search.simplified.SearchTracksRequest;
import se.michaelthelin.spotify.requests.data.tracks.GetAudioFeaturesForTrackRequest;

public class SpotifyService {
    private static final String CLIENT_ID = "f5367df15e144c49986a2f6b1165b561";
    private static final String CLIENT_SECRET = "3d7450b1fed7409cb482948db9e7a0e4";
    
    private SpotifyApi spotifyApi;
    private boolean isAuthenticated = false;
    
    public SpotifyService() {
        if (!CLIENT_ID.equals("your_client_id_here") && !CLIENT_SECRET.equals("your_client_secret_here")) {
            spotifyApi = new SpotifyApi.Builder()
                    .setClientId(CLIENT_ID)
                    .setClientSecret(CLIENT_SECRET)
                    .build();
        }
    }
    
    public void authenticate() {
        if (spotifyApi == null) {
            System.out.println("Spotify API not configured. Using filename metadata.");
            return;
        }
        
        try {
            ClientCredentialsRequest clientCredentialsRequest = spotifyApi.clientCredentials().build();
            ClientCredentials clientCredentials = clientCredentialsRequest.execute();
            spotifyApi.setAccessToken(clientCredentials.getAccessToken());
            isAuthenticated = true;
            System.out.println("Successfully authenticated with Spotify!");
        } catch (Exception e) {
            System.err.println("Spotify authentication failed. Using filename metadata. Error: " + e.getMessage());
            isAuthenticated = false;
        }
    }
    
    public TrackInfo searchTrack(String songName) {
        if (!isAuthenticated || spotifyApi == null) {
            return parseFileName(songName);
        }
        
        try {
            String searchQuery = songName.replaceAll("\\.(mp3|wav|ogg)$", "");
            searchQuery = searchQuery.substring(Math.max(searchQuery.lastIndexOf('/'), searchQuery.lastIndexOf('\\')) + 1);
            
            SearchTracksRequest searchTracksRequest = spotifyApi.searchTracks(searchQuery)
                    .limit(1)
                    .build();
            
            Paging<Track> trackPaging = searchTracksRequest.execute();
            
            if (trackPaging.getItems().length > 0) {
                Track track = trackPaging.getItems()[0];
                String artist = track.getArtists().length > 0 ? track.getArtists()[0].getName() : "Unknown Artist";
                String albumArtUrl = track.getAlbum().getImages().length > 0 
                        ? track.getAlbum().getImages()[0].getUrl() 
                        : null;
                
                return new TrackInfo(track.getName(), artist, albumArtUrl);
            }
        } catch (Exception e) {
            System.err.println("Error searching for track on Spotify. Using filename. Error: " + e.getMessage());
        }
        
        return parseFileName(songName);
    }
    
    private TrackInfo parseFileName(String fileName) {
        String cleanName = fileName.replaceAll("\\.(mp3|wav|ogg)$", "");
        
        cleanName = cleanName.substring(Math.max(cleanName.lastIndexOf('/'), cleanName.lastIndexOf('\\')) + 1);
        
        if (cleanName.contains(" - ")) {
            String[] parts = cleanName.split(" - ", 2);
            return new TrackInfo(parts[1].trim(), parts[0].trim(), null);
        }
        
        return new TrackInfo(cleanName, "Unknown Artist", null);
    }
    
    public AudioFeaturesData getAudioFeatures(String songName) {
        // Always return estimated features as fallback - never return null
        if (!isAuthenticated || spotifyApi == null) {
            System.out.println("Not authenticated, using estimated features for: " + songName);
            return generateEstimatedFeatures(songName);
        }
        
        try {
            String searchQuery = songName.replaceAll("\\.(mp3|wav|ogg)$", "");
            searchQuery = searchQuery.substring(Math.max(searchQuery.lastIndexOf('/'), searchQuery.lastIndexOf('\\')) + 1);
            
            SearchTracksRequest searchTracksRequest = spotifyApi.searchTracks(searchQuery)
                    .limit(1)
                    .build();
            
            Paging<Track> trackPaging = searchTracksRequest.execute();
            
            if (trackPaging.getItems().length > 0) {
                Track track = trackPaging.getItems()[0];
                String trackId = track.getId();
                
                GetAudioFeaturesForTrackRequest audioFeaturesRequest = spotifyApi
                        .getAudioFeaturesForTrack(trackId)
                        .build();
                
                AudioFeatures features = audioFeaturesRequest.execute();
                
                System.out.println("Found features for: " + track.getName());
                return new AudioFeaturesData(
                    songName,
                    features.getEnergy(),
                    features.getValence(),
                    features.getDanceability(),
                    features.getTempo(),
                    features.getAcousticness(),
                    features.getInstrumentalness()
                );
            } else {
                System.out.println("No match found on Spotify, using estimated features for: " + songName);
                return generateEstimatedFeatures(songName);
            }
        } catch (Exception e) {
            // Check if it's a Forbidden error (expired token)
            if (e.getMessage() != null && e.getMessage().contains("Forbidden")) {
                System.out.println("Token expired, re-authenticating...");
                authenticate(); // Try to re-authenticate
            }
            System.err.println("Error fetching audio features (" + e.getMessage() + "), using estimated features for: " + songName);
            return generateEstimatedFeatures(songName);
        }
    }
    
    private AudioFeaturesData generateEstimatedFeatures(String songName) {
        // Generate varied but plausible audio features based on song name hash
        // This ensures consistent features for the same song across runs
        int hash = songName.hashCode();
        java.util.Random random = new java.util.Random(hash);
        
        // Generate features with realistic distributions
        float energy = 0.3f + random.nextFloat() * 0.7f; // 0.3 to 1.0
        float valence = 0.2f + random.nextFloat() * 0.8f; // 0.2 to 1.0
        float danceability = 0.3f + random.nextFloat() * 0.6f; // 0.3 to 0.9
        float tempo = 80 + random.nextFloat() * 100; // 80 to 180 BPM
        float acousticness = random.nextFloat(); // 0 to 1.0
        float instrumentalness = random.nextFloat() * 0.5f; // 0 to 0.5
        
        return new AudioFeaturesData(
            songName,
            energy,
            valence,
            danceability,
            tempo,
            acousticness,
            instrumentalness
        );
    }
    
    public static class TrackInfo {
        private String title;
        private String artist;
        private String albumArtUrl;
        
        public TrackInfo(String title, String artist, String albumArtUrl) {
            this.title = title;
            this.artist = artist;
            this.albumArtUrl = albumArtUrl;
        }
        
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getAlbumArtUrl() { return albumArtUrl; }
    }
    
    public static class AudioFeaturesData {
        private String songPath;
        private float energy;
        private float valence;
        private float danceability;
        private float tempo;
        private float acousticness;
        private float instrumentalness;
        
        public AudioFeaturesData(String songPath, float energy, float valence, 
                                float danceability, float tempo, 
                                float acousticness, float instrumentalness) {
            this.songPath = songPath;
            this.energy = energy;
            this.valence = valence;
            this.danceability = danceability;
            this.tempo = tempo;
            this.acousticness = acousticness;
            this.instrumentalness = instrumentalness;
        }
        
        public String getSongPath() { return songPath; }
        public float getEnergy() { return energy; }
        public float getValence() { return valence; }
        public float getDanceability() { return danceability; }
        public float getTempo() { return tempo; }
        public float getAcousticness() { return acousticness; }
        public float getInstrumentalness() { return instrumentalness; }
    }
}
