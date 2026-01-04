package com.p2pstream.net.tcp;

import com.p2pstream.HeadlessPeer;
import com.p2pstream.model.Constants;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class ParallelDownloader implements Runnable {

    private final String fileName;
    private final String fileHash;
    private final long totalSize;
    private final List<String> peerIps;
    private final int totalChunks;

    private final ConcurrentHashMap<Integer, Boolean> downloadedChunks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Integer> failedChunks = new ConcurrentLinkedQueue<>();

    public ParallelDownloader(String fileName, String fileHash, long totalSize, List<String> peerIps) {
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.totalSize = totalSize;
        this.peerIps = peerIps;
        this.totalChunks = (int) Math.ceil((double) totalSize / Constants.CHUNK_SIZE);
    }

    @Override
    public void run() {
        System.out.println("⬇️  İNDİRME BAŞLATILDI: " + fileName);

        // --- 1. İSTEK: DETAYLI BAŞLANGIÇ LOGLARI ---
        HeadlessPeer.broadcastLog("------------------------------------------------");
        HeadlessPeer.broadcastLog("🎬 STARTING DOWNLOAD: " + fileName);
        HeadlessPeer.broadcastLog("📦 Total Size: " + (totalSize / 1024) + " KB");
        HeadlessPeer.broadcastLog("🧩 Total Chunks: " + totalChunks);
        HeadlessPeer.broadcastLog("👥 Active Peers: " + peerIps.size() + " (" + peerIps + ")");
        HeadlessPeer.broadcastLog("------------------------------------------------");

        File bufferFile = new File(Constants.BUFFER_FOLDER + "/" + fileName);
        if (bufferFile.getParentFile() != null) bufferFile.getParentFile().mkdirs();

        try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
            if (raf.length() != totalSize) raf.setLength(totalSize);
        } catch (IOException e) { e.printStackTrace(); return; }

        int numPeers = peerIps.size();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, numPeers));

        int chunksPerPeer = totalChunks / numPeers;
        int remainder = totalChunks % numPeers;
        int startChunkIndex = 0;

        for (String peerIp : peerIps) {
            int assignedCount = chunksPerPeer + (remainder > 0 ? 1 : 0);
            if (remainder > 0) remainder--;
            if (assignedCount == 0) continue;

            int endChunkIndex = startChunkIndex + assignedCount;

            // --- LOG: KİM HANGİ PARÇAYI ALIYOR? ---
            String assignmentLog = String.format("👉 TASK ASSIGNED: Peer [%s] gets Chunks [%d - %d]", peerIp, startChunkIndex, (endChunkIndex - 1));
            System.out.println(assignmentLog);
            HeadlessPeer.broadcastLog(assignmentLog);

            final int myStart = startChunkIndex;
            final int myEnd = endChunkIndex;

            executor.submit(() -> downloadRangePersistent(peerIp, myStart, myEnd, bufferFile));
            startChunkIndex = endChunkIndex;
        }

        executor.shutdown();
        try { executor.awaitTermination(60, TimeUnit.MINUTES); } catch (InterruptedException e) {}

        if (!failedChunks.isEmpty()) {
            HeadlessPeer.broadcastLog("⚠️ Retrying " + failedChunks.size() + " failed chunks...");
            processRetryQueue(bufferFile);
        }

        if (downloadedChunks.size() == totalChunks) {
            if (verifyFileHash(bufferFile)) {
                try {
                    File finalFile = new File(Constants.SHARED_FOLDER + "/" + fileName);
                    Files.move(bufferFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    HeadlessPeer.broadcastProgress(fileHash, totalSize, totalSize, "Completed");
                    HeadlessPeer.broadcastLog("✅ DONE: " + fileName + " is ready!");
                    HeadlessPeer.downloadedChunksCache.remove(fileName);
                } catch (IOException e) { e.printStackTrace(); }
            } else {
                HeadlessPeer.broadcastLog("❌ HASH MISMATCH: File corrupted.");
            }
        } else {
            HeadlessPeer.broadcastProgress(fileHash, downloadedChunks.size() * Constants.CHUNK_SIZE, totalSize, "Incomplete");
        }
    }

    private void downloadRangePersistent(String ip, int startIndex, int endIndex, File bufferFile) {
        try (Socket socket = new Socket(ip, Constants.TCP_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             InputStream in = socket.getInputStream()) {

            for (int i = startIndex; i < endIndex; i++) {
                if (downloadedChunks.containsKey(i)) continue;

                try {
                    out.println(fileName + ":" + i);

                    byte[] chunkBuffer = new byte[Constants.CHUNK_SIZE];
                    int totalBytesRead = 0;
                    int read;

                    while (totalBytesRead < Constants.CHUNK_SIZE) {
                        int toRead = Constants.CHUNK_SIZE - totalBytesRead;
                        read = in.read(chunkBuffer, totalBytesRead, toRead);
                        if (read == -1) break;
                        totalBytesRead += read;
                        if ((long) i * Constants.CHUNK_SIZE + totalBytesRead >= totalSize) break;
                    }

                    if (totalBytesRead > 0) {
                        synchronized (bufferFile) {
                            try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
                                raf.seek((long) i * Constants.CHUNK_SIZE);
                                raf.write(chunkBuffer, 0, totalBytesRead);
                            }
                        }
                        downloadedChunks.put(i, true);

                        Set<Integer> globalSet = HeadlessPeer.downloadedChunksCache.get(fileName);
                        if (globalSet != null) globalSet.add(i);

                        reportProgress();

                        try { Thread.sleep(300); } catch (InterruptedException e) {}

                    } else {
                        failedChunks.add(i);
                    }
                } catch (Exception ex) {
                    failedChunks.add(i);
                    throw ex;
                }
            }
        } catch (Exception e) {
            for (int i = startIndex; i < endIndex; i++) {
                if (!downloadedChunks.containsKey(i) && !failedChunks.contains(i)) {
                    failedChunks.add(i);
                }
            }
        }
    }

    private void processRetryQueue(File bufferFile) {
        Random rand = new Random();
        while (!failedChunks.isEmpty()) {
            Integer chunkId = failedChunks.poll();
            if (chunkId == null) break;

            String backupIp = peerIps.get(rand.nextInt(peerIps.size()));

            try (Socket socket = new Socket(backupIp, Constants.TCP_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 InputStream in = socket.getInputStream()) {

                out.println(fileName + ":" + chunkId);
                byte[] data = new byte[Constants.CHUNK_SIZE];
                // Basit okuma
                int read = in.read(data);

                if (read > 0) {
                    synchronized (bufferFile) {
                        try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
                            raf.seek((long) chunkId * Constants.CHUNK_SIZE);
                            raf.write(data, 0, read);
                        }
                    }
                    downloadedChunks.put(chunkId, true);
                    Set<Integer> globalSet = HeadlessPeer.downloadedChunksCache.get(fileName);
                    if (globalSet != null) globalSet.add(chunkId);
                    reportProgress();

                    // Retry yaparken de yavaşlatalım ki görebilesiniz
                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                }
            } catch (Exception e) {}
        }
    }

    private boolean verifyFileHash(File file) {
        try (InputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[4096];
            int length;
            while ((length = fis.read(block)) > 0) digest.update(block, 0, length);

            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) sb.append(String.format("%02x", b));

            if (fileHash == null || fileHash.isEmpty()) return true;
            return sb.toString().equalsIgnoreCase(fileHash);
        } catch (Exception e) { return false; }
    }

    private void reportProgress() {
        long currentBytes = (long) downloadedChunks.size() * Constants.CHUNK_SIZE;
        if (currentBytes > totalSize) currentBytes = totalSize;
        String status = (currentBytes > totalSize * 0.05) ? "Playing/Downloading" : "Buffering...";
        HeadlessPeer.broadcastProgress(fileHash, currentBytes, totalSize, status);
    }
}