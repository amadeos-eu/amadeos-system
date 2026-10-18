package eu.amadeos.wm;

public record WindowGeometry(int x, int y, int width, int height) {
    public WindowGeometry {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Window dimensions must be positive");
        }
    }
}

