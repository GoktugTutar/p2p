package com.p2pstream.net.udp;

import com.p2pstream.net.Packet;

import java.net.InetSocketAddress;

@FunctionalInterface
public interface UdpHandler {
    void handle(Packet packet, InetSocketAddress remote);
}
