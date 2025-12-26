package com.p2pstream.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Packet {

    public enum OpCode {
        HELLO((byte) 1),
        SEARCH((byte) 2),
        FOUND((byte) 3),
        REQUEST_CHUNK((byte) 4);

        private final byte code;
        OpCode(byte code) { this.code = code; }
        public byte code() { return code; }

        public static OpCode from(byte b) {
            for (OpCode o : values()) if (o.code == b) return o;
            throw new IllegalArgumentException("Unknown OpCode: " + b);
        }
    }

    // Header
    private static final byte[] MAGIC = new byte[] { 'P','2','P','S' }; // 4 bytes
    private static final byte VERSION = 1;

    private final OpCode opCode;
    private final int seqNo;
    private final int ttl;       // 0..255 stored as byte
    private final byte[] payload;

    public Packet(OpCode opCode, int seqNo, int ttl, byte[] payload) {
        this.opCode = opCode;
        this.seqNo = seqNo;
        this.ttl = ttl;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public OpCode getOpCode() { return opCode; }
    public int getSeqNo() { return seqNo; }
    public int getTtl() { return ttl; }
    public byte[] getPayload() { return payload; }

    public Packet withTtl(int newTtl) {
        return new Packet(this.opCode, this.seqNo, newTtl, this.payload);
    }

    // Layout:
    // magic(4) version(1) opcode(1) ttl(1) reserved(1) seqNo(4) payloadLen(4) payload(n)
    public byte[] toBytes() {
        int headerLen = 4 + 1 + 1 + 1 + 1 + 4 + 4;
        ByteBuffer buf = ByteBuffer.allocate(headerLen + payload.length);
        buf.put(MAGIC);
        buf.put(VERSION);
        buf.put(opCode.code());
        buf.put((byte) (ttl & 0xFF));
        buf.put((byte) 0); // reserved
        buf.putInt(seqNo);
        buf.putInt(payload.length);
        buf.put(payload);
        return buf.array();
    }

    public static Packet fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 16) throw new IllegalArgumentException("Packet too small");
        ByteBuffer buf = ByteBuffer.wrap(bytes);

        byte[] magic = new byte[4];
        buf.get(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IllegalArgumentException("Bad magic");

        byte ver = buf.get();
        if (ver != VERSION) throw new IllegalArgumentException("Bad version: " + ver);

        OpCode op = OpCode.from(buf.get());
        int ttl = Byte.toUnsignedInt(buf.get());
        buf.get(); // reserved

        int seq = buf.getInt();
        int len = buf.getInt();
        if (len < 0 || len > (bytes.length - buf.position()))
            throw new IllegalArgumentException("Bad payload length: " + len);

        byte[] payload = new byte[len];
        buf.get(payload);
        return new Packet(op, seq, ttl, payload);
    }

    // helpers for string payloads (optional)
    public static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    public static String utf8(byte[] p) {
        return p == null ? "" : new String(p, StandardCharsets.UTF_8);
    }
}
