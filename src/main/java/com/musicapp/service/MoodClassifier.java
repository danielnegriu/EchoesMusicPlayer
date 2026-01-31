package com.musicapp.service;

import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import java.util.*;

public class MoodClassifier {
    
    public enum Mood {
        ENERGETIC("Energetic", "High energy, upbeat songs"),
        CALM("Calm", "Relaxing, peaceful songs"),
        HAPPY("Happy", "Cheerful, positive songs"),
        MELANCHOLIC("Melancholic", "Sad, emotional songs");
        
        private final String displayName;
        private final String description;
        
        Mood(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    public static Map<Mood, List<String>> classifySongsByMood(
            List<SpotifyService.AudioFeaturesData> audioFeaturesList) {
        
        System.out.println("MoodClassifier: Starting classification...");
        
        if (audioFeaturesList == null || audioFeaturesList.isEmpty()) {
            System.out.println("MoodClassifier: Empty audio features list");
            return new HashMap<>();
        }
        
        System.out.println("MoodClassifier: Processing " + audioFeaturesList.size() + " songs");
        
        try {
            System.out.println("MoodClassifier: Creating dataset...");
            // Create dataset for clustering
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("energy"));
            attributes.add(new Attribute("valence"));
            attributes.add(new Attribute("danceability"));
            
            Instances dataset = new Instances("Songs", attributes, audioFeaturesList.size());
            
            System.out.println("MoodClassifier: Adding instances to dataset...");
            // Add instances
            for (SpotifyService.AudioFeaturesData features : audioFeaturesList) {
                Instance instance = new DenseInstance(3);
                instance.setValue(0, features.getEnergy());
                instance.setValue(1, features.getValence());
                instance.setValue(2, features.getDanceability());
                instance.setDataset(dataset); // Important: set the dataset for the instance
                dataset.add(instance);
            }
            
            System.out.println("MoodClassifier: Running k-means clustering...");
            // Perform k-means clustering with 4 clusters (one for each mood)
            SimpleKMeans kMeans = new SimpleKMeans();
            kMeans.setNumClusters(Math.min(4, dataset.numInstances())); // Handle case with fewer than 4 songs
            kMeans.buildClusterer(dataset);
            
            System.out.println("MoodClassifier: K-means clustering completed");
            System.out.println("MoodClassifier: Getting cluster assignments...");
            
            // Get cluster assignments
            int[] assignments = new int[audioFeaturesList.size()];
            for (int i = 0; i < dataset.numInstances(); i++) {
                assignments[i] = kMeans.clusterInstance(dataset.instance(i));
            }
            
            System.out.println("MoodClassifier: Analyzing centroids...");
            // Analyze cluster centroids to assign moods
            Instances centroids = kMeans.getClusterCentroids();
            double[][] centroidArray = new double[centroids.numInstances()][centroids.numAttributes()];
            for (int i = 0; i < centroids.numInstances(); i++) {
                for (int j = 0; j < centroids.numAttributes(); j++) {
                    centroidArray[i][j] = centroids.instance(i).value(j);
                }
            }
            
            System.out.println("MoodClassifier: Grouping songs by mood...");
            Map<Integer, Mood> clusterToMood = assignMoodsToClusters(centroidArray);
            
            // Group songs by mood
            Map<Mood, List<String>> moodToSongs = new HashMap<>();
            for (Mood mood : Mood.values()) {
                moodToSongs.put(mood, new ArrayList<>());
            }
            
            for (int i = 0; i < assignments.length; i++) {
                int cluster = assignments[i];
                Mood mood = clusterToMood.get(cluster);
                String songPath = audioFeaturesList.get(i).getSongPath();
                moodToSongs.get(mood).add(songPath);
            }
            
            System.out.println("MoodClassifier: Classification complete!");
            for (Mood moodResult : Mood.values()) {
                System.out.println("  " + moodResult.getDisplayName() + ": " + moodToSongs.get(moodResult).size() + " songs");
            }
            
            return moodToSongs;
            
        } catch (Exception e) {
            System.err.println("Error in mood classification: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }
    
    private static Map<Integer, Mood> assignMoodsToClusters(double[][] centroids) {
        // Analyze centroids to determine which cluster represents which mood
        // centroids[cluster][attribute] where attributes are: [energy, valence, danceability]
        
        Map<Integer, Mood> mapping = new HashMap<>();
        Mood[] moodArray = Mood.values();
        
        // Simple approach: assign each cluster to the best matching mood
        for (int cluster = 0; cluster < centroids.length; cluster++) {
            double energy = centroids[cluster][0];
            double valence = centroids[cluster][1];
            double danceability = centroids[cluster][2];
            
            // Calculate scores for each mood
            double energeticScore = energy * 0.6 + danceability * 0.4;
            double calmScore = (1 - energy) * 0.7 + (1 - danceability) * 0.3;
            double happyScore = valence * 0.7 + energy * 0.3;
            double melancholicScore = (1 - valence) * 0.8 + (1 - energy) * 0.2;
            
            // Find which mood has the highest score
            double maxScore = energeticScore;
            Mood bestMood = Mood.ENERGETIC;
            
            if (calmScore > maxScore) {
                maxScore = calmScore;
                bestMood = Mood.CALM;
            }
            if (happyScore > maxScore) {
                maxScore = happyScore;
                bestMood = Mood.HAPPY;
            }
            if (melancholicScore > maxScore) {
                bestMood = Mood.MELANCHOLIC;
            }
            
            mapping.put(cluster, bestMood);
            System.out.println("Cluster " + cluster + " -> " + bestMood.getDisplayName() + 
                             " (energy=" + String.format("%.2f", energy) + 
                             ", valence=" + String.format("%.2f", valence) + 
                             ", dance=" + String.format("%.2f", danceability) + ")");
        }
        
        return mapping;
    }
}
