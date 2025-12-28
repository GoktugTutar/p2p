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
            // PacketCodec.decode metodunu senin dosyana uygun (byte[], int) çağırdık
            Packet packet = PacketCodec.decode(datagramPacket.getData(), datagramPacket.getLength());

            if (packet.myIp.equals(this.myIp)) return;

            switch (packet.messageType) {
                case SEARCH:
                    handleSearch(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
                case SEARCH_REPLY:
                    handleSearchReply(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
                case DISCOVER:
                    handleDiscover(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
                case DISCOVER_REPLY:
                    handleDiscoverReply(packet, datagramPacket.getAddress(), datagramPacket.getPort());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleDiscover(Packet packet, InetAddress sender, int senderPort) {
        try {
            if (!seenMessages.add(packet.messageId)) return;
            System.out.println("👋 Keşif isteği alındı. Hedef: " + packet.myIp);

            List<VideoMetadata> allFiles = fileService.searchFiles(""); // Hepsini getir

            if (!allFiles.isEmpty()) {
                for (VideoMetadata meta : allFiles) {
                    // IP:PORT:HASH:FILENAME:SIZE
                    String payload = this.myIp + ":" + this.myPort + ":" + meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();

                    Packet listPacket = Packet.simpleText(MessageType.DISCOVER_REPLY, this.myIp, this.myPort, 0, payload);
                    udpSender.send(PacketCodec.encode(listPacket), sender, packet.myPort);
                }
            }

            if (packet.ttl > 0) {
                Packet forward = new Packet(packet.messageId, packet.messageType, packet.myIp, packet.myPort, packet.ttl - 1, packet.data);
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(forward), Constants.UDP_PORT);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void handleDiscoverReply(Packet packet, InetAddress sender, int senderPort) {
        String payload = new String(packet.data, StandardCharsets.UTF_8);
        try {
            String[] parts = payload.split(":");
            if (parts.length >= 5) {
                String sourceIp = parts[0]; // Payload'dan IP alıyoruz
                String hash = parts[2];
                String fileName = parts[3];
                long size = Long.parseLong(parts[4]);
                HeadlessPeer.broadcastToWeb("DISCOVER_RESULT", fileName, size, hash, sourceIp);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void handleSearch(Packet packet, InetAddress sender, int senderPort) {
        try {
            if (!seenMessages.add(packet.messageId)) return;
            String query = new String(packet.data, StandardCharsets.UTF_8);
            List<VideoMetadata> results = fileService.searchFiles(query);

            for (VideoMetadata meta : results) {
                String responsePayload = this.myIp + ":" + this.myPort + ":" + meta.getFileHash() + ":" + meta.getFileName() + ":" + meta.getFileSize();
                Packet reply = Packet.simpleText(MessageType.SEARCH_REPLY, this.myIp, this.myPort, 0, responsePayload);
                udpSender.send(PacketCodec.encode(reply), sender, packet.myPort);
            }

            if (packet.ttl > 0) {
                Packet forward = new Packet(packet.messageId, packet.messageType, packet.myIp, packet.myPort, packet.ttl - 1, packet.data);
                udpSender.sendToAllLocalSubnets(PacketCodec.encode(forward), Constants.UDP_PORT);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // İMZA DÜZELTİLDİ: Override hatasını çözmek için sender ve port eklendi
    @Override
    public void handleSearchReply(Packet packet, InetAddress sender, int senderPort) {
        String payload = new String(packet.data, StandardCharsets.UTF_8);
        try {
            String[] parts = payload.split(":");
            if (parts.length >= 5) {
                String sourceIp = parts[0]; // Payload'dan IP alıyoruz
                String hash = parts[2];
                String fileName = parts[3];
                long size = Long.parseLong(parts[4]);
                HeadlessPeer.broadcastToWeb("SEARCH_RESULT", fileName, size, hash, sourceIp);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}