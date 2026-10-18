package eu.amadeos.print.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.print.ui.MainWindow;

public final class AmadePrintApplication {
    public static final AppInfo APP = new AppInfo("amadeos-print", "Print Manager", "print");

    private AmadePrintApplication() {
    }

    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            window.setVisible(true);
            window.start();
        }, new PrintModule());
    }
}
