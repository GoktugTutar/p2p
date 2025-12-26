package com.p2pstream.app;

import com.p2pstream.common.AppLogger;
import com.p2pstream.common.ConfigLoader;

public final class AppLauncher {

    public static void main(String[] args) {
        ConfigLoader cfg = ConfigLoader.getInstance();
        cfg.load(args);

        AppLogger.info("Config: mode=" + cfg.getMode()
                + " rootFolder=" + cfg.getRootFolder()
                + " bufferFolder=" + cfg.getBufferFolder());

        if (cfg.getMode() == ConfigLoader.Mode.HEADLESS) {
            System.out.println("Starting in Headless Mode");
            // ileride CLI service burada
            waitForever();
        } else {
            javafx.application.Application.launch(MainApp.class, args);
        }
    }

    private static void waitForever() {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }
}
