package com.p2pstream.network;

import com.p2pstream.common.AppLogger;
import com.p2pstream.common.ConfigLoader;
import com.p2pstream.common.Constants;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TcpFileServer extends Thread {

    // Simple TCP protocol:
    // client -> server:
    //   byte 'C'
    //   byte[32] fileHash
    //   int chunkIndex
    // server -> client:
    //   int dataLen (0 if missing/error)
    //   byte[dataLen]

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ServerSocket serverSocket;

    public TcpFileServer() throws IOException {
        super("TcpFileServer");
        this.serverSocket = new ServerSocket(Constants.TCP_PORT);
    }

    public void shutdown() {
        running.set(false);
        try { serverSocket.close(); } catch (Exception ignored) {}
    }

    @Override
    public void run() {
        AppLogger.info("TCP FileServer listening on " + Constants.TCP_PORT);
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                handleClient(client);
            } catch (IOException e) {
                if (running.get()) AppLogger.error("TCP accept failed", e);
            }
        }
    }

    private void handleClient(Socket client) {
        try (client;
             DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()))) {

            byte type = in.readByte();
            if (type != (byte) 'C') {
                out.writeInt(0);
                out.flush();
                return;
            }

            byte[] hash = new byte[32];
            in.readFully(hash);

            int chunkIndex = in.readInt();

            // Demo mapping: rootFolder içinde dosya adı hashHex + ".mp4" gibi ileride netleştireceğiz.
            // Şimdilik: "shared.mp4" gibi tek video için bile çalışacak şekilde fallback koyuyoruz.
            Path root = ConfigLoader.getInstance().getRootFolder();
            Path file = tryResolveByHashOrFallback(root, hash);

            if (file == null || !Files.exists(file)) {
                out.writeInt(0);
                out.flush();
                return;
            }

            long offset = (long) chunkIndex * Constants.CHUNK_SIZE;
            long size = Files.size(file);
            if (offset >= size) {
                out.writeInt(0);
                out.flush();
                return;
            }

            int len = (int) Math.min(Constants.CHUNK_SIZE, size - offset);
            byte[] data = new byte[len];

            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                raf.seek(offset);
                raf.readFully(data);
            }

            out.writeInt(len);
            out.write(data);
            out.flush();
        } catch (Exception e) {
            AppLogger.error("TCP client handler failed", e);
        }
    }

    private Path tryResolveByHashOrFallback(Path root, byte[] hash) throws IOException {
        if (root == null || !Files.isDirectory(root)) return null;

        // fallback: first mp4 file
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root, "*.{mp4,mkv,avi}")) {
            for (Path p : ds) return p;
        } catch (Exception ignored) {}

        // fallback: any file
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            for (Path p : ds) if (Files.isRegularFile(p)) return p;
        }
        return null;
    }
}
