package com.p2pstream.net.udp;

import com.p2pstream.HeadlessPeer;
import com.p2pstream.model.Constants;
import com.p2pstream.model.MessageType;
import com.p2pstream.model.VideoMetadata;
import com.p2pstream.service.FileService;
import com.p2pstream.service.PacketCodec;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MyUdpHandler implements UdpPacketHandler {

    private final UdpSender udpSender;
    private final FileService fileService;
    private final Set<UUID> seenMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private final String myPeerId;
    private final String myIp;
    private final int myPort;

    public MyUdpHandler(UdpSender udpSender, FileService fileService, String myPeerId, String myIp, int myPort) {
        this.udpSender = udpSender;
        this.fileService = fileService;
        this.myPeerId = myPeerId;
        this.myIp = myIp;
        this.myPort = myPort;
    }

    @Override
    public void handlePacket(DatagramPacket datagramPacket) {
        try {
            Packet packet = PacketCodec.decode(datagramPacket.getData(), datagramPacket.getLength());
            if (packet.myIp.equals(this.myIp)) return;
            knownPeers.add(packet.myIp);

            switch (packet.messageType) {
                case HELLO: handleHello(packet, datagramPacket.getAddress(), datagramPacket.getPort()); break;
                case DISCOVER: handleDiscover(packet, datagramPacket.getAddress(), datagramPacket.getPort()); break;
                case DISCOVER_REPLY: handleDiscoverReply(packet, datagramPacket.getAddress(), datagramPacket.getPort()); break;
                case SEARCH: handleSearch(packet, datagramPacket.getAddress(), datagramPacket.getPort()); break;
                case SEARCH_REPLY: handleSearchReply(packet, datagramPacket.getAddress(), datagramPacket.getPort()); break;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- 1. HELLO (Doğrudan Komşu) ---
    @Override
    public void handleHello(Packet packet, InetAddress sender, int senderPort) {
        System.out.println("👋 HELLO alındı: " + packet.myIp);
        HeadlessPeer.broadcastLog("👋 Yeni Peer Bağlandı (Local): " + packet.myIp);
        knownPeers.add(packet.myIp);

        // A. Dedikodu (Gossip) Başlat: Diğer Subnettekilere haber ver
        int forwardTtl = packet.ttl - 1;
        if (forwardTtl > 0) {
            Packet gossip = Packet.simpleText(MessageType.DISCOVER, this.myIp, this.myPort, forwardTtl, packet.myIp);
            floodToNetwork(gossip);
        }

        // B. Cevap Ver: Kendi dosyalarımı gönder
        sendMyFileList(packet.myIp, MessageType.DISCOVER_REPLY);
    }

    // --- 2. DISCOVER (Uzaktan Gelen Duyuru) ---
    @Override
    public void handleDiscover(Packet packet, InetAddress sender, int senderPort) {
        if (!seenMessages.add(packet.messageId)) return;

        // Payload içinde yeni gelen Peer'ın asıl IP'si yazar (Örn: 172.30.0.10)
        String newPeerIp = new String(packet.data, StandardCharsets.UTF_8);
        knownPeers.add(newPeerIp);

        System.out.println("🌍 DISCOVER alındı. Yeni Peer: " + newPeerIp);

        // A. Yaymaya devam et (Forwarding)
        forwardPacket(packet);

        // B. EKSİK OLAN KISIM BURASIYDI: BEN DE ONA CEVAP VERMELİYİM
        // Node 10 burada devreye girip "Hoşgeldin, ben de buradayım" diyecek.
        sendMyFileList(newPeerIp, MessageType.DISCOVER_REPLY);
    }

    @Override
    public void handleDiscoverReply(Packet packet, InetAddress sender, int senderPort) {
        processReply(packet, "DISCOVER_RESULT");
    }

    // --- 3. SEARCH (Arama) ---
    @Override
    public void handleSearch(Packet packet, InetAddress sender, int senderPort) {
        if (!seenMessages.add(packet.messageId)) return;
        String query = new String(packet.data, StandardCharsets.UTF_8);

        System.out.println("🔍 Dosya Aranıyor: '" + query + "'");
        HeadlessPeer.broadcastLog("🔍 Dosya isteği: '" + query + "' <- " + packet.myIp);

        // A. Dosya Bende Var mı?
        List<VideoMetadata> results = fileService.searchFiles(query);
        for (VideoMetadata meta : results) {
            System.out.println("✅ Dosya bende var! Cevap dönülüyor: " + meta.getFileName());
            String payload = this.myIp + ":" + this.myPort + ":" + meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();

            // Cevabı Flood ile gönder
            Packet replyFlood = Packet.simpleText(MessageType.SEARCH_REPLY, this.myIp, this.myPort, 2, payload);
            seenMessages.add(replyFlood.messageId);
            floodToNetwork(replyFlood);
        }

        // B. Başkasına Sor (Forward)
        forwardPacket(packet);
    }

    @Override
    public void handleSearchReply(Packet packet, InetAddress sender, int senderPort) {
        processReply(packet, "SEARCH_RESULT");
    }

    // --- YARDIMCI METODLAR ---

    private void processReply(Packet packet, String webEventType) {
        if (!seenMessages.add(packet.messageId)) return;

        try {
            String payload = new String(packet.data, StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            if (parts.length >= 5) {
                String sourceIp = parts[0];
                String hash = parts[2];
                String fileName = parts[3];
                long size = Long.parseLong(parts[4]);

                System.out.println("✅ SONUÇ ALINDI: " + fileName + " [" + sourceIp + "]");
                HeadlessPeer.broadcastLog("✅ Sonuç: " + fileName + " (" + sourceIp + ")");
                HeadlessPeer.broadcastToWeb(webEventType, fileName, size, hash, sourceIp);
            }
        } catch (Exception e) { e.printStackTrace(); }

        // Cevabı zincirleme yay (Forwarding)
        forwardPacket(packet);
    }

    private void forwardPacket(Packet packet) {
        int forwardTtl = packet.ttl - 1;
        if (forwardTtl > 0) {
            Packet forward = new Packet(packet.messageId, packet.messageType, packet.myIp, packet.myPort, forwardTtl, packet.data);
            floodToNetwork(forward);
        }
    }

    private void floodToNetwork(Packet packet) {
        try {
            byte[] data = PacketCodec.encode(packet);

            // 1. Unicast (Bildiğim herkese)
            for (String targetIp : knownPeers) {
                if (!targetIp.equals(this.myIp) && !targetIp.equals(packet.myIp)) {
                    udpSender.send(data, InetAddress.getByName(targetIp), Constants.UDP_PORT);
                }
            }
            // 2. Broadcast (Kendi mahallene)
            udpSender.sendToAllLocalSubnets(data, Constants.UDP_PORT);

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendMyFileList(String targetIpForLog, MessageType type) {
        try {
            List<VideoMetadata> myFiles = fileService.searchFiles("");
            for (VideoMetadata meta : myFiles) {
                String payload = this.myIp + ":" + this.myPort + ":" + meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();

                Packet p = Packet.simpleText(type, this.myIp, this.myPort, 2, payload);
                floodToNetwork(p);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}