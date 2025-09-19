# Alpha Texting

A real-time multi-user chat application that enables seamless communication between computers and mobile devices. Built entirely in Java using the built-in HTTP server.

## Features

• **Multi-User Chat**: Password-protected sessions with multiple users  
• **Cross-Device Sync**: Real-time messaging between computer and mobile  
• **User Profiles**: Customizable user profiles and settings  
• **Network Ready**: Works across local network and with ngrok  
• **No Dependencies**: Pure Java implementation with no external libraries  

## Quick Start

### 1. Run the Server
```bash
# Compile and run
javac Main.java
java Main

# Or use the provided script
chmod +x start-enhanced.sh
./start-enhanced.sh
```

### 2. Access the Application
- **Computer**: Open `http://localhost:8082`
- **Mobile**: Use `http://[YOUR_IP]:8082` (replace with your computer's IP)

### 3. Start Chatting
1. Register/login on your computer
2. Create a chat session with a password
3. Copy the chat URL to your phone
4. Enter the password and your username on mobile
5. Start messaging!

## How It Works

### Session Creation
- Computer user creates a password-protected session
- Session password acts as the room ID
- Share URL: `http://[IP]:8082/chat/[password]`

### Multi-User Support
- Multiple users can join with the same password
- Each user has a unique username
- Messages show sender identification

### Real-Time Sync
- HTTP polling every 1 second for real-time updates
- Messages sync across all connected devices
- Shows device type and username for each message

## Network Configuration

### Local Network Access
The server binds to all network interfaces (`0.0.0.0:8082`) to allow external connections.

Find your IP address:
```bash
# macOS/Linux
ifconfig | grep "inet "

# Windows
ipconfig
```

### External Access with ngrok
```bash
# Install ngrok and authenticate
ngrok authtoken [YOUR_TOKEN]

# Start ngrok tunnel
ngrok http 8082

# Run server with ngrok URL
java Main https://[xxx].ngrok-free.app
```

## File Structure

```
AlphaTexting/
├── Main.java                    # Main application
├── start-enhanced.sh            # Startup script
├── README.md                    # This file
└── DOCUMENTATION.md             # Detailed technical docs
```













