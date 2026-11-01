package eu.amadeos.system.wm.decoration;

import java.awt.Rectangle;

/**
 * The measures of the Horizon frame: a 34 pixel title bar with the program icon (window menu) on the left and
 * minimise, maximise and close on the right, and a thin 4 pixel border that is the resize handle.
 */
public final class HorizonFrameMetrics extends FrameMetrics {
    public static final int TITLE_BAR_HEIGHT = 34;
    public static final int BORDER = 4;
    static final int BUTTON_WIDTH = 30;
    static final int BUTTON_HEIGHT = 22;
    static final int BUTTON_GAP = 4;
    static final int RIGHT_MARGIN = 8;
    static final int MENU_SIZE = 24;
    static final int MENU_X = 8;

    public HorizonFrameMetrics() {
        super(TITLE_BAR_HEIGHT, BORDER, 24, BUTTON_WIDTH, BUTTON_GAP);
    }

    private static int buttonY() {
        return (TITLE_BAR_HEIGHT - BUTTON_HEIGHT) / 2;
    }

    @Override
    public Rectangle getButtonBounds(int frameWidth, WindowButton button) {
        int closeX = frameWidth - RIGHT_MARGIN - BUTTON_WIDTH;
        return switch (button) {
            case MENU -> new Rectangle(MENU_X, (TITLE_BAR_HEIGHT - MENU_SIZE) / 2, MENU_SIZE, MENU_SIZE);
            case CLOSE -> new Rectangle(closeX, buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT);
            case MAXIMIZE -> new Rectangle(closeX - BUTTON_WIDTH - BUTTON_GAP, buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT);
            case MINIMIZE -> new Rectangle(closeX - 2 * (BUTTON_WIDTH + BUTTON_GAP), buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT);
        };
    }

    /** The title bar between the icon and the buttons: where the title stands and the window is dragged. */
    @Override
    public Rectangle getTitleBarBounds(int frameWidth) {
        int x = MENU_X + MENU_SIZE + 6;
        int right = getButtonBounds(frameWidth, WindowButton.MINIMIZE).x - 6;
        return new Rectangle(x, BORDER, Math.max(1, right - x), TITLE_BAR_HEIGHT - BORDER);
    }

    /** The whole title bar row drags the window, except for the icon and the buttons. */
    @Override
    public boolean isTitleBarDragZone(int frameWidth, int x, int y) {
        if (y < BORDER || y >= TITLE_BAR_HEIGHT || x < BORDER || x >= frameWidth - BORDER) return false;
        return hitTestButton(frameWidth, x, y).isEmpty();
    }
}
