package com.p2pstream.model;

public final class Constants {
    private Constants() {}

    // Discovery UDP port (tüm peer’lerde aynı dinlenecek)
    public static final int UDP_PORT = 50000;

    // UDP packet limit / ttl gibi şeyleri sonra buraya ekleyeceğiz
    public static final int MAX_UDP_PACKET_BYTES = 64 * 1024; // güvenli tavan
}
