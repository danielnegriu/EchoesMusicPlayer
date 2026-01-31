package com.musicapp.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseConnector {
    private static final String URL = "jdbc:sqlite:musicapp.db";

    public static Connection connect(){
        try {
            Connection conn = DriverManager.getConnection(URL);
            return conn;
        } catch(SQLException e){
            System.out.println("Database connection error: " + e.getMessage());
            return null;
        }
    }
    
    public static void initializeDatabase() {
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
        """;
        
        String createSongsTable = """
            CREATE TABLE IF NOT EXISTS user_songs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                song_path TEXT NOT NULL,
                added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id),
                UNIQUE(user_id, song_path)
            );
        """;
        
        String createPlaylistsTable = """
            CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
        """;
        
        String createPlaylistSongsTable = """
            CREATE TABLE IF NOT EXISTS playlist_songs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                playlist_id INTEGER NOT NULL,
                song_path TEXT NOT NULL,
                position INTEGER NOT NULL,
                added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
            );
        """;
        
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createSongsTable);
            stmt.execute(createPlaylistsTable);
            stmt.execute(createPlaylistSongsTable);
            System.out.println("Database initialized successfully!");
        } catch (SQLException e) {
            System.out.println("Error initializing database: " + e.getMessage());
        }
    }
    
    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES(?, ?)";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password); // In production, hash this!
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Registration error: " + e.getMessage());
            return false;
        }
    }
    
    public static Integer loginUser(String username, String password) {
        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.out.println("Login error: " + e.getMessage());
        }
        return null;
    }
    
    public static void addSongForUser(int userId, String songPath) {
        String sql = "INSERT OR IGNORE INTO user_songs(user_id, song_path) VALUES(?, ?)";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, songPath);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error adding song: " + e.getMessage());
        }
    }
    
    public static void removeSongForUser(int userId, String songPath) {
        String sql = "DELETE FROM user_songs WHERE user_id = ? AND song_path = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, songPath);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error removing song: " + e.getMessage());
        }
    }
    
    public static List<String> getSongsForUser(int userId) {
        String sql = "SELECT song_path FROM user_songs WHERE user_id = ? ORDER BY added_at";
        List<String> songs = new ArrayList<>();
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                songs.add(rs.getString("song_path"));
            }
        } catch (SQLException e) {
            System.out.println("Error loading songs: " + e.getMessage());
        }
        return songs;
    }
    
    public static String getUsernameById(int userId) {
        String sql = "SELECT username FROM users WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            System.out.println("Error getting username: " + e.getMessage());
        }
        return null;
    }
    
    // Playlist methods
    public static int createPlaylist(int userId, String playlistName) {
        String insertSql = "INSERT INTO playlists(user_id, name) VALUES(?, ?)";
        String selectSql = "SELECT last_insert_rowid() as id";
        
        try (Connection conn = connect();
             PreparedStatement insertStmt = conn.prepareStatement(insertSql);
             PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            insertStmt.setInt(1, userId);
            insertStmt.setString(2, playlistName);
            insertStmt.executeUpdate();
            
            ResultSet rs = selectStmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.out.println("Error creating playlist: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }
    
    public static List<Playlist> getPlaylistsForUser(int userId) {
        String sql = "SELECT id, name, created_at FROM playlists WHERE user_id = ? ORDER BY created_at DESC";
        List<Playlist> playlists = new ArrayList<>();
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                playlists.add(new Playlist(
                    rs.getInt("id"),
                    rs.getString("name")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Error loading playlists: " + e.getMessage());
        }
        return playlists;
    }
    
    public static void deletePlaylist(int playlistId) {
        String sql = "DELETE FROM playlists WHERE id = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playlistId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error deleting playlist: " + e.getMessage());
        }
    }
    
    public static void addSongToPlaylist(int playlistId, String songPath) {
        // Get the next position
        String getMaxPos = "SELECT COALESCE(MAX(position), -1) + 1 as next_pos FROM playlist_songs WHERE playlist_id = ?";
        String insertSong = "INSERT INTO playlist_songs(playlist_id, song_path, position) VALUES(?, ?, ?)";
        
        try (Connection conn = connect()) {
            int nextPosition = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(getMaxPos)) {
                pstmt.setInt(1, playlistId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    nextPosition = rs.getInt("next_pos");
                }
            }
            
            try (PreparedStatement pstmt = conn.prepareStatement(insertSong)) {
                pstmt.setInt(1, playlistId);
                pstmt.setString(2, songPath);
                pstmt.setInt(3, nextPosition);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Error adding song to playlist: " + e.getMessage());
        }
    }
    
    public static void removeSongFromPlaylist(int playlistId, String songPath) {
        String sql = "DELETE FROM playlist_songs WHERE playlist_id = ? AND song_path = ?";
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playlistId);
            pstmt.setString(2, songPath);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error removing song from playlist: " + e.getMessage());
        }
    }
    
    public static List<String> getSongsInPlaylist(int playlistId) {
        String sql = "SELECT song_path FROM playlist_songs WHERE playlist_id = ? ORDER BY position";
        List<String> songs = new ArrayList<>();
        
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playlistId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                songs.add(rs.getString("song_path"));
            }
        } catch (SQLException e) {
            System.out.println("Error loading playlist songs: " + e.getMessage());
        }
        return songs;
    }
    
    // Inner class for Playlist
    public static class Playlist {
        private int id;
        private String name;
        
        public Playlist(int id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public int getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
}
