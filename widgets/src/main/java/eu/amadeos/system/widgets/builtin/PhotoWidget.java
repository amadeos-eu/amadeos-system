package eu.amadeos.system.widgets.builtin;

import eu.amadeos.commons.widget.DesktopWidget;
import eu.amadeos.commons.widget.WidgetColors;
import eu.amadeos.commons.widget.WidgetContext;
import eu.amadeos.commons.widget.WidgetSettings;
import eu.amadeos.system.widgets.WidgetTexts;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A photo frame: one picture, or the pictures of a folder one after the other. The picture fills the card
 * and is cut to its shape, as a print in a frame.
 */
public final class PhotoWidget implements DesktopWidget {
    static final Set<String> TYPES = Set.of("jpg", "jpeg", "png", "gif", "bmp");

    @Override
    public String id() {
        return "photo";
    }

    @Override
    public String name() {
        return WidgetTexts.get("photo.name");
    }

    @Override
    public String description() {
        return WidgetTexts.get("photo.description");
    }

    @Override
    public Dimension defaultSize() {
        return new Dimension(300, 220);
    }

    @Override
    public JComponent create(WidgetContext context) {
        Frame frame = new Frame(context);
        long minutes = Math.max(1, parseLong(context.get("minutes", "10"), 10));
        context.every(Duration.ofMinutes(minutes), frame::next);
        return frame;
    }

    /** The pictures of a folder, by name, or the one picture; nothing if the path holds none. */
    static List<Path> pictures(Path path) {
        if (path == null) return List.of();
        if (Files.isRegularFile(path)) return isPicture(path) ? List.of(path) : List.of();
        if (!Files.isDirectory(path)) return List.of();
        try (Stream<Path> s = Files.list(path)) {
            return s.filter(PhotoWidget::isPicture).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static boolean isPicture(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        return dot > 0 && TYPES.contains(n.substring(dot + 1)) && !n.startsWith(".");
    }

    static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class Frame extends JComponent {
        private final WidgetContext context;
        private BufferedImage image;
        private int index = -1;
        private String shownPath = "";

        Frame(WidgetContext context) {
            this.context = context;
        }

        void next() {
            String setting = context.get("path", "");
            List<Path> list = pictures(setting.isBlank() ? null : Path.of(setting));
            if (!setting.equals(shownPath)) index = -1;
            shownPath = setting;
            if (list.isEmpty()) {
                image = null;
            } else {
                index = (index + 1) % list.size();
                try {
                    image = ImageIO.read(list.get(index).toFile());
                } catch (IOException e) {
                    image = null;
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (!shownPath.equals(context.get("path", ""))) next();
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            WidgetColors c = context.colors();
            int w = getWidth();
            int h = getHeight();
            if (image == null) {
                g.setColor(WidgetColors.mix(c.card(), c.text(), 0.06));
                g.fillRoundRect(0, 0, w, h, 12, 12);
                g.setColor(c.dim());
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                FontMetrics fm = g.getFontMetrics();
                String text = WidgetTexts.get("photo.empty");
                g.drawString(text, Math.max(4, (w - fm.stringWidth(text)) / 2), h / 2 + fm.getAscent() / 2);
            } else {
                // Fill the frame, cut what is left over, as a print is cut
                double scale = Math.max(w / (double) image.getWidth(), h / (double) image.getHeight());
                int iw = (int) Math.round(image.getWidth() * scale);
                int ih = (int) Math.round(image.getHeight() * scale);
                g.setClip(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
                g.drawImage(image, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
            }
            g.dispose();
        }
    }

    @Override
    public WidgetSettings settings(WidgetContext context) {
        JTextField path = new JTextField(context.get("path", ""), 28);
        JButton choose = new JButton(WidgetTexts.get("photo.choose"));
        choose.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(path.getText().isBlank()
                    ? Path.of(System.getProperty("user.home"), "Pictures").toFile() : Path.of(path.getText()).toFile());
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (chooser.showOpenDialog(choose) == JFileChooser.APPROVE_OPTION) {
                path.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JSpinner minutes = new JSpinner(new SpinnerNumberModel(
                (int) Math.max(1, parseLong(context.get("minutes", "10"), 10)), 1, 1440, 1));
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.add(new JLabel(WidgetTexts.get("photo.path")));
        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.add(path, BorderLayout.CENTER);
        pathRow.add(choose, BorderLayout.EAST);
        form.add(pathRow);
        JPanel minutesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        minutesRow.add(new JLabel(WidgetTexts.get("photo.minutes") + " "));
        minutesRow.add(minutes);
        form.add(minutesRow);
        return new WidgetSettings(form, () -> {
            context.set("path", path.getText().strip());
            context.set("minutes", minutes.getValue().toString());
        });
    }
}
