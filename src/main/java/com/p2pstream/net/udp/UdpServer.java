package com.p2pstream.net.udp;

import com.p2pstream.model.MessageType;
import com.p2pstream.service.PacketCodec;

import java.io.IOException;
import java.net.*;

public class UdpServer implements Runnable {
    private final int port;
    private DatagramSocket socket;
    private volatile boolean running = false;
    private final UdpPacketHandler handler;

    public UdpServer(int port, UdpPacketHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    public void start() throws SocketException {
        if (running) return;
        socket = new DatagramSocket(port);
        running = true;
        new Thread(this).start();
        System.out.println("UDP Server started on port " + port);
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        System.out.println("UDP Server stopped");
    }

    @Override
    public void run() {
        byte[] buffer = new byte[65535];
        while (running) {
            try {
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(datagramPacket);

                // Parse packet
                Packet packet = PacketCodec.decode(datagramPacket.getData(), datagramPacket.getLength());
                InetAddress senderAddress = datagramPacket.getAddress();
                int senderPort = datagramPacket.getPort();

                // Route based on MessageType
                routePacket(packet, senderAddress, senderPort);

            } catch (SocketException e) {
                if (running) {
                    System.err.println("Socket error: " + e.getMessage());
                }
            } catch (IOException e) {
                System.err.println("Error receiving/parsing packet: " + e.getMessage());
            }
        }
    }

    private void routePacket(Packet packet, InetAddress sender, int senderPort) {
        switch (packet.messageType) {
            // --- EKLENEN KISIM BAŞLANGIÇ ---
            case HELLO:
                handler.handleHello(packet, sender, senderPort);
                break;
            // --- EKLENEN KISIM BİTİŞ ---

            case DISCOVER:
                handler.handleDiscover(packet, sender, senderPort);
                break;
            case DISCOVER_REPLY:
                handler.handleDiscoverReply(packet, sender, senderPort);
                break;
            case SEARCH:
                handler.handleSearch(packet, sender, senderPort);
                break;
            case SEARCH_REPLY:
                handler.handleSearchReply(packet, sender, senderPort);
                break;
            default:
                System.err.println("Unknown message type: " + packet.messageType);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}