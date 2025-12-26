package com.p2pstream.domain;

import java.util.Objects;

public final class VideoMetadata {
    private final byte[] fileHashSha256; // 32 bytes
    private final String fileName;
    private final long fileSize;
    private final int totalChunks;

    public VideoMetadata(byte[] fileHashSha256, String fileName, long fileSize, int totalChunks) {
        if (fileHashSha256 == null || fileHashSha256.length != 32)
            throw new IllegalArgumentException("SHA-256 hash must be 32 bytes");
        this.fileHashSha256 = fileHashSha256.clone();
        this.fileName = Objects.requireNonNull(fileName);
        this.fileSize = fileSize;
        this.totalChunks = totalChunks;
    }

    public byte[] getFileHashSha256() { return fileHashSha256.clone(); }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public int getTotalChunks() { return totalChunks; }
}
