package eu.amadeos.sysinfo.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.sysinfo.ui.MainWindow;

public final class AmadeSysinfoApplication {
    public static final AppInfo APP = new AppInfo("amadeos-sysinfo", "System Information", "sysinfo");

    private AmadeSysinfoApplication() {
    }

    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            window.setVisible(true);
            window.load();
        }, new SysinfoModule());
    }
}
