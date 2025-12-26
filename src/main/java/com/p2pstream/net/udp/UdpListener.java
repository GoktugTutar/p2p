package com.p2pstream.net.udp;

import com.p2pstream.model.Constants;
import com.p2pstream.net.Packet;
import com.p2pstream.service.PacketCodec;

import java.net.*;

public final class UdpListener implements Runnable {
    private final UdpDispatcher dispatcher;
    private volatile boolean running = true;

    public UdpListener(UdpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(Constants.UDP_PORT)) {
            socket.setBroadcast(true);

            byte[] buf = new byte[Constants.MAX_UDP_PACKET_BYTES];

            while (running) {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);

                Packet packet = PacketCodec.decode(dp.getData(), dp.getLength());
                InetSocketAddress remote = new InetSocketAddress(dp.getAddress(), dp.getPort());

                dispatcher.dispatch(packet, remote);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
