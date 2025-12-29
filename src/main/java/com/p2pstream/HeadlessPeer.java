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

    // 1. EKSİK OLAN KISIM BURASIYDI: Değişkeni sınıf seviyesinde tanımlıyoruz
    private static UdpServer udpServer;

    public static void main(String[] args) {
        try {
            System.setProperty("org.slf4j.simpleLogger.log.io.javalin", "off");
            System.setProperty("org.slf4j.simpleLogger.log.org.eclipse.jetty", "off");

            System.out.println(">>> P2P CLIENT SYSTEM STARTED...");

            FileService fileService = new FileService("shared_videos");
            fileService.scanFiles();

            String peerId = UUID.randomUUID().toString();
            String myIp = getRealIp();
            System.out.println("✅ TESPİT EDİLEN IP ADRESİM: " + myIp);
            int myPort = Constants.UDP_PORT;

            UdpSender udpSender = new UdpSender();
            MyUdpHandler udpHandler = new MyUdpHandler(udpSender, fileService, peerId, myIp, myPort);

            // 2. DEĞİŞİKLİK BURADA: Artık 'new Thread' içine gizlemiyoruz.
            // Değişkene atayıp başlatıyoruz. (UdpServer.start() zaten kendi thread'ini açıyor)
            udpServer = new UdpServer(Constants.UDP_PORT, udpHandler);
            udpServer.start();

            new TcpServer(fileService).start();

            // --- WEB GUI ---
            try {
                if (Files.exists(Paths.get("/app/web"))) {
                    Javalin app = Javalin.create(config -> {
                        config.staticFiles.add("/app/web", Location.EXTERNAL);
                        config.showJavalinBanner = false;
                    }).start(8080);

                    // 1. CONNECT API
                    app.post("/api/connect", ctx -> {
                        broadcastLog("Connecting... Sending HELLO to local subnet.");

                        // Server durmuşsa tekrar başlat
                        if (!udpServer.isRunning()) {
                            udpServer.start();
                            broadcastLog("UDP Server restarted.");
                        }

                        int ttl = Constants.ttl;
                        Packet p = Packet.simpleText(MessageType.HELLO, myIp, myPort, ttl, "Hi");
                        udpSender.sendToAllLocalSubnets(PacketCodec.encode(p), Constants.UDP_PORT);
                        ctx.result("HELLO Broadcast Sent");
                    });

                    // 3. EKSİK OLAN DISCONNECT API'Sİ
                    app.post("/api/disconnect", ctx -> {
                        broadcastLog("Disconnecting from network...");

                        // Artık 'udpServer' değişkenine erişebiliyoruz!
                        if (udpServer != null && udpServer.isRunning()) {
                            udpServer.stop();
                            System.out.println("⛔ UDP Server durduruldu (Disconnect isteği).");
                        }
                        ctx.result("Disconnected");
                    });

                    // 2. SEARCH API
                    app.post("/api/search", ctx -> {
                        if (!udpServer.isRunning()) {
                            ctx.status(400).result("Offline. Please connect first.");
                            return;
                        }
                        String query = ctx.queryParam("q");
                        searchResultsCache.clear();
                        broadcastLog("Searching network for: '" + query + "'");

                        Packet p = Packet.simpleText(MessageType.SEARCH, myIp, myPort, Constants.ttl, query);
                        udpSender.sendToAllLocalSubnets(PacketCodec.encode(p), Constants.UDP_PORT);
                        ctx.result("OK");
                    });

                    // 3. DOWNLOAD API
                    app.post("/api/download", ctx -> {
                        String fileName = ctx.queryParam("file");
                        String fileHash = ctx.queryParam("hash");
                        long size = Long.parseLong(ctx.queryParam("size"));

                        Set<String> owners = searchResultsCache.getOrDefault(fileHash, new HashSet<>());
                        String fallbackIp = ctx.queryParam("ip");
                        if (fallbackIp != null) owners.add(fallbackIp);
                        owners.remove(myIp);

                        if (owners.isEmpty()) {
                            ctx.status(400).result("No peers found.");
                            return;
                        }

                        List<String> sourceIps = new ArrayList<>(owners);
                        ParallelDownloader downloader = new ParallelDownloader(fileName, fileHash, size, sourceIps);
                        new Thread(downloader).start();

                        ctx.result("Download Started");
                    });

                    // 4. STREAM API
                    app.get("/api/watch/{filename}", ctx -> {
                        java.io.File buffer = new java.io.File(Constants.BUFFER_FOLDER + "/" + ctx.pathParam("filename"));
                        java.io.File shared = new java.io.File(Constants.SHARED_FOLDER + "/" + ctx.pathParam("filename"));
                        java.io.File target = buffer.exists() ? buffer : shared;
                        if (target.exists()) {
                            String fName = ctx.pathParam("filename");
                            String mime = fName.endsWith(".mp4") ? "video/mp4" : (fName.endsWith(".png") ? "image/png" : "application/octet-stream");
                            ctx.contentType(mime);
                            ctx.writeSeekableStream(new java.io.FileInputStream(target), mime);
                        } else { ctx.status(404).result("Wait for chunks..."); }
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

    public static void broadcastToWeb(String resultType, String fileName, long size, String hash, String peerIp) {
        searchResultsCache.computeIfAbsent(hash, k -> ConcurrentHashMap.newKeySet()).add(peerIp);
        sendJson(String.format(
                "{\"type\":\"RESULT\", \"resultType\":\"%s\", \"fileName\":\"%s\", \"size\":%d, \"hash\":\"%s\", \"peerIp\":\"%s\"}",
                resultType, fileName, size, hash, peerIp
        ));
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
                    if (addr instanceof Inet4Address && addr.getHostAddress().startsWith("172.")) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) { return "127.0.0.1"; }
    }

    public static void broadcastLog(String message) {
        sendJson(String.format("{\"type\":\"LOG\", \"message\":\"%s\"}", message));
    }

    public static void broadcastProgress(String hash, int percent, String status) {
        sendJson(String.format("{\"type\":\"PROGRESS\", \"hash\":\"%s\", \"percent\":%d, \"status\":\"%s\"}", hash, percent, status));
    }

    private static void sendJson(String json) {
        if (webClients.isEmpty()) return;
        for (WsContext ctx : webClients) { if (ctx.session.isOpen()) ctx.send(json); }
    }
}