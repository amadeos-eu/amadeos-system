package eu.amadeos.system.taskmanager.ui;

import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.system.taskmanager.proc.ProcFs;
import eu.amadeos.system.taskmanager.proc.ProcessInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ends a process, a process with the processes it started, or a program. First the processes are asked to
 * quit (SIGTERM); if one is still running after {@link #GRACE_MILLIS}, <i>Force End</i> ends it at once
 * (SIGKILL). The dialog closes by itself when all of them are gone.
 */
public final class EndProcessDialog extends JDialog {
    /** How long the processes get to quit before Force End is offered. */
    static final int GRACE_MILLIS = 5000;
    private static final int POLL_MILLIS = 250;

    /**
     * What to end.
     *
     * @param name      the program's or process's name, shown in the question
     * @param pids      the processes, the main one first
     * @param uid       the user the main process belongs to
     * @param user      that user's name
     * @param important whether it is a part of the desktop or of the system
     */
    public record Target(String name, List<Integer> pids, int uid, String user, boolean important) {
    }

    private final Context context;
    private final Target target;
    private final ProcFs proc;
    private final JLabel waiting = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar(0, GRACE_MILLIS);
    private final JButton end = new JButton();
    private final JButton force = new JButton();
    private final JButton cancel = new JButton();
    private final Timer poll;
    private long sentAt;
    private boolean forceOffered;
    /** Called when the processes are gone (on the event dispatch thread). */
    private Runnable onEnded = () -> {
    };

    public EndProcessDialog(Window owner, Context context, Target target) {
        super(owner, context.texts().get("end.title"), ModalityType.MODELESS);
        this.context = context;
        this.target = target;
        this.proc = context.monitor().sampler().proc();
        Messages t = context.texts();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(t.format("end.question", target.name()));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        text.add(left(heading));
        text.add(Box.createVerticalStrut(6));
        String explain = target.pids().size() > 1
                ? t.format("end.tree-explain", Integer.toString(target.pids().size() - 1)) : t.get("end.explain");
        text.add(left(wrap(explain)));
        if (target.uid() != context.ownUid()) {
            text.add(Box.createVerticalStrut(4));
            text.add(left(wrap(t.format("end.system", target.user()))));
        }
        if (target.important()) {
            text.add(Box.createVerticalStrut(4));
            JLabel warn = wrap(t.get("end.important"));
            warn.setFont(warn.getFont().deriveFont(Font.BOLD));
            text.add(left(warn));
        }
        text.add(Box.createVerticalStrut(8));
        progress.setStringPainted(true);
        progress.setString(" ");
        progress.setVisible(false);
        text.add(left(progress));
        text.add(left(waiting));

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        body.add(new JLabel(UIManager.getIcon(target.important() ? "OptionPane.warningIcon"
                : "OptionPane.questionIcon")), BorderLayout.WEST);
        body.add(text, BorderLayout.CENTER);

        Mnemonics.apply(end, t.get("end.ok")).addActionListener(e -> sendTerm());
        Mnemonics.apply(force, t.get("end.force")).addActionListener(e -> sendKill());
        Mnemonics.apply(cancel, t.get("end.cancel")).addActionListener(e -> dispose());
        force.setVisible(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 14, 12, 14));
        buttons.add(cancel);
        buttons.add(force);
        buttons.add(end);
        add(body, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(end);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        poll = new Timer(POLL_MILLIS, e -> check());
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Runs when every process has ended. */
    public EndProcessDialog onEnded(Runnable action) {
        this.onEnded = action;
        return this;
    }

    @Override
    public void dispose() {
        poll.stop();
        super.dispose();
    }

    private void sendTerm() {
        end.setEnabled(false);
        cancel.setEnabled(false);
        Thread.ofVirtual().start(() -> {
            IOException failure = null;
            for (int pid : target.pids()) {
                try {
                    context.control().terminate(pid);
                } catch (IOException e) {
                    if (pid == target.pids().get(0) && alive(pid)) failure = e;
                }
            }
            IOException f = failure;
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (f != null) {
                    context.failed(this, "error.verb.end", f);
                    end.setEnabled(true);
                    cancel.setEnabled(true);
                    return;
                }
                sentAt = System.currentTimeMillis();
                end.setVisible(false);
                force.setVisible(true);
                force.setEnabled(false);
                Mnemonics.apply(cancel, context.texts().get("end.close"));
                cancel.setEnabled(true);
                progress.setVisible(true);
                pack();
                poll.start();
                check();
            });
        });
    }

    private void sendKill() {
        force.setEnabled(false);
        List<Integer> left = running();
        Thread.ofVirtual().start(() -> {
            IOException failure = null;
            for (int pid : left) {
                try {
                    context.control().kill(pid);
                } catch (IOException e) {
                    if (alive(pid)) failure = e;
                }
            }
            IOException f = failure;
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (f != null) {
                    context.failed(this, "error.verb.end", f);
                    force.setEnabled(true);
                }
                check();
            });
        });
    }

    private void check() {
        if (running().isEmpty()) {
            context.monitor().refresh();
            onEnded.run();
            dispose();
            return;
        }
        long waited = System.currentTimeMillis() - sentAt;
        progress.setValue((int) Math.min(GRACE_MILLIS, waited));
        if (waited >= GRACE_MILLIS) {
            progress.setString(context.texts().get("end.still-running"));
            if (!forceOffered) {
                forceOffered = true;
                force.setEnabled(true);
                getRootPane().setDefaultButton(force);
            }
        } else {
            progress.setString(context.texts().format("end.waiting",
                    Long.toString((GRACE_MILLIS - waited + 999) / 1000)));
        }
    }

    private List<Integer> running() {
        List<Integer> result = new ArrayList<>();
        for (int pid : target.pids()) if (alive(pid)) result.add(pid);
        return result;
    }

    /** Still there, and not only a zombie waiting for its parent. */
    private boolean alive(int pid) {
        Optional<ProcessInfo> p = proc.process(pid);
        return p.isPresent() && p.get().state() != 'Z' && p.get().state() != 'X';
    }

    private static JLabel wrap(String s) {
        return new JLabel("<html><div style=\"width:300px\">" + escape(s) + "</div></html>");
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static <C extends JComponent> C left(C c) {
        c.setAlignmentX(0);
        return c;
    }
}
