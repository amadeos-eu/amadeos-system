package eu.amadeos.system.disks.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/** A FlowLayout whose preferred size wraps its components to the width it gets, so rows of buttons break. */
final class WrapLayout extends FlowLayout {
    private static final long serialVersionUID = 1L;

    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return size(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = size(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int width = target.getSize().width;
            Container c = target;
            while (width == 0 && c.getParent() != null) {
                c = c.getParent();
                width = c.getSize().width;
            }
            if (width == 0) width = Integer.MAX_VALUE;
            Insets in = target.getInsets();
            int max = width - in.left - in.right - getHgap() * 2;
            int rowW = 0, rowH = 0, w = 0, h = 0;
            for (Component m : target.getComponents()) {
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowW + d.width > max && rowW > 0) {
                    w = Math.max(w, rowW);
                    h += rowH + getVgap();
                    rowW = 0;
                    rowH = 0;
                }
                rowW += d.width + getHgap();
                rowH = Math.max(rowH, d.height);
            }
            w = Math.max(w, rowW);
            h += rowH;
            if (h == 0) return new Dimension(0, 0);
            return new Dimension(w + in.left + in.right + getHgap() * 2, h + in.top + in.bottom + getVgap() * 2);
        }
    }
}
