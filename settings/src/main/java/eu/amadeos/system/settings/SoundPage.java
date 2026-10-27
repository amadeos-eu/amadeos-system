package eu.amadeos.system.settings;

import eu.amadeos.system.base.sound.AudioDevice;
import eu.amadeos.system.base.sound.Pactl;
import eu.amadeos.system.base.sound.SoundCard;
import eu.amadeos.system.base.sound.SoundService;
import eu.amadeos.system.base.sound.SoundStatus;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Sound: which output and input programs use, their volume, a test tone and a level meter, and the profile of
 * each sound card (e.g. analog or HDMI). Everything acts at once through pipewire-pulse ({@link SoundService},
 * the same backend as the Front Panel's sound cell) and follows changes made elsewhere.
 */
final class SoundPage extends SettingsPage {
    private final SoundService sound;
    private final SoundCheck check;
    // One worker, so that quick slider moves reach the server in order.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sound-page");
        t.setDaemon(true);
        return t;
    });

    private final DefaultListModel<AudioDevice> outputs = new DefaultListModel<>();
    private final JList<AudioDevice> outputList = new JList<>(outputs);
    private final JSlider outputVolume = volumeSlider();
    private final JLabel outputPercent = new JLabel("100 %");
    private final JCheckBox outputMute = new JCheckBox(SettingsTexts.get("sound.mute"));
    private final DefaultListModel<AudioDevice> inputs = new DefaultListModel<>();
    private final JList<AudioDevice> inputList = new JList<>(inputs);
    private final JSlider inputVolume = volumeSlider();
    private final JLabel inputPercent = new JLabel("100 %");
    private final JCheckBox inputMute = new JCheckBox(SettingsTexts.get("sound.mute"));
    private final JProgressBar level = new JProgressBar(0, 100);
    private final JPanel cardsPanel = new JPanel(new GridBagLayout());
    private final JLabel unavailable = new JLabel(" ");

    private SoundStatus current = SoundStatus.UNAVAILABLE;
    private boolean updating;
    private long lastUserChange;
    private AutoCloseable watch;
    private AutoCloseable meter;
    private String meterDevice = "";
    private final Timer refreshLater = new Timer(250, e -> refresh());

    SoundPage(Consumer<String> status, SoundService sound, SoundCheck check) {
        super(SettingsCategory.SOUND, status);
        this.sound = sound;
        this.check = check;
        refreshLater.setRepeats(false);

        setUpList(outputList);
        setUpList(inputList);
        outputList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating) choose(outputList.getSelectedValue(), true);
        });
        inputList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating) choose(inputList.getSelectedValue(), false);
        });
        outputVolume.addChangeListener(e -> volumeMoved(true));
        inputVolume.addChangeListener(e -> volumeMoved(false));
        outputMute.addActionListener(e -> muteChanged(true));
        inputMute.addActionListener(e -> muteChanged(false));
        level.setStringPainted(false);
        level.setPreferredSize(new Dimension(200, 12));

        JButton left = new JButton(SettingsTexts.get("sound.test.left"));
        JButton both = new JButton(SettingsTexts.get("sound.test.both"));
        JButton right = new JButton(SettingsTexts.get("sound.test.right"));
        left.addActionListener(e -> test(SoundCheck.Channel.LEFT));
        both.addActionListener(e -> test(SoundCheck.Channel.BOTH));
        right.addActionListener(e -> test(SoundCheck.Channel.RIGHT));

        layoutSections(
                line(unavailable),
                section(SettingsTexts.get("sound.output.title"), line(scroll(outputList)),
                        form("sound.volume", outputVolume, outputPercent, outputMute),
                        line(new JLabel(SettingsTexts.get("sound.test")), left, both, right)),
                section(SettingsTexts.get("sound.input.title"), line(scroll(inputList)),
                        form("sound.input.volume", inputVolume, inputPercent, inputMute),
                        line(new JLabel(SettingsTexts.get("sound.level")), level)),
                section(SettingsTexts.get("sound.cards.title"), line(cardsPanel),
                        note(SettingsTexts.get("sound.cards.hint"))),
                note(SettingsTexts.get("sound.hint")));
        unavailable.setVisible(false);
        refresh();
    }

    private static JSlider volumeSlider() {
        JSlider slider = new JSlider(0, Pactl.MAX_VOLUME, 100);
        slider.setMajorTickSpacing(50);
        slider.setMinorTickSpacing(10);
        slider.setPaintTicks(true);
        Hashtable<Integer, JComponent> labels = new Hashtable<>();
        labels.put(0, new JLabel("0"));
        labels.put(100, new JLabel("100"));
        labels.put(150, new JLabel("150"));
        slider.setLabelTable(labels);
        slider.setPaintLabels(true);
        slider.setPreferredSize(new Dimension(260, slider.getPreferredSize().height));
        return slider;
    }

    private static JPanel form(String label, JSlider slider, JLabel percent, JCheckBox mute) {
        percent.setPreferredSize(new Dimension(48, percent.getPreferredSize().height));
        return line(new JLabel(SettingsTexts.get(label)), slider, percent, mute);
    }

    private static void setUpList(JList<AudioDevice> list) {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(3);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                AudioDevice d = (AudioDevice) value;
                return super.getListCellRendererComponent(l, d.description(), index, selected, focus);
            }
        });
    }

    private static JScrollPane scroll(JList<?> list) {
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 66));
        return scroll;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (watch == null) watch = sound.watch(() -> SwingUtilities.invokeLater(refreshLater::restart));
        refresh();
    }

    @Override
    public void removeNotify() {
        close(watch);
        watch = null;
        stopMeter();
        super.removeNotify();
    }

    /** Reads the devices again in the background and shows them. */
    void refresh() {
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() {
                return new Object[]{sound.status(), sound.cards()};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                try {
                    Object[] result = get();
                    show((SoundStatus) result[0], (List<SoundCard>) result[1]);
                } catch (Exception e) {
                    show(SoundStatus.UNAVAILABLE, List.of());
                }
            }
        }.execute();
    }

    void show(SoundStatus status, List<SoundCard> cards) {
        current = status;
        updating = true;
        try {
            unavailable.setVisible(!status.available());
            unavailable.setText(SettingsTexts.get("sound.unavailable"));
            fill(outputs, outputList, status.outputs(), status.output().map(AudioDevice::name).orElse(""));
            fill(inputs, inputList, status.inputs(), status.input().map(AudioDevice::name).orElse(""));
            // A slider the user is moving keeps its place; the server's answer comes back a moment later.
            boolean recent = System.currentTimeMillis() - lastUserChange < 800;
            status.output().ifPresent(d -> {
                if (!outputVolume.getValueIsAdjusting() && !recent) outputVolume.setValue(d.volume());
                outputMute.setSelected(d.muted());
            });
            status.input().ifPresent(d -> {
                if (!inputVolume.getValueIsAdjusting() && !recent) inputVolume.setValue(d.volume());
                inputMute.setSelected(d.muted());
            });
            outputPercent.setText(outputVolume.getValue() + " %");
            inputPercent.setText(inputVolume.getValue() + " %");
            for (JComponent c : new JComponent[]{outputVolume, outputMute}) c.setEnabled(status.output().isPresent());
            for (JComponent c : new JComponent[]{inputVolume, inputMute}) c.setEnabled(status.input().isPresent());
            showCards(cards);
        } finally {
            updating = false;
        }
        String input = status.input().map(AudioDevice::name).orElse("");
        if (!input.equals(meterDevice) && isShowing()) startMeter(status.input().orElse(null));
    }

    private static void fill(DefaultListModel<AudioDevice> model, JList<AudioDevice> list, List<AudioDevice> devices,
                             String chosen) {
        model.clear();
        devices.forEach(model::addElement);
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).name().equals(chosen)) list.setSelectedIndex(i);
        }
    }

    private void showCards(List<SoundCard> cards) {
        cardsPanel.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 8, 2, 8);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        for (SoundCard card : cards) {
            List<SoundCard.Profile> choices = new ArrayList<>(card.profiles().stream()
                    .filter(p -> p.available() || p.name().equals(card.activeProfile())).toList());
            JComboBox<SoundCard.Profile> box = new JComboBox<>(choices.toArray(SoundCard.Profile[]::new));
            box.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                              boolean focus) {
                    String text = value instanceof SoundCard.Profile p
                            ? (p.name().equals("off") ? SettingsTexts.get("sound.profile.off") : p.description()) : "";
                    return super.getListCellRendererComponent(l, text, index, selected, focus);
                }
            });
            card.active().ifPresent(box::setSelectedItem);
            box.addActionListener(e -> {
                if (box.getSelectedItem() instanceof SoundCard.Profile p && !p.name().equals(card.activeProfile())) {
                    run(() -> sound.setProfile(card, p.name()));
                }
            });
            c.gridy = row++;
            c.gridx = 0;
            cardsPanel.add(new JLabel(card.description()), c);
            c.gridx = 1;
            cardsPanel.add(box, c);
        }
        if (cards.isEmpty()) {
            c.gridx = 0;
            c.gridy = 0;
            cardsPanel.add(new JLabel(SettingsTexts.get("sound.cards.none")), c);
        }
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private void choose(AudioDevice device, boolean output) {
        if (device == null) return;
        String now = output ? current.defaultOutput() : current.defaultInput();
        if (device.name().equals(now)) return;
        run(() -> {
            if (output) sound.setOutput(device);
            else sound.setInput(device);
        });
        status(SettingsTexts.format(output ? "sound.status.output" : "sound.status.input", device.description()));
    }

    private void volumeMoved(boolean output) {
        JSlider slider = output ? outputVolume : inputVolume;
        (output ? outputPercent : inputPercent).setText(slider.getValue() + " %");
        if (updating) return;
        lastUserChange = System.currentTimeMillis();
        AudioDevice device = (output ? current.output() : current.input()).orElse(null);
        if (device == null) return;
        int value = slider.getValue();
        run(() -> {
            if (output) sound.setOutputVolume(device, value);
            else sound.setInputVolume(device, value);
        });
    }

    private void muteChanged(boolean output) {
        AudioDevice device = (output ? current.output() : current.input()).orElse(null);
        if (device == null) return;
        boolean muted = (output ? outputMute : inputMute).isSelected();
        run(() -> {
            if (output) sound.setOutputMuted(device, muted);
            else sound.setInputMuted(device, muted);
        });
    }

    private void test(SoundCheck.Channel channel) {
        current.output().ifPresent(d -> {
            check.playTest(d, channel);
            status(SettingsTexts.format("sound.status.test", d.description()));
        });
    }

    private void startMeter(AudioDevice input) {
        stopMeter();
        if (input == null) return;
        meterDevice = input.name();
        meter = check.meter(input, value -> SwingUtilities.invokeLater(() -> level.setValue(value)));
    }

    private void stopMeter() {
        close(meter);
        meter = null;
        meterDevice = "";
        level.setValue(0);
    }

    private interface SoundCall {
        void run() throws SoundService.SoundException;
    }

    private void run(SoundCall call) {
        worker.execute(() -> {
            try {
                call.run();
            } catch (SoundService.SoundException e) {
                SwingUtilities.invokeLater(() -> status(SettingsTexts.format("status.error", e.getMessage())));
            }
        });
    }

    private static void close(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignored) {
            // nothing left to do
        }
    }

    JList<AudioDevice> outputList() {
        return outputList;
    }

    JSlider outputVolume() {
        return outputVolume;
    }

    JProgressBar level() {
        return level;
    }

    @Override
    protected String apply() {
        return null;
    }
}
