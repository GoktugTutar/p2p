package com.p2pstream.common;

public final class Constants {
    private Constants() {}

    public static final int UDP_PORT = 50000;
    public static final int TCP_PORT = 50000;

    public static final int CHUNK_SIZE = 256 * 1024; // 256KB
    public static final int DEFAULT_TTL = 5;

    public static final String DEFAULT_CONFIG_FILE = "p2pstream.properties";

    public static final String ENV_MODE = "MODE";
    public static final String ENV_ROOT = "ROOT_FOLDER";
    public static final String ENV_BUFFER = "BUFFER_FOLDER";
}
