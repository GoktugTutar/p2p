package com.p2pstream.model;

public class Constants {
    public static final int ttl = 2;
    // UDP & TCP
    public static final int UDP_PORT = 50000;
    public static final int TCP_PORT = 50001;
    public static final int MAX_UDP_PACKET_BYTES = 65000;

    // Klasörler
    public static final String SHARED_FOLDER = "/app/shared_videos";
    public static final String BUFFER_FOLDER = "/app/buffer";

    // --- PDF GEREKSİNİMİ: 256 KB CHUNK SIZE ---
    public static final int CHUNK_SIZE = 256 * 1024; // 256 KB

    // Başlatma tamponu: Kaç chunk dolunca oynatma başlasın?
    public static final int BUFFER_READY_CHUNKS = 8;
}