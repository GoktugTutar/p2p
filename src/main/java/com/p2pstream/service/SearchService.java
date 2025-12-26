package com.p2pstream.service;

import com.p2pstream.common.ConfigLoader;
import com.p2pstream.common.Constants;
import com.p2pstream.domain.Packet;
import com.p2pstream.domain.VideoMetadata;
import com.p2pstream.network.PacketDispatcher;
import com.p2pstream.network.UdpTransceiver;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SearchService implements PacketDispatcher {

    public record SearchResult(VideoMetadata meta, InetAddress sourceIp) {}

    private final EventBus bus;
    private final UdpTransceiver udp;

    private final Set<String> seenSearch = ConcurrentHashMap.newKeySet();
    private final Set<String> seenFound = ConcurrentHashMap.newKeySet();
    private volatile int seq = 1000;

    public SearchService(EventBus bus, UdpTransceiver udp) {
        this.bus = bus;
        this.udp = udp;
    }

    public void search(String query) {
        int s = seq++;
        byte[] payload = Packet.utf8(query);
        Packet pkt = new Packet(Packet.OpCode.SEARCH, s, Constants.DEFAULT_TTL, payload);
        udp.broadcast(pkt);
        bus.emit(EventBus.EventType.LOG, "SEARCH flood: \"" + query + "\" seq=" + s);
    }

    @Override
    public void onPacket(Packet packet, InetAddress fromIp, int fromPort) {
        switch (packet.getOpCode()) {
            case SEARCH -> handleSearch(packet, fromIp);
            case FOUND -> handleFound(packet, fromIp);
            default -> {}
        }
    }

    private void handleSearch(Packet packet, InetAddress fromIp) {
        String q = Packet.utf8(packet.getPayload()).trim().toLowerCase(Locale.ROOT);
        String key = fromIp.getHostAddress() + ":" + packet.getSeqNo();
        if (!seenSearch.add(key)) return;

        // forward
        if (packet.getTtl() > 0) udp.broadcast(packet.withTtl(packet.getTtl() - 1));

        // local match
        List<Path> matches = findMatchingFiles(q);
        for (Path p : matches) {
            VideoMetadata meta = buildMetadata(p);
            if (meta == null) continue;

            Packet found = new Packet(Packet.OpCode.FOUND, packet.getSeqNo(), 0, serializeFound(meta));
            // simplest: broadcast FOUND (PDF GUI expects discovery; later optimize with unicast)
            udp.broadcast(found);
            bus.emit(EventBus.EventType.LOG, "FOUND broadcast for: " + p.getFileName());
        }
    }

    private void handleFound(Packet packet, InetAddress fromIp) {
        String key = fromIp.getHostAddress() + ":" + packet.getSeqNo() + ":" + Arrays.hashCode(packet.getPayload());
        if (!seenFound.add(key)) return;

        VideoMetadata meta = deserializeFound(packet.getPayload());
        if (meta == null) return;

        bus.emit(EventBus.EventType.SEARCH_RESULT, new SearchResult(meta, fromIp));
    }

    private List<Path> findMatchingFiles(String q) {
        Path root = ConfigLoader.getInstance().getRootFolder();
        if (root == null || !Files.isDirectory(root)) return List.of();

        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            for (Path p : ds) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (q.isEmpty() || name.contains(q)) out.add(p);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private VideoMetadata buildMetadata(Path file) {
        try {
            long size = Files.size(file);
            int chunks = (int) ((size + Constants.CHUNK_SIZE - 1) / Constants.CHUNK_SIZE);
            byte[] hash = sha256(file);
            return new VideoMetadata(hash, file.getFileName().toString(), size, chunks);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(p)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
        }
        return md.digest();
    }

    // FOUND payload format:
    // hash(32) fileSize(8) totalChunks(4) nameLen(4) name(utf8)
    private static byte[] serializeFound(VideoMetadata m) {
        byte[] name = m.getFileName().getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(32 + 8 + 4 + 4 + name.length);
        b.put(m.getFileHashSha256());
        b.putLong(m.getFileSize());
        b.putInt(m.getTotalChunks());
        b.putInt(name.length);
        b.put(name);
        return b.array();
    }

    private static VideoMetadata deserializeFound(byte[] payload) {
        try {
            if (payload == null || payload.length < 32 + 8 + 4 + 4) return null;
            ByteBuffer b = ByteBuffer.wrap(payload);
            byte[] hash = new byte[32];
            b.get(hash);
            long size = b.getLong();
            int chunks = b.getInt();
            int nameLen = b.getInt();
            if (nameLen < 0 || nameLen > (payload.length - b.position())) return null;
            byte[] name = new byte[nameLen];
            b.get(name);
            return new VideoMetadata(hash, new String(name, StandardCharsets.UTF_8), size, chunks);
        } catch (Exception e) {
            return null;
        }
    }
}
