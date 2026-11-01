package eu.amadeos.system.disks.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.system.disks.Disks;
import eu.amadeos.system.disks.FakeDisks;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Locale;

/**
 * Opens Disks with the computer of {@link FakeDisks}, for the pictures of the handbooks:
 * {@code DisksDemo [system|stick|format|delete|create|resize|health|write|save] [language]}.
 */
final class DisksDemo {
    private DisksDemo() {
    }

    public static void main(String[] args) {
        String scene = args.length > 0 ? args[0] : "system";
        Locale locale = args.length > 1 ? Locale.forLanguageTag(args[1]) : Locale.ENGLISH;
        Locale.setDefault(locale);
        FakeDisks disks = new FakeDisks();
        SwingUtilities.invokeLater(() -> {
            Context context = new Context(Messages.load(Disks.class, Disks.TEXTS, locale), disks, "ralf");
            MainWindow w = new MainWindow(context);
            switch (scene) {
                case "stick", "format", "delete", "write", "save" -> w.select("/dev/sdb", false);
                default -> { }
            }
            w.show(disks.drives());
            w.setVisible(true);
            Timer later = new Timer(1200, e -> {
                switch (scene) {
                    case "system", "resize" -> w.partitionMap().select(1);
                    case "create" -> w.partitionMap().select(3);
                    default -> { }
                }
                switch (scene) {
                    case "format" -> w.action("action.format-drive").actionPerformed(null);
                    case "delete" -> w.action("action.delete").actionPerformed(null);
                    case "create" -> w.action("action.create").actionPerformed(null);
                    case "resize" -> w.action("action.resize").actionPerformed(null);
                    case "health" -> w.action("action.health").actionPerformed(null);
                    case "write" -> w.action("action.write-image").actionPerformed(null);
                    case "save" -> w.action("action.save-image").actionPerformed(null);
                    default -> { }
                }
            });
            later.setRepeats(false);
            later.start();
        });
    }
}
