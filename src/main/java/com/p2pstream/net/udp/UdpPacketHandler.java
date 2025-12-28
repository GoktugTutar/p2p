package com.p2pstream.net.udp;

import java.net.DatagramPacket;
import java.net.InetAddress;

public interface UdpPacketHandler {
    void handlePacket(DatagramPacket packet);

    // Tüm metodlar standart imzaya sahip olmalı:
    void handleDiscover(Packet packet, InetAddress sender, int senderPort);
    void handleDiscoverReply(Packet packet, InetAddress sender, int senderPort);

    void handleSearch(Packet packet, InetAddress sender, int senderPort);
    void handleSearchReply(Packet packet, InetAddress sender, int senderPort);
}