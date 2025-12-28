package com.p2pstream.model;

public class Constants {
    // UDP & TCP
    public static final int UDP_PORT = 50000;
    public static final int TCP_PORT = 50001;
    public static final int MAX_UDP_PACKET_BYTES = 65000;

    // Klasörler
    public static final String SHARED_FOLDER = "/app/shared_videos";
    public static final String BUFFER_FOLDER = "/app/buffer";

    // --- PDF GEREKSİNİMİ: 256 KB CHUNK SIZE ---
    public static final int CHUNK_SIZE = 256 * 1024; // 256 KB
}