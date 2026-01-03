package com.p2pstream.net.tcp;

import com.p2pstream.model.Constants;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer extends Thread {
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(Constants.TCP_PORT)) {
            System.out.println("🚀 Chunk File Server Başlatıldı (Persistent): Port " + Constants.TCP_PORT);
            while (true) {
                Socket client = serverSocket.accept();
                // Her bağlantıyı ayrı bir thread'de "Sürekli Dinle" modunda çalıştır
                new Thread(() -> handlePersistentConnection(client)).start();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void handlePersistentConnection(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                OutputStream out = socket.getOutputStream()
        ) {
            String request;
            // while döngüsü ile bağlantı kopana kadar istekleri dinle (Keep-Alive)
            while ((request = in.readLine()) != null) {
                String[] parts = request.split(":");
                if (parts.length < 2) continue;

                String fileName = parts[0];
                int chunkIndex = Integer.parseInt(parts[1]);

                File file = new File(Constants.SHARED_FOLDER + "/" + fileName);
                if (!file.exists()) continue; // Dosya yoksa sessizce geç veya hata kodu dön

                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    long offset = (long) chunkIndex * Constants.CHUNK_SIZE;
                    if (offset >= file.length()) continue;

                    raf.seek(offset);
                    byte[] buffer = new byte[Constants.CHUNK_SIZE];
                    int bytesRead = raf.read(buffer);

                    if (bytesRead > 0) {
                        out.write(buffer, 0, bytesRead);
                        out.flush(); // Tamponu boşalt, veriyi yolla
                    }
                }
            }
        } catch (Exception e) {
            // Bağlantı koptuğunda buraya düşer
            System.out.println("connection ended: " + socket.getInetAddress());
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }
}