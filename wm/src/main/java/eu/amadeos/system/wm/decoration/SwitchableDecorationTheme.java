package eu.amadeos.system.wm.decoration;

import eu.amadeos.commons.theme.DesktopLook;
import eu.amadeos.commons.theme.DesktopTheme;

import java.awt.Graphics2D;

/**
 * The frame style of the desktop look: Motif frames for the classic look, Horizon frames for Horizon. Every
 * frame holds this one object, so a change of look reaches all frames at once; the window manager then fits
 * the frames around their windows again, since the two styles have different measures.
 */
public final class SwitchableDecorationTheme implements DecorationTheme {
    private final MotifDecorationTheme motif;
    private final HorizonDecorationTheme horizon;
    private volatile DecorationTheme current;

    public SwitchableDecorationTheme(DesktopTheme theme) {
        this.motif = new MotifDecorationTheme(FrameMetrics.defaultMotif(), theme);
        this.horizon = new HorizonDecorationTheme(theme);
        this.current = styleOf(theme);
    }

    private DecorationTheme styleOf(DesktopTheme theme) {
        return theme != null && theme.look() == DesktopLook.HORIZON ? horizon : motif;
    }

    /** Takes the colours of the theme and the frame style of its look; true if the style (and its measures) changed. */
    public boolean setTheme(DesktopTheme theme) {
        motif.setTheme(theme);
        horizon.setTheme(theme);
        DecorationTheme next = styleOf(theme);
        boolean changed = next != current;
        current = next;
        return changed;
    }

    /** The style in use. */
    public DecorationTheme current() {
        return current;
    }

    @Override
    public String name() {
        return current.name();
    }

    @Override
    public FrameMetrics metrics() {
        return current.metrics();
    }

    @Override
    public void paintFrame(Graphics2D g, int width, int height, String title, boolean active,
                           WindowButton hoveredButton, WindowButton pressedButton) {
        current.paintFrame(g, width, height, title, active, hoveredButton, pressedButton);
    }

    @Override
    public void paintFrame(Graphics2D g, FrameWindow frame) {
        current.paintFrame(g, frame);
    }

    @Override
    public int cornerRadius() {
        return current.cornerRadius();
    }
}
