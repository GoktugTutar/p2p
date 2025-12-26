package com.p2pstream.domain;

import java.util.BitSet;

public final class ChunkMap {
    private final BitSet bits;
    private final int totalChunks;

    public ChunkMap(int totalChunks) {
        if (totalChunks <= 0) throw new IllegalArgumentException("totalChunks must be > 0");
        this.totalChunks = totalChunks;
        this.bits = new BitSet(totalChunks);
    }

    public int getTotalChunks() { return totalChunks; }

    public synchronized boolean hasChunk(int idx) {
        rangeCheck(idx);
        return bits.get(idx);
    }

    public synchronized void setChunk(int idx, boolean value) {
        rangeCheck(idx);
        bits.set(idx, value);
    }

    public synchronized int countHave() {
        return bits.cardinality();
    }

    public synchronized byte[] toByteArray() {
        // BitSet#toByteArray is little-endian bit order, OK as long as we use the same both sides
        return bits.toByteArray();
    }

    public static ChunkMap fromByteArray(int totalChunks, byte[] data) {
        ChunkMap cm = new ChunkMap(totalChunks);
        if (data == null) return cm;
        BitSet bs = BitSet.valueOf(data);
        synchronized (cm) {
            cm.bits.or(bs);
            // ensure no bits beyond totalChunks are considered
            cm.bits.clear(totalChunks, cm.bits.length());
        }
        return cm;
    }

    private void rangeCheck(int idx) {
        if (idx < 0 || idx >= totalChunks) throw new IndexOutOfBoundsException(idx);
    }
}
