#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if ! command -v Xephyr >/dev/null 2>&1; then
    echo "=================================================================" >&2
    echo "Xephyr is not installed." >&2
    echo "To test amadeOS instantly in a native window without QEMU or ISO building, install it with:" >&2
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

echo "[run-xephyr] Building the window manager, panel and session jars..."
(cd .. && ./gradlew :wm:jar :panel-cde:jar :session:jar)

WM_JAR="$(ls "$ROOT_DIR"/build/libs/amadeos-wm-*.jar 2>/dev/null | head -n 1)"
PANEL_JAR="$(ls "$ROOT_DIR"/../panel-cde/build/libs/amadeos-panel-cde-*.jar 2>/dev/null | head -n 1)"
SESSION_JAR="$(ls "$ROOT_DIR"/../session/build/libs/amadeos-session-*.jar 2>/dev/null | head -n 1)"
for jar in "$WM_JAR" "$PANEL_JAR" "$SESSION_JAR"; do
    if [ ! -f "$jar" ]; then
        echo "[run-xephyr] Error: jar not found: $jar" >&2
        exit 1
    fi
done

echo "[run-xephyr] Starting Xephyr nested X server on $DISPLAY_NUM (1024x768)..."
Xephyr "$DISPLAY_NUM" -ac -screen 1024x768 -resizeable -title "amadeOS Desktop (Xephyr)" &
XEPHYR_PID=$!

cleanup() {
    echo "[run-xephyr] Shutting down session..."
    kill ${PANEL_PID:-} ${WM_PID:-} $XEPHYR_PID 2>/dev/null || true
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

# The session jar on the class path lets Super+L start the screen lock without the installed wrapper.
echo "[run-xephyr] Starting amadeOS Window Manager on $DISPLAY_NUM..."
DISPLAY="$DISPLAY_NUM" java --enable-native-access=ALL-UNNAMED -cp "$WM_JAR:$SESSION_JAR" \
    org.datazuul.amadeos.wm.AmadeWindowManagerMain &
WM_PID=$!
sleep 0.5

echo "[run-xephyr] Starting amadeOS Desktop Panel Dock on $DISPLAY_NUM..."
DISPLAY="$DISPLAY_NUM" java --enable-native-access=ALL-UNNAMED -jar "$PANEL_JAR" &
PANEL_PID=$!

echo "[run-xephyr] amadeOS is running! Close the Xephyr window to exit."
wait $XEPHYR_PID

