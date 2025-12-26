
package com.p2pstream.net.udp;

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

    // Constructor with explicit messageId (for special cases)
    public Packet(UUID messageId, MessageType messageType, String myIp, int myPort, int ttl, byte[] data) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.myIp = myIp;
        this.myPort = myPort;
        this.ttl = ttl;
        this.data = (data == null) ? new byte[0] : data;
    }

    // Constructor with auto-generated messageId (most common use case)
    public Packet(MessageType messageType, String myIp, int myPort, int ttl, byte[] data) {
        this(UUID.randomUUID(), messageType, myIp, myPort, ttl, data);
    }

    // Convenience constructor for text data with auto-generated messageId
    public static Packet simpleText(MessageType type, String myIp, int myPort, int ttl, String text) {
        return new Packet(type, myIp, myPort, ttl,
                text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    // Convenience constructor for reply packets (same messageId)
    public static Packet createReply(Packet originalPacket, MessageType replyType, String myIp, int myPort, byte[] data) {
        return new Packet(originalPacket.messageId, replyType, myIp, myPort, originalPacket.ttl - 1, data);
    }

    // Convenience constructor for text reply
    public static Packet createTextReply(Packet originalPacket, MessageType replyType, String myIp, int myPort, String text) {
        return createReply(originalPacket, replyType, myIp, myPort,
                text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }
}