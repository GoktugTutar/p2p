package com.p2pstream.net;

import com.p2pstream.model.MessageType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Packet {
    public final UUID messageId;
    public final MessageType messageType;
    public final String myIp;
    public final int myPort;
    public final int ttl;
    public final byte[] data;

    public Packet(UUID messageId, MessageType messageType, String myIp, int myPort, int ttl, byte[] data) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.myIp = myIp;
        this.myPort = myPort;
        this.ttl = ttl;
        this.data = (data == null) ? new byte[0] : data;
    }

    public static Packet simpleText(MessageType type, String myIp, int myPort, int ttl, String text) {
        return new Packet(UUID.randomUUID(), type, myIp, myPort, ttl,
                text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }
}
