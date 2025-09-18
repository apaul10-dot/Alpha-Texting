# Alpha Texting - Technical Documentation

## Overview

Alpha Texting is a real-time multi-user chat application that enables seamless communication between computers and mobile devices. Built entirely in Java using the built-in HTTP server, it supports password-protected chat sessions, user profiles, settings management, and cross-device synchronization.

## Architecture

### Core Components

#### 1. Main Server Class: `EnhancedSimpleServer`
The main application class that orchestrates all functionality:

```java
public class EnhancedSimpleServer {
    private static final Map<String, User> users = new ConcurrentHashMap<>();
    private static final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();
    private static String baseUrl = "http://10.0.0.95:8082";
}
```

**Key Features:**
- HTTP server running on port 8082
- Concurrent user and session management
- CORS-enabled for cross-origin requests
- Binds to all network interfaces (0.0.0.0) for external access

#### 2. Data Models

##### User Class
Represents a registered user with comprehensive profile information:

```java
static class User {
    String username;        // Unique identifier
    String password;        // Authentication
    String displayName;     // Display name
    String email;          // Contact information
    String phoneNumber;    // Phone contact
    String bio;            // User biography
    String profilePicture; // Profile image URL
    Date createdAt;        // Registration timestamp
    Date lastActive;       // Last activity timestamp
    int totalMessages;     // Message count
    int totalSessions;     // Session count
}
```

##### UserProfile Class
Extended profile information:

```java
static class UserProfile {
    String theme;          // UI theme preference
    String language;       // Language preference
    boolean notifications; // Notification settings
    String timezone;       // User timezone
}
```

##### UserSettings Class
Application-specific settings:

```java
static class UserSettings {
    boolean soundEnabled;     // Sound notifications
    boolean autoSave;        // Auto-save messages
    int messageHistory;      // Message history limit
    String defaultStatus;    // Default user status
}
```

##### Message Class
Represents a chat message:

```java
static class Message {
    String content;     // Message text
    String sender;      // Sender device type
    String username;    // Sender username
    String timestamp;   // Creation time
    String deviceType;  // Device identifier
}
```

### 3. HTTP Handlers

#### MainHandler
**Endpoint:** `/dashboard`
**Purpose:** User dashboard and session management

**GET Requests:**
- Displays user statistics (sessions, messages, total users)
- Shows session creation form or active session interface
- Provides chat URL for sharing

**POST Requests:**
- Creates new password-protected chat sessions
- Validates session passwords
- Updates user session count

**Key Methods:**
```java
private void handleCreateSession(HttpExchange exchange, User user)
private String generateDashboardPage(User user, String sessionId, String message)
```

#### ChatHandler
**Endpoint:** `/chat/{sessionId}`
**Purpose:** Mobile chat interface

**Functionality:**
- Serves mobile-optimized chat interface
- Handles session joining with password verification
- Provides real-time messaging interface
- Auto-creates sessions if they don't exist

**Key Features:**
- Responsive mobile design
- Password-protected access
- Username-based identification
- Real-time message polling

#### ApiHandler
**Endpoint:** `/api/messages/{sessionId}`
**Purpose:** RESTful API for message operations

**GET Requests:**
- Retrieves all messages for a session
- Returns JSON array of messages
- Includes sender information and timestamps

**POST Requests:**
- Accepts new messages
- Validates user authentication
- Updates user statistics
- Broadcasts to all session participants

**JSON Response Format:**
```json
[
  {
    "content": "Hello world",
    "sender": "computer",
    "username": "john_doe",
    "deviceType": "computer",
    "timestamp": "Thu Sep 18 14:30:00 PDT 2025"
  }
]
```

#### ProfileHandler
**Endpoint:** `/profile`
**Purpose:** User profile management

**GET Requests:**
- Displays user profile form
- Shows current profile information
- Provides editing interface

**POST Requests:**
- Updates user profile information
- Validates input data
- Persists changes to user object

#### SettingsHandler
**Endpoint:** `/settings`
**Purpose:** Application settings management

**GET Requests:**
- Shows current user settings
- Provides settings modification interface

**POST Requests:**
- Updates user preferences
- Saves configuration changes
- Applies new settings immediately

#### QRHandler
**Endpoint:** `/qr/{sessionId}`
**Purpose:** QR code generation (legacy)

**Functionality:**
- Generates QR-like visual patterns
- Creates finder patterns and timing patterns
- Uses session URL for data encoding
- Returns PNG image format

## Session Management

### Password-Based Sessions

The application uses a password-based session system where:

1. **Computer User (Session Creator):**
   - Creates a session with a custom password
   - Password becomes the session ID
   - Receives a shareable URL: `http://10.0.0.95:8082/chat/{password}`

2. **Mobile User (Session Joiner):**
   - Accesses the shared URL
   - Enters the session password for verification
   - Provides a unique username
   - Joins the chat session

### Session Flow

```
1. Computer → Create Session → Enter Password → Session Created
2. Computer → Share URL → Copy to Phone
3. Phone → Access URL → Enter Password + Username → Join Chat
4. Both → Real-time Messaging → Synchronized Chat
```

## Real-Time Communication

### Message Synchronization

The application uses HTTP polling for real-time communication:

#### Desktop Chat Script
```javascript
function pollMessages() {
    fetch('/api/messages/' + sessionId)
        .then(r => r.json())
        .then(messages => {
            const container = document.getElementById('chatContainer');
            container.innerHTML = ''; // Clear existing messages
            messages.forEach(msg => {
                addMessage(msg.deviceType, msg.content, msg.timestamp, msg.username);
            });
        });
}

// Poll every 1 second
setInterval(pollMessages, 1000);
```

#### Mobile Chat Script
Similar polling mechanism with mobile-optimized interface:
```javascript
function addMessage(device, content, timestamp, msgUsername) {
    const deviceIcon = device === 'computer' ? '[Computer]' : '[Phone]';
    const displayName = msgUsername || (device === 'computer' ? 'Computer' : 'Phone');
    // Create message element with header and content
}
```

### Message Display Format

Messages are displayed with:
- **Device Indicator:** `[Computer]` or `[Phone]`
- **Username:** Sender's chosen username
- **Content:** The actual message text
- **Timestamp:** When the message was sent

Example: `[Phone] john_doe: Hello from my phone!`

## Network Configuration

### Local Network Access

The server binds to all network interfaces for cross-device communication:

```java
HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8082), 0);
```

**Access URLs:**
- **Local:** `http://localhost:8082`
- **Network:** `http://10.0.0.95:8082` (replace with actual IP)
- **ngrok:** `https://xxx.ngrok-free.app` (when using ngrok)

### CORS Configuration

All HTTP responses include CORS headers for cross-origin access:

```java
exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
```

## User Interface

### Responsive Design

The application features a modern, responsive design with:

#### CSS Framework
```css
.container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 20px;
}

/* Mobile-first responsive design */
@media (max-width: 768px) {
    .container { padding: 10px; }
}
```

#### Color Scheme
- **Primary:** `#667eea` (Blue gradient)
- **Secondary:** `#764ba2` (Purple gradient)
- **Success:** `#28a745` (Green)
- **Error:** `#dc3545` (Red)

#### Key UI Components

1. **Navigation Bar:** Fixed top navigation with brand and user menu
2. **Dashboard:** Statistics cards and session management
3. **Chat Interface:** Message container with input field
4. **Forms:** Styled input forms for login/registration
5. **Cards:** Container elements with shadows and rounded corners

### Mobile Optimization

Mobile interface features:
- Touch-friendly buttons and inputs
- Optimized message display
- Responsive layout
- Smooth animations and transitions

## Data Persistence

### In-Memory Storage

The application uses concurrent hash maps for data storage:

```java
private static final Map<String, User> users = new ConcurrentHashMap<>();
private static final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();
```

**Advantages:**
- Fast access and modification
- Thread-safe operations
- No external database dependencies

**Limitations:**
- Data lost on server restart
- Memory usage scales with user/message count
- No persistent storage

### Future Enhancements

For production use, consider:
- Database integration (H2, PostgreSQL, MongoDB)
- Message persistence
- User authentication tokens
- Session cleanup mechanisms

## Security Considerations

### Current Security Measures

1. **Password Protection:** Sessions require password verification
2. **CORS Headers:** Controlled cross-origin access
3. **Input Validation:** Basic parameter validation
4. **Network Binding:** Configurable network access

### Security Limitations

1. **No HTTPS:** Plain HTTP communication
2. **No Encryption:** Messages sent in plain text
3. **No Rate Limiting:** Potential for abuse
4. **No Session Expiry:** Sessions persist indefinitely
5. **No Input Sanitization:** Potential XSS vulnerabilities

### Recommended Improvements

1. Implement HTTPS/TLS encryption
2. Add input sanitization and validation
3. Implement rate limiting
4. Add session expiry mechanisms
5. Use secure authentication tokens
6. Implement proper error handling

## Deployment

### Local Development

1. **Compile:** `javac EnhancedSimpleServer.java`
2. **Run:** `java EnhancedSimpleServer`
3. **Access:** `http://localhost:8082`

### Network Deployment

1. **Find IP:** `ifconfig` or `ipconfig`
2. **Update baseUrl:** Modify IP address in code
3. **Firewall:** Ensure port 8082 is open
4. **Access:** `http://[YOUR_IP]:8082`

### ngrok Integration

For external access through ngrok:

1. **Install ngrok:** Download from ngrok.com
2. **Authenticate:** `ngrok authtoken [TOKEN]`
3. **Run Server:** `java EnhancedSimpleServer [NGROK_URL]`
4. **Start Tunnel:** `ngrok http 8082`

## API Reference

### Authentication Endpoints

#### POST `/login`
**Parameters:**
- `username`: User identifier
- `password`: User password

**Response:** Redirects to dashboard or returns error

#### POST `/register`
**Parameters:**
- `username`: Unique identifier
- `password`: User password
- `displayName`: Display name
- `email`: Email address

**Response:** Creates user and redirects to dashboard

### Session Endpoints

#### GET `/dashboard?user={username}`
**Purpose:** Display user dashboard
**Response:** HTML dashboard page

#### POST `/dashboard?user={username}`
**Parameters:**
- `password`: Session password

**Purpose:** Create new chat session
**Response:** Dashboard with active session

#### GET `/chat/{sessionId}`
**Purpose:** Mobile chat interface
**Response:** HTML mobile chat page

### API Endpoints

#### GET `/api/messages/{sessionId}`
**Purpose:** Retrieve session messages
**Response:** JSON array of messages

#### POST `/api/messages/{sessionId}`
**Parameters:**
- `content`: Message text
- `senderDevice`: Device type (computer/phone)
- `username`: Sender username

**Purpose:** Send new message
**Response:** JSON success/error status

### Profile Endpoints

#### GET `/profile?user={username}`
**Purpose:** Display profile page
**Response:** HTML profile form

#### POST `/profile?user={username}`
**Parameters:** Profile fields (displayName, email, phoneNumber, bio)
**Purpose:** Update user profile
**Response:** Updated profile page

#### GET `/settings?user={username}`
**Purpose:** Display settings page
**Response:** HTML settings form

#### POST `/settings?user={username}`
**Parameters:** Settings fields (theme, notifications, soundEnabled, etc.)
**Purpose:** Update user settings
**Response:** Updated settings page

## Error Handling

### HTTP Status Codes

- **200:** Success
- **400:** Bad Request (invalid parameters)
- **404:** Not Found (invalid endpoint)
- **500:** Internal Server Error

### Error Messages

The application provides user-friendly error messages for:
- Invalid login credentials
- Missing required fields
- Session creation failures
- Network connectivity issues

## Performance Considerations

### Scalability

**Current Limitations:**
- Single-threaded message processing
- In-memory storage limits
- No connection pooling
- Polling-based updates (not WebSocket)

**Optimization Opportunities:**
- Implement WebSocket connections
- Add database persistence
- Use connection pooling
- Implement caching mechanisms
- Add load balancing support

### Memory Usage

**Current Usage:**
- Each user: ~1KB memory
- Each message: ~200 bytes
- Each session: Variable based on message count

**Monitoring:**
- Monitor heap usage
- Implement garbage collection tuning
- Add memory usage logging

## Testing

### Manual Testing Scenarios

1. **User Registration/Login**
   - Create new account
   - Login with existing account
   - Handle invalid credentials

2. **Session Management**
   - Create new session
   - Join existing session
   - Handle invalid passwords

3. **Cross-Device Messaging**
   - Send from computer to phone
   - Send from phone to computer
   - Verify message synchronization

4. **Profile Management**
   - Update profile information
   - Verify changes persist
   - Handle validation errors

### Automated Testing

Consider implementing:
- Unit tests for core functionality
- Integration tests for API endpoints
- Load testing for concurrent users
- Security testing for vulnerabilities

## Troubleshooting

### Common Issues

1. **Port Already in Use**
   - **Error:** `java.net.BindException: Address already in use`
   - **Solution:** Kill existing process or change port

2. **Cross-Device Connection Failed**
   - **Error:** "Only local files allowed"
   - **Solution:** Ensure server binds to 0.0.0.0, not localhost

3. **Messages Not Syncing**
   - **Issue:** Messages not appearing on other devices
   - **Solution:** Check polling function, verify API endpoints

4. **ngrok Session Limit**
   - **Error:** "Your account is limited to 1 simultaneous ngrok agent sessions"
   - **Solution:** Kill existing ngrok processes: `pkill ngrok`

### Debug Information

Enable debug logging by adding:
```java
System.out.println("Debug: " + debugInfo);
```

Monitor network traffic with browser developer tools or network monitoring tools.

## Future Roadmap

### Short-term Enhancements

1. **WebSocket Implementation:** Replace polling with real-time WebSocket connections
2. **Database Integration:** Add persistent storage with H2 or PostgreSQL
3. **File Sharing:** Enable image and file sharing capabilities
4. **Push Notifications:** Implement browser push notifications

### Long-term Features

1. **Voice Messages:** Add audio message support
2. **Video Chat:** Integrate WebRTC for video calling
3. **Group Management:** Advanced group chat features
4. **Mobile App:** Native iOS/Android applications
5. **End-to-End Encryption:** Secure message encryption

### Technical Improvements

1. **Microservices Architecture:** Split into separate services
2. **Container Orchestration:** Kubernetes deployment
3. **CI/CD Pipeline:** Automated testing and deployment
4. **Monitoring:** Application performance monitoring
5. **Analytics:** User behavior and usage analytics

---

## Getting Started

### Quick Start Guide

1. **Clone Repository:** `git clone [repository-url]`
2. **Navigate:** `cd AlphaTexting`
3. **Compile:** `javac EnhancedSimpleServer.java`
4. **Run:** `java EnhancedSimpleServer`
5. **Access:** Open `http://localhost:8082` in browser
6. **Register:** Create a new user account
7. **Create Session:** Set up a password-protected chat session
8. **Share:** Copy the chat URL to your mobile device
9. **Chat:** Start messaging between devices!

### Development Setup

1. **Java Requirements:** Java 11 or higher
2. **Network Configuration:** Ensure port 8082 is available
3. **Firewall:** Allow incoming connections on port 8082
4. **Mobile Testing:** Connect devices to same network

For questions or support, refer to this documentation or check the troubleshooting section. 