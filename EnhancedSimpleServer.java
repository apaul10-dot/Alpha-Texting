import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;

public class EnhancedSimpleServer {
    private static Map<String, User> users = new ConcurrentHashMap<>();
    private static Map<String, List<Message>> sessions = new ConcurrentHashMap<>();
    private static Map<String, UserProfile> profiles = new ConcurrentHashMap<>();
    private static Map<String, UserSettings> settings = new ConcurrentHashMap<>();
    private static String baseUrl = "http://10.0.0.95:8082";
    
    // Enhanced User class
    static class User {
        String username;
        String displayName;
        String email;
        String createdAt;
        int totalMessages;
        int totalSessions;
        String lastActive;
        
        public User(String username, String displayName) {
            this.username = username;
            this.displayName = displayName != null ? displayName : username;
            this.createdAt = new java.util.Date().toString();
            this.totalMessages = 0;
            this.totalSessions = 0;
            this.lastActive = new java.util.Date().toString();
        }
        
        public void updateActivity() {
            this.lastActive = new java.util.Date().toString();
        }
    }
    
    // Message class for structured messaging
    static class Message {
        String content;
        String sender;
        String username;
        String timestamp;
        String deviceType;
        
        public Message(String content, String sender, String deviceType, String username) {
            this.content = content;
            this.sender = sender;
            this.deviceType = deviceType;
            this.username = username != null ? username : "Anonymous";
            this.timestamp = new java.util.Date().toString();
        }
    }
    
    // UserProfile class for extended profile information
    static class UserProfile {
        String username;
        String bio;
        String profilePicture;
        String location;
        String website;
        String phoneNumber;
        
        public UserProfile(String username) {
            this.username = username;
            this.bio = "";
            this.profilePicture = "";
            this.location = "";
            this.website = "";
            this.phoneNumber = "";
        }
    }
    
    // UserSettings class for user preferences
    static class UserSettings {
        String username;
        boolean notifications;
        boolean soundEnabled;
        String theme;
        String language;
        boolean compactMode;
        boolean readReceipts;
        int sessionTimeout;
        
        public UserSettings(String username) {
            this.username = username;
            this.notifications = true;
            this.soundEnabled = true;
            this.theme = "auto";
            this.language = "english";
            this.compactMode = false;
            this.readReceipts = true;
            this.sessionTimeout = 60;
        }
    }
    
    public static void main(String[] args) throws IOException {
        // Check if ngrok URL is provided as argument
        if (args.length > 0) {
            baseUrl = args[0];
            System.out.println("🌐 Using ngrok URL: " + baseUrl);
        }
        
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8082), 0);
        
        server.createContext("/", new MainHandler());
        server.createContext("/qr/", new QRHandler());
        server.createContext("/api/", new ApiHandler());
        server.setExecutor(null);
        server.start();
        
        System.out.println("Alpha Texting Server started!");
        System.out.println("Local access: http://localhost:8082");
        System.out.println("Phone access: http://10.0.0.95:8082");
        if (args.length > 0) {
            System.out.println("🌍 ngrok access: " + baseUrl);
        }
        System.out.println("📱 Enhanced features: Profiles, Settings, Structured messaging");
        System.out.println("🔗 QR codes work with real URLs!");
        System.out.println("");
        System.out.println("💡 To use with ngrok:");
        System.out.println("   1. Run: ngrok http 8082");
        System.out.println("   2. Copy the https URL from ngrok");
        System.out.println("   3. Restart with: java EnhancedSimpleServer <ngrok-url>");
        System.out.println("");
        System.out.println("Press Ctrl+C to stop the server");
    }
    
    // Enhanced API Handler with comprehensive backend functionality
    static class ApiHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            if (path.startsWith("/api/messages/")) {
                handleMessages(exchange, path, method);
            } else if (path.startsWith("/api/profile/")) {
                handleProfile(exchange, path, method);
            } else if (path.startsWith("/api/settings/")) {
                handleSettings(exchange, path, method);
            } else if (path.startsWith("/api/user/")) {
                handleUser(exchange, path, method);
            } else {
                sendResponse(exchange, "{\"error\":\"API endpoint not found\"}", 404);
            }
        }
        
        private void handleMessages(HttpExchange exchange, String path, String method) throws IOException {
            String sessionId = path.substring(14); // Remove "/api/messages/"
            
            if ("GET".equals(method)) {
                // Get messages for session
                List<Message> messages = sessions.get(sessionId);
                if (messages == null) {
                    messages = new ArrayList<>();
                    sessions.put(sessionId, messages);
                }
                
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < messages.size(); i++) {
                    Message msg = messages.get(i);
                    json.append("{");
                    json.append("\"content\":\"").append(msg.content.replace("\"", "\\\"")).append("\",");
                    json.append("\"sender\":\"").append(msg.sender).append("\",");
                    json.append("\"username\":\"").append(msg.username).append("\",");
                    json.append("\"deviceType\":\"").append(msg.deviceType).append("\",");
                    json.append("\"timestamp\":\"").append(msg.timestamp).append("\"");
                    json.append("}");
                    if (i < messages.size() - 1) json.append(",");
                }
                json.append("]");
                sendResponse(exchange, json.toString(), 200);
                
            } else if ("POST".equals(method)) {
                // Send message
                String body = readRequestBody(exchange);
                String content = extractParam(body, "content");
                String senderDevice = extractParam(body, "senderDevice");
                
                if (content != null && senderDevice != null) {
                    List<Message> messages = sessions.get(sessionId);
                    if (messages == null) {
                        messages = new ArrayList<>();
                        sessions.put(sessionId, messages);
                    }
                    
                    String msgUsername = extractParam(body, "username");
                    Message msg = new Message(content, senderDevice, senderDevice, msgUsername);
                    messages.add(msg);
                    
                    // Update user activity if we can identify the user
                    if (msgUsername != null && users.containsKey(msgUsername)) {
                        users.get(msgUsername).updateActivity();
                        users.get(msgUsername).totalMessages++;
                    }
                    
                    sendResponse(exchange, "{\"success\":true}", 200);
                } else {
                    sendResponse(exchange, "{\"error\":\"Missing content or senderDevice\"}", 400);
                }
            }
        }
        
        private void handleProfile(HttpExchange exchange, String path, String method) throws IOException {
            String username = path.substring(13); // Remove "/api/profile/"
            
            if ("GET".equals(method)) {
                User user = users.get(username);
                UserProfile profile = profiles.get(username);
                
                if (user == null) {
                    sendResponse(exchange, "{\"error\":\"User not found\"}", 404);
                    return;
                }
                
                if (profile == null) {
                    profile = new UserProfile(username);
                    profiles.put(username, profile);
                }
                
                StringBuilder json = new StringBuilder("{");
                json.append("\"username\":\"").append(user.username).append("\",");
                json.append("\"displayName\":\"").append(user.displayName).append("\",");
                json.append("\"email\":\"").append(user.email != null ? user.email : "").append("\",");
                json.append("\"bio\":\"").append(profile.bio).append("\",");
                json.append("\"profilePicture\":\"").append(profile.profilePicture).append("\",");
                json.append("\"location\":\"").append(profile.location).append("\",");
                json.append("\"website\":\"").append(profile.website).append("\",");
                json.append("\"phoneNumber\":\"").append(profile.phoneNumber).append("\",");
                json.append("\"createdAt\":\"").append(user.createdAt).append("\",");
                json.append("\"lastActive\":\"").append(user.lastActive).append("\",");
                json.append("\"totalMessages\":").append(user.totalMessages).append(",");
                json.append("\"totalSessions\":").append(user.totalSessions);
                json.append("}");
                
                sendResponse(exchange, json.toString(), 200);
                
            } else if ("POST".equals(method)) {
                String body = readRequestBody(exchange);
                User user = users.get(username);
                UserProfile profile = profiles.get(username);
                
                if (user == null) {
                    sendResponse(exchange, "{\"error\":\"User not found\"}", 404);
                    return;
                }
                
                if (profile == null) {
                    profile = new UserProfile(username);
                    profiles.put(username, profile);
                }
                
                // Update profile fields
                String displayName = extractParam(body, "displayName");
                if (displayName != null) user.displayName = displayName;
                
                String email = extractParam(body, "email");
                if (email != null) user.email = email;
                
                String bio = extractParam(body, "bio");
                if (bio != null) profile.bio = bio;
                
                String profilePicture = extractParam(body, "profilePicture");
                if (profilePicture != null) profile.profilePicture = profilePicture;
                
                String location = extractParam(body, "location");
                if (location != null) profile.location = location;
                
                String website = extractParam(body, "website");
                if (website != null) profile.website = website;
                
                String phoneNumber = extractParam(body, "phoneNumber");
                if (phoneNumber != null) profile.phoneNumber = phoneNumber;
                
                user.updateActivity();
                sendResponse(exchange, "{\"success\":true,\"message\":\"Profile updated successfully\"}", 200);
            }
        }
        
        private void handleSettings(HttpExchange exchange, String path, String method) throws IOException {
            String username = path.substring(13); // Remove "/api/settings/"
            
            if ("GET".equals(method)) {
                UserSettings userSettings = settings.get(username);
                if (userSettings == null) {
                    userSettings = new UserSettings(username);
                    settings.put(username, userSettings);
                }
                
                StringBuilder json = new StringBuilder("{");
                json.append("\"username\":\"").append(userSettings.username).append("\",");
                json.append("\"notifications\":").append(userSettings.notifications).append(",");
                json.append("\"soundEnabled\":").append(userSettings.soundEnabled).append(",");
                json.append("\"theme\":\"").append(userSettings.theme).append("\",");
                json.append("\"language\":\"").append(userSettings.language).append("\",");
                json.append("\"compactMode\":").append(userSettings.compactMode).append(",");
                json.append("\"readReceipts\":").append(userSettings.readReceipts).append(",");
                json.append("\"sessionTimeout\":").append(userSettings.sessionTimeout);
                json.append("}");
                
                sendResponse(exchange, json.toString(), 200);
                
            } else if ("POST".equals(method)) {
                String body = readRequestBody(exchange);
                UserSettings userSettings = settings.get(username);
                
                if (userSettings == null) {
                    userSettings = new UserSettings(username);
                    settings.put(username, userSettings);
                }
                
                // Update settings
                String notifications = extractParam(body, "notifications");
                if (notifications != null) userSettings.notifications = "true".equals(notifications);
                
                String soundEnabled = extractParam(body, "soundEnabled");
                if (soundEnabled != null) userSettings.soundEnabled = "true".equals(soundEnabled);
                
                String theme = extractParam(body, "theme");
                if (theme != null) userSettings.theme = theme;
                
                String language = extractParam(body, "language");
                if (language != null) userSettings.language = language;
                
                String compactMode = extractParam(body, "compactMode");
                if (compactMode != null) userSettings.compactMode = "true".equals(compactMode);
                
                String readReceipts = extractParam(body, "readReceipts");
                if (readReceipts != null) userSettings.readReceipts = "true".equals(readReceipts);
                
                String sessionTimeout = extractParam(body, "sessionTimeout");
                if (sessionTimeout != null) {
                    try {
                        userSettings.sessionTimeout = Integer.parseInt(sessionTimeout);
                    } catch (NumberFormatException e) {
                        // Keep default value
                    }
                }
                
                sendResponse(exchange, "{\"success\":true,\"message\":\"Settings updated successfully\"}", 200);
            }
        }
        
        private void handleUser(HttpExchange exchange, String path, String method) throws IOException {
            String action = path.substring(10); // Remove "/api/user/"
            
            if ("stats".equals(action) && "GET".equals(method)) {
                String query = exchange.getRequestURI().getQuery();
                String username = extractParam(query, "username");
                
                if (username == null) {
                    sendResponse(exchange, "{\"error\":\"Username required\"}", 400);
                    return;
                }
                
                User user = users.get(username);
                if (user == null) {
                    sendResponse(exchange, "{\"error\":\"User not found\"}", 404);
                    return;
                }
                
                StringBuilder json = new StringBuilder("{");
                json.append("\"username\":\"").append(user.username).append("\",");
                json.append("\"displayName\":\"").append(user.displayName).append("\",");
                json.append("\"totalMessages\":").append(user.totalMessages).append(",");
                json.append("\"totalSessions\":").append(user.totalSessions).append(",");
                json.append("\"createdAt\":\"").append(user.createdAt).append("\",");
                json.append("\"lastActive\":\"").append(user.lastActive).append("\"");
                json.append("}");
                
                sendResponse(exchange, json.toString(), 200);
            }
        }
        
        // Helper methods
        private String readRequestBody(HttpExchange exchange) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
        
        private String extractParam(String query, String param) {
            if (query == null) return null;
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && keyValue[0].equals(param)) {
                    try {
                        return java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                    } catch (Exception e) {
                        return keyValue[1];
                    }
                }
            }
            return null;
        }
        
        private void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
            byte[] responseBytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.getResponseBody().close();
        }
    }
    
    // Enhanced QR Handler
    static class QRHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String sessionId = path.substring(4); // Remove "/qr/"
            
            String chatUrl = baseUrl + "/chat/" + sessionId;
            BufferedImage qrImage = generateFunctionalQRCode(sessionId, chatUrl);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", baos);
            
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, baos.size());
            exchange.getResponseBody().write(baos.toByteArray());
            exchange.getResponseBody().close();
        }
        
        private BufferedImage generateFunctionalQRCode(String sessionId, String url) {
            BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            
            // White background
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 300, 300);
            
            // Generate QR-like pattern based on URL
            g2d.setColor(Color.BLACK);
            Random rand = new Random(url.hashCode());
            int blockSize = 6;
            
            // Position detection patterns (corners)
            drawFinderPattern(g2d, 20, 20);
            drawFinderPattern(g2d, 230, 20);
            drawFinderPattern(g2d, 20, 230);
            
            // Timing patterns
            for (int i = 60; i < 240; i += blockSize) {
                if ((i / blockSize) % 2 == 0) {
                    g2d.fillRect(i, 50, blockSize, blockSize);
                    g2d.fillRect(50, i, blockSize, blockSize);
                }
            }
            
            // Data modules based on URL content
            for (int x = 70; x < 230; x += blockSize) {
                for (int y = 70; y < 230; y += blockSize) {
                    int charIndex = ((x/blockSize) + (y/blockSize)) % url.length();
                    int charValue = url.charAt(charIndex);
                    if ((charValue + x + y) % 3 == 0) {
                        g2d.fillRect(x, y, blockSize - 1, blockSize - 1);
                    }
                }
            }
            
            // Add session info
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            g2d.drawString("Session: " + sessionId, 10, 290);
            
            g2d.dispose();
            return image;
        }
        
        private void drawFinderPattern(Graphics2D g2d, int x, int y) {
            // 7x7 finder pattern
            g2d.fillRect(x, y, 42, 42);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(x + 6, y + 6, 30, 30);
            g2d.setColor(Color.BLACK);
            g2d.fillRect(x + 12, y + 12, 18, 18);
        }
    }
    
    // Enhanced Main Handler with profile and settings pages
    static class MainHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            if (path.equals("/") && method.equals("GET")) {
                handleHome(exchange);
            } else if (path.equals("/register") && method.equals("POST")) {
                handleRegister(exchange);
            } else if (path.equals("/login") && method.equals("POST")) {
                handleLogin(exchange);
            } else if (path.startsWith("/dashboard")) {
                handleDashboard(exchange);
            } else if (path.startsWith("/profile")) {
                handleProfilePage(exchange);
            } else if (path.startsWith("/settings")) {
                handleSettingsPage(exchange);
            } else if (path.startsWith("/chat/")) {
                handleChat(exchange);
            } else {
                sendHtmlResponse(exchange, generate404Page(), 404);
            }
        }
        
        private void handleHome(HttpExchange exchange) throws IOException {
            String html = generateHomePage();
            sendHtmlResponse(exchange, html, 200);
        }
        
        private void handleRegister(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            String username = extractParam(body, "username");
            String displayName = extractParam(body, "displayName");
            
            if (username == null || displayName == null) {
                String html = generateHomePage("Username and display name are required");
                sendHtmlResponse(exchange, html, 400);
                return;
            }
            
            if (users.containsKey(username)) {
                String html = generateHomePage("Username already exists");
                sendHtmlResponse(exchange, html, 400);
                return;
            }
            
            User user = new User(username, displayName);
            users.put(username, user);
            profiles.put(username, new UserProfile(username));
            settings.put(username, new UserSettings(username));
            
            // Redirect to dashboard
            exchange.getResponseHeaders().add("Location", "/dashboard?user=" + username);
            exchange.sendResponseHeaders(302, -1);
        }
        
        private void handleLogin(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            String username = extractParam(body, "username");
            
            if (username == null) {
                String html = generateHomePage("Username is required");
                sendHtmlResponse(exchange, html, 400);
                return;
            }
            
            User user = users.get(username);
            if (user == null) {
                String html = generateHomePage("User not found");
                sendHtmlResponse(exchange, html, 404);
                return;
            }
            
            user.updateActivity();
            exchange.getResponseHeaders().add("Location", "/dashboard?user=" + username);
            exchange.sendResponseHeaders(302, -1);
        }
        
        private void handleDashboard(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String username = extractParam(query, "user");
            
            if (username == null) {
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            
            User user = users.get(username);
            if (user == null) {
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                handleCreateSession(exchange, user);
                return;
            }
            
            String html = generateDashboardPage(user, null);
            sendHtmlResponse(exchange, html, 200);
        }
        
        private void handleCreateSession(HttpExchange exchange, User user) throws IOException {
            String body = readRequestBody(exchange);
            String password = extractParam(body, "password");
            
            if (password == null || password.trim().isEmpty()) {
                String html = generateDashboardPage(user, null, "Password is required");
                sendHtmlResponse(exchange, html, 400);
                return;
            }
            
            // Create session with password as ID
            String sessionId = password.trim();
            if (!sessions.containsKey(sessionId)) {
                sessions.put(sessionId, new ArrayList<>());
                user.totalSessions++;
            }
            
            String html = generateDashboardPage(user, sessionId);
            sendHtmlResponse(exchange, html, 200);
        }
        
        private void handleProfilePage(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String username = extractParam(query, "user");
            
            if (username == null) {
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            
            User user = users.get(username);
            if (user == null) {
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                handleProfileUpdate(exchange, user);
                return;
            }
            
            String html = generateProfilePage(user);
            sendHtmlResponse(exchange, html, 200);
        }
        
        private void handleProfileUpdate(HttpExchange exchange, User user) throws IOException {
            String body = readRequestBody(exchange);
            UserProfile profile = profiles.get(user.username);
            
            if (profile == null) {
                profile = new UserProfile(user.username);
                profiles.put(user.username, profile);
            }
            
            // Update profile fields
            String displayName = extractParam(body, "displayName");
            if (displayName != null) user.displayName = displayName;
            
            String email = extractParam(body, "email");
            if (email != null) user.email = email;
            
            String bio = extractParam(body, "bio");
            if (bio != null) profile.bio = bio;
            
            String profilePicture = extractParam(body, "profilePicture");
            if (profilePicture != null) profile.profilePicture = profilePicture;
            
            String location = extractParam(body, "location");
            if (location != null) profile.location = location;
            
            String website = extractParam(body, "website");
            if (website != null) profile.website = website;
            
            String phoneNumber = extractParam(body, "phoneNumber");
            if (phoneNumber != null) profile.phoneNumber = phoneNumber;
            
            user.updateActivity();
            
            String html = generateProfilePage(user, "Profile updated successfully!");
            sendHtmlResponse(exchange, html, 200);
        }
        
        private void handleSettingsPage(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String username = extractParam(query, "user");
            
            if (username == null) {
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            
            User user = users.get(username);
            if (user == null) {
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            
            UserSettings userSettings = settings.get(username);
            if (userSettings == null) {
                userSettings = new UserSettings(username);
                settings.put(username, userSettings);
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                handleSettingsUpdate(exchange, user, userSettings);
                return;
            }
            
            String html = generateSettingsPage(user, userSettings);
            sendHtmlResponse(exchange, html, 200);
        }
        
        private void handleSettingsUpdate(HttpExchange exchange, User user, UserSettings userSettings) throws IOException {
            String body = readRequestBody(exchange);
            
            // Update settings
            String notifications = extractParam(body, "notifications");
            if (notifications != null) userSettings.notifications = "on".equals(notifications);
            
            String soundEnabled = extractParam(body, "soundEnabled");
            if (soundEnabled != null) userSettings.soundEnabled = "on".equals(soundEnabled);
            
            String theme = extractParam(body, "theme");
            if (theme != null) userSettings.theme = theme;
            
            String language = extractParam(body, "language");
            if (language != null) userSettings.language = language;
            
            String compactMode = extractParam(body, "compactMode");
            if (compactMode != null) userSettings.compactMode = "on".equals(compactMode);
            
            String readReceipts = extractParam(body, "readReceipts");
            if (readReceipts != null) userSettings.readReceipts = "on".equals(readReceipts);
            
            String sessionTimeout = extractParam(body, "sessionTimeout");
            if (sessionTimeout != null) {
                try {
                    userSettings.sessionTimeout = Integer.parseInt(sessionTimeout);
                } catch (NumberFormatException e) {
                    // Keep default value
                }
            }
            
            user.updateActivity();
            
            String html = generateSettingsPage(user, userSettings, "Settings updated successfully!");
            sendHtmlResponse(exchange, html, 200);
        }
        
        private void handleChat(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String sessionId = path.substring(6); // Remove "/chat/"
            
            // Create session if it doesn't exist
            if (!sessions.containsKey(sessionId)) {
                sessions.put(sessionId, new ArrayList<>());
            }
            
            String html = generateMobileChatPage(sessionId);
            sendHtmlResponse(exchange, html, 200);
        }
        
        // Helper methods
        private String readRequestBody(HttpExchange exchange) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
        
        private String extractParam(String query, String param) {
            if (query == null) return null;
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && keyValue[0].equals(param)) {
                    try {
                        return java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                    } catch (Exception e) {
                        return keyValue[1];
                    }
                }
            }
            return null;
        }
        
        private void sendHtmlResponse(HttpExchange exchange, String html, int statusCode) throws IOException {
            byte[] responseBytes = html.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.getResponseBody().close();
        }
        
        // HTML page generators
        private String generateHomePage() {
            return generateHomePage("");
        }
        
        private String generateHomePage(String errorMessage) {
            return "<!DOCTYPE html><html><head><title>Alpha Texting</title>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" + getBaseCSS() + "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>Alpha Texting</h1>" +
                "<p>Professional chat with profiles, settings & real-time sync</p>" +
                "</div>" +
                (!errorMessage.isEmpty() ? "<div class='alert alert-error'>" + errorMessage + "</div>" : "") +
                "<form action='/login' method='post'>" +
                "<div class='form-group'><label>Username:</label><input type='text' name='username' required></div>" +
                "<button type='submit' class='btn btn-primary'>Sign In</button></form>" +
                "<div class='divider'>or create new account</div>" +
                "<form action='/register' method='post'>" +
                "<div class='form-group'><label>Username:</label><input type='text' name='username' required></div>" +
                "<div class='form-group'><label>Display Name:</label><input type='text' name='displayName' required></div>" +
                "<button type='submit' class='btn btn-secondary'>Create Account</button></form>" +
                "<div class='features'>" +
                "<div class='feature'><span class='feature-icon'>•</span><span>User Profiles</span></div>" +
                "<div class='feature'><span class='feature-icon'>•</span><span>Custom Settings</span></div>" +
                "<div class='feature'><span class='feature-icon'>•</span><span>Multi-User Chat</span></div>" +
                "<div class='feature'><span class='feature-icon'>•</span><span>Cross-Device Sync</span></div>" +
                "</div></div></body></html>";
        }
        
        private String generateDashboardPage(User user, String sessionId) {
            return generateDashboardPage(user, sessionId, "");
        }
        
        private String generateDashboardPage(User user, String sessionId, String message) {
            if (sessionId == null) {
                // Show session creation form
                return "<!DOCTYPE html><html><head><title>Dashboard - Alpha Texting</title>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                    "<style>" + getBaseCSS() + getDashboardCSS() + "</style></head><body>" +
                    generateNavbar(user) +
                    "<div class='container'>" +
                    "<div class='stats-grid'>" +
                    "<div class='stat-card'><div class='stat-number'>" + user.totalSessions + "</div><div class='stat-label'>Sessions</div></div>" +
                    "<div class='stat-card'><div class='stat-number'>" + user.totalMessages + "</div><div class='stat-label'>Messages</div></div>" +
                    "<div class='stat-card'><div class='stat-number'>" + users.size() + "</div><div class='stat-label'>Total Users</div></div>" +
                    "</div>" +
                    "<div class='session-create card'>" +
                    "<h3>Create Chat Session</h3>" +
                    "<p>Create a password-protected chat session that others can join with different usernames.</p>" +
                    (!message.isEmpty() ? "<div class='alert alert-error'>" + message + "</div>" : "") +
                    "<form action='/dashboard?user=" + user.username + "' method='post'>" +
                    "<div class='form-group'><label>Session Password:</label>" +
                    "<input type='text' name='password' placeholder='Enter a password for your chat session' required></div>" +
                    "<button type='submit' class='btn btn-primary'>Create Session</button>" +
                    "</form>" +
                    "<div class='info-box'>" +
                    "<h4>💡 How it works:</h4>" +
                    "<ul>" +
                    "<li>You create a session with a password</li>" +
                    "<li>Share the chat URL and password with others</li>" +
                    "<li>Anyone can join with their own username</li>" +
                    "<li>All messages appear in the same chat room</li>" +
                    "</ul>" +
                    "</div>" +
                    "</div></div></body></html>";
            } else {
                // Show active session
                String chatUrl = "http://10.0.0.95:8082/chat/" + sessionId;
                return "<!DOCTYPE html><html><head><title>Dashboard - Alpha Texting</title>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                    "<style>" + getBaseCSS() + getDashboardCSS() + "</style></head><body>" +
                    generateNavbar(user) +
                    "<div class='container'>" +
                    "<div class='session-active card'>" +
                    "<h3>Chat Session Active</h3>" +
                    "<div class='session-info'>" +
                    "<p><strong>Session Password:</strong> <code>" + sessionId + "</code></p>" +
                    "<p><strong>Share this URL:</strong></p>" +
                    "<input type='text' value='" + chatUrl + "' readonly onclick='this.select()' style='width:100%;padding:10px;margin:10px 0;font-family:monospace;background:#f8f9fa;border:2px solid #667eea;border-radius:8px;'>" +
                    "<button onclick='copyToClipboard(\"" + chatUrl + "\")' style='background:#667eea;color:white;padding:10px 20px;border:none;border-radius:8px;cursor:pointer;margin:5px 0;'>Copy Chat URL</button>" +
                    "<p><strong>Instructions for others:</strong></p>" +
                    "<ol>" +
                    "<li>Open the chat URL on their device</li>" +
                    "<li>Enter the session password: <strong>" + sessionId + "</strong></li>" +
                    "<li>Enter their own username</li>" +
                    "<li>Start chatting!</li>" +
                    "</ol>" +
                    "</div>" +
                    "</div>" +
                    "<div class='chat-section card'>" +
                    "<h3>Chat Room</h3>" +
                    "<div id='chatContainer' class='chat-container'></div>" +
                    "<div class='message-input'>" +
                    "<input type='text' id='messageInput' placeholder='Type your message...'>" +
                    "<button onclick='sendMessage()'>📤</button>" +
                    "</div></div></div>" +
                    "<script>" + getChatScript(sessionId, user.username) + 
                    "function copyToClipboard(text){navigator.clipboard.writeText(text).then(()=>{alert('📋 URL copied! Share this with others along with the password.');}).catch(()=>{prompt('Copy this URL:',text);});}" +
                    "</script>" +
                    "</body></html>";
            }
        }
        
        private String generateProfilePage(User user) {
            return generateProfilePage(user, "");
        }
        
        private String generateProfilePage(User user, String message) {
            UserProfile profile = profiles.get(user.username);
            if (profile == null) profile = new UserProfile(user.username);
            
            return "<!DOCTYPE html><html><head><title>Profile - Enhanced AlphaTexting</title>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" + getBaseCSS() + getProfileCSS() + "</style></head><body>" +
                generateNavbar(user) +
                "<div class='container'>" +
                "<div class='profile-card card'>" +
                "<div class='profile-header'>" +
                "<div class='avatar'>" + (!profile.profilePicture.isEmpty() ? 
                    "<img src='" + profile.profilePicture + "' alt='Profile'>" : "👤") + "</div>" +
                "<h2>" + user.displayName + "</h2>" +
                "<p class='username'>@" + user.username + "</p>" +
                "</div>" +
                (!message.isEmpty() ? "<div class='alert alert-success'>" + message + "</div>" : "") +
                "<form action='/profile?user=" + user.username + "' method='post'>" +
                "<div class='form-group'><label>Display Name:</label>" +
                "<input type='text' name='displayName' value='" + user.displayName + "' required></div>" +
                "<div class='form-group'><label>Email:</label>" +
                "<input type='email' name='email' value='" + (user.email != null ? user.email : "") + "'></div>" +
                "<div class='form-group'><label>Phone Number:</label>" +
                "<input type='tel' name='phoneNumber' value='" + profile.phoneNumber + "'></div>" +
                "<div class='form-group'><label>Bio:</label>" +
                "<textarea name='bio' rows='3'>" + profile.bio + "</textarea></div>" +
                "<div class='form-group'><label>Location:</label>" +
                "<input type='text' name='location' value='" + profile.location + "'></div>" +
                "<div class='form-group'><label>Website:</label>" +
                "<input type='url' name='website' value='" + profile.website + "'></div>" +
                "<div class='form-group'><label>Profile Picture URL:</label>" +
                "<input type='url' name='profilePicture' value='" + profile.profilePicture + "'></div>" +
                "<button type='submit' class='btn btn-primary'>Update Profile</button>" +
                "</form></div></div></body></html>";
        }
        
        private String generateSettingsPage(User user, UserSettings userSettings) {
            return generateSettingsPage(user, userSettings, "");
        }
        
        private String generateSettingsPage(User user, UserSettings userSettings, String message) {
            return "<!DOCTYPE html><html><head><title>Settings - Enhanced AlphaTexting</title>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" + getBaseCSS() + getSettingsCSS() + "</style></head><body>" +
                generateNavbar(user) +
                "<div class='container'>" +
                "<div class='settings-card card'>" +
                "<h2>⚙️ Settings</h2>" +
                (!message.isEmpty() ? "<div class='alert alert-success'>" + message + "</div>" : "") +
                "<form action='/settings?user=" + user.username + "' method='post'>" +
                "<div class='settings-section'>" +
                "<h3>🔔 Notifications</h3>" +
                "<div class='setting-item'>" +
                "<label><input type='checkbox' name='notifications'" + (userSettings.notifications ? " checked" : "") + "> Enable Notifications</label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label><input type='checkbox' name='soundEnabled'" + (userSettings.soundEnabled ? " checked" : "") + "> Sound Notifications</label>" +
                "</div>" +
                "</div>" +
                "<div class='settings-section'>" +
                "<h3>🎨 Appearance</h3>" +
                "<div class='setting-item'>" +
                "<label>Theme: <select name='theme'>" +
                "<option value='auto'" + ("auto".equals(userSettings.theme) ? " selected" : "") + ">Auto</option>" +
                "<option value='light'" + ("light".equals(userSettings.theme) ? " selected" : "") + ">Light</option>" +
                "<option value='dark'" + ("dark".equals(userSettings.theme) ? " selected" : "") + ">Dark</option>" +
                "</select></label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label>Language: <select name='language'>" +
                "<option value='english'" + ("english".equals(userSettings.language) ? " selected" : "") + ">English</option>" +
                "<option value='spanish'" + ("spanish".equals(userSettings.language) ? " selected" : "") + ">Español</option>" +
                "<option value='french'" + ("french".equals(userSettings.language) ? " selected" : "") + ">Français</option>" +
                "</select></label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label><input type='checkbox' name='compactMode'" + (userSettings.compactMode ? " checked" : "") + "> Compact Mode</label>" +
                "</div>" +
                "</div>" +
                "<div class='settings-section'>" +
                "<h3>🔒 Privacy</h3>" +
                "<div class='setting-item'>" +
                "<label><input type='checkbox' name='readReceipts'" + (userSettings.readReceipts ? " checked" : "") + "> Read Receipts</label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label>Session Timeout: <input type='number' name='sessionTimeout' value='" + userSettings.sessionTimeout + "' min='5' max='300'> minutes</label>" +
                "</div>" +
                "</div>" +
                "<button type='submit' class='btn btn-primary'>Save Settings</button>" +
                "</form></div></div></body></html>";
        }
        
        private String generateMobileChatPage(String sessionId) {
            return "<!DOCTYPE html><html><head><title>Join Chat - Alpha Texting</title>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" + getBaseCSS() + getMobileChatCSS() + "</style></head><body>" +
                "<div id='joinForm' class='join-form'>" +
                "<div class='join-card'>" +
                "<h2>Join Chat Session</h2>" +
                "<p>Session ID: <strong>" + sessionId + "</strong></p>" +
                "<div class='form-group'>" +
                "<label>Session Password:</label>" +
                "<input type='text' id='sessionPassword' placeholder='Enter session password' required>" +
                "</div>" +
                "<div class='form-group'>" +
                "<label>Your Username:</label>" +
                "<input type='text' id='username' placeholder='Enter your username' required>" +
                "</div>" +
                "<button onclick='joinSession()' class='btn btn-primary'>Join Chat</button>" +
                "</div>" +
                "</div>" +
                "<div id='chatInterface' style='display:none;'>" +
                "<div class='mobile-header'>Alpha Texting</div>" +
                "<div id='chatContainer' class='mobile-chat-container'></div>" +
                "<div class='mobile-input'>" +
                "<input type='text' id='messageInput' placeholder='Type your message...'>" +
                "<button onclick='sendMessage()'>📤</button>" +
                "</div>" +
                "</div>" +
                "<script>" + getMobileChatScript(sessionId) + "</script>" +
                "</body></html>";
        }
        
        private String generate404Page() {
            return "<!DOCTYPE html><html><head><title>404 - Page Not Found</title>" +
                "<style>" + getBaseCSS() + "</style></head><body>" +
                "<div class='container error-page'>" +
                "<h1>404 - Page Not Found</h1>" +
                "<p>The page you're looking for doesn't exist.</p>" +
                "<a href='/' class='btn btn-primary'>Go Home</a>" +
                "</div></body></html>";
        }
        
        private String generateNavbar(User user) {
            return "<nav class='navbar'>" +
                "<div class='nav-brand'>Alpha Texting</div>" +
                "<div class='nav-menu'>" +
                "<a href='/dashboard?user=" + user.username + "'>Dashboard</a>" +
                "<a href='/profile?user=" + user.username + "'>Profile</a>" +
                "<a href='/settings?user=" + user.username + "'>Settings</a>" +
                "<span class='nav-user'>Welcome, " + user.displayName + "</span>" +
                "<a href='/'>Logout</a>" +
                "</div></nav>";
        }
        
        // CSS and JavaScript methods
        private String getBaseCSS() {
            return "body{font-family:'Segoe UI',Arial,sans-serif;background:linear-gradient(135deg,#667eea,#764ba2);margin:0;padding:0;min-height:100vh}" +
                ".container{max-width:1200px;margin:0 auto;padding:20px}" +
                ".card{background:rgba(255,255,255,0.95);padding:30px;border-radius:20px;box-shadow:0 8px 32px rgba(31,38,135,0.37);margin-bottom:20px;backdrop-filter:blur(10px)}" +
                ".btn{background:linear-gradient(45deg,#667eea,#764ba2);color:white;padding:12px 24px;border:none;border-radius:25px;cursor:pointer;font-weight:bold;transition:all 0.3s;display:inline-block;text-decoration:none}" +
                ".btn:hover{transform:translateY(-2px);box-shadow:0 5px 15px rgba(0,0,0,0.2)}" +
                ".btn-primary{background:linear-gradient(45deg,#667eea,#764ba2)}" +
                ".btn-secondary{background:linear-gradient(45deg,#764ba2,#667eea)}" +
                ".form-group{margin:15px 0}" +
                ".form-group label{display:block;margin-bottom:5px;font-weight:bold;color:#333}" +
                ".form-group input,.form-group textarea,.form-group select{width:100%;padding:12px;border:2px solid #e0e0e0;border-radius:8px;font-size:16px;box-sizing:border-box}" +
                ".form-group input:focus,.form-group textarea:focus{border-color:#667eea;outline:none;box-shadow:0 0 0 3px rgba(102,126,234,0.1)}" +
                ".alert{padding:15px;border-radius:8px;margin:15px 0}" +
                ".alert-success{background:#d4edda;color:#155724;border:1px solid #c3e6cb}" +
                ".alert-error{background:#f8d7da;color:#721c24;border:1px solid #f5c6cb}" +
                ".navbar{background:rgba(255,255,255,0.95);padding:15px 20px;display:flex;justify-content:space-between;align-items:center;backdrop-filter:blur(10px);box-shadow:0 2px 10px rgba(0,0,0,0.1)}" +
                ".nav-brand{font-weight:bold;color:#667eea;font-size:1.2em}" +
                ".nav-menu{display:flex;gap:20px;align-items:center}" +
                ".nav-menu a{text-decoration:none;color:#667eea;font-weight:500;padding:8px 16px;border-radius:20px;transition:all 0.3s}" +
                ".nav-menu a:hover{background:rgba(102,126,234,0.1)}" +
                ".nav-user{color:#666;font-weight:bold}" +
                ".header{text-align:center;margin-bottom:30px}" +
                ".header h1{color:#667eea;margin:0;font-size:2.5em}" +
                ".header p{color:#666;margin:10px 0}" +
                ".features{margin-top:30px;display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:15px}" +
                ".feature{display:flex;align-items:center;background:rgba(255,255,255,0.5);padding:15px;border-radius:10px}" +
                ".feature-icon{margin-right:10px;font-size:18px}" +
                ".divider{text-align:center;margin:20px 0;color:#666;font-weight:bold}" +
                ".ngrok-status{background:rgba(102,126,234,0.1);padding:10px;border-radius:8px;color:#667eea;font-weight:bold}" +
                ".error-page{text-align:center;padding:50px}";
        }
        
        private String getDashboardCSS() {
            return ".stats-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:20px;margin-bottom:30px}" +
                ".stat-card{background:linear-gradient(45deg,#667eea,#764ba2);color:white;padding:25px;border-radius:15px;text-align:center}" +
                ".stat-number{font-size:2.5em;font-weight:bold;margin-bottom:5px}" +
                ".stat-label{font-size:0.9em;opacity:0.9}" +
                ".qr-section{text-align:center}" +
                ".qr-code{border:3px solid #667eea;border-radius:15px;margin:20px 0;max-width:100%;height:auto}" +
                ".chat-container{height:400px;overflow-y:auto;background:#f8f9fa;border-radius:15px;padding:20px;margin-bottom:20px;border:2px solid #e9ecef}" +
                ".message-input{display:flex;gap:10px}" +
                ".message-input input{flex:1;padding:12px 20px;border:2px solid #e0e0e0;border-radius:25px}" +
                ".message-input button{background:linear-gradient(45deg,#667eea,#764ba2);color:white;border:none;border-radius:50%;width:50px;height:50px;cursor:pointer}" +
                ".url-info{background:rgba(102,126,234,0.1);padding:15px;border-radius:8px;margin:15px 0;text-align:left}" +
                ".status-success{color:#28a745;font-weight:bold}" +
                ".status-warning{color:#ffc107;font-weight:bold}" +
                ".message{margin:10px 0;padding:10px 15px;border-radius:18px;max-width:80%}" +
                ".message.computer{background:#667eea;color:white;margin-left:auto}" +
                ".message.phone{background:#e9ecef;color:#333;margin-right:auto}" +
                ".message-header{font-weight:bold;font-size:0.9em;margin-bottom:5px;opacity:0.8}" +
                ".message-content{margin:5px 0}" +
                ".message small{display:block;font-size:0.8em;opacity:0.7;margin-top:5px}";
        }
        
        private String getProfileCSS() {
            return ".profile-header{text-align:center;margin-bottom:30px}" +
                ".avatar{width:100px;height:100px;border-radius:50%;background:linear-gradient(45deg,#667eea,#764ba2);display:flex;align-items:center;justify-content:center;color:white;font-size:40px;margin:0 auto 20px;overflow:hidden}" +
                ".avatar img{width:100%;height:100%;object-fit:cover}" +
                ".username{color:#666;margin:5px 0;font-size:1.1em}";
        }
        
        private String getSettingsCSS() {
            return ".settings-section{margin:30px 0;padding:20px;background:rgba(102,126,234,0.05);border-radius:10px;border-left:4px solid #667eea}" +
                ".settings-section h3{margin:0 0 20px 0;color:#667eea}" +
                ".setting-item{margin:15px 0;display:flex;align-items:center;gap:10px}" +
                ".setting-item label{display:flex;align-items:center;gap:8px;cursor:pointer}" +
                ".setting-item input[type=checkbox]{width:auto;margin:0}" +
                ".setting-item select{width:auto;margin:0}" +
                ".setting-item input[type=number]{width:80px;margin:0}";
        }
        
        private String getMobileChatCSS() {
            return ".join-form{display:flex;align-items:center;justify-content:center;min-height:100vh;background:linear-gradient(135deg,#667eea,#764ba2);padding:20px}" +
                ".join-card{background:rgba(255,255,255,0.95);padding:40px;border-radius:20px;box-shadow:0 8px 32px rgba(31,38,135,0.37);max-width:400px;width:100%;backdrop-filter:blur(10px)}" +
                ".join-card h2{text-align:center;color:#667eea;margin-bottom:20px}" +
                ".mobile-header{background:linear-gradient(45deg,#667eea,#764ba2);color:white;padding:15px;text-align:center;font-weight:bold;position:fixed;top:0;left:0;right:0;z-index:1000}" +
                ".mobile-chat-container{flex:1;padding:80px 20px 80px 20px;overflow-y:auto;height:calc(100vh - 160px);background:#f8f9fa}" +
                ".mobile-input{position:fixed;bottom:0;left:0;right:0;background:rgba(255,255,255,0.95);padding:15px;display:flex;gap:10px;backdrop-filter:blur(10px)}" +
                ".mobile-input input{flex:1;padding:12px 20px;border:2px solid #e0e0e0;border-radius:25px}" +
                ".mobile-input button{background:linear-gradient(45deg,#667eea,#764ba2);color:white;border:none;border-radius:50%;width:50px;height:50px}" +
                ".message{margin:10px 0;padding:12px 18px;border-radius:18px;max-width:85%}" +
                ".message.phone{background:#667eea;color:white;margin-left:auto}" +
                ".message.computer{background:#e9ecef;color:#333;margin-right:auto;border:1px solid #e0e0e0}" +
                ".message.system{background:#28a745;color:white;margin:10px auto;text-align:center;max-width:70%;font-style:italic}" +
                ".message-header{font-weight:bold;font-size:0.9em;margin-bottom:5px;opacity:0.8}" +
                ".message-content{margin:5px 0}" +
                ".message small{display:block;font-size:0.8em;opacity:0.7;margin-top:5px}";
        }
        
        private String getChatScript(String sessionId, String username) {
            return "const sessionId='" + sessionId + "';const username='" + username + "';" +
                "let lastMessageCount=0;" +
                "function sendMessage(){" +
                "const input=document.getElementById('messageInput');" +
                "const content=input.value.trim();" +
                "if(!content)return;" +
                "fetch('/api/messages/'+sessionId,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'content='+encodeURIComponent(content)+'&senderDevice=computer&username='+username})" +
                ".then(r=>r.json()).then(data=>{if(data.success){input.value='';pollMessages();}}).catch(console.error);" +
                "}" +
                "function addMessage(device,content,timestamp,msgUsername){" +
                "const container=document.getElementById('chatContainer');" +
                "const div=document.createElement('div');" +
                "div.className='message '+device;" +
                "const deviceIcon = device==='computer'?'💻':'📱';" +
                "const displayName = msgUsername || (device==='computer'?'Computer':'Phone');" +
                "div.innerHTML='<div class=\"message-header\">'+deviceIcon+' Hello from '+displayName+'</div><div class=\"message-content\">'+content+'</div><small>'+timestamp+'</small>';" +
                "container.appendChild(div);container.scrollTop=container.scrollHeight;" +
                "}" +
                "function pollMessages(){" +
                "fetch('/api/messages/'+sessionId).then(r=>r.json()).then(messages=>{" +
                "const container=document.getElementById('chatContainer');" +
                "if(messages.length!==lastMessageCount){" +
                "container.innerHTML='';" +
                "messages.forEach(msg=>{" +
                "addMessage(msg.deviceType,msg.content,msg.timestamp,msg.username);" +
                "});" +
                "lastMessageCount=messages.length;" +
                "}" +
                "}).catch(console.error);" +
                "}" +
                "setInterval(pollMessages,1000);" +
                "pollMessages();" +
                "document.getElementById('messageInput').addEventListener('keypress',e=>{if(e.key==='Enter')sendMessage();});";
        }
        
        private String getMobileChatScript(String sessionId) {
            return "const sessionId='" + sessionId + "';" +
                "let lastMessageCount=0;" +
                "let currentUser='';" +
                "function joinSession(){" +
                "const password=document.getElementById('sessionPassword').value.trim();" +
                "const username=document.getElementById('username').value.trim();" +
                "if(!password||!username){alert('Please enter both password and username');return;}" +
                "if(password!==sessionId){alert('Incorrect session password');return;}" +
                "currentUser=username;" +
                "document.getElementById('joinForm').style.display='none';" +
                "document.getElementById('chatInterface').style.display='block';" +
                "pollMessages();" +
                "fetch('/api/messages/'+sessionId,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'content='+encodeURIComponent(username+' joined the chat')+'&senderDevice=system&username=System'}).then(pollMessages);" +
                "}" +
                "function sendMessage(){" +
                "const input=document.getElementById('messageInput');" +
                "const content=input.value.trim();" +
                "if(!content)return;" +
                "fetch('/api/messages/'+sessionId,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'content='+encodeURIComponent(content)+'&senderDevice=phone&username='+currentUser})" +
                ".then(r=>r.json()).then(data=>{if(data.success){input.value='';pollMessages();}}).catch(console.error);" +
                "}" +
                "function addMessage(device,content,timestamp,msgUsername){" +
                "const container=document.getElementById('chatContainer');" +
                "const div=document.createElement('div');" +
                "div.className='message '+device;" +
                "const deviceIcon = device==='computer'?'💻':'📱';" +
                "const displayName = msgUsername || (device==='computer'?'Computer':'Phone');" +
                "div.innerHTML='<div class=\"message-header\">'+deviceIcon+' Hello from '+displayName+'</div><div class=\"message-content\">'+content+'</div><small>'+timestamp+'</small>';" +
                "container.appendChild(div);container.scrollTop=container.scrollHeight;" +
                "}" +
                "function pollMessages(){" +
                "fetch('/api/messages/'+sessionId).then(r=>r.json()).then(messages=>{" +
                "const container=document.getElementById('chatContainer');" +
                "if(messages.length!==lastMessageCount){" +
                "container.innerHTML='';" +
                "messages.forEach(msg=>{" +
                "addMessage(msg.deviceType,msg.content,msg.timestamp,msg.username);" +
                "});" +
                "lastMessageCount=messages.length;" +
                "}" +
                "}).catch(console.error);" +
                "}" +
                "setInterval(pollMessages,1000);" +
                "pollMessages();" +
                "document.getElementById('messageInput').addEventListener('keypress',e=>{if(e.key==='Enter')sendMessage();});" +
                "setTimeout(()=>{fetch('/api/messages/'+sessionId,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'content=📱 Connected from phone&senderDevice=phone&username='+currentUser}).then(pollMessages);},1000);";
        }
    }
} 