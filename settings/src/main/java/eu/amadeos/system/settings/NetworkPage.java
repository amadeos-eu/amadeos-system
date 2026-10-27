package eu.amadeos.system.settings;

import eu.amadeos.commons.net.NetworkProxy;
import eu.amadeos.commons.net.ProxyCheck;
import eu.amadeos.commons.net.ProxySettings;
import eu.amadeos.commons.net.ProxyStore;
import eu.amadeos.system.base.net.NetworkService;
import eu.amadeos.system.packages.PackageService;
import eu.amadeos.system.packages.ProgressListener;
import eu.amadeos.system.packages.Result;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Network, in two tabs. Connections: the Ethernet and WLAN devices of NetworkManager ({@link ConnectionsPanel}),
 * which act at once. Proxy: the proxy every program of amadeOS uses, in {@code ~/.config/amadeos/proxy.conf} with the password in
 * the keyring (amade-commons {@link ProxyStore}). Programs follow the file at once; the session exports it as
 * {@code http_proxy} at the next login. With "also for software", apt gets it too, through the package helper,
 * for which polkit asks for the administrator's password. Apply and Reset concern the proxy.
 */
final class NetworkPage extends SettingsPage {
    private final ProxyStore store;
    private final PackageService packages;
    private final SettingsStores stores;
    private final BiFunction<ProxySettings, String, ProxyCheck.Result> checker;
    private final Map<String, String> environment;

    private final JRadioButton system = new JRadioButton(SettingsTexts.get("network.mode.system"));
    private final JRadioButton none = new JRadioButton(SettingsTexts.get("network.mode.none"));
    private final JRadioButton manual = new JRadioButton(SettingsTexts.get("network.mode.manual"));
    private final JLabel detected = new JLabel();
    private final JTextField host = new JTextField(22);
    private final JSpinner port = port();
    private final JCheckBox httpsSame = new JCheckBox(SettingsTexts.get("network.https.same"));
    private final JTextField httpsHost = new JTextField(22);
    private final JSpinner httpsPort = port();
    private final JTextField noProxy = new JTextField(30);
    private final JTextField user = new JTextField(14);
    private final JPasswordField password = new JPasswordField(14);
    private final JButton test = new JButton(SettingsTexts.get("network.test"));
    private final JLabel testResult = new JLabel(" ");
    private final JCheckBox apt = new JCheckBox(SettingsTexts.get("network.apt"));
    private final ConnectionsPanel connections;
    private final JTabbedPane tabs = new JTabbedPane();

    /** What was saved when the page opened or was last applied, to tell whether apt needs the new proxy. */
    private ProxySettings savedSettings;
    private String savedPassword = "";
    private boolean savedApt;

    NetworkPage(Consumer<String> status, ProxyStore store, PackageService packages, SettingsStores stores,
                BiFunction<ProxySettings, String, ProxyCheck.Result> checker, Map<String, String> environment,
                NetworkService network) {
        super(SettingsCategory.NETWORK, status);
        this.connections = new ConnectionsPanel(network, this::status);
        this.store = store;
        this.packages = packages;
        this.stores = stores;
        this.checker = checker;
        this.environment = Map.copyOf(environment);
        ButtonGroup modes = new ButtonGroup();
        for (JRadioButton b : new JRadioButton[]{system, none, manual}) {
            modes.add(b);
            b.addActionListener(e -> enableFields());
        }
        httpsSame.addActionListener(e -> enableFields());
        test.addActionListener(e -> runTest());
        password.setEnabled(false);

        show(store.current(), "");
        savedSettings = store.current();
        savedApt = stores.aptProxy();
        apt.setSelected(savedApt);
        detected.setText(describeEnvironment());

        tabs.addTab(SettingsTexts.get("network.tab.connections"), connections);
        tabs.addTab(SettingsTexts.get("network.tab.proxy"), stack(
                section(SettingsTexts.get("network.proxy.title"), system, indented(detected), none, manual),
                section(SettingsTexts.get("network.manual.title"), form(),
                        line(test, testResult)),
                section(SettingsTexts.get("network.apt.title"), apt, note(SettingsTexts.get("network.apt.hint"))),
                note(SettingsTexts.get("network.hint"))));
        layoutPage(tabs);
        readPassword();
    }

    private static JSpinner port() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(ProxySettings.DEFAULT_PORT, 1, 65535, 1));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#"));
        return spinner;
    }

    private static JComponent indented(JComponent c) {
        JPanel p = line(c);
        p.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 20, 0, 0));
        return p;
    }

    private JPanel form() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 8, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        row(form, c, 0, "network.host", host, new JLabel(SettingsTexts.get("network.port")), port);
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 3;
        form.add(httpsSame, c);
        c.gridwidth = 1;
        row(form, c, 2, "network.https.host", httpsHost, new JLabel(SettingsTexts.get("network.port")), httpsPort);
        row(form, c, 3, "network.noproxy", noProxy, null, null);
        row(form, c, 4, "network.user", user, new JLabel(SettingsTexts.get("network.password")), password);
        return form;
    }

    private static void row(JPanel form, GridBagConstraints c, int y, String label, JComponent field,
                            JComponent label2, JComponent field2) {
        c.gridy = y;
        c.gridx = 0;
        form.add(new JLabel(SettingsTexts.get(label)), c);
        c.gridx = 1;
        c.gridwidth = label2 == null ? 3 : 1;
        form.add(field, c);
        c.gridwidth = 1;
        if (label2 != null) {
            c.gridx = 2;
            form.add(label2, c);
            c.gridx = 3;
            form.add(field2, c);
        }
    }

    private void show(ProxySettings s, String pw) {
        (switch (s.mode()) {
            case NONE -> none;
            case MANUAL -> manual;
            case SYSTEM -> system;
        }).setSelected(true);
        host.setText(s.host());
        port.setValue(s.port());
        httpsSame.setSelected(s.httpsSame());
        httpsHost.setText(s.httpsHost());
        httpsPort.setValue(s.httpsPort());
        noProxy.setText(s.noProxy());
        user.setText(s.user());
        password.setText(pw);
        enableFields();
    }

    /** The keyring may take a moment; the password field waits for it. */
    private void readPassword() {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return store.current().user().isEmpty() ? "" : store.password();
            }

            @Override
            protected void done() {
                try {
                    savedPassword = get();
                } catch (InterruptedException | ExecutionException e) {
                    savedPassword = "";
                }
                // Unless the user typed one meanwhile.
                if (password.getPassword().length == 0) password.setText(savedPassword);
            }
        }.execute();
    }

    private void enableFields() {
        boolean m = manual.isSelected();
        for (JComponent c : new JComponent[]{host, port, httpsSame, noProxy, user, password}) c.setEnabled(m);
        httpsHost.setEnabled(m && !httpsSame.isSelected());
        httpsPort.setEnabled(m && !httpsSame.isSelected());
    }

    /** E.g. "Now: proxy.example:3128" or "Now: none, direct connection", from the session's variables. */
    String describeEnvironment() {
        NetworkProxy probe = new NetworkProxy(environment);
        probe.configure(ProxySettings.DEFAULT, "");
        return probe.describe().map(p -> SettingsTexts.format("network.mode.system.now", p))
                .orElse(SettingsTexts.get("network.mode.system.none"));
    }

    ProxySettings selected() {
        ProxySettings.Mode mode = manual.isSelected() ? ProxySettings.Mode.MANUAL
                : none.isSelected() ? ProxySettings.Mode.NONE : ProxySettings.Mode.SYSTEM;
        return new ProxySettings(mode, host.getText(), (Integer) port.getValue(), httpsSame.isSelected(),
                httpsHost.getText(), (Integer) httpsPort.getValue(), user.getText(), noProxy.getText());
    }

    String password() {
        return new String(password.getPassword());
    }

    private void runTest() {
        ProxySettings candidate = selected();
        String pw = password();
        test.setEnabled(false);
        testResult.setText(SettingsTexts.get("network.test.running"));
        new SwingWorker<ProxyCheck.Result, Void>() {
            @Override
            protected ProxyCheck.Result doInBackground() {
                return checker.apply(candidate, pw);
            }

            @Override
            protected void done() {
                test.setEnabled(true);
                try {
                    testResult.setText(describe(get()));
                } catch (InterruptedException | ExecutionException e) {
                    testResult.setText(SettingsTexts.format("network.test.failed", "", e.getMessage()));
                }
            }
        }.execute();
    }

    static String describe(ProxyCheck.Result r) {
        String via = r.via().isEmpty() ? SettingsTexts.get("network.test.direct") : r.via();
        return switch (r.outcome()) {
            case OK -> SettingsTexts.format("network.test.ok", via);
            case CREDENTIALS_NEEDED -> SettingsTexts.get("network.test.credentials-needed");
            case CREDENTIALS_REFUSED -> SettingsTexts.get("network.test.credentials-refused");
            case HTTP_ERROR -> SettingsTexts.format("network.test.http-error", r.detail());
            case FAILED -> SettingsTexts.format("network.test.failed", via, r.detail());
        };
    }

    JTabbedPane tabs() {
        return tabs;
    }

    ConnectionsPanel connections() {
        return connections;
    }

    JLabel testResult() {
        return testResult;
    }

    JCheckBox aptBox() {
        return apt;
    }

    JTextField hostField() {
        return host;
    }

    JTextField userField() {
        return user;
    }

    JPasswordField passwordField() {
        return password;
    }

    // --8<-- [start:apply]
    @Override
    protected String apply() {
        ProxySettings settings = selected();
        String pw = settings.mode() == ProxySettings.Mode.MANUAL ? password() : "";
        boolean aptOn = apt.isSelected();
        boolean aptChanged = aptOn != savedApt || (aptOn && (!settings.equals(savedSettings) || !pw.equals(savedPassword)));
        // The keyring and the helper can take a while (polkit asks for a password); the file is written first.
        new SwingWorker<Result, Void>() {
            @Override
            protected Result doInBackground() {
                store.write(settings, pw);
                stores.saveAptProxy(aptOn);
                if (!aptChanged) return null;
                if (!aptOn) return packages.setAptProxy("", "", ProgressListener.NONE);
                NetworkProxy candidate = new NetworkProxy(environment);
                candidate.configure(settings, pw);
                return packages.setAptProxy(candidate.endpoint(false).map(NetworkProxy.Endpoint::url).orElse(""),
                        candidate.endpoint(true).map(NetworkProxy.Endpoint::url).orElse(""), ProgressListener.NONE);
            }

            @Override
            protected void done() {
                try {
                    Result result = get();
                    savedSettings = settings;
                    savedPassword = pw;
                    if (result == null || result.success()) savedApt = aptOn;
                    if (result == null) status(SettingsTexts.format("status.applied", SettingsCategory.NETWORK.label()));
                    else if (result.success()) status(SettingsTexts.get(aptOn ? "network.status.apt" : "network.status.apt-off"));
                    else status(SettingsTexts.format("network.status.apt-failed", result.message()));
                } catch (InterruptedException | ExecutionException e) {
                    status(SettingsTexts.format("status.error", e.getMessage()));
                }
            }
        }.execute();
        return SettingsTexts.get("network.status.saving");
    }
    // --8<-- [end:apply]

    @Override
    protected void cancelled() {
        show(savedSettings, savedPassword);
        apt.setSelected(savedApt);
        testResult.setText(" ");
    }
}
