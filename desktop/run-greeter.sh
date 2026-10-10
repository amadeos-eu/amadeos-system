#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if ! command -v Xephyr >/dev/null 2>&1; then
    echo "=================================================================" >&2
    echo "Xephyr is not installed." >&2
    echo "To test DazeOS Login Greeter instantly, install it with:" >&2
    echo "  sudo apt-get install -y xserver-xephyr" >&2
    echo "=================================================================" >&2
    exit 1
fi

DISPLAY_NUM=":1"
for d in $(seq 1 10); do
    if [ ! -e "/tmp/.X11-unix/X$d" ]; then
        DISPLAY_NUM=":$d"
        break
    fi
done

echo "[run-greeter] Building latest DazeOS JAR..."
./gradlew jar

JAR="$ROOT_DIR/build/libs/dazeos-0.1.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
    echo "[run-greeter] Error: JAR not found at $JAR" >&2
    exit 1
fi

echo "[run-greeter] Starting Xephyr nested X server on $DISPLAY_NUM (1024x768)..."
Xephyr "$DISPLAY_NUM" -ac -screen 1024x768 -resizeable -title "DazeOS Login Greeter (Xephyr)" &
XEPHYR_PID=$!

cleanup() {
    echo "[run-greeter] Shutting down session..."
    kill ${DM_PID:-} $XEPHYR_PID 2>/dev/null || true
    wait $XEPHYR_PID 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Wait for Xephyr to accept connections
for i in $(seq 1 30); do
    if xset -q -display "$DISPLAY_NUM" >/dev/null 2>&1; then
        break
    fi
    sleep 0.1
done

echo "[run-greeter] Starting DazeOS Display Manager & Login Greeter on $DISPLAY_NUM..."
echo "[run-greeter] Credentials: User 'dazeos', Password 'dazeos'"
DAZEOS_MOCK_PAM=1 DISPLAY="$DISPLAY_NUM" java --enable-native-access=ALL-UNNAMED -jar "$JAR" dm "$DISPLAY_NUM" &
DM_PID=$!

echo "[run-greeter] Greeter is running! Close the Xephyr window to exit."
wait $XEPHYR_PID

