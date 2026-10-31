package eu.amadeos.system.taskmanager.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Dimension;

/** The look shared by the tables of the Task Manager. */
final class Tables {
    private Tables() {
    }

    static void style(JTable table, int[] widths, int... rightAligned) {
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setRowHeight(Math.max(20, table.getFontMetrics(table.getFont()).getHeight() + 6));
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(false);
        Padded left = new Padded(SwingConstants.LEADING);
        Padded right = new Padded(SwingConstants.TRAILING);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(left);
            if (i < widths.length) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        for (int i : rightAligned) table.getColumnModel().getColumn(i).setCellRenderer(right);
    }

    /** Text with some room on both sides. */
    static class Padded extends DefaultTableCellRenderer {
        Padded(int alignment) {
            setHorizontalAlignment(alignment);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, selected, false, row, column);
            l.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 10));
            return l;
        }
    }
}
