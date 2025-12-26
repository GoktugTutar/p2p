package com.p2pstream.net.udp;

import java.net.InetSocketAddress;
import java.util.EnumMap;
import java.util.Map;
import com.p2pstream.model.MessageType;
import com.p2pstream.net.Packet;

public final class UdpDispatcher {
    private final Map<MessageType, UdpHandler> handlers = new EnumMap<>(MessageType.class);

    public void register(MessageType type, UdpHandler handler) {
        handlers.put(type, handler);
    }

    public void dispatch(Packet packet, InetSocketAddress remote) {
        UdpHandler h = handlers.get(packet.messageType);
        if (h != null) h.handle(packet, remote);
        // else: bilinmeyen type -> ignore/log
    }
}
