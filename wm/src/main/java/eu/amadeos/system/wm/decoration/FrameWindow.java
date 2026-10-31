package eu.amadeos.system.wm.decoration;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Objects;
import java.util.Optional;
import eu.amadeos.system.base.x11.LibC;
import eu.amadeos.system.base.x11.LibX11;

/**
 * Encapsulates an X11 server-side decoration frame window wrapping a reparented client window.
 */
public final class FrameWindow {
    private final NativeLong clientWindow;
    private final NativeLong frameWindow;
    private final DecorationTheme theme;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    private String title = "";
    /** Shown after the title, e.g. "(Not Responding)"; empty for none. */
    private String titleSuffix = "";
    private boolean active = false;
    private boolean maximized = false;
    private boolean minimized = false;
    private Rectangle unmaximizedBounds;
    private boolean fullScreen = false;
    private Rectangle normalBounds;

    private WindowButton hoveredButton;
    private WindowButton pressedButton;

    private BufferedImage buffer;

    public FrameWindow(
        NativeLong clientWindow,
        NativeLong frameWindow,
        int x,
        int y,
        int width,
        int height,
        DecorationTheme theme
    ) {
        this.clientWindow = Objects.requireNonNull(clientWindow, "clientWindow");
        this.frameWindow = Objects.requireNonNull(frameWindow, "frameWindow");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.frameX = x;
        this.frameY = y;
        this.frameWidth = Math.max(1, width);
        this.frameHeight = Math.max(1, height);
        initBuffer();
    }

    public static FrameWindow createForClient(
        NativeLong clientWindow,
        NativeLong frameWindow,
        int clientX,
        int clientY,
        int clientWidth,
        int clientHeight,
        DecorationTheme theme
    ) {
        FrameMetrics m = theme.metrics();
        int fx = clientX - m.clientOffsetX();
        int fy = clientY - m.clientOffsetY();
        int fw = m.frameWidth(clientWidth);
        int fh = m.frameHeight(clientHeight);
        return new FrameWindow(clientWindow, frameWindow, fx, fy, fw, fh, theme);
    }

    private void initBuffer() {
        this.buffer = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_RGB);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.frameX = x;
        this.frameY = y;
        int newW = Math.max(1, width);
        int newH = Math.max(1, height);
        if (this.frameWidth != newW || this.frameHeight != newH) {
            this.frameWidth = newW;
            this.frameHeight = newH;
            initBuffer();
        }
    }

    public int frameX() {
        return frameX;
    }

    public int frameY() {
        return frameY;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public int clientX() {
        return theme.metrics().clientOffsetX();
    }

    public int clientY() {
        return theme.metrics().clientOffsetY();
    }

    public int clientWidth() {
        return theme.metrics().clientWidth(frameWidth);
    }

    public int clientHeight() {
        return theme.metrics().clientHeight(frameHeight);
    }

    public FrameMetrics metrics() {
        return theme.metrics();
    }

    public DecorationTheme theme() {
        return theme;
    }

    public NativeLong clientWindow() {
        return clientWindow;
    }

    public NativeLong frameWindow() {
        return frameWindow;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public String titleSuffix() {
        return titleSuffix;
    }

    /** Text shown after the title, such as the window manager's "(Not Responding)"; null or empty for none. */
    public void setTitleSuffix(String suffix) {
        this.titleSuffix = suffix != null ? suffix : "";
    }

    /** The title as the title bar shows it: the window's title and the suffix, if any. */
    public String displayedTitle() {
        return titleSuffix.isEmpty() ? title : title + " " + titleSuffix;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isMaximized() {
        return maximized;
    }

    public void setMaximized(boolean maximized) {
        this.maximized = maximized;
    }

    public boolean isMinimized() {
        return minimized;
    }

    public void setMinimized(boolean minimized) {
        this.minimized = minimized;
    }

    public Rectangle unmaximizedBounds() {
        return unmaximizedBounds;
    }

    public void setUnmaximizedBounds(Rectangle bounds) {
        this.unmaximizedBounds = bounds;
    }

    /** Whether the client covers the whole screen (_NET_WM_STATE_FULLSCREEN), its decoration off screen. */
    public boolean isFullScreen() {
        return fullScreen;
    }

    public void setFullScreen(boolean fullScreen) {
        this.fullScreen = fullScreen;
    }

    /** The frame bounds before full screen, restored when the client leaves it. */
    public Rectangle normalBounds() {
        return normalBounds;
    }

    public void setNormalBounds(Rectangle bounds) {
        this.normalBounds = bounds;
    }

    public WindowButton hoveredButton() {
        return hoveredButton;
    }

    public void setHoveredButton(WindowButton button) {
        this.hoveredButton = button;
    }

    public WindowButton pressedButton() {
        return pressedButton;
    }

    public void setPressedButton(WindowButton button) {
        this.pressedButton = button;
    }

    public Optional<WindowButton> hitTestButton(int x, int y) {
        return theme.metrics().hitTestButton(frameWidth, x, y);
    }

    public boolean isTitleBarDragZone(int x, int y) {
        return theme.metrics().isTitleBarDragZone(frameWidth, x, y);
    }

    public ResizeDirection hitTestBorder(int x, int y) {
        return theme.metrics().hitTestBorder(frameWidth, frameHeight, x, y);
    }

    public BufferedImage buffer() {
        return buffer;
    }

    /**
     * Renders decorations into the off-screen image buffer.
     */
    public void render() {
        if (buffer == null || buffer.getWidth() != frameWidth || buffer.getHeight() != frameHeight) {
            initBuffer();
        }
        Graphics2D g = buffer.createGraphics();
        try {
            theme.paintFrame(g, frameWidth, frameHeight, displayedTitle(), active, hoveredButton, pressedButton);
        } finally {
            g.dispose();
        }
    }

    /**
     * Blits the off-screen buffer into the native X11 frame window.
     */
    public void blit(Pointer display, Pointer gc, int screen) {
        if (display == null || gc == null || buffer == null) {
            return;
        }
        int w = buffer.getWidth();
        int h = buffer.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        int[] pixels = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
        Pointer visual = LibX11.INSTANCE.XDefaultVisual(display, screen);
        int depth = LibX11.INSTANCE.XDefaultDepth(display, screen);

        long dataSize = (long) w * h * 4L;
        Pointer nativeMem = LibC.INSTANCE.malloc(dataSize);
        if (nativeMem == null) {
            return;
        }

        boolean imageCreated = false;
        try {
            nativeMem.write(0, pixels, 0, pixels.length);
            Pointer ximage = LibX11.INSTANCE.XCreateImage(
                display, visual, depth, LibX11.ZPixmap, 0,
                nativeMem, w, h, 32, w * 4
            );
            if (ximage != null) {
                imageCreated = true;
                LibX11.INSTANCE.XPutImage(
                    display, frameWindow, gc, ximage,
                    0, 0, 0, 0, w, h
                );
                LibX11.INSTANCE.XDestroyImage(ximage);
            }
        } finally {
            if (!imageCreated) {
                LibC.INSTANCE.free(nativeMem);
            }
        }
    }
}
