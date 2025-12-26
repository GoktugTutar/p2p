package com.p2pstream.net.udp;

import java.net.*;

public final class UdpSender {
    public void send(byte[] payload, InetAddress targetIp, int targetPort) throws Exception {
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            DatagramPacket p = new DatagramPacket(payload, payload.length, targetIp, targetPort);
            s.send(p);
        }
    }
}
