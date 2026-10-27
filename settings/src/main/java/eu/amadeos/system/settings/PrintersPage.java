package eu.amadeos.system.settings;

import eu.amadeos.system.print.cups.Printer;
import eu.amadeos.system.printers.FoundPrinter;
import eu.amadeos.system.printers.PrinterOptions;
import eu.amadeos.system.printers.PrinterService;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Printers: the CUPS queues with their state, adding a printer found in the network or on USB (driverless, IPP
 * Everywhere), removing one, the default printer, a test page, and the default paper size and two-sided printing.
 * The print jobs stay in the print manager (amadeos-print), which "Print Queue" opens. Everything acts at once.
 */
final class PrintersPage extends SettingsPage {
    /** The Duplex values of a CUPS PPD, in the order the page offers them. */
    static final List<String> DUPLEX = List.of("None", "DuplexNoTumble", "DuplexTumble");

    private final PrinterService printers;
    private final Consumer<List<String>> launcher;

    private final DefaultListModel<Printer> model = new DefaultListModel<>();
    private final JList<Printer> list = new JList<>(model);
    private final JButton add = new JButton(SettingsTexts.get("printers.add"));
    private final JButton remove = new JButton(SettingsTexts.get("printers.remove"));
    private final JButton makeDefault = new JButton(SettingsTexts.get("printers.default"));
    private final JButton testPage = new JButton(SettingsTexts.get("printers.testpage"));
    private final JButton queue = new JButton(SettingsTexts.get("printers.queue"));
    private final JLabel empty = new JLabel(" ");
    private final JPanel optionsSection;
    private final JComboBox<String> pageSize = new JComboBox<>();
    private final JComboBox<String> duplex = new JComboBox<>();
    private final JButton saveOptions = new JButton(SettingsTexts.get("printers.options.save"));
    private PrinterOptions options = PrinterOptions.NONE;
    private boolean busy;
    private final Timer poll = new Timer(5_000, e -> refresh());

    PrintersPage(Consumer<String> status, PrinterService printers, Consumer<List<String>> launcher) {
        super(SettingsCategory.PRINTERS, status);
        this.printers = printers;
        this.launcher = launcher;

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(4);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(l, describe((Printer) value), index, selected, focus);
                setIcon(DeviceIcons.printer());
                setIconTextGap(8);
                return this;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectionChanged();
        });
        add.addActionListener(e -> new AddDialog().setVisible(true));
        remove.addActionListener(e -> withSelected(this::confirmRemove));
        makeDefault.addActionListener(e -> withSelected(p -> act(() -> printers.makeDefault(p),
                SettingsTexts.format("printers.status.default", p.title()))));
        testPage.addActionListener(e -> withSelected(p -> act(() -> printers.printTestPage(p),
                SettingsTexts.format("printers.status.testpage", p.title()))));
        queue.addActionListener(e -> launcher.accept(List.of("amadeos-print")));
        pageSize.setRenderer(new DefaultListCellRenderer());
        duplex.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(l, duplexLabel((String) value), index, selected, focus);
            }
        });
        saveOptions.addActionListener(e -> withSelected(p -> {
            String size = (String) pageSize.getSelectedItem();
            String sides = (String) duplex.getSelectedItem();
            act(() -> printers.setOptions(p, size == null ? "" : size, sides == null ? "" : sides),
                    SettingsTexts.format("printers.status.options", p.title()));
        }));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(460, 160));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 8, 2, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        form.add(new JLabel(SettingsTexts.get("printers.options.paper")), c);
        form.add(pageSize, c);
        c.gridy = 1;
        form.add(new JLabel(SettingsTexts.get("printers.options.duplex")), c);
        form.add(duplex, c);
        optionsSection = section(SettingsTexts.get("printers.options.title"), line(form), line(saveOptions));
        layoutSections(
                section(SettingsTexts.get("printers.list.title"), line(empty), line(scroll),
                        line(add, remove, makeDefault), line(testPage, queue)),
                optionsSection,
                note(SettingsTexts.get("printers.hint")));
        empty.setVisible(false);
        selectionChanged();
        refresh();
    }

    /** "<b>Brother HL-L2350DW</b> (default)<br>Ready - Brother HL-L2350DW series, Office". */
    static String describe(Printer p) {
        String title = escape(p.title()) + (p.isDefault() ? " " + SettingsTexts.get("printers.is-default") : "");
        List<String> second = new ArrayList<>();
        second.add(SettingsTexts.get("printers.state." + p.state().name().toLowerCase(Locale.ROOT)));
        if (!p.accepting()) second.add(SettingsTexts.get("printers.rejecting"));
        if (!p.makeAndModel().isBlank()) second.add(p.makeAndModel());
        if (!p.location().isBlank()) second.add(p.location());
        if (p.jobCount() > 0) second.add(SettingsTexts.format("printers.jobs", p.jobCount()));
        return "<html><b>" + title + "</b><br>" + escape(String.join(" · ", second)) + "</html>";
    }

    static String duplexLabel(String value) {
        if (value == null) return "";
        return switch (value) {
            case "None" -> SettingsTexts.get("printers.duplex.none");
            case "DuplexNoTumble" -> SettingsTexts.get("printers.duplex.long");
            case "DuplexTumble" -> SettingsTexts.get("printers.duplex.short");
            default -> value;
        };
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    @Override
    public void addNotify() {
        super.addNotify();
        poll.start();
    }

    @Override
    public void removeNotify() {
        poll.stop();
        super.removeNotify();
    }

    void refresh() {
        new SwingWorker<List<Printer>, Void>() {
            private String error;

            @Override
            protected List<Printer> doInBackground() {
                try {
                    return printers.printers();
                } catch (PrinterService.PrinterException e) {
                    error = e.getMessage();
                    return List.of();
                }
            }

            @Override
            protected void done() {
                try {
                    show(get(), error);
                } catch (Exception e) {
                    show(List.of(), e.getMessage());
                }
            }
        }.execute();
    }

    void show(List<Printer> list, String error) {
        String selected = this.list.getSelectedValue() == null ? null : this.list.getSelectedValue().name();
        boolean same = list.size() == model.size();
        for (int i = 0; same && i < list.size(); i++) same = list.get(i).equals(model.get(i));
        if (!same) {
            model.clear();
            list.forEach(model::addElement);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).name().equals(selected)) this.list.setSelectedIndex(i);
            }
            if (this.list.getSelectedIndex() < 0 && !list.isEmpty()) this.list.setSelectedIndex(0);
        }
        empty.setText(error != null ? SettingsTexts.get("printers.no-cups")
                : SettingsTexts.get("printers.none"));
        empty.setVisible(error != null || list.isEmpty());
        add.setEnabled(error == null && !busy);
    }

    private void selectionChanged() {
        Printer p = list.getSelectedValue();
        for (JComponent b : new JComponent[]{remove, testPage}) b.setEnabled(p != null && !busy);
        makeDefault.setEnabled(p != null && !p.isDefault() && !busy);
        optionsSection.setVisible(p != null);
        if (p == null) return;
        optionsSection.setBorder(javax.swing.BorderFactory.createTitledBorder(
                SettingsTexts.format("printers.options.title.of", p.title())));
        new SwingWorker<PrinterOptions, Void>() {
            @Override
            protected PrinterOptions doInBackground() {
                return printers.options(p);
            }

            @Override
            protected void done() {
                try {
                    showOptions(get());
                } catch (Exception e) {
                    showOptions(PrinterOptions.NONE);
                }
            }
        }.execute();
    }

    void showOptions(PrinterOptions o) {
        options = o;
        pageSize.removeAllItems();
        o.pageSizes().forEach(pageSize::addItem);
        pageSize.setSelectedItem(o.pageSize());
        duplex.removeAllItems();
        DUPLEX.stream().filter(o.duplexes()::contains).forEach(duplex::addItem);
        duplex.setSelectedItem(o.duplex());
        pageSize.setEnabled(!o.pageSizes().isEmpty());
        duplex.setEnabled(o.canDuplex());
        saveOptions.setEnabled(!o.pageSizes().isEmpty() || o.canDuplex());
    }

    private void withSelected(Consumer<Printer> action) {
        Printer p = list.getSelectedValue();
        if (p != null) action.accept(p);
    }

    private void confirmRemove(Printer p) {
        int answer = JOptionPane.showConfirmDialog(this, SettingsTexts.format("printers.remove.question", p.title()),
                SettingsTexts.get("printers.remove.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            act(() -> printers.remove(p), SettingsTexts.format("printers.status.removed", p.title()));
        }
    }

    private interface PrinterCall {
        void run() throws PrinterService.PrinterException;
    }

    private void act(PrinterCall call, String success) {
        busy = true;
        selectionChanged();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    call.run();
                    return null;
                } catch (PrinterService.PrinterException e) {
                    return e.getMessage();
                }
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    String error = get();
                    status(error == null ? success : SettingsTexts.format("status.error", error));
                } catch (Exception e) {
                    status(SettingsTexts.format("status.error", e.getMessage()));
                }
                selectionChanged();
                refresh();
            }
        }.execute();
    }

    /**
     * Add Printer: the printers CUPS finds (a search of a few seconds), or an address typed in, and a name.
     */
    final class AddDialog extends JDialog {
        private final DefaultListModel<FoundPrinter> found = new DefaultListModel<>();
        private final JList<FoundPrinter> foundList = new JList<>(found);
        private final JLabel searchState = new JLabel(SettingsTexts.get("printers.add.searching"));
        private final JTextField address = new JTextField(28);
        private final JTextField name = new JTextField(28);
        private final JTextField location = new JTextField(28);
        private final JButton ok = new JButton(SettingsTexts.get("printers.add.ok"));

        AddDialog() {
            super((Window) SwingUtilities.getWindowAncestor(PrintersPage.this), SettingsTexts.get("printers.add.title"),
                    ModalityType.APPLICATION_MODAL);
            foundList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            foundList.setVisibleRowCount(5);
            foundList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                              boolean focus) {
                    FoundPrinter f = (FoundPrinter) value;
                    String text = f.info().isBlank() ? f.makeAndModel() : f.info();
                    if (!f.location().isBlank()) text += " (" + f.location() + ")";
                    super.getListCellRendererComponent(l, text, index, selected, focus);
                    setIcon(DeviceIcons.printer());
                    return this;
                }
            });
            foundList.addListSelectionListener(e -> {
                FoundPrinter f = foundList.getSelectedValue();
                if (f == null) return;
                address.setText("");
                name.setText(f.info().isBlank() ? f.makeAndModel() : f.info());
                location.setText(f.location());
                enableOk();
            });
            address.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { typed(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { typed(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { typed(); }
            });
            name.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { enableOk(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { enableOk(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { enableOk(); }
            });
            JButton cancel = new JButton(SettingsTexts.get("button.cancel"));
            cancel.addActionListener(e -> dispose());
            ok.addActionListener(e -> addPrinter());
            ok.setEnabled(false);

            JScrollPane scroll = new JScrollPane(foundList);
            scroll.setPreferredSize(new Dimension(440, 120));
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 8, 2, 8);
            c.anchor = GridBagConstraints.WEST;
            c.gridy = 0;
            form.add(new JLabel(SettingsTexts.get("printers.add.address")), c);
            form.add(address, c);
            c.gridy = 1;
            form.add(new JLabel(SettingsTexts.get("printers.add.name")), c);
            form.add(name, c);
            c.gridy = 2;
            form.add(new JLabel(SettingsTexts.get("printers.add.location")), c);
            form.add(location, c);

            JPanel content = new JPanel();
            content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
            content.add(section(SettingsTexts.get("printers.add.found"), line(searchState), line(scroll)));
            content.add(section(SettingsTexts.get("printers.add.manual"), form,
                    note(SettingsTexts.get("printers.add.address.hint"))));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            buttons.add(ok);
            buttons.add(cancel);
            JPanel root = new JPanel(new BorderLayout(0, 10));
            root.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 14, 10, 14));
            root.add(content, BorderLayout.CENTER);
            root.add(buttons, BorderLayout.SOUTH);
            setContentPane(root);
            getRootPane().setDefaultButton(ok);
            pack();
            setLocationRelativeTo(PrintersPage.this);
            search();
        }

        private void typed() {
            if (!address.getText().isBlank()) foundList.clearSelection();
            enableOk();
        }

        private void enableOk() {
            ok.setEnabled(!name.getText().isBlank()
                    && (foundList.getSelectedValue() != null || !address.getText().isBlank()));
        }

        private void search() {
            new SwingWorker<List<FoundPrinter>, Void>() {
                @Override
                protected List<FoundPrinter> doInBackground() throws Exception {
                    return printers.discover();
                }

                @Override
                protected void done() {
                    try {
                        List<FoundPrinter> list = get();
                        list.forEach(found::addElement);
                        searchState.setText(SettingsTexts.get(list.isEmpty() ? "printers.add.none" : "printers.add.choose"));
                    } catch (Exception e) {
                        searchState.setText(SettingsTexts.format("status.error", e.getCause() == null
                                ? e.getMessage() : e.getCause().getMessage()));
                    }
                }
            }.execute();
        }

        private void addPrinter() {
            FoundPrinter f = foundList.getSelectedValue();
            String uri = f != null ? f.uri() : uri(address.getText());
            String info = name.getText().strip();
            String queueName = FoundPrinter.queueName(info);
            String where = location.getText().strip();
            dispose();
            status(SettingsTexts.format("printers.status.adding", info));
            act(() -> printers.add(queueName, uri, info, where), SettingsTexts.format("printers.status.added", info));
        }
    }

    /** An address as typed: "192.168.1.40" or "drucker.local" becomes {@code ipp://.../ipp/print}; URIs stay. */
    static String uri(String typed) {
        String t = typed.strip();
        if (t.contains("://")) return t;
        return "ipp://" + t + "/ipp/print";
    }

    JList<Printer> printerList() {
        return list;
    }

    @Override
    protected String apply() {
        return null;
    }
}
