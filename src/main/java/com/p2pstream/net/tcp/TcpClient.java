package com.p2pstream.net.tcp;

import com.p2pstream.HeadlessPeer;
import com.p2pstream.model.Constants;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class TcpClient implements Runnable {

    private final String targetIp;
    private final String fileName;
    private final String fileHash;
    private final long totalSize;

    public TcpClient(String targetIp, String fileName, String fileHash, long totalSize) {
        this.targetIp = targetIp;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.totalSize = totalSize;
    }

    @Override
    public void run() {
        File bufferFile = new File(Constants.BUFFER_FOLDER + "/" + fileName);
        File finalFile = new File(Constants.SHARED_FOLDER + "/" + fileName);

        System.out.println("⬇️ TCP İndirme Başlıyor: " + fileName + " Kaynak: " + targetIp);

        HeadlessPeer.broadcastProgress(fileHash, 0, totalSize, "Connecting...");

        try (Socket socket = new Socket(targetIp, Constants.TCP_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             InputStream in = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream(bufferFile)) {

            // 1. Dosya adını sunucuya gönder
            out.println(fileName);

            // 2. Gelen veriyi Chunk Chunk Buffer dosyasına yaz
            byte[] buffer = new byte[Constants.CHUNK_SIZE];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                // DÜZELTME 2: İlerleme bildirimi yeni format (totalRead, totalSize)
                if (totalRead % (Constants.CHUNK_SIZE * 10) == 0 || totalRead == totalSize) {
                    int percent = (int) ((totalRead * 100) / totalSize);
                    String status = (percent >= 10) ? "Playing" : "Buffering...";
                    HeadlessPeer.broadcastProgress(fileHash, totalRead, totalSize, status);
                }
            }

            System.out.println("✅ İndirme bitti. Dosya taşınıyor...");
            // DÜZELTME 3: Finalizing durumu
            HeadlessPeer.broadcastProgress(fileHash, totalSize, totalSize, "Finalizing...");

            // 3. İndirme bitince dosyayı BUFFER -> SHARED klasörüne taşı
            Files.move(bufferFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.out.println("✅ Dosya başarıyla kaydedildi: " + finalFile.getAbsolutePath());
            HeadlessPeer.broadcastLog("Download Completed: " + fileName);

            // Tamamlandı durumu
            HeadlessPeer.broadcastProgress(fileHash, totalSize, totalSize, "Completed");

        } catch (IOException e) {
            System.err.println("TCP İndirme Hatası: " + e.getMessage());
            HeadlessPeer.broadcastLog("Download Failed: " + e.getMessage());
            // Hata durumu
            HeadlessPeer.broadcastProgress(fileHash, 0, totalSize, "Error");
        }
    }
}