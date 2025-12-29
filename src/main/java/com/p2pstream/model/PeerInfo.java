package com.p2pstream.model;

import java.time.Instant;
import java.util.Objects;
//bu dosya kullanılmıyor çünkü
//!! SADECE IP GEREKTIĞI ICIN KULLANMADIM MYUDPHANDLER DA
public final class PeerInfo {
    private final String peerId;
    private final String ip;
    private final int udpPort;
    private volatile Instant lastSeen;

    public PeerInfo(String peerId, String ip, int udpPort) {
        this.peerId = peerId;
        this.ip = ip;
        this.udpPort = udpPort;
        this.lastSeen = Instant.now();
    }

    public String getPeerId() { return peerId; }
    public String getIp() { return ip; }
    public int getUdpPort() { return udpPort; }
    public Instant getLastSeen() { return lastSeen; }
    public void touch() { this.lastSeen = Instant.now(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerInfo that)) return false;
        return udpPort == that.udpPort && Objects.equals(ip, that.ip);
    }

    @Override public int hashCode() {
        return Objects.hash(ip, udpPort);
    }

    @Override public String toString() {
        return peerId + "@" + ip + ":" + udpPort;
    }
}
