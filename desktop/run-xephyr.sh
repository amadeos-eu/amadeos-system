#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if ! command -v Xephyr >/dev/null 2>&1; then
    echo "=================================================================" >&2
    echo "Xephyr is not installed." >&2
    echo "To test DazeOS instantly in a native window without QEMU or ISO building, install it with:" >&2
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

echo "[run-xephyr] Building latest DazeOS JAR..."
./gradlew jar

JAR="$ROOT_DIR/build/libs/dazeos-0.1.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
    echo "[run-xephyr] Error: JAR not found at $JAR" >&2
    exit 1
fi

echo "[run-xephyr] Starting Xephyr nested X server on $DISPLAY_NUM (1024x768)..."
Xephyr "$DISPLAY_NUM" -ac -screen 1024x768 -resizeable -title "DazeOS Desktop (Xephyr)" &
XEPHYR_PID=$!

cleanup() {
    echo "[run-xephyr] Shutting down session..."
    kill ${DEMO_PID:-} ${PANEL_PID:-} ${WM_PID:-} $XEPHYR_PID 2>/dev/null || true
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

echo "[run-xephyr] Starting DazeOS Window Manager on $DISPLAY_NUM..."
DISPLAY="$DISPLAY_NUM" java --enable-native-access=ALL-UNNAMED -jar "$JAR" wm &
WM_PID=$!
sleep 0.5

echo "[run-xephyr] Starting DazeOS Desktop Panel Dock on $DISPLAY_NUM..."
DISPLAY="$DISPLAY_NUM" java --enable-native-access=ALL-UNNAMED -jar "$JAR" panel &
PANEL_PID=$!
sleep 0.3

echo "[run-xephyr] Starting DazeOS Demo Application on $DISPLAY_NUM..."
DISPLAY="$DISPLAY_NUM" java --enable-native-access=ALL-UNNAMED -jar "$JAR" demo &
DEMO_PID=$!

echo "[run-xephyr] DazeOS is running! Close the Xephyr window to exit."
wait $XEPHYR_PID

