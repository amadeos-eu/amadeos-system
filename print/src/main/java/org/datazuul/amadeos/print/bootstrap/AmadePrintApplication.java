package org.datazuul.amadeos.print.bootstrap;

import org.datazuul.amadeos.commons.app.AmadeApplication;
import org.datazuul.amadeos.commons.app.AppInfo;
import org.datazuul.amadeos.print.ui.MainWindow;

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
