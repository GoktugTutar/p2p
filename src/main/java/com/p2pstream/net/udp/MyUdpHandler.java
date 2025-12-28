package com.p2pstream.net.udp;

import com.p2pstream.HeadlessPeer;
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
        try {
            if (!seenMessages.add(packet.messageId)) return;
            String incomingPeerId = new String(packet.data);
            if (incomingPeerId.equals(this.myPeerId)) return;

            System.out.println("Keşif isteği alındı: " + sender.getHostAddress());
            knownPeers.put(incomingPeerId, new PeerInfo(incomingPeerId, sender.getHostAddress(), senderPort));

            Packet replyPacket = Packet.simpleText(MessageType.DISCOVER_REPLY, this.myIp, this.myPort, 0, this.myPeerId);

            // DÜZELTME: Cevabı, isteği yapanın bildirdiği dinleme portuna (packet.myPort) atıyoruz.
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
            // Burada da packet.myIp kullanmak daha güvenlidir ama sender şimdilik kalsın.
            knownPeers.put(incomingPeerId, new PeerInfo(incomingPeerId, sender.getHostAddress(), senderPort));
        }
    }

    @Override
    public void handleSearch(Packet packet, InetAddress sender, int senderPort) {
        try {
            if (!seenMessages.add(packet.messageId)) return;

            String query = new String(packet.data, StandardCharsets.UTF_8);
            System.out.println("Arama isteği alındı: '" + query + "' Kimden: " + packet.myIp);

            List<VideoMetadata> results = fileService.searchFiles(query);

            if (!results.isEmpty()) {
                System.out.println("Eşleşen dosya bulundu! Adet: " + results.size());

                for (VideoMetadata meta : results) {
                    // Payload: HASH:FILENAME:SIZE
                    String responsePayload = meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();

                    Packet reply = Packet.simpleText(
                            MessageType.SEARCH_REPLY,
                            this.myIp,   // Cevap veren BENİM IP'm (Dosya Sahibi)
                            this.myPort,
                            0,
                            responsePayload
                    );

                    // DÜZELTME: Cevabı direkt isteği yapan IP'ye (sender) ve onun dinlediği porta (packet.myPort) atıyoruz.
                    // packet.myPort genellikle 50000'dir (Server Portu). senderPort ise rastgele bir port olabilir.
                    udpSender.send(PacketCodec.encode(reply), sender, packet.myPort);
                }
            }

            // Flooding (Yayma)
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
        String payload = new String(packet.data, StandardCharsets.UTF_8);

        // KRİTİK DÜZELTME: IP adresi olarak "sender" (paketi getiren) değil,
        // "packet.myIp" (paketin içindeki asıl sahip) kullanılmalı.
        System.out.println(">>> P2P SONUCU GELDİ: " + payload + " Kaynak: " + packet.myIp);

        try {
            String[] parts = payload.split(":");
            if (parts.length >= 3) {
                String hash = parts[0];
                String fileName = parts[1];
                long size = Long.parseLong(parts[2]);

                // BURADAKİ DEĞİŞİKLİK: sender.getHostAddress() -> packet.myIp
                // Bu sayede arayüzde 172.30.0.10 değil, dosya kimdeyse (örn 172.30.0.11) o yazar.
                HeadlessPeer.broadcastToWeb(fileName, size, hash, packet.myIp);
            }
        } catch (Exception e) {
            System.err.println("Web yayını hatası: " + e.getMessage());
        }
    }
}