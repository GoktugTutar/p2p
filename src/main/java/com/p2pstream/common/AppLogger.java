package com.p2pstream.common;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;

public final class AppLogger {
    private static final Object LOCK = new Object();
    private static volatile Path logFile = Paths.get("p2pstream.log");

    private AppLogger() {}

    public static void setLogFile(Path path) {
        if (path == null) return;
        logFile = path;
    }

    public static void info(String msg) { write("INFO", msg, null); }
    public static void warn(String msg) { write("WARN", msg, null); }
    public static void error(String msg, Throwable t) { write("ERROR", msg, t); }

    private static void write(String level, String msg, Throwable t) {
        String line = String.format("%s [%s] %s", Instant.now(), level, msg == null ? "" : msg);

        System.out.println(line);
        if (t != null) t.printStackTrace(System.out);

        synchronized (LOCK) {
            try {
                Files.createDirectories(logFile.toAbsolutePath().getParent() == null
                        ? Paths.get(".")
                        : logFile.toAbsolutePath().getParent());

                try (FileWriter fw = new FileWriter(logFile.toFile(), true);
                     BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write(line);
                    bw.newLine();
                    if (t != null) {
                        bw.write("  " + t);
                        bw.newLine();
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
