package eu.amadeos.system.settings;

import eu.amadeos.system.base.shortcuts.KeyCombo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.Locale;

/**
 * "Press the new keys for Terminal": waits for a key combination. It reads the keys through {@link KeyCapture};
 * when the keyboard cannot be grabbed it reads them from its own key events, which then miss the keys other
 * programs have grabbed.
 */
final class KeyCaptureDialog extends JDialog {
    private final JLabel keys = new JLabel(" ", SwingConstants.CENTER);
    private KeyCapture.Result result = new KeyCapture.Result.Cancelled();
    private KeyCapture.Session session;
    private boolean ownKeys;
    private final java.awt.KeyEventDispatcher dispatcher = this::ownKey;

    private KeyCaptureDialog(Window owner, String what, KeyCapture capture) {
        super(owner, SettingsTexts.get("shortcuts.capture.title"), ModalityType.APPLICATION_MODAL);
        JLabel prompt = new JLabel("<html>" + SettingsTexts.format("shortcuts.capture.prompt", what) + "</html>");
        keys.setFont(keys.getFont().deriveFont(Font.BOLD, keys.getFont().getSize2D() * 1.6f));
        keys.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLoweredBevelBorder(),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        keys.setText(SettingsTexts.get("shortcuts.capture.waiting"));
        JLabel hint = new JLabel("<html>" + SettingsTexts.get("shortcuts.capture.hint") + "</html>");
        JButton cancel = new JButton(SettingsTexts.get("button.cancel"));
        cancel.setFocusable(false);
        cancel.addActionListener(e -> finish(new KeyCapture.Result.Cancelled()));
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.add(hint, BorderLayout.CENTER);
        south.add(cancel, BorderLayout.EAST);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        content.add(prompt, BorderLayout.NORTH);
        content.add(keys, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        // Tab and the arrows are keys like any other here.
        setFocusTraversalKeysEnabled(false);
        for (int id : new int[]{KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS}) {
            setFocusTraversalKeys(id, Collections.emptySet());
        }
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                session = capture.start(
                        held -> SwingUtilities.invokeLater(() -> showHeld(held)),
                        r -> SwingUtilities.invokeLater(() -> {
                            if (r instanceof KeyCapture.Result.Unavailable) {
                                ownKeys = true;
                            } else {
                                finish(r);
                            }
                        }));
            }

            @Override
            public void windowClosed(WindowEvent e) {
                if (session != null) session.cancel();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
            }
        });
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        setSize(Math.max(420, getPreferredSize().width), getPreferredSize().height + 10);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog and waits; the result is Cancelled if it was closed. */
    static KeyCapture.Result ask(Component parent, String what, KeyCapture capture) {
        KeyCaptureDialog dialog = new KeyCaptureDialog(SwingUtilities.getWindowAncestor(parent), what, capture);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void showHeld(int modifiers) {
        if (!isDisplayable()) return;
        keys.setText(modifiers == 0 ? SettingsTexts.get("shortcuts.capture.waiting")
                : ShortcutsPage.display(KeyCombo.of(modifiers, "x")).replaceAll("X$", "…"));
    }

    private void finish(KeyCapture.Result r) {
        if (!isDisplayable()) return;
        result = r;
        if (session != null) session.cancel();
        dispose();
    }

    /** The key events of the dialog itself, used only when the X capture is not available. */
    private boolean ownKey(KeyEvent e) {
        if (!ownKeys || !isDisplayable() || e.getID() != KeyEvent.KEY_PRESSED
                || SwingUtilities.getWindowAncestor(e.getComponent()) != this && e.getComponent() != this) {
            return false;
        }
        int modifiers = (e.isShiftDown() ? KeyCombo.SHIFT : 0) | (e.isControlDown() ? KeyCombo.CTRL : 0)
                | (e.isAltDown() ? KeyCombo.ALT : 0) | (e.isMetaDown() ? KeyCombo.SUPER : 0);
        String keysym = keysym(e.getKeyCode());
        KeyCapture.Result r = KeyCapture.result(keysym, modifiers);
        if (r == null) {
            showHeld(modifiers);
        } else {
            finish(r);
        }
        e.consume();
        return true;
    }

    /** The X keysym name of a Java key code, for the keys a shortcut uses; null for the modifiers and the rest. */
    static String keysym(int code) {
        if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) return String.valueOf((char) code).toLowerCase(Locale.ROOT);
        if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) return String.valueOf((char) code);
        if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12) return "F" + (code - KeyEvent.VK_F1 + 1);
        if (code >= KeyEvent.VK_F13 && code <= KeyEvent.VK_F24) return "F" + (code - KeyEvent.VK_F13 + 13);
        return switch (code) {
            case KeyEvent.VK_ESCAPE -> "Escape";
            case KeyEvent.VK_BACK_SPACE -> "BackSpace";
            case KeyEvent.VK_SPACE -> "space";
            case KeyEvent.VK_ENTER -> "Return";
            case KeyEvent.VK_TAB -> "Tab";
            case KeyEvent.VK_DELETE -> "Delete";
            case KeyEvent.VK_INSERT -> "Insert";
            case KeyEvent.VK_HOME -> "Home";
            case KeyEvent.VK_END -> "End";
            case KeyEvent.VK_PAGE_UP -> "Prior";
            case KeyEvent.VK_PAGE_DOWN -> "Next";
            case KeyEvent.VK_UP -> "Up";
            case KeyEvent.VK_DOWN -> "Down";
            case KeyEvent.VK_LEFT -> "Left";
            case KeyEvent.VK_RIGHT -> "Right";
            case KeyEvent.VK_PRINTSCREEN -> "Print";
            case KeyEvent.VK_PAUSE -> "Pause";
            case KeyEvent.VK_MINUS -> "minus";
            case KeyEvent.VK_PLUS -> "plus";
            case KeyEvent.VK_EQUALS -> "equal";
            case KeyEvent.VK_COMMA -> "comma";
            case KeyEvent.VK_PERIOD -> "period";
            default -> null;
        };
    }
}
