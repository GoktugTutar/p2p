package com.p2pstream.net.tcp;

import com.p2pstream.HeadlessPeer;
import com.p2pstream.model.Constants;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ParallelDownloader implements Runnable {

    private final String fileName;
    private final String fileHash;
    private final long totalSize;
    private final List<String> peerIps;
    private final int totalChunks;

    private final ConcurrentHashMap<Integer, Boolean> downloadedChunks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Integer> chunkQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer, Integer> retryCounter = new ConcurrentHashMap<>();

    public ParallelDownloader(String fileName, String fileHash, long totalSize, List<String> peerIps) {
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.totalSize = totalSize;
        this.peerIps = new ArrayList<>(peerIps);
        Collections.shuffle(this.peerIps);
        this.totalChunks = (int) Math.ceil((double) totalSize / Constants.CHUNK_SIZE);

        int prioritized = Math.min(Constants.BUFFER_READY_CHUNKS, totalChunks);
        for (int i = 0; i < prioritized; i++) {
            chunkQueue.offer(i);
        }
        for (int i = prioritized; i < totalChunks; i++) {
            chunkQueue.offer(i);
        }
    }

    @Override
    public void run() {
        // BAŞLANGIÇ LOGU
        System.out.println("⬇️  İNDİRME BAŞLATILDI: " + fileName);
        System.out.println("    └─ Boyut: " + (totalSize / 1024) + " KB | Parça Sayısı: " + totalChunks);
        System.out.println("    └─ Kaynaklar: " + peerIps);

        File bufferFile = new File(Constants.BUFFER_FOLDER + "/" + fileName);
        int workerCount = Math.min(Math.max(4, peerIps.size()), totalChunks);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
            raf.setLength(totalSize);

            for (int i = 0; i < workerCount; i++) {
                executor.submit(() -> workerLoop(bufferFile));
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);

            if (downloadedChunks.size() == totalChunks) {
                // BİTİŞ LOGU
                System.out.println("✅  İNDİRME TAMAMLANDI: " + fileName);

                File finalFile = new File(Constants.SHARED_FOLDER + "/" + fileName);
                Files.move(bufferFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                HeadlessPeer.broadcastProgress(fileHash, 100, "Completed");
                HeadlessPeer.broadcastLog("Download Finished: " + fileName);
            } else {
                System.err.println("❌  İndirme Hatası: Eksik parçalar var.");
                HeadlessPeer.broadcastProgress(fileHash, 0, "Error");
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void workerLoop(File bufferFile) {
        while (hasWorkPending()) {
            Integer chunkIndex = chunkQueue.poll();
            if (chunkIndex == null) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                continue;
            }
            if (downloadedChunks.containsKey(chunkIndex)) {
                continue;
            }

            int attempt = retryCounter.merge(chunkIndex, 1, Integer::sum);
            String ip = peerIps.get((attempt - 1) % peerIps.size());

            boolean ok = downloadChunk(chunkIndex, ip, bufferFile);
            if (!ok && attempt < peerIps.size() * 3) {
                chunkQueue.offer(chunkIndex);
            }
        }
    }

    private boolean hasWorkPending() {
        if (downloadedChunks.size() >= totalChunks) return false;
        if (!chunkQueue.isEmpty()) return true;

        for (int i = 0; i < totalChunks; i++) {
            if (!downloadedChunks.containsKey(i)) {
                int attempt = retryCounter.getOrDefault(i, 0);
                if (attempt < peerIps.size() * 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean downloadChunk(int index, String ip, File bufferFile) {
        if (downloadedChunks.containsKey(index)) return true;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, Constants.TCP_PORT), 2000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            InputStream in = socket.getInputStream();

            out.println(fileHash + ":" + index);

            String header = readLine(in);
            if (header == null || header.isEmpty()) return false;

            String[] tokens = header.split(":");
            if (tokens.length < 3) return false;
            String responseHash = tokens[0];
            int chunkIndex = Integer.parseInt(tokens[1]);
            int payloadLength = Integer.parseInt(tokens[2]);

            if (!responseHash.equals(fileHash) || chunkIndex != index || payloadLength <= 0) return false;

            byte[] payload = in.readNBytes(payloadLength);
            if (payload.length != payloadLength) return false;

            if (downloadedChunks.putIfAbsent(index, true) != null) {
                return true; // Duplicate geldi, yazma.
            }

            synchronized (bufferFile) {
                try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
                    raf.seek((long) index * Constants.CHUNK_SIZE);
                    raf.write(payload);
                }
            }

            reportProgress(ip);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void reportProgress(String sourceIp) {
        int downloaded = downloadedChunks.size();
        int percent = (downloaded * 100) / totalChunks;
        int bufferTarget = Math.min(Constants.BUFFER_READY_CHUNKS, totalChunks);
        boolean readyToPlay = downloaded >= bufferTarget;
        String status = readyToPlay ? "Playing" : "Buffering (" + downloaded + "/" + bufferTarget + ")";

        String annotatedStatus = status + " via " + sourceIp + " [" + downloaded + "/" + totalChunks + " chunks]";
        HeadlessPeer.broadcastProgress(fileHash, percent, annotatedStatus);

        if (percent > 0 && percent % 20 == 0) {
            System.out.println("⏳  İlerleme: %" + percent + " (" + fileName + ")");
        }
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b = -1;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            buffer.write(b);
        }
        if (buffer.size() == 0 && b == -1) return null;
        return buffer.toString();
    }
}