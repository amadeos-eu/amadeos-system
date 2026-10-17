package org.datazuul.amadeos.wm.decoration;

import java.awt.Rectangle;
import java.util.Optional;

public final class FrameMetrics {
    private final int titleBarHeight;
    private final int borderWidth;
    private final int cornerGrabLength;
    private final int buttonSize;
    private final int buttonSpacing;

    public FrameMetrics(int titleBarHeight, int borderWidth, int cornerGrabLength, int buttonSize, int buttonSpacing) {
        if (titleBarHeight <= 0 || borderWidth <= 0) {
            throw new IllegalArgumentException("titleBarHeight and borderWidth must be positive");
        }
        this.titleBarHeight = titleBarHeight;
        this.borderWidth = borderWidth;
        this.cornerGrabLength = Math.max(cornerGrabLength, borderWidth * 2);
        this.buttonSize = buttonSize;
        this.buttonSpacing = buttonSpacing;
    }

    public static FrameMetrics defaultMotif() {
        return new FrameMetrics(26, 6, 26, 20, 0);
    }

    public int titleBarHeight() {
        return titleBarHeight;
    }

    public int borderWidth() {
        return borderWidth;
    }

    public int buttonSize() {
        return buttonSize;
    }

    public int buttonSpacing() {
        return buttonSpacing;
    }

    public int frameWidth(int clientWidth) {
        return clientWidth + (2 * borderWidth);
    }

    public int frameHeight(int clientHeight) {
        return clientHeight + titleBarHeight + borderWidth;
    }

    public int clientWidth(int frameWidth) {
        return Math.max(1, frameWidth - (2 * borderWidth));
    }

    public int clientHeight(int frameHeight) {
        return Math.max(1, frameHeight - titleBarHeight - borderWidth);
    }

    public int clientOffsetX() {
        return borderWidth;
    }

    public int clientOffsetY() {
        return titleBarHeight;
    }

    public Rectangle getTitleBarBounds(int frameWidth) {
        int x = borderWidth + buttonSize;
        int w = Math.max(1, frameWidth - (2 * borderWidth) - (4 * buttonSize));
        int h = titleBarHeight - borderWidth;
        return new Rectangle(x, borderWidth, w, h);
    }

    public Rectangle getButtonBounds(int frameWidth, WindowButton button) {
        int y = borderWidth;
        int h = titleBarHeight - borderWidth;
        return switch (button) {
            case MENU -> new Rectangle(borderWidth, y, buttonSize, h);
            case CLOSE -> new Rectangle(frameWidth - borderWidth - buttonSize, y, buttonSize, h);
            case MAXIMIZE -> new Rectangle(frameWidth - borderWidth - (2 * buttonSize), y, buttonSize, h);
            case MINIMIZE -> new Rectangle(frameWidth - borderWidth - (3 * buttonSize), y, buttonSize, h);
        };
    }

    public Optional<WindowButton> hitTestButton(int frameWidth, int x, int y) {
        if (y < borderWidth || y >= titleBarHeight) {
            return Optional.empty();
        }
        for (WindowButton button : WindowButton.values()) {
            Rectangle bounds = getButtonBounds(frameWidth, button);
            if (bounds.contains(x, y)) {
                return Optional.of(button);
            }
        }
        return Optional.empty();
    }

    public boolean isTitleBarDragZone(int frameWidth, int x, int y) {
        return getTitleBarBounds(frameWidth).contains(x, y);
    }

    public ResizeDirection hitTestBorder(int frameWidth, int frameHeight, int x, int y) {
        boolean onTop = y >= 0 && y < borderWidth;
        boolean onBottom = y >= frameHeight - borderWidth && y < frameHeight;
        boolean onLeft = x >= 0 && x < borderWidth;
        boolean onRight = x >= frameWidth - borderWidth && x < frameWidth;

        // Must be on at least one border edge
        if (!onTop && !onBottom && !onLeft && !onRight) {
            return ResizeDirection.NONE;
        }

        int cornerLeft = borderWidth + buttonSize;
        int cornerRight = frameWidth - borderWidth - buttonSize;
        int cornerTop = titleBarHeight;
        int cornerBottom = frameHeight - titleBarHeight;

        boolean nearLeft = x < cornerLeft;
        boolean nearRight = x >= cornerRight;
        boolean nearTop = y < cornerTop;
        boolean nearBottom = y >= cornerBottom;

        // Corners
        if ((onTop || onLeft) && nearTop && nearLeft) return ResizeDirection.NW;
        if ((onTop || onRight) && nearTop && nearRight) return ResizeDirection.NE;
        if ((onBottom || onLeft) && nearBottom && nearLeft) return ResizeDirection.SW;
        if ((onBottom || onRight) && nearBottom && nearRight) return ResizeDirection.SE;

        // Edges
        if (onTop) return ResizeDirection.N;
        if (onBottom) return ResizeDirection.S;
        if (onLeft) return ResizeDirection.W;
        if (onRight) return ResizeDirection.E;

        return ResizeDirection.NONE;
    }
}
