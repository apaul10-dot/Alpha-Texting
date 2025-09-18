#!/bin/bash

echo "=== Alpha Texting Server ==="
echo

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    echo "Please install Java 11 or higher and try again"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 1)
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo "❌ Error: Java 11 or higher is required"
    echo "Current version: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

# Kill any existing server process
echo "🔍 Checking for existing server processes..."
pkill -f Main 2>/dev/null && echo "✅ Stopped existing server"

# Compile the application
echo "🔨 Compiling Alpha Texting..."
if javac Main.java; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

# Get local IP address
LOCAL_IP=""
if command -v ifconfig &> /dev/null; then
    LOCAL_IP=$(ifconfig | grep "inet " | grep -v "127.0.0.1" | head -1 | awk '{print $2}' | cut -d: -f2)
elif command -v ip &> /dev/null; then
    LOCAL_IP=$(ip route get 1 | awk '{print $7; exit}')
fi

# Start the server
echo "🚀 Starting Alpha Texting server..."
echo
echo "📍 Access URLs:"
echo "   Computer: http://localhost:8082"
if [ ! -z "$LOCAL_IP" ]; then
    echo "   Mobile:   http://$LOCAL_IP:8082"
fi
echo
echo "💡 Tips:"
echo "   • Register a new account or login"
echo "   • Create a chat session with a password"
echo "   • Share the mobile URL with your phone"
echo "   • Use the same password to join from mobile"
echo
echo "🛑 Press Ctrl+C to stop the server"
echo "================================================"

# Check if ngrok URL is provided as argument
if [ $# -gt 0 ]; then
    echo "🌐 Using ngrok URL: $1"
    java Main "$1"
else
    java Main
fi 