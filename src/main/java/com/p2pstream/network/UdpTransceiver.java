package com.p2pstream.network;

import com.p2pstream.common.AppLogger;
import com.p2pstream.common.Constants;
import com.p2pstream.domain.Packet;

import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UdpTransceiver extends Thread {

    private final DatagramSocket socket;
    private final PacketDispatcher dispatcher;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public UdpTransceiver(PacketDispatcher dispatcher) throws SocketException {
        super("UdpTransceiver");
        this.dispatcher = dispatcher;
        this.socket = new DatagramSocket(Constants.UDP_PORT);
        this.socket.setReuseAddress(true);
    }

    public void shutdown() {
        running.set(false);
        socket.close();
    }

    public void send(Packet packet, InetAddress targetIp, int targetPort) {
        try {
            byte[] data = packet.toBytes();
            DatagramPacket dp = new DatagramPacket(data, data.length, targetIp, targetPort);
            socket.send(dp);
        } catch (Exception e) {
            AppLogger.error("UDP send failed", e);
        }
    }

    public void broadcast(Packet packet) {
        try {
            socket.setBroadcast(true);
            byte[] data = packet.toBytes();
            DatagramPacket dp = new DatagramPacket(
                    data, data.length,
                    InetAddress.getByName("255.255.255.255"),
                    Constants.UDP_PORT
            );
            socket.send(dp);
        } catch (Exception e) {
            AppLogger.error("UDP broadcast failed", e);
        }
    }

    @Override
    public void run() {
        byte[] buf = new byte[64 * 1024];
        while (running.get()) {
            try {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);

                byte[] data = new byte[dp.getLength()];
                System.arraycopy(dp.getData(), dp.getOffset(), data, 0, dp.getLength());

                Packet pkt = Packet.fromBytes(data);
                dispatcher.onPacket(pkt, dp.getAddress(), dp.getPort());
            } catch (SocketException se) {
                // close() triggers this, ignore if stopping
                if (running.get()) AppLogger.error("UDP socket error", se);
            } catch (Exception e) {
                AppLogger.error("UDP receive/dispatch failed", e);
            }
        }
    }
}
