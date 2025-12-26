package com.p2pstream.service;

import com.p2pstream.common.AppLogger;
import com.p2pstream.common.Constants;
import com.p2pstream.domain.Packet;
import com.p2pstream.domain.PeerInfo;
import com.p2pstream.network.PacketDispatcher;
import com.p2pstream.network.UdpTransceiver;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PeerDiscoveryService implements PacketDispatcher {

    private final UUID selfId = UUID.randomUUID();
    private final EventBus bus;
    private final UdpTransceiver udp;

    private final Map<UUID, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Set<String> seenHello = ConcurrentHashMap.newKeySet(); // de-dup by (seq,peerId)

    private volatile int seq = 1;

    public PeerDiscoveryService(EventBus bus, UdpTransceiver udp) {
        this.bus = bus;
        this.udp = udp;
    }

    public UUID getSelfId() { return selfId; }

    public Collection<PeerInfo> getPeers() { return peers.values(); }

    public void broadcastHello() {
        int s = seq++;
        Packet pkt = new Packet(Packet.OpCode.HELLO, s, Constants.DEFAULT_TTL, helloPayload(selfId, Constants.UDP_PORT));
        udp.broadcast(pkt);
        bus.emit(EventBus.EventType.LOG, "HELLO broadcast seq=" + s);
    }

    @Override
    public void onPacket(Packet packet, InetAddress fromIp, int fromPort) {
        if (packet.getOpCode() != Packet.OpCode.HELLO) return;

        UUID peerId = readHelloPeerId(packet.getPayload());
        if (peerId == null || peerId.equals(selfId)) return;

        String key = peerId + ":" + packet.getSeqNo();
        if (!seenHello.add(key)) return;

        PeerInfo pi = new PeerInfo(peerId, fromIp, Constants.UDP_PORT);
        peers.put(peerId, pi);
        bus.emit(EventBus.EventType.PEER_FOUND, pi);

        int ttl = packet.getTtl();
        if (ttl > 0) {
            // limited scope flooding: forward with ttl-1
            udp.broadcast(packet.withTtl(ttl - 1));
        }
    }

    private static byte[] helloPayload(UUID id, int udpPort) {
        ByteBuffer b = ByteBuffer.allocate(16 + 4);
        b.putLong(id.getMostSignificantBits());
        b.putLong(id.getLeastSignificantBits());
        b.putInt(udpPort);
        return b.array();
    }

    private static UUID readHelloPeerId(byte[] payload) {
        try {
            if (payload == null || payload.length < 16) return null;
            ByteBuffer b = ByteBuffer.wrap(payload);
            long msb = b.getLong();
            long lsb = b.getLong();
            return new UUID(msb, lsb);
        } catch (Exception e) {
            AppLogger.error("Bad HELLO payload", e);
            return null;
        }
    }
}
