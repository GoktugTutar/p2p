package com.p2pstream.domain;

import java.net.InetAddress;
import java.util.Objects;
import java.util.UUID;

public final class PeerInfo {
    private final UUID id;
    private final InetAddress ip;
    private final int port;

    public PeerInfo(UUID id, InetAddress ip, int port) {
        this.id = Objects.requireNonNull(id);
        this.ip = Objects.requireNonNull(ip);
        this.port = port;
    }

    public UUID getId() { return id; }
    public InetAddress getIp() { return ip; }
    public int getPort() { return port; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerInfo that)) return false;
        return port == that.port && id.equals(that.id) && ip.equals(that.ip);
    }

    @Override public int hashCode() { return Objects.hash(id, ip, port); }

    @Override public String toString() {
        return "PeerInfo{id=" + id + ", ip=" + ip.getHostAddress() + ", port=" + port + "}";
    }
}
