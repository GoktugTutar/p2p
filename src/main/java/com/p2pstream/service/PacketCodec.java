package com.p2pstream.service;

import com.p2pstream.model.Constants;
import com.p2pstream.model.MessageType;
import com.p2pstream.net.udp.Packet;

import java.io.*;
import java.util.UUID;

public final class PacketCodec {
    private PacketCodec() {}
    public static byte[] encode(Packet p) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        dos.writeLong(p.messageId.getMostSignificantBits());
        dos.writeLong(p.messageId.getLeastSignificantBits());

        dos.writeInt(p.messageType.ordinal());

        writeString(dos, p.myIp);
        dos.writeInt(p.myPort);

        dos.writeInt(p.ttl);

        dos.writeInt(p.data.length);
        dos.write(p.data);

        dos.flush();
        return bos.toByteArray();
    }

    public static Packet decode(byte[] bytes, int length) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes, 0, length));

        long msb = dis.readLong();
        long lsb = dis.readLong();
        UUID messageId = new UUID(msb, lsb);

        int typeOrd = dis.readInt();
        MessageType type = MessageType.values()[typeOrd];

        String myIp = readString(dis);
        int myPort = dis.readInt();

        int ttl = dis.readInt();

        int dataLen = dis.readInt();
        if (dataLen < 0 || dataLen > Constants.MAX_UDP_PACKET_BYTES) {
            throw new IOException("Invalid dataLen=" + dataLen);
        }
        byte[] data = new byte[dataLen];
        dis.readFully(data);

        return new Packet(messageId, type, myIp, myPort, ttl, data);
    }

    private static void writeString(DataOutputStream dos, String s) throws IOException {
        byte[] b = (s == null ? "" : s).getBytes();
        dos.writeInt(b.length);
        dos.write(b);
    }

    private static String readString(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        if (len < 0 || len > 4096) throw new IOException("Invalid string len=" + len);
        byte[] b = new byte[len];
        dis.readFully(b);
        return new String(b);
    }
}
