package com.p2pstream.network;

import com.p2pstream.common.AppLogger;
import com.p2pstream.common.ConfigLoader;
import com.p2pstream.common.Constants;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.*;
import java.util.HexFormat;

public final class TcpFileDownloader {

    public Path downloadChunk(InetAddress peerIp, int peerTcpPort, byte[] fileHash, int chunkIndex) throws IOException {
        Path bufferDir = ConfigLoader.getInstance().getBufferFolder();
        Files.createDirectories(bufferDir);

        String hex = HexFormat.of().formatHex(fileHash);
        Path bufferFile = bufferDir.resolve(hex + ".buffer");

        try (Socket s = new Socket(peerIp, peerTcpPort);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
             RandomAccessFile raf = new RandomAccessFile(bufferFile.toFile(), "rw")) {

            out.writeByte((byte) 'C');
            out.write(fileHash);
            out.writeInt(chunkIndex);
            out.flush();

            int len = in.readInt();
            if (len <= 0) throw new IOException("Chunk not available (len=" + len + ")");

            byte[] data = new byte[len];
            in.readFully(data);

            long offset = (long) chunkIndex * Constants.CHUNK_SIZE;
            raf.seek(offset);
            raf.write(data);
            return bufferFile;

        } catch (IOException e) {
            AppLogger.error("Chunk download failed: idx=" + chunkIndex + " from " + peerIp, e);
            throw e;
        }
    }
}
