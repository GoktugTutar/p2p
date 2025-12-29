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
    // Bildiğimiz peer'ların IP adreslerini tutan liste (Gossip için rehber)
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
                case HELLO:
                    handleHello(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
                case DISCOVER:
                    handleDiscover(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
                case DISCOVER_REPLY:
                    handleDiscoverReply(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
                case SEARCH:
                    handleSearch(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
                case SEARCH_REPLY:
                    handleSearchReply(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleHello(Packet packet, InetAddress sender, int senderPort) {
        System.out.println("👋 HELLO alındı: " + packet.myIp);
        HeadlessPeer.broadcastLog("👋 Yeni Peer Bağlandı: " + packet.myIp);
        knownPeers.add(packet.myIp);

        // --- GOSSIP YAYILIMI ---
        int forwardTtl = packet.ttl - 1;
        if (forwardTtl > 0) {
            String newPeerIp = packet.myIp;
            Packet gossipPacket = Packet.simpleText(
                    MessageType.DISCOVER,
                    this.myIp,
                    this.myPort,
                    forwardTtl,
                    newPeerIp
            );

            // 1. Unicast Yayılım
            floodToKnownPeers(gossipPacket);

            // 2. Broadcast Yayılım (Köprüler için) - DÜZELTİLDİ: TRY-CATCH EKLENDİ
            try {
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(gossipPacket), Constants.UDP_PORT);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // --- HOŞGELDİN CEVABI (DOSYA LİSTESİ) ---
        try {
            List<VideoMetadata> myFiles = fileService.searchFiles("");
            if (!myFiles.isEmpty()) {
                for (VideoMetadata meta : myFiles) {
                    String payload = this.myIp + ":" + this.myPort + ":" + meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();
                    Packet fileListPacket = Packet.simpleText(MessageType.DISCOVER_REPLY, this.myIp, this.myPort, 5, payload); // TTL 5 yapıldı

                    // Cevabı flood ile yayalım ki Web-Peer duysun
                    floodToKnownPeers(fileListPacket);
                    udpSender.sendToAllLocalSubnets(PacketCodec.encode(fileListPacket), Constants.UDP_PORT);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void handleDiscover(Packet packet, InetAddress sender, int senderPort) {
        try {
            if (!seenMessages.add(packet.messageId)) return;
            String discoveredPeerIp = new String(packet.data, StandardCharsets.UTF_8);
            System.out.println("🌍 AĞDAN BİLGİ GELDİ (Gossip): " + discoveredPeerIp + " online.");

            if(!discoveredPeerIp.equals(this.myIp)) {
                knownPeers.add(discoveredPeerIp);
            }

            int forwardTtl = packet.ttl - 1;
            if (forwardTtl > 0) {
                Packet forward = new Packet(packet.messageId, packet.messageType, packet.myIp, packet.myPort, forwardTtl, packet.data);
                floodToKnownPeers(forward);
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(forward), Constants.UDP_PORT);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void floodToKnownPeers(Packet packet) {
        try {
            byte[] data = PacketCodec.encode(packet);
            for (String targetIp : knownPeers) {
                if (!targetIp.equals(this.myIp) && !targetIp.equals(packet.myIp)) {
                    udpSender.send(data, InetAddress.getByName(targetIp), Constants.UDP_PORT);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleSearch(Packet packet, InetAddress sender, int senderPort) {
        try {
            if (!seenMessages.add(packet.messageId)) return;
            String query = new String(packet.data, StandardCharsets.UTF_8);

            System.out.println("🔍 Dosya Aranıyor: '" + query + "' (İsteyen: " + packet.myIp + ")");
            HeadlessPeer.broadcastLog("🔍 Dosya isteği geldi: '" + query + "' -> İsteyen: " + packet.myIp);

            List<VideoMetadata> results = fileService.searchFiles(query);
            for (VideoMetadata meta : results) {
                System.out.println("✅ Dosya bende var! Cevap dönülüyor: " + meta.getFileName());
                String responsePayload = this.myIp + ":" + this.myPort + ":" + meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();

                // Cevabı Flood ile yay (sadece bilinen peerlar bilsin o yüzden ttl = 1,network yorulmasın)
                Packet replyFlood = Packet.simpleText(
                        MessageType.SEARCH_REPLY,
                        this.myIp,
                        this.myPort,
                        1,
                        responsePayload
                );

                seenMessages.add(replyFlood.messageId); // Kendi cevabımı görüp tekrar işlemeyeyim

                floodToKnownPeers(replyFlood);
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(replyFlood), Constants.UDP_PORT);
            }

            int forwardTtl = packet.ttl - 1;
            if (forwardTtl > 0) {
                Packet forward = new Packet(packet.messageId, packet.messageType, packet.myIp, packet.myPort, forwardTtl, packet.data);
                floodToKnownPeers(forward);
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(forward), Constants.UDP_PORT);
            } else {
                System.out.println("🛑 TTL Bitti. Arama paketi burada durdu.");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void handleDiscoverReply(Packet packet, InetAddress sender, int senderPort) {
        handleSearchReply(packet, sender, senderPort);
    }

    @Override
    public void handleSearchReply(Packet packet, InetAddress sender, int senderPort) {
        if (!seenMessages.add(packet.messageId)) return;

        String payload = new String(packet.data, StandardCharsets.UTF_8);
        try {
            String[] parts = payload.split(":");
            if (parts.length >= 5) {
                String sourceIp = parts[0];
                String hash = parts[2];
                String fileName = parts[3];
                long size = Long.parseLong(parts[4]);

                String logMsg = "✅ SONUÇ ALINDI: " + fileName + " -> Kaynak: " + sourceIp;
                System.out.println(logMsg);
                HeadlessPeer.broadcastLog(logMsg);
                HeadlessPeer.broadcastToWeb(
                        packet.messageType == MessageType.DISCOVER_REPLY ? "DISCOVER_RESULT" : "SEARCH_RESULT",
                        fileName, size, hash, sourceIp
                );
            }

            // Cevap paketini de yay (Forwarding)
            int forwardTtl = packet.ttl - 1;
            if (forwardTtl > 0) {
                Packet forward = new Packet(
                        packet.messageId,
                        packet.messageType,
                        packet.myIp,
                        packet.myPort,
                        forwardTtl,
                        packet.data
                );
                floodToKnownPeers(forward);
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(forward), Constants.UDP_PORT);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }
}