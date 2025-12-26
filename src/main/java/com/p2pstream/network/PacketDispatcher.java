package com.p2pstream.network;

import com.p2pstream.domain.Packet;

import java.net.InetAddress;

public interface PacketDispatcher {
    void onPacket(Packet packet, InetAddress fromIp, int fromPort);
}
