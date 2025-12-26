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

// "UdpPacketHandler" interface'ini implements ettiğini varsayıyorum
public class MyUdpHandler implements UdpPacketHandler {

    // 1. Gerekli Servisler
    private final UdpSender udpSender;
    private final FileService fileService; // ARTIK ENJEKTE EDİLDİ

    // 2. Hafıza (Peer'lar ve Mesaj Geçmişi)
    private final Map<String, PeerInfo> knownPeers = new ConcurrentHashMap<>();
    // Loop koruması için görülen mesaj ID'leri
    private final Set<UUID> seenMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // 3. Kendi Kimlik Bilgilerimiz
    private final String myPeerId;
    private final String myIp;
    private final int myPort;

    // CONSTRUCTOR GÜNCELLENDİ: FileService parametresi eklendi
    public MyUdpHandler(UdpSender udpSender, FileService fileService, String myPeerId, String myIp, int myPort) {
        this.udpSender = udpSender;
        this.fileService = fileService; // Dışarıdan gelen hazır servisi alıyoruz
        this.myPeerId = myPeerId;
        this.myIp = myIp;
        this.myPort = myPort;
    }

    // --- DISCOVER MANTIĞI ---

    @Override
    public void handleDiscover(Packet packet, InetAddress sender, int senderPort) {
        try {
            // A. DEDUPLICATION (Loop Koruması)
            // Eğer bu mesajı daha önce gördüysek tekrar işleme
            if (!seenMessages.add(packet.messageId)) {
                return;
            }

            String incomingPeerId = new String(packet.data);

            // Kendi kendimize konuşmayalım
            if (incomingPeerId.equals(this.myPeerId)) return;

            System.out.println("Keşif isteği alındı: " + sender.getHostAddress() + " ID: " + incomingPeerId);

            // Peer kaydet
            PeerInfo info = new PeerInfo(incomingPeerId, sender.getHostAddress(), senderPort);
            knownPeers.put(incomingPeerId, info);

            // Cevap ver (Unicast)
            Packet replyPacket = Packet.simpleText(
                    MessageType.DISCOVER_REPLY,
                    this.myIp,
                    this.myPort,
                    0,
                    this.myPeerId
            );
            udpSender.send(PacketCodec.encode(replyPacket), sender, senderPort);

            // Flooding (Yayma)
            if (packet.ttl > 0) {
                Packet forwardPacket = new Packet(
                        packet.messageId, // ID korunmalı
                        packet.messageType,
                        packet.myIp,
                        packet.myPort,
                        packet.ttl - 1,   // TTL azaltıldı
                        packet.data
                );
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(forwardPacket), Constants.UDP_PORT);
                System.out.println("Keşif paketi yayılıyor (Flooding), Yeni TTL: " + (packet.ttl - 1));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleDiscoverReply(Packet packet, InetAddress sender, int senderPort) {
        String incomingPeerId = new String(packet.data);
        if (incomingPeerId.equals(this.myPeerId)) return;

        System.out.println("Keşif cevabı (REPLY) geldi: " + sender.getHostAddress());
        PeerInfo info = new PeerInfo(incomingPeerId, sender.getHostAddress(), senderPort);
        knownPeers.put(incomingPeerId, info);
    }

    // --- SEARCH MANTIĞI ---

    @Override
    public void handleSearch(Packet packet, InetAddress sender, int senderPort) {
        try {
            // 1. DEDUPLICATION: Mesaj daha önce işlendi mi?
            if (!seenMessages.add(packet.messageId)) {
                return;
            }

            String query = new String(packet.data, StandardCharsets.UTF_8);
            System.out.println("Arama isteği alındı: '" + query + "' Kimden: " + sender.getHostAddress());

            // 2. LOCAL SEARCH: Hazır olan fileService üzerinden arama yap
            // (Artık new FileService() yapmıyoruz)
            List<VideoMetadata> results = fileService.searchFiles(query);

            if (!results.isEmpty()) {
                System.out.println("Eşleşen dosya bulundu! Adet: " + results.size());

                for (VideoMetadata meta : results) {
                    // Cevap formatı: HASH:FILENAME:SIZE
                    String responsePayload = meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();

                    Packet reply = Packet.simpleText(
                            MessageType.SEARCH_REPLY,
                            this.myIp,
                            this.myPort,
                            0, // Reply direkt gider
                            responsePayload
                    );

                    // Bulan peer, Arayan peer'a unicast cevap döner
                    udpSender.send(PacketCodec.encode(reply), sender, senderPort);
                }
            }

            // 3. FLOODING: Paketi yay
            if (packet.ttl > 0) {
                Packet forwardPacket = new Packet(
                        packet.messageId,
                        packet.messageType,
                        packet.myIp,
                        packet.myPort,
                        packet.ttl - 1,
                        packet.data
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
        System.out.println(">>> ARAMA SONUCU GELDİ <<<");
        System.out.println("Kaynak: " + sender.getHostAddress());
        System.out.println("Dosya Bilgisi: " + payload);
    }

    public Map<String, PeerInfo> getPeers() {
        return knownPeers;
    }
}