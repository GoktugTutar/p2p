package com.p2pstream;

import com.p2pstream.model.Constants;
import com.p2pstream.model.MessageType;
import com.p2pstream.net.tcp.ParallelDownloader;
import com.p2pstream.net.tcp.TcpServer;
import com.p2pstream.net.udp.*;
import com.p2pstream.service.FileService;
import com.p2pstream.service.PacketCodec;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HeadlessPeer {

    public static final Set<WsContext> webClients = ConcurrentHashMap.newKeySet();
    public static final ConcurrentHashMap<String, Set<String>> searchResultsCache = new ConcurrentHashMap<>();

    public static final ConcurrentHashMap<String, Set<Integer>> downloadedChunksCache = new ConcurrentHashMap<>();

    private static UdpServer udpServer;

    public static void main(String[] args) {
        try {
            System.setProperty("org.slf4j.simpleLogger.log.io.javalin", "off");
            System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty", "off");

            System.out.println(">>> P2P CLIENT SYSTEM STARTED...");

            // Klasörleri garantiye al
            new File(Constants.BUFFER_FOLDER).mkdirs();
            new File(Constants.SHARED_FOLDER).mkdirs();

            FileService fileService = new FileService("shared_videos");
            fileService.scanFiles();

            String peerId = UUID.randomUUID().toString();
            String myIp = getRealIp();
            System.out.println("✅ TESPİT EDİLEN IP ADRESİM: " + myIp);
            int myPort = Constants.UDP_PORT;

            UdpSender udpSender = new UdpSender();
            MyUdpHandler udpHandler = new MyUdpHandler(udpSender, fileService, peerId, myIp, myPort);

            udpServer = new UdpServer(Constants.UDP_PORT, udpHandler);
            udpServer.start();

            new TcpServer().start();

            // --- WEB GUI ---
            try {
                if (Files.exists(Paths.get("/app/web"))) {
                    Javalin app = Javalin.create(config -> {
                        config.staticFiles.add("/app/web", Location.EXTERNAL);
                        config.showJavalinBanner = false;
                    }).start(8080);

                    // 1. CONNECT API
                    app.post("/api/connect", ctx -> {
                        if (!udpServer.isRunning()) { udpServer.start(); broadcastLog("UDP Server restarted."); }
                        int ttl = Constants.ttl;
                        Packet p = Packet.simpleText(MessageType.HELLO, myIp, myPort, ttl, "Hi");
                        udpSender.sendToAllLocalSubnets(PacketCodec.encode(p), Constants.UDP_PORT);
                        ctx.result("HELLO Broadcast Sent");
                    });

                    // 2. DISCONNECT API
                    app.post("/api/disconnect", ctx -> {
                        if (udpServer != null && udpServer.isRunning()) { udpServer.stop(); }
                        broadcastLog("Disconnected from network.");
                        ctx.result("Disconnected");
                    });

                    // 3. SEARCH API
                    app.post("/api/search", ctx -> {
                        if (!udpServer.isRunning()) { ctx.status(400).result("Offline"); return; }
                        String query = ctx.queryParam("q");
                        searchResultsCache.clear();
                        Packet p = Packet.simpleText(MessageType.SEARCH, myIp, myPort, Constants.ttl, query);
                        udpSender.sendToAllLocalSubnets(PacketCodec.encode(p), Constants.UDP_PORT);
                        ctx.result("OK");
                    });

                    // 4. DOWNLOAD API
                    app.post("/api/download", ctx -> {
                        String fileName = ctx.queryParam("file");
                        String fileHash = ctx.queryParam("hash");
                        long size = Long.parseLong(ctx.queryParam("size"));

                        Set<String> owners = searchResultsCache.getOrDefault(fileHash, new HashSet<>());
                        String fallbackIp = ctx.queryParam("ip");
                        if (fallbackIp != null) owners.add(fallbackIp);
                        owners.remove(myIp);

                        if (owners.isEmpty()) { ctx.status(400).result("No peers found."); return; }

                        // Cache'i temizle/hazırla
                        downloadedChunksCache.put(fileName, ConcurrentHashMap.newKeySet());

                        List<String> sourceIps = new ArrayList<>(owners);
                        ParallelDownloader downloader = new ParallelDownloader(fileName, fileHash, size, sourceIps);
                        new Thread(downloader).start();
                        ctx.result("Download Started");
                    });

                    // 5. STREAM API
                    app.get("/api/watch/{filename}", ctx -> {
                        String fileName = ctx.pathParam("filename");
                        File bufferFile = new File(Constants.BUFFER_FOLDER + "/" + fileName);
                        File sharedFile = new File(Constants.SHARED_FOLDER + "/" + fileName);

                        // Eğer shared klasöründeyse (indirme bitmiş), direkt oradan sun
                        if (sharedFile.exists()) {
                            serveFileStandard(ctx, sharedFile);
                            return;
                        }

                        // Buffer'da yoksa 404
                        if (!bufferFile.exists()) { ctx.status(404).result("File not found"); return; }

                        long fileLen = bufferFile.length();
                        String range = ctx.header("Range");
                        ctx.contentType("video/mp4");
                        ctx.header("Accept-Ranges", "bytes");

                        if (range == null) {
                            ctx.status(200);
                            ctx.result(new FileInputStream(bufferFile));
                        } else {
                            String[] parts = range.replace("bytes=", "").split("-");
                            long start = Long.parseLong(parts[0]);
                            long end = parts.length > 1 ? Long.parseLong(parts[1]) : fileLen - 1;
                            if (end >= fileLen) end = fileLen - 1;
                            long len = end - start + 1;

                            ctx.status(206);
                            ctx.header("Content-Range", "bytes " + start + "-" + end + "/" + fileLen);
                            ctx.header("Content-Length", String.valueOf(len));

                            RandomAccessFile raf = new RandomAccessFile(bufferFile, "r");
                            raf.seek(start);

                            InputStream is = new InputStream() {
                                long bytesLeft = len;
                                long currentPos = start;

                                @Override
                                public int read() throws IOException {
                                    waitForData(currentPos);
                                    int b = raf.read();
                                    if (b != -1) {
                                        bytesLeft--;
                                        currentPos++;
                                    }
                                    return b;
                                }

                                @Override
                                public int read(byte[] b, int off, int l) throws IOException {
                                    waitForData(currentPos);
                                    int read = raf.read(b, off, (int) Math.min(l, bytesLeft));
                                    if (read != -1) {
                                        bytesLeft -= read;
                                        currentPos += read;
                                    }
                                    return read;
                                }

                                @Override
                                public void close() throws IOException { raf.close(); }

                                private void waitForData(long pos) {
                                    int chunkIndex = (int) (pos / Constants.CHUNK_SIZE);
                                    Set<Integer> downloaded = downloadedChunksCache.get(fileName);

                                    if (downloaded != null) {
                                        int waitTime = 0;
                                        while (!downloaded.contains(chunkIndex) && waitTime < 30000) {
                                            try { Thread.sleep(100); waitTime += 100; } catch (Exception e) {}
                                        }
                                    }
                                }
                            };
                            ctx.result(is);
                        }
                    });

                    app.ws("/ws", ws -> {
                        ws.onConnect(ctx -> {
                            ctx.session.setIdleTimeout(Duration.ofMinutes(60));
                            webClients.add(ctx);
                        });
                        ws.onClose(ctx -> webClients.remove(ctx));
                    });
                }
            } catch (Exception e) {}
            synchronized (HeadlessPeer.class) { HeadlessPeer.class.wait(); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void serveFileStandard(io.javalin.http.Context ctx, File file) throws IOException {
        ctx.contentType("video/mp4");
        ctx.writeSeekableStream(new FileInputStream(file), "video/mp4");
    }

    public static void broadcastToWeb(String resultType, String fileName, long size, String hash, String peerIp) {
        searchResultsCache.computeIfAbsent(hash, k -> ConcurrentHashMap.newKeySet()).add(peerIp);
        sendJson(String.format("{\"type\":\"RESULT\", \"resultType\":\"%s\", \"fileName\":\"%s\", \"size\":%d, \"hash\":\"%s\", \"peerIp\":\"%s\"}", resultType, fileName, size, hash, peerIp));
    }

    private static String getRealIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && addr.getHostAddress().startsWith("172.")) return addr.getHostAddress();
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) { return "127.0.0.1"; }
    }

    public static void broadcastLog(String message) {
        sendJson(String.format("{\"type\":\"LOG\", \"message\":\"%s\"}", message));
    }

    public static void broadcastProgress(String hash, long current, long total, String status) {
        int percent = (total > 0) ? (int)((current * 100) / total) : 0;
        sendJson(String.format("{\"type\":\"PROGRESS\", \"hash\":\"%s\", \"current\":%d, \"total\":%d, \"percent\":%d, \"status\":\"%s\"}", hash, current, total, percent, status));
    }

    private static void sendJson(String json) {
        if (webClients.isEmpty()) return;
        for (WsContext ctx : webClients) { if (ctx.session.isOpen()) ctx.send(json); }
    }
}