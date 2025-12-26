package com.p2pstream.net.udp;

import com.p2pstream.model.VideoMetadata;
import com.p2pstream.model.Constants;
import com.p2pstream.model.MessageType;
import com.p2pstream.model.PeerInfo;
import com.p2pstream.service.FileService;
import com.p2pstream.service.PacketCodec;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MyUdpHandler implements UdpPacketHandler {

    private final UdpSender udpSender;
    private final FileService fileService;
    private final Map<String, PeerInfo> knownPeers = new ConcurrentHashMap<>();
    private final Set<UUID> seenMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());
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
    public void handleDiscover(Packet packet, InetAddress sender, int senderPort) {
        // (Burası aynı kalıyor, sadece SEARCH kısmındaki mantık DISCOVER için de geçerli olabilir ama şimdilik SEARCH'ü düzeltelim)
        try {
            if (!seenMessages.add(packet.messageId)) return;
            String incomingPeerId = new String(packet.data);
            if (incomingPeerId.equals(this.myPeerId)) return;

            System.out.println("Keşif isteği alındı: " + sender.getHostAddress());
            knownPeers.put(incomingPeerId, new PeerInfo(incomingPeerId, sender.getHostAddress(), senderPort));

            Packet replyPacket = Packet.simpleText(MessageType.DISCOVER_REPLY, this.myIp, this.myPort, 0, this.myPeerId);

            // DİKKAT: Discover reply için de packet.myPort kullanmak daha garantidir
            udpSender.send(PacketCodec.encode(replyPacket), sender, packet.myPort);

            if (packet.ttl > 0) {
                Packet forward = new Packet(packet.messageId, packet.messageType, packet.myIp, packet.myPort, packet.ttl - 1, packet.data);
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(forward), Constants.UDP_PORT);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void handleDiscoverReply(Packet packet, InetAddress sender, int senderPort) {
        String incomingPeerId = new String(packet.data);
        if (!incomingPeerId.equals(this.myPeerId)) {
            System.out.println("Keşif cevabı (REPLY) geldi: " + sender.getHostAddress());
            knownPeers.put(incomingPeerId, new PeerInfo(incomingPeerId, sender.getHostAddress(), senderPort));
        }
    }

    // --- KRİTİK DÜZELTME BURADA ---
    @Override
    public void handleSearch(Packet packet, InetAddress sender, int senderPort) {
        try {
            if (!seenMessages.add(packet.messageId)) return;

            String query = new String(packet.data, StandardCharsets.UTF_8);
            System.out.println("Arama isteği alındı: '" + query + "' Kimden: " + sender.getHostAddress());

            List<VideoMetadata> results = fileService.searchFiles(query);

            if (!results.isEmpty()) {
                System.out.println("Eşleşen dosya bulundu! Adet: " + results.size());

                for (VideoMetadata meta : results) {
                    String responsePayload = meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();

                    Packet reply = Packet.simpleText(
                            MessageType.SEARCH_REPLY,
                            this.myIp,
                            this.myPort,
                            0,
                            responsePayload
                    );

                    // ESKİSİ (HATA): udpSender.send(..., sender, senderPort);
                    // YENİSİ (DOĞRU): Cevabı, paketin içinde yazan ASIL dinleme portuna (50000) atıyoruz.
                    udpSender.send(PacketCodec.encode(reply), sender, packet.myPort);
                }
            }

            if (packet.ttl > 0) {
                Packet forwardPacket = new Packet(
                        packet.messageId, packet.messageType, packet.myIp, packet.myPort, packet.ttl - 1, packet.data
                );
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(forwardPacket), Constants.UDP_PORT);
                System.out.println("Arama paketi yayılıyor (Flooding), TTL: " + (packet.ttl - 1));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleSearchReply(Packet packet, InetAddress sender, int senderPort) {
        // İŞTE BU LOG ARTIK GÖRÜNECEK
        String payload = new String(packet.data, StandardCharsets.UTF_8);
        System.out.println(">>> ARAMA SONUCU GELDİ (BULUNDU!) <<<");
        System.out.println("Kaynak Peer IP: " + sender.getHostAddress());
        System.out.println("Dosya Detayı: " + payload);
    }
}