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
            if (!file.exists()) return;

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long offset = (long) chunkIndex * Constants.CHUNK_SIZE;
                if (offset >= file.length()) return;

                raf.seek(offset);
                byte[] buffer = new byte[Constants.CHUNK_SIZE];
                int bytesRead = raf.read(buffer);

                if (bytesRead > 0) {
                    // --- GECİKME AYARI ---
                    try { Thread.sleep(3000); } catch (InterruptedException e) {}

                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            }
        } catch (Exception e) {
            // Hata logunu gizle
        } finally {
            try { socket.close(); } catch (IOException e) {}
        }
    }
}