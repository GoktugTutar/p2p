package com.p2pstream.net.tcp;

import com.p2pstream.HeadlessPeer;
import com.p2pstream.model.Constants;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
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

    // İndirilen parçaların kaydı (Global Liste)
    private final ConcurrentHashMap<Integer, Boolean> downloadedChunks = new ConcurrentHashMap<>();

    public ParallelDownloader(String fileName, String fileHash, long totalSize, List<String> peerIps) {
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.totalSize = totalSize;
        this.peerIps = peerIps;
        // Toplam chunk sayısını hesapla
        this.totalChunks = (int) Math.ceil((double) totalSize / Constants.CHUNK_SIZE);
    }

    @Override
    public void run() {
        System.out.println("⬇️  STATİK BÖLÜMLENDİRME İLE İNDİRME BAŞLATILDI: " + fileName);
        System.out.println("    └─ Dosya Boyutu: " + (totalSize / 1024) + " KB");
        System.out.println("    └─ Toplam Parça (Chunk): " + totalChunks);
        System.out.println("    └─ Kaynak Peer Sayısı: " + peerIps.size());

        // 1. Dosya ve Klasör Hazırlığı
        File bufferFile = new File(Constants.BUFFER_FOLDER + "/" + fileName);
        if (bufferFile.getParentFile() != null && !bufferFile.getParentFile().exists()) {
            bufferFile.getParentFile().mkdirs();
        }

        try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
            // YouTube tarzı izleme için dosyayı baştan tam boyuta getir (Pre-allocation)
            raf.setLength(totalSize);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 2. Görev Dağılımı (Partitioning Logic)
        int numPeers = peerIps.size();
        if (numPeers == 0) return;

        // Her peer'a kaç parça düşecek?
        int chunksPerPeer = totalChunks / numPeers;
        int remainder = totalChunks % numPeers; // Kalan parçalar (Eşit bölünmezse)

        // Peer sayısı kadar Thread açıyoruz
        ExecutorService executor = Executors.newFixedThreadPool(numPeers);

        int startChunkIndex = 0;

        for (String peerIp : peerIps) {
            // Bu peer kaç parça alacak? (Kalan varsa sırayla 1'er tane ekle)
            int assignedCount = chunksPerPeer + (remainder > 0 ? 1 : 0);
            if (remainder > 0) remainder--;

            if (assignedCount == 0) continue; // Peer sayısı chunk sayısından fazlaysa bazıları boş kalabilir

            int endChunkIndex = startChunkIndex + assignedCount;

            // Log: Kim nereyi alıyor?
            System.out.println("    👉 Görev Ataması: " + peerIp + " -> Chunk [" + startChunkIndex + " - " + (endChunkIndex - 1) + "]");

            // Thread'i başlat (final değişkenler lambda için gereklidir)
            final int myStart = startChunkIndex;
            final int myEnd = endChunkIndex;

            executor.submit(() -> downloadRangeFromPeer(peerIp, myStart, myEnd, bufferFile));

            // Bir sonraki peer için başlangıç noktasını kaydır
            startChunkIndex = endChunkIndex;
        }

        // 3. Bitmesini Bekle
        executor.shutdown();
        try {
            // 15 dakika veya işlem bitene kadar bekle
            executor.awaitTermination(15, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 4. Sonuç Kontrolü
        if (downloadedChunks.size() == totalChunks) {
            System.out.println("✅  TÜM PARÇALAR TAMAMLANDI: " + fileName);
            try {
                File finalFile = new File(Constants.SHARED_FOLDER + "/" + fileName);
                if (finalFile.getParentFile() != null) finalFile.getParentFile().mkdirs();
                Files.move(bufferFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                HeadlessPeer.broadcastProgress(fileHash, totalSize, totalSize, "Completed");
                HeadlessPeer.broadcastLog("Download Finished: " + fileName);
            } catch (IOException e) { e.printStackTrace(); }
        } else {
            System.err.println("❌  İndirme Eksik Kaldı: " + downloadedChunks.size() + "/" + totalChunks + " parça indi.");
            HeadlessPeer.broadcastProgress(fileHash, downloadedChunks.size() * Constants.CHUNK_SIZE, totalSize, "Error/Incomplete");
        }
    }

    /**
     * Bu metod, spesifik bir Peer'dan, belirli bir ARALIKTAKİ (Range) chunkları ister.
     */
// ParallelDownloader.java içindeki metodun güncel hali:

    private void downloadRangeFromPeer(String ip, int startIndex, int endIndex, File bufferFile) {
        System.out.println("THREAD BAŞLADI [" + ip + "]: Chunk " + startIndex + "'den " + endIndex + "'e kadar istiyor.");

        try (Socket socket = new Socket(ip, Constants.TCP_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             InputStream in = socket.getInputStream()) {

            // Tek bir soket bağlantısı üzerinden seri istek atmak yerine,
            // her chunk için ayrı bağlantı açmak daha güvenlidir (Stateless).
            // Mevcut yapınızda "Keep-Alive" yoksa döngü içinde soket açmak gerekebilir.
            // Ancak performans için soketi dışarıda tutuyoruz.

            // DİKKAT: Mevcut TcpServer kodunuz "Bir istek al, cevapla, kapat" mantığında çalışıyor olabilir.
            // Eğer TcpServer "while" döngüsü ile sürekli dinlemiyorsa, soket her chunk'ta kapanır.
            // Bu yüzden döngüyü BURADA DEĞİL, dışarıda yapıp her chunk için yeniden bağlanmalıyız.
        } catch (IOException e) {
            // ...
        }

        // --- DÜZELTME: Her Chunk İçin Yeni Bağlantı ---
        // TcpServer kodunuz "request = in.readLine()" sonrası cevabı verip finally bloğunda socket.close() yapıyor.
        // Bu yüzden tek soketle birden fazla chunk isteyemezsiniz.

        for (int i = startIndex; i < endIndex; i++) {
            if (downloadedChunks.containsKey(i)) continue;

            try (Socket socket = new Socket(ip, Constants.TCP_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 InputStream in = socket.getInputStream()) {

                out.println(fileName + ":" + i);

                // Veriyi oku
                byte[] chunkBuffer = new byte[Constants.CHUNK_SIZE];
                int totalBytesRead = 0;
                int read;

                // Tampon dolana veya veri bitene kadar oku
                while ((read = in.read(chunkBuffer, totalBytesRead, Constants.CHUNK_SIZE - totalBytesRead)) != -1) {
                    totalBytesRead += read;
                    // Eğer buffer dolduysa çık (Gereksiz beklemeyi önle)
                    if (totalBytesRead == Constants.CHUNK_SIZE) break;
                }

                if (totalBytesRead > 0) {
                    synchronized (bufferFile) {
                        try (RandomAccessFile raf = new RandomAccessFile(bufferFile, "rw")) {
                            raf.seek((long) i * Constants.CHUNK_SIZE);
                            raf.write(chunkBuffer, 0, totalBytesRead);
                        }
                    }
                    downloadedChunks.put(i, true);
                    reportProgress();
                } else {
                    System.err.println("⚠️  Boş veri geldi (veya bağlantı kapandı): " + ip + " Chunk: " + i);
                }

            } catch (IOException e) {
                System.err.println("❌  Bağlantı Hatası (" + ip + "): " + e.getMessage());
            }
        }

        System.out.println("THREAD BİTTİ [" + ip + "]");
    }

    private void reportProgress() {
        long currentBytes = (long) downloadedChunks.size() * Constants.CHUNK_SIZE;
        if (currentBytes > totalSize) currentBytes = totalSize;

        String status = (currentBytes > totalSize * 0.1) ? "Playing" : "Buffering...";
        HeadlessPeer.broadcastProgress(fileHash, currentBytes, totalSize, status);
    }
}