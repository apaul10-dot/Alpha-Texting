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

public class Main {
    private static Map<String, User> users = new ConcurrentHashMap<>();
    private static Map<String, List<Message>> sessions = new ConcurrentHashMap<>();
    private static Map<String, UserProfile> profiles = new ConcurrentHashMap<>();
    private static Map<String, UserSettings> settings = new ConcurrentHashMap<>();
    private static Map<String, Map<String, Set<String>>> messageReactions = new ConcurrentHashMap<>();
    private static Map<String, Set<String>> typingUsers = new ConcurrentHashMap<>();
    private static Map<String, Long> lastTypingTime = new ConcurrentHashMap<>();
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
    
    // Enhanced Message class with advanced features
    static class Message {
        String id;
        String content;
        String sender;
        String username;
        String timestamp;
        String deviceType;
        String replyToId;
        String replyToContent;
        String replyToUsername;
        boolean isEdited;
        String editedTimestamp;
        boolean isDeleted;
        Map<String, Integer> reactions;
        
        public Message(String content, String sender, String deviceType, String username) {
            this.id = generateMessageId();
            this.content = content;
            this.sender = sender;
            this.deviceType = deviceType;
            this.username = username != null ? username : "Anonymous";
            this.timestamp = new java.util.Date().toString();
            this.replyToId = null;
            this.replyToContent = null;
            this.replyToUsername = null;
            this.isEdited = false;
            this.editedTimestamp = null;
            this.isDeleted = false;
            this.reactions = new ConcurrentHashMap<>();
        }
        
        public Message(String content, String sender, String deviceType, String username, String replyToId, String replyToContent, String replyToUsername) {
            this(content, sender, deviceType, username);
            this.replyToId = replyToId;
            this.replyToContent = replyToContent;
            this.replyToUsername = replyToUsername;
        }
        
        private String generateMessageId() {
            return "msg_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        }
        
        public void editMessage(String newContent) {
            this.content = newContent;
            this.isEdited = true;
            this.editedTimestamp = new java.util.Date().toString();
        }
        
        public void deleteMessage() {
            this.isDeleted = true;
            this.content = "[Message deleted]";
        }
        
        public void addReaction(String emoji) {
            reactions.put(emoji, reactions.getOrDefault(emoji, 0) + 1);
        }
        
        public void removeReaction(String emoji) {
            if (reactions.containsKey(emoji)) {
                int count = reactions.get(emoji) - 1;
                if (count <= 0) {
                    reactions.remove(emoji);
                } else {
                    reactions.put(emoji, count);
                }
            }
        }
    }
    
    // Typing indicator class
    static class TypingIndicator {
        String sessionId;
        String username;
        long timestamp;
        
        public TypingIndicator(String sessionId, String username) {
            this.sessionId = sessionId;
            this.username = username;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 3000; // 3 seconds
        }
    }
    
    // Message search utility
    static class MessageSearch {
        public static List<Message> searchMessages(String sessionId, String query) {
            List<Message> allMessages = sessions.get(sessionId);
            if (allMessages == null || query == null || query.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            List<Message> results = new ArrayList<>();
            String lowerQuery = query.toLowerCase().trim();
            
            for (Message message : allMessages) {
                if (message.isDeleted) continue;
                
                if (message.content.toLowerCase().contains(lowerQuery) ||
                    message.username.toLowerCase().contains(lowerQuery)) {
                    results.add(message);
                }
            }
            
            return results;
        }
        
        public static Message findMessageById(String sessionId, String messageId) {
            List<Message> messages = sessions.get(sessionId);
            if (messages == null) return null;
            
            for (Message message : messages) {
                if (message.id.equals(messageId)) {
                    return message;
                }
            }
            return null;
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
        // Additional functional settings
        boolean autoRefresh;
        int refreshInterval; // in milliseconds
        boolean showTimestamps;
        boolean showTypingIndicators;
        int maxMessagesDisplay;
        
        public UserSettings(String username) {
            this.username = username;
            this.notifications = true;
            this.soundEnabled = true;
            this.theme = "auto";
            this.language = "english";
            this.compactMode = false;
            this.readReceipts = true;
            this.sessionTimeout = 60;
            // Initialize new functional settings
            this.autoRefresh = true;
            this.refreshInterval = 1000; // 1 second
            this.showTimestamps = true;
            this.showTypingIndicators = true;
            this.maxMessagesDisplay = 100;
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
        
        System.out.println("http://localhost:8082");
        System.out.println("http://10.0.0.95:8082");
        if (args.length > 0) {
            System.out.println(baseUrl);
        }
    }
    
    // Enhanced API Handler with comprehensive backend functionality
    static class ApiHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            
            if (path.startsWith("/api/messages/")) {
                handleMessages(exchange, path, method);
            } else if (path.startsWith("/api/reactions/")) {
                handleReactions(exchange, path, method);
            } else if (path.startsWith("/api/typing/")) {
                handleTyping(exchange, path, method);
            } else if (path.startsWith("/api/search/")) {
                handleSearch(exchange, path, method);
            } else if (path.startsWith("/api/edit/")) {
                handleMessageEdit(exchange, path, method);
            } else if (path.startsWith("/api/delete/")) {
                handleMessageDelete(exchange, path, method);
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
                    if (msg.isDeleted) continue; // Skip deleted messages in list view
                    
                    json.append("{");
                    json.append("\"id\":\"").append(msg.id).append("\",");
                    json.append("\"content\":\"").append(msg.content.replace("\"", "\\\"")).append("\",");
                    json.append("\"sender\":\"").append(msg.sender).append("\",");
                    json.append("\"username\":\"").append(msg.username).append("\",");
                    json.append("\"deviceType\":\"").append(msg.deviceType).append("\",");
                    json.append("\"timestamp\":\"").append(msg.timestamp).append("\",");
                    json.append("\"isEdited\":").append(msg.isEdited).append(",");
                    json.append("\"isDeleted\":").append(msg.isDeleted).append(",");
                    
                    // Reply information
                    if (msg.replyToId != null) {
                        json.append("\"replyToId\":\"").append(msg.replyToId).append("\",");
                        json.append("\"replyToContent\":\"").append(msg.replyToContent != null ? msg.replyToContent.replace("\"", "\\\"") : "").append("\",");
                        json.append("\"replyToUsername\":\"").append(msg.replyToUsername != null ? msg.replyToUsername : "").append("\",");
                    } else {
                        json.append("\"replyToId\":null,");
                        json.append("\"replyToContent\":null,");
                        json.append("\"replyToUsername\":null,");
                    }
                    
                    // Reactions
                    json.append("\"reactions\":{");
                    boolean first = true;
                    for (Map.Entry<String, Integer> reaction : msg.reactions.entrySet()) {
                        if (!first) json.append(",");
                        json.append("\"").append(reaction.getKey()).append("\":").append(reaction.getValue());
                        first = false;
                    }
                    json.append("}");
                    
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
                    String replyToId = extractParam(body, "replyToId");
                    
                    Message msg;
                    if (replyToId != null && !replyToId.isEmpty()) {
                        // This is a reply message
                        Message replyToMessage = MessageSearch.findMessageById(sessionId, replyToId);
                        if (replyToMessage != null) {
                            msg = new Message(content, senderDevice, senderDevice, msgUsername, 
                                            replyToId, replyToMessage.content, replyToMessage.username);
                        } else {
                            msg = new Message(content, senderDevice, senderDevice, msgUsername);
                        }
                    } else {
                        msg = new Message(content, senderDevice, senderDevice, msgUsername);
                    }
                    
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
        
        // Advanced chat feature handlers
        private void handleReactions(HttpExchange exchange, String path, String method) throws IOException {
            if (!"POST".equals(method)) {
                sendResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            
            String[] pathParts = path.split("/");
            if (pathParts.length < 5) {
                sendResponse(exchange, "{\"error\":\"Invalid path\"}", 400);
                return;
            }
            
            String sessionId = pathParts[3];
            String messageId = pathParts[4];
            
            String body = readRequestBody(exchange);
            String emoji = extractParam(body, "emoji");
            String action = extractParam(body, "action");
            
            if (emoji == null || action == null) {
                sendResponse(exchange, "{\"error\":\"Missing emoji or action\"}", 400);
                return;
            }
            
            Message message = MessageSearch.findMessageById(sessionId, messageId);
            if (message == null) {
                sendResponse(exchange, "{\"error\":\"Message not found\"}", 404);
                return;
            }
            
            if ("add".equals(action)) {
                message.addReaction(emoji);
            } else if ("remove".equals(action)) {
                message.removeReaction(emoji);
            }
            
            sendResponse(exchange, "{\"success\":true}", 200);
        }
        
        private void handleTyping(HttpExchange exchange, String path, String method) throws IOException {
            if (!"POST".equals(method)) {
                sendResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            
            String sessionId = path.substring(13);
            String body = readRequestBody(exchange);
            String username = extractParam(body, "username");
            String action = extractParam(body, "action");
            
            if (username == null || action == null) {
                sendResponse(exchange, "{\"error\":\"Missing username or action\"}", 400);
                return;
            }
            
            Set<String> typingInSession = typingUsers.getOrDefault(sessionId, new HashSet<>());
            
            if ("start".equals(action)) {
                typingInSession.add(username);
                lastTypingTime.put(sessionId + ":" + username, System.currentTimeMillis());
            } else if ("stop".equals(action)) {
                typingInSession.remove(username);
                lastTypingTime.remove(sessionId + ":" + username);
            }
            
            typingUsers.put(sessionId, typingInSession);
            cleanupExpiredTyping(sessionId);
            
            sendResponse(exchange, "{\"success\":true}", 200);
        }
        
        private void handleSearch(HttpExchange exchange, String path, String method) throws IOException {
            if (!"GET".equals(method)) {
                sendResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            
            String sessionId = path.substring(12);
            String query = exchange.getRequestURI().getQuery();
            String searchTerm = extractParam(query, "q");
            
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                sendResponse(exchange, "{\"results\":[]}", 200);
                return;
            }
            
            List<Message> results = MessageSearch.searchMessages(sessionId, searchTerm);
            
            StringBuilder json = new StringBuilder("{\"results\":[");
            for (int i = 0; i < results.size(); i++) {
                Message msg = results.get(i);
                json.append("{");
                json.append("\"id\":\"").append(msg.id).append("\",");
                json.append("\"content\":\"").append(msg.content.replace("\"", "\\\"")).append("\",");
                json.append("\"username\":\"").append(msg.username).append("\",");
                json.append("\"timestamp\":\"").append(msg.timestamp).append("\"");
                json.append("}");
                if (i < results.size() - 1) json.append(",");
            }
            json.append("]}");
            
            sendResponse(exchange, json.toString(), 200);
        }
        
        private void handleMessageEdit(HttpExchange exchange, String path, String method) throws IOException {
            if (!"POST".equals(method)) {
                sendResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            
            String[] pathParts = path.split("/");
            if (pathParts.length < 5) {
                sendResponse(exchange, "{\"error\":\"Invalid path\"}", 400);
                return;
            }
            
            String sessionId = pathParts[3];
            String messageId = pathParts[4];
            
            String body = readRequestBody(exchange);
            String newContent = extractParam(body, "content");
            String username = extractParam(body, "username");
            
            if (newContent == null || username == null) {
                sendResponse(exchange, "{\"error\":\"Missing content or username\"}", 400);
                return;
            }
            
            Message message = MessageSearch.findMessageById(sessionId, messageId);
            if (message == null) {
                sendResponse(exchange, "{\"error\":\"Message not found\"}", 404);
                return;
            }
            
            if (!message.username.equals(username)) {
                sendResponse(exchange, "{\"error\":\"Unauthorized\"}", 403);
                return;
            }
            
            message.editMessage(newContent);
            sendResponse(exchange, "{\"success\":true}", 200);
        }
        
        private void handleMessageDelete(HttpExchange exchange, String path, String method) throws IOException {
            if (!"POST".equals(method)) {
                sendResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            
            String[] pathParts = path.split("/");
            if (pathParts.length < 5) {
                sendResponse(exchange, "{\"error\":\"Invalid path\"}", 400);
                return;
            }
            
            String sessionId = pathParts[3];
            String messageId = pathParts[4];
            
            String body = readRequestBody(exchange);
            String username = extractParam(body, "username");
            
            if (username == null) {
                sendResponse(exchange, "{\"error\":\"Missing username\"}", 400);
                return;
            }
            
            Message message = MessageSearch.findMessageById(sessionId, messageId);
            if (message == null) {
                sendResponse(exchange, "{\"error\":\"Message not found\"}", 404);
                return;
            }
            
            if (!message.username.equals(username)) {
                sendResponse(exchange, "{\"error\":\"Unauthorized\"}", 403);
                return;
            }
            
            message.deleteMessage();
            sendResponse(exchange, "{\"success\":true}", 200);
        }
        
        private void cleanupExpiredTyping(String sessionId) {
            Set<String> typingInSession = typingUsers.get(sessionId);
            if (typingInSession == null) return;
            
            long currentTime = System.currentTimeMillis();
            Iterator<String> iterator = typingInSession.iterator();
            
            while (iterator.hasNext()) {
                String user = iterator.next();
                String key = sessionId + ":" + user;
                Long lastTime = lastTypingTime.get(key);
                
                if (lastTime == null || currentTime - lastTime > 3000) {
                    iterator.remove();
                    lastTypingTime.remove(key);
                }
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
            
            // Update new functional settings
            String autoRefresh = extractParam(body, "autoRefresh");
            if (autoRefresh != null) userSettings.autoRefresh = "on".equals(autoRefresh);
            
            String refreshInterval = extractParam(body, "refreshInterval");
            if (refreshInterval != null) {
                try {
                    int interval = Integer.parseInt(refreshInterval);
                    if (interval >= 500 && interval <= 5000) {
                        userSettings.refreshInterval = interval;
                    }
                } catch (NumberFormatException e) {
                    // Keep default value
                }
            }
            
            String showTimestamps = extractParam(body, "showTimestamps");
            if (showTimestamps != null) userSettings.showTimestamps = "on".equals(showTimestamps);
            
            String showTypingIndicators = extractParam(body, "showTypingIndicators");
            if (showTypingIndicators != null) userSettings.showTypingIndicators = "on".equals(showTypingIndicators);
            
            String maxMessagesDisplay = extractParam(body, "maxMessagesDisplay");
            if (maxMessagesDisplay != null) {
                try {
                    int maxMessages = Integer.parseInt(maxMessagesDisplay);
                    if (maxMessages >= 20 && maxMessages <= 500) {
                        userSettings.maxMessagesDisplay = maxMessages;
                    }
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
                "<style>" + getImprovedHomeCSS() + "</style></head><body>" +
                "<div class='hero-section'>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1 class='main-title'>Alpha Texting</h1>" +
                "<p class='subtitle'>Professional cross-device chat with advanced features</p>" +
                "</div>" +
                (!errorMessage.isEmpty() ? "<div class='alert alert-error'>" + errorMessage + "</div>" : "") +
                "<div class='auth-container'>" +
                "<div class='auth-card login-card'>" +
                "<h3>Welcome Back</h3>" +
                "<form action='/login' method='post'>" +
                "<div class='form-group'>" +
                "<input type='text' name='username' placeholder='Enter your username' required>" +
                "</div>" +
                "<button type='submit' class='btn btn-primary'>Sign In</button>" +
                "</form>" +
                "</div>" +
                "<div class='auth-card register-card'>" +
                "<h3>Create Account</h3>" +
                "<form action='/register' method='post'>" +
                "<div class='form-group'>" +
                "<input type='text' name='username' placeholder='Choose username' required>" +
                "</div>" +
                "<div class='form-group'>" +
                "<input type='text' name='displayName' placeholder='Your display name' required>" +
                "</div>" +
                "<button type='submit' class='btn btn-secondary'>Get Started</button>" +
                "</form>" +
                "</div>" +
                "</div>" +
                "<div class='features'>" +
                "<div class='feature'><div class='feature-icon'>💬</div><div class='feature-text'><strong>Advanced Messaging</strong><br>Reactions, replies, and real-time sync</div></div>" +
                "<div class='feature'><div class='feature-icon'>🔍</div><div class='feature-text'><strong>Message Search</strong><br>Find any message instantly</div></div>" +
                "<div class='feature'><div class='feature-icon'>🌙</div><div class='feature-text'><strong>Dark Mode</strong><br>Easy on the eyes</div></div>" +
                "<div class='feature'><div class='feature-icon'>📱</div><div class='feature-text'><strong>Cross-Device</strong><br>Chat between phone and computer</div></div>" +
                "</div>" +
                "</div>" +
                "</div></body></html>";
        }
        
        private String generateDashboardPage(User user, String sessionId) {
            return generateDashboardPage(user, sessionId, "");
        }
        
        private String generateDashboardPage(User user, String sessionId, String message) {
            if (sessionId == null) {
                // Show session creation form
                UserSettings userSettings = settings.getOrDefault(user.username, new UserSettings(user.username));
                String themeCSS = getThemeCSS(userSettings.theme);
                return "<!DOCTYPE html><html><head><title>Dashboard - Alpha Texting</title>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                    "<style>" + getBaseCSS() + getDashboardCSS() + themeCSS + "</style></head><body class='" + userSettings.theme + "-theme'>" +
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
                UserSettings userSettings = settings.getOrDefault(user.username, new UserSettings(user.username));
                String themeCSS = getThemeCSS(userSettings.theme);
                String chatUrl = "http://10.0.0.95:8082/chat/" + sessionId;
                return "<!DOCTYPE html><html><head><title>Dashboard - Alpha Texting</title>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                    "<style>" + getBaseCSS() + getDashboardCSS() + themeCSS + "</style></head><body class='" + userSettings.theme + "-theme'>" +
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
                    "<script>" + getChatScript(sessionId, user.username, userSettings) + 
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
            String themeCSS = getThemeCSS(userSettings.theme);
            return "<!DOCTYPE html><html><head><title>Settings - Alpha Texting</title>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<style>" + getBaseCSS() + getSettingsCSS() + themeCSS + "</style></head><body class='" + userSettings.theme + "-theme'>" +
                generateNavbar(user) +
                "<div class='container'>" +
                "<div class='settings-card card'>" +
                "<h2>Settings</h2>" +
                (!message.isEmpty() ? "<div class='alert alert-success'>" + message + "</div>" : "") +
                "<form action='/settings?user=" + user.username + "' method='post' id='settingsForm'>" +
                "<div class='settings-section'>" +
                "<h3>🔔 Notifications & Alerts</h3>" +
                "<div class='setting-item'>" +
                "<label class='toggle-label'>" +
                "<input type='checkbox' name='notifications' class='toggle-input'" + (userSettings.notifications ? " checked" : "") + ">" +
                "<span class='toggle-slider'></span>" +
                "Browser Notifications" +
                "</label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label class='toggle-label'>" +
                "<input type='checkbox' name='soundEnabled' class='toggle-input'" + (userSettings.soundEnabled ? " checked" : "") + ">" +
                "<span class='toggle-slider'></span>" +
                "Sound Alerts" +
                "</label>" +
                "</div>" +
                "</div>" +
                "<div class='settings-section'>" +
                "<h3>🎨 Appearance</h3>" +
                "<div class='setting-item'>" +
                "<label>Theme:</label>" +
                "<select name='theme' class='theme-selector' onchange='previewTheme(this.value)'>" +
                "<option value='auto'" + ("auto".equals(userSettings.theme) ? " selected" : "") + ">Auto (System)</option>" +
                "<option value='light'" + ("light".equals(userSettings.theme) ? " selected" : "") + ">Light Mode</option>" +
                "<option value='dark'" + ("dark".equals(userSettings.theme) ? " selected" : "") + ">Dark Mode</option>" +
                "</select>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label>Language:</label>" +
                "<select name='language' class='language-selector'>" +
                "<option value='english'" + ("english".equals(userSettings.language) ? " selected" : "") + ">English</option>" +
                "<option value='spanish'" + ("spanish".equals(userSettings.language) ? " selected" : "") + ">Español</option>" +
                "<option value='french'" + ("french".equals(userSettings.language) ? " selected" : "") + ">Français</option>" +
                "</select>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label class='toggle-label'>" +
                "<input type='checkbox' name='compactMode' class='toggle-input'" + (userSettings.compactMode ? " checked" : "") + ">" +
                "<span class='toggle-slider'></span>" +
                "Compact Message View" +
                "</label>" +
                "</div>" +
                "</div>" +
                "<div class='settings-section'>" +
                "<h3>💬 Chat Behavior</h3>" +
                "<div class='setting-item'>" +
                "<label class='toggle-label'>" +
                "<input type='checkbox' name='autoRefresh' class='toggle-input'" + (userSettings.autoRefresh ? " checked" : "") + ">" +
                "<span class='toggle-slider'></span>" +
                "Auto-refresh Messages" +
                "</label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label>Refresh Interval:</label>" +
                "<input type='range' name='refreshInterval' value='" + userSettings.refreshInterval + "' min='500' max='5000' step='500' class='refresh-slider' oninput='updateRefreshDisplay(this.value)'>" +
                "<span class='refresh-display'>" + (userSettings.refreshInterval / 1000.0) + "s</span>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label class='toggle-label'>" +
                "<input type='checkbox' name='showTimestamps' class='toggle-input'" + (userSettings.showTimestamps ? " checked" : "") + ">" +
                "<span class='toggle-slider'></span>" +
                "Show Message Timestamps" +
                "</label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label class='toggle-label'>" +
                "<input type='checkbox' name='showTypingIndicators' class='toggle-input'" + (userSettings.showTypingIndicators ? " checked" : "") + ">" +
                "<span class='toggle-slider'></span>" +
                "Show Typing Indicators" +
                "</label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label>Max Messages to Display:</label>" +
                "<input type='range' name='maxMessagesDisplay' value='" + userSettings.maxMessagesDisplay + "' min='20' max='500' step='10' class='messages-slider' oninput='updateMessagesDisplay(this.value)'>" +
                "<span class='messages-display'>" + userSettings.maxMessagesDisplay + " messages</span>" +
                "</div>" +
                "</div>" +
                "<div class='settings-section'>" +
                "<h3>🔒 Privacy & Security</h3>" +
                "<div class='setting-item'>" +
                "<label class='toggle-label'>" +
                "<input type='checkbox' name='readReceipts' class='toggle-input'" + (userSettings.readReceipts ? " checked" : "") + ">" +
                "<span class='toggle-slider'></span>" +
                "Read Receipts" +
                "</label>" +
                "</div>" +
                "<div class='setting-item'>" +
                "<label>Session Timeout:</label>" +
                "<input type='range' name='sessionTimeout' value='" + userSettings.sessionTimeout + "' min='5' max='300' class='timeout-slider' oninput='updateTimeoutDisplay(this.value)'>" +
                "<span class='timeout-display'>" + userSettings.sessionTimeout + " minutes</span>" +
                "</div>" +
                "</div>" +
                "<div class='settings-actions'>" +
                "<button type='submit' class='btn btn-primary'>Save Settings</button>" +
                "<button type='button' class='btn btn-secondary' onclick='resetSettings()'>Reset to Default</button>" +
                "</div>" +
                "</form></div></div>" +
                "<script>" + getSettingsScript() + "</script>" +
                "</body></html>";
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
        private String getImprovedHomeCSS() {
            return "*{margin:0;padding:0;box-sizing:border-box}" +
                "body{font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);min-height:100vh;color:#333}" +
                ".hero-section{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}" +
                ".container{max-width:1200px;width:100%;text-align:center}" +
                ".header{margin-bottom:60px}" +
                ".main-title{font-size:4rem;font-weight:800;color:white;margin-bottom:20px;text-shadow:2px 2px 4px rgba(0,0,0,0.3);letter-spacing:-2px}" +
                ".subtitle{font-size:1.3rem;color:rgba(255,255,255,0.9);margin-bottom:40px;font-weight:300}" +
                ".auth-container{display:grid;grid-template-columns:1fr 1fr;gap:40px;margin-bottom:80px;max-width:800px;margin-left:auto;margin-right:auto}" +
                ".auth-card{background:rgba(255,255,255,0.95);padding:40px;border-radius:20px;box-shadow:0 20px 40px rgba(0,0,0,0.1);backdrop-filter:blur(10px);transition:transform 0.3s ease,box-shadow 0.3s ease}" +
                ".auth-card:hover{transform:translateY(-5px);box-shadow:0 25px 50px rgba(0,0,0,0.15)}" +
                ".auth-card h3{color:#667eea;margin-bottom:30px;font-size:1.5rem;font-weight:600}" +
                ".form-group{margin-bottom:25px}" +
                ".form-group input{width:100%;padding:15px 20px;border:2px solid #e2e8f0;border-radius:12px;font-size:16px;transition:all 0.3s ease;background:#f8f9fa}" +
                ".form-group input:focus{outline:none;border-color:#667eea;background:white;box-shadow:0 0 0 3px rgba(102,126,234,0.1)}" +
                ".form-group input::placeholder{color:#a0aec0}" +
                ".btn{width:100%;padding:15px;border:none;border-radius:12px;font-size:16px;font-weight:600;cursor:pointer;transition:all 0.3s ease;text-transform:uppercase;letter-spacing:1px}" +
                ".btn-primary{background:linear-gradient(135deg,#667eea,#764ba2);color:white;box-shadow:0 4px 15px rgba(102,126,234,0.4)}" +
                ".btn-primary:hover{transform:translateY(-2px);box-shadow:0 8px 25px rgba(102,126,234,0.6)}" +
                ".btn-secondary{background:linear-gradient(135deg,#764ba2,#667eea);color:white;box-shadow:0 4px 15px rgba(118,75,162,0.4)}" +
                ".btn-secondary:hover{transform:translateY(-2px);box-shadow:0 8px 25px rgba(118,75,162,0.6)}" +
                ".features{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:30px;max-width:1000px;margin:0 auto}" +
                ".feature{background:rgba(255,255,255,0.1);padding:30px;border-radius:20px;backdrop-filter:blur(10px);border:1px solid rgba(255,255,255,0.2);transition:all 0.3s ease}" +
                ".feature:hover{background:rgba(255,255,255,0.15);transform:translateY(-5px)}" +
                ".feature-icon{font-size:3rem;margin-bottom:20px;display:block}" +
                ".feature-text{color:white;text-align:left}" +
                ".feature-text strong{display:block;font-size:1.2rem;margin-bottom:8px}" +
                ".alert{padding:20px;border-radius:12px;margin:20px 0;background:#fee;border:2px solid #fcc;color:#c33;font-weight:500}" +
                "@media(max-width:768px){" +
                ".main-title{font-size:2.5rem}" +
                ".auth-container{grid-template-columns:1fr;gap:20px}" +
                ".features{grid-template-columns:1fr}" +
                ".container{padding:0 20px}" +
                "}";
        }
        
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
        
        private String getThemeCSS(String theme) {
            if ("dark".equals(theme)) {
                return ".dark-theme{background:#1a1a1a !important;color:#e2e8f0 !important}" +
                    ".dark-theme .navbar{background:rgba(30,30,30,0.95) !important;color:#e2e8f0 !important}" +
                    ".dark-theme .nav-brand{color:#667eea !important}" +
                    ".dark-theme .nav-menu a{color:#e2e8f0 !important}" +
                    ".dark-theme .card{background:rgba(40,40,40,0.95) !important;color:#e2e8f0 !important}" +
                    ".dark-theme .settings-section{background:rgba(60,60,60,0.3) !important}" +
                    ".dark-theme .form-group input, .dark-theme select{background:#2d3748 !important;border-color:#4a5568 !important;color:#e2e8f0 !important}" +
                    ".dark-theme .form-group input:focus, .dark-theme select:focus{border-color:#667eea !important;background:#374151 !important}" +
                    ".dark-theme .message{background:#374151 !important;border-color:#4a5568 !important}" +
                    ".dark-theme .chat-container{background:#2d3748 !important}";
            } else if ("light".equals(theme)) {
                return ".light-theme{background:#f7fafc !important;color:#2d3748 !important}" +
                    ".light-theme .navbar{background:rgba(255,255,255,0.95) !important}" +
                    ".light-theme .card{background:rgba(255,255,255,0.95) !important}" +
                    ".light-theme .settings-section{background:rgba(247,250,252,0.8) !important}";
            }
            return ""; // auto theme uses default styles
        }
        
        private String getSettingsScript() {
            return "function previewTheme(theme) {" +
                "document.body.className = theme + '-theme';" +
                "}" +
                "function updateTimeoutDisplay(value) {" +
                "document.querySelector('.timeout-display').textContent = value + ' minutes';" +
                "}" +
                "function updateRefreshDisplay(value) {" +
                "document.querySelector('.refresh-display').textContent = (value / 1000) + 's';" +
                "}" +
                "function updateMessagesDisplay(value) {" +
                "document.querySelector('.messages-display').textContent = value + ' messages';" +
                "}" +
                "function resetSettings() {" +
                "if(confirm('Reset all settings to default values?')) {" +
                "document.querySelector('select[name=theme]').value = 'auto';" +
                "document.querySelector('select[name=language]').value = 'english';" +
                "document.querySelector('input[name=notifications]').checked = true;" +
                "document.querySelector('input[name=soundEnabled]').checked = true;" +
                "document.querySelector('input[name=compactMode]').checked = false;" +
                "document.querySelector('input[name=readReceipts]').checked = true;" +
                "document.querySelector('input[name=sessionTimeout]').value = 60;" +
                "document.querySelector('input[name=autoRefresh]').checked = true;" +
                "document.querySelector('input[name=refreshInterval]').value = 1000;" +
                "document.querySelector('input[name=showTimestamps]').checked = true;" +
                "document.querySelector('input[name=showTypingIndicators]').checked = true;" +
                "document.querySelector('input[name=maxMessagesDisplay]').value = 100;" +
                "updateTimeoutDisplay(60);" +
                "updateRefreshDisplay(1000);" +
                "updateMessagesDisplay(100);" +
                "previewTheme('auto');" +
                "}" +
                "}" +
                "document.addEventListener('DOMContentLoaded', function() {" +
                "const toggles = document.querySelectorAll('.toggle-input');" +
                "toggles.forEach(toggle => {" +
                "toggle.addEventListener('change', function() {" +
                "this.parentElement.classList.toggle('active', this.checked);" +
                "});" +
                "if(toggle.checked) toggle.parentElement.classList.add('active');" +
                "});" +
                "});";
        }
        
        private String getSettingsCSS() {
            return ".settings-section{margin:30px 0;padding:25px;background:rgba(102,126,234,0.05);border-radius:15px;border-left:4px solid #667eea;transition:all 0.3s ease}" +
                ".settings-section:hover{background:rgba(102,126,234,0.08)}" +
                ".settings-section h3{margin:0 0 25px 0;color:#667eea;font-size:1.3rem}" +
                ".setting-item{margin:20px 0;display:flex;align-items:center;justify-content:space-between;padding:15px 0;border-bottom:1px solid rgba(0,0,0,0.05)}" +
                ".setting-item:last-child{border-bottom:none}" +
                ".toggle-label{display:flex;align-items:center;gap:15px;cursor:pointer;font-weight:500;flex:1}" +
                ".toggle-input{display:none}" +
                ".toggle-slider{width:50px;height:25px;background:#ccc;border-radius:25px;position:relative;transition:all 0.3s ease;cursor:pointer}" +
                ".toggle-slider:before{content:'';position:absolute;height:19px;width:19px;left:3px;top:3px;background:white;border-radius:50%;transition:all 0.3s ease}" +
                ".toggle-input:checked + .toggle-slider{background:#667eea}" +
                ".toggle-input:checked + .toggle-slider:before{transform:translateX(25px)}" +
                ".theme-selector, .language-selector{padding:10px 15px;border:2px solid #e2e8f0;border-radius:8px;font-size:14px;min-width:150px;cursor:pointer;transition:all 0.3s ease}" +
                ".theme-selector:focus, .language-selector:focus{outline:none;border-color:#667eea;box-shadow:0 0 0 3px rgba(102,126,234,0.1)}" +
                ".timeout-slider{width:200px;height:6px;border-radius:3px;background:#e2e8f0;outline:none;cursor:pointer;transition:all 0.3s ease}" +
                ".timeout-slider::-webkit-slider-thumb{appearance:none;width:20px;height:20px;border-radius:50%;background:#667eea;cursor:pointer;transition:all 0.3s ease}" +
                ".timeout-slider::-webkit-slider-thumb:hover{transform:scale(1.1)}" +
                ".timeout-display{background:#667eea;color:white;padding:5px 12px;border-radius:15px;font-size:12px;font-weight:600;margin-left:15px}" +
                ".settings-actions{display:flex;gap:15px;justify-content:center;margin-top:40px;padding-top:30px;border-top:2px solid rgba(0,0,0,0.05)}" +
                ".settings-actions .btn{min-width:150px}";
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
        
        private String getChatScript(String sessionId, String username, UserSettings userSettings) {
            return "const sessionId='" + sessionId + "';const username='" + username + "';" +
                "const userSettings={autoRefresh:" + userSettings.autoRefresh + ",refreshInterval:" + userSettings.refreshInterval + ",showTimestamps:" + userSettings.showTimestamps + ",showTypingIndicators:" + userSettings.showTypingIndicators + ",maxMessagesDisplay:" + userSettings.maxMessagesDisplay + ",compactMode:" + userSettings.compactMode + ",soundEnabled:" + userSettings.soundEnabled + "};" +
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