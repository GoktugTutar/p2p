package com.p2pstream.net.tcp;

import com.p2pstream.model.Constants;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer extends Thread {
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(Constants.TCP_PORT)) {
            System.out.println("🚀 Chunk File Server Başlatıldı: Port " + Constants.TCP_PORT);
            while (true) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleChunkRequest(client)).start();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleChunkRequest(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                OutputStream out = socket.getOutputStream()
        ) {
            String request = in.readLine();
            if (request == null) return;

            String[] parts = request.split(":");
            String fileName = parts[0];
            int chunkIndex = Integer.parseInt(parts[1]);

            File file = new File(Constants.SHARED_FOLDER + "/" + fileName);
            if (!file.exists()) {
                System.err.println("❌ Dosya bulunamadı: " + fileName);
                return;
            }

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long offset = (long) chunkIndex * Constants.CHUNK_SIZE;

                // Eğer istenen parça dosya boyutunu aşıyorsa, boş dön
                if (offset >= file.length()) return;

                raf.seek(offset);
                byte[] buffer = new byte[Constants.CHUNK_SIZE];

                // DÜZELTME BURADA:
                // Son chunk, standart boyuttan küçük olabilir.
                // Ne kadar okunabiliyorsa o kadar oku.
                int bytesRead = raf.read(buffer);

                if (bytesRead > 0) {
                    // Yapay gecikme (İsteğe bağlı, test için)
                    try { Thread.sleep(3500); } catch (InterruptedException e) {}

                    // Okunan kadarını gönder (buffer'ın tamamını değil)
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                    // System.out.println("📤 Chunk " + chunkIndex + " gönderildi (" + bytesRead + " bytes)");
                }
            }
        } catch (Exception e) {
            // e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }
}