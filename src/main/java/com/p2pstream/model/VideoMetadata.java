package com.p2pstream.model;

import java.io.Serializable;
import java.util.Objects;

public final class VideoMetadata implements Serializable {
    // PDF Gereksinimi: Dosyaları benzersiz tanımlamak için Hash kullanın
    private final String fileHash; // SHA-256 (Hex formatında tutmak daha kolaydır)
    private final String fileName;
    private final long fileSize;
    private final int totalChunks; // Toplam parça sayısı (256KB'lık)

    public VideoMetadata(String fileHash, String fileName, long fileSize, int totalChunks) {
        this.fileHash = Objects.requireNonNull(fileHash);
        this.fileName = Objects.requireNonNull(fileName);
        this.fileSize = fileSize;
        this.totalChunks = totalChunks;
    }

    public String getFileHash() { return fileHash; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public int getTotalChunks() { return totalChunks; }

    @Override
    public String toString() {
        return fileName + " [" + fileSize + " bytes] Hash:" + fileHash.substring(0, 8) + "...";
    }
}