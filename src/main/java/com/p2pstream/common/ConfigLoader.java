package com.p2pstream.common;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class ConfigLoader {

    public enum Mode { GUI, HEADLESS }

    private static volatile ConfigLoader INSTANCE;

    private final Properties fileProps = new Properties();
    private final Map<String, String> argMap = new HashMap<>();

    private Mode mode = Mode.GUI;
    private Path rootFolder = Paths.get(System.getProperty("user.home"), "p2p_root");
    private Path bufferFolder = Paths.get(System.getProperty("user.home"), "p2p_buffer");

    private ConfigLoader() {}

    public static ConfigLoader getInstance() {
        if (INSTANCE == null) {
            synchronized (ConfigLoader.class) {
                if (INSTANCE == null) INSTANCE = new ConfigLoader();
            }
        }
        return INSTANCE;
    }

    public void load(String[] args) {
        parseArgs(args);
        loadConfigFile();

        // precedence: ENV > ARGS > FILE > DEFAULTS
        this.mode = readMode();
        this.rootFolder = readPath("rootFolder", Constants.ENV_ROOT, this.rootFolder);
        this.bufferFolder = readPath("bufferFolder", Constants.ENV_BUFFER, this.bufferFolder);
    }

    private void parseArgs(String[] args) {
        argMap.clear();
        if (args == null) return;

        for (String a : args) {
            // supports: --key=value  OR  -Dkey=value style is handled by JVM, not here
            if (a == null) continue;
            if (a.startsWith("--") && a.contains("=")) {
                String[] parts = a.substring(2).split("=", 2);
                argMap.put(parts[0].trim(), parts[1].trim());
            }
        }
    }

    private void loadConfigFile() {
        fileProps.clear();

        // Try local working dir first, then user.home
        List<Path> candidates = List.of(
                Paths.get(Constants.DEFAULT_CONFIG_FILE),
                Paths.get(System.getProperty("user.home"), Constants.DEFAULT_CONFIG_FILE)
        );

        for (Path p : candidates) {
            try (InputStream in = new FileInputStream(p.toFile())) {
                fileProps.load(in);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private Mode readMode() {
        String env = System.getenv(Constants.ENV_MODE);
        if (env != null && !env.isBlank()) return parseMode(env);

        String arg = argMap.get("mode");
        if (arg != null && !arg.isBlank()) return parseMode(arg);

        String fp = fileProps.getProperty("mode");
        if (fp != null && !fp.isBlank()) return parseMode(fp);

        return this.mode;
    }

    private Mode parseMode(String v) {
        String s = v.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "HEADLESS" -> Mode.HEADLESS;
            default -> Mode.GUI;
        };
    }

    private Path readPath(String key, String envKey, Path defaultVal) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) return Paths.get(env.trim());

        String arg = argMap.get(key);
        if (arg != null && !arg.isBlank()) return Paths.get(arg.trim());

        String fp = fileProps.getProperty(key);
        if (fp != null && !fp.isBlank()) return Paths.get(fp.trim());

        return defaultVal;
    }

    public Mode getMode() { return mode; }
    public Path getRootFolder() { return rootFolder; }
    public Path getBufferFolder() { return bufferFolder; }
}
