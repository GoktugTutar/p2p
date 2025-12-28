package com.p2pstream.net.tcp;

import com.p2pstream.HeadlessPeer;
import com.p2pstream.model.Constants;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    public ParallelDownloader(String fileName, String fileHash, long totalSize, List<String> peerIps) {
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.totalSize = totalSize;
        this.peerIps = peerIps;
        this.totalChunks = (int) Math.ceil((double) totalSize / Constants.CHUNK_SIZE);
    }

    @Override
    public void run() {
        // BAŞLANGIÇ LOGU
        System.out.println("⬇️  İNDİRME BAŞLATILDI: " + fileName);
        System.out.println("    └─ Boyut: " + (totalSize / 1024) + " KB | Parça Sayısı: " + totalChunks);
        System.out.println("    └─ Kaynaklar: " + peerIps);

        File bufferFile = new File(Constants.BUFFER_FOLDER + "/" + fileName);
        ExecutorService executor = Executors.newFixedThreadPool(4); // 4 Paralel Kanal

        try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
            raf.setLength(totalSize);

            for (int i = 0; i < totalChunks; i++) {
                final int chunkIndex = i;
                String targetIp = peerIps.get(i % peerIps.size());
                executor.submit(() -> downloadChunk(chunkIndex, targetIp, bufferFile));
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

    private void downloadChunk(int index, String ip, File bufferFile) {
        if (downloadedChunks.containsKey(index)) return;

        try (Socket socket = new Socket(ip, Constants.TCP_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             InputStream in = socket.getInputStream()) {

            out.println(fileName + ":" + index);
            byte[] data = in.readAllBytes();

            if (data.length > 0) {
                synchronized (bufferFile) {
                    try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
                        raf.seek((long) index * Constants.CHUNK_SIZE);
                        raf.write(data);
                    }
                }
                downloadedChunks.put(index, true);

                int percent = (downloadedChunks.size() * 100) / totalChunks;
                String status = (percent > 10) ? "Playing" : "Buffering...";

                // WEB ARAYÜZÜNE SIK BİLDİRİM YAP (Akıcı bar için)
                HeadlessPeer.broadcastProgress(fileHash, percent, status);

                // TERMİNALE SEYREK LOG BAS (Her %20'de bir)
                if (percent > 0 && percent % 20 == 0 && downloadedChunks.size() % (totalChunks/5) == 0) {
                    System.out.println("⏳  İlerleme: %" + percent + " (" + fileName + ")");
                }
            }
        } catch (IOException e) {
            // Hata logunu basma, retry mekanizması halleder veya sessiz kalsın
        }
    }
}