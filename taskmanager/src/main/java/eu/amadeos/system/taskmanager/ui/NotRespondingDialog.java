package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.system.taskmanager.model.ProcessRow;
import eu.amadeos.system.taskmanager.proc.ProcessInfo;
import eu.amadeos.system.taskmanager.x.WindowInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * "… is not responding": the window manager starts {@code amadeos-taskmanager --not-responding <window>} when a
 * window has not answered its ping for five seconds. <i>Wait</i> closes the question, <i>End Program</i> ends
 * the program, <i>Open Task Manager</i> shows it in the Programs tab. The dialog closes by itself when the
 * window answers again or is gone.
 */
public final class NotRespondingDialog extends JDialog {
    private static final int CHECK_MILLIS = 1000;

    private final Context context;
    private final WindowInfo window;
    private final Timer check;

    public NotRespondingDialog(Context context, WindowInfo window) {
        super((java.awt.Frame) null, context.texts().get("hang.title"), ModalityType.MODELESS);
        this.context = context;
        this.window = window;
        Messages t = context.texts();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);

        Optional<ProcessInfo> process = window.pid() > 0
                ? context.monitor().sampler().proc().process((int) window.pid()) : Optional.empty();
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(t.format("hang.heading", window.title()));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setAlignmentX(0);
        text.add(heading);
        text.add(Box.createVerticalStrut(6));
        JLabel explain = new JLabel("<html><div style=\"width:300px\">"
                + EndProcessDialog.escape(t.get("hang.explain")) + "</div></html>");
        explain.setAlignmentX(0);
        text.add(explain);
        process.ifPresent(p -> {
            JLabel which = new JLabel(t.format("hang.since", p.name(), Integer.toString(p.pid())));
            which.setFont(which.getFont().deriveFont(which.getFont().getSize2D() * 0.85f));
            which.setAlignmentX(0);
            text.add(Box.createVerticalStrut(4));
            text.add(which);
        });

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        body.add(new JLabel(UIManager.getIcon("OptionPane.warningIcon")), BorderLayout.WEST);
        body.add(text, BorderLayout.CENTER);

        JButton open = Mnemonics.apply(new JButton(), t.get("hang.open"));
        JButton wait = Mnemonics.apply(new JButton(), t.get("hang.wait"));
        JButton end = Mnemonics.apply(new JButton(), t.get("hang.end"));
        open.addActionListener(e -> {
            dispose();
            context.showProgram().accept(window.id());
        });
        wait.addActionListener(e -> dispose());
        end.addActionListener(e -> endProgram());
        end.setEnabled(window.pid() > 0);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 14, 12, 14));
        buttons.add(open);
        buttons.add(wait);
        buttons.add(end);
        add(body, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(wait);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        check = new Timer(CHECK_MILLIS, e -> stillHanging());
        check.start();
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    public long windowId() {
        return window.id();
    }

    @Override
    public void dispose() {
        check.stop();
        super.dispose();
    }

    /** Closes the question once the window answers again or has gone. */
    private void stillHanging() {
        context.monitor().onWorker(w -> {
            boolean hanging = w.find(window.id()).map(WindowInfo::notResponding).orElse(false);
            if (!hanging) SwingUtilities.invokeLater(this::dispose);
        });
    }

    private void endProgram() {
        dispose();
        Map<Integer, ProcessRow> processes = context.monitor().last() == null ? new HashMap<>()
                : context.monitor().last().sample().processes();
        ProgramsPanel.end(null, context, window, processes);
    }
}
