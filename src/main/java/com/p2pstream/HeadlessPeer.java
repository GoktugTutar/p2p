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

    // SEARCH SONUÇLARI CACHE
    public static final ConcurrentHashMap<String, Set<String>> searchResultsCache = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            System.out.println(">>> P2P CLIENT SYSTEM STARTED...");

            // 1. Dosya Sistemi Hazırlığı
            FileService fileService = new FileService("shared_videos");
            fileService.scanFiles();

            String peerId = UUID.randomUUID().toString();

            // --- DÜZELTME 1: Gerçek IP Adresini Bul (Docker Ağı İçin) ---
            String myIp = getRealIp();
            System.out.println("✅ TESPİT EDİLEN IP ADRESİM: " + myIp);

            int myPort = Constants.UDP_PORT;

            // 2. Ağ Başlatma
            UdpSender udpSender = new UdpSender();
            MyUdpHandler udpHandler = new MyUdpHandler(udpSender, fileService, peerId, myIp, myPort);

            new Thread(() -> { try { new UdpServer(Constants.UDP_PORT, udpHandler).start(); } catch (Exception e) {} }).start();
            new TcpServer().start();

            // 3. Web GUI
            try {
                if (Files.exists(Paths.get("/app/web"))) {
                    Javalin app = Javalin.create(config -> {
                        config.staticFiles.add("/app/web", Location.EXTERNAL);
                    }).start(8080);

                    // SEARCH API
                    app.post("/api/search", ctx -> {
                        String query = ctx.queryParam("q");
                        searchResultsCache.clear();
                        broadcastLog("Flooding network for: '" + query + "'");
                        Packet p = Packet.simpleText(MessageType.SEARCH, myIp, myPort, 5, query);
                        udpSender.sendToAllLocalSubnets(PacketCodec.encode(p), Constants.UDP_PORT);
                        ctx.result("OK");
                    });

                    // DOWNLOAD API
                    app.post("/api/download", ctx -> {
                        String fileName = ctx.queryParam("file");
                        String fileHash = ctx.queryParam("hash");
                        long size = Long.parseLong(ctx.queryParam("size"));

                        Set<String> owners = searchResultsCache.getOrDefault(fileHash, new HashSet<>());
                        String fallbackIp = ctx.queryParam("ip");
                        if (fallbackIp != null) owners.add(fallbackIp);

                        if (owners.isEmpty()) {
                            ctx.status(400).result("No peers found.");
                            return;
                        }

                        // Kendi IP'mizi listeden çıkaralım (Kendimizden indirmeyelim)
                        owners.remove(myIp);

                        if (owners.isEmpty()) {
                            ctx.status(400).result("Dosya sadece bende var, indirme gereksiz.");
                            return;
                        }

                        List<String> sourceIps = new ArrayList<>(owners);
                        ParallelDownloader downloader = new ParallelDownloader(fileName, fileHash, size, sourceIps);
                        new Thread(downloader).start();

                        ctx.result("Download Started");
                    });

                    // STREAM API
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

                    // --- DÜZELTME 2: WebSocket Timeout Süresini Uzat ---
                    app.ws("/ws", ws -> {
                        ws.onConnect(ctx -> {
                            // 1 Saat (60 dakika) boyunca işlem olmasa bile kopma
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

    // --- YARDIMCI: DOĞRU IP BULMA ---
    private static String getRealIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Docker genelde eth0 kullanır, loopback (127.0.0.1) hariç tut
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Sadece IPv4 ve 172.x ile başlayan (Docker ağı) adresini al
                    if (addr instanceof Inet4Address && addr.getHostAddress().startsWith("172.")) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress(); // Bulamazsa eskiye dön
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // --- DİĞER METODLAR AYNI ---
    public static void broadcastToWeb(String fileName, long size, String hash, String peerIp) {
        searchResultsCache.computeIfAbsent(hash, k -> ConcurrentHashMap.newKeySet()).add(peerIp);
        sendJson(String.format("{\"type\":\"RESULT\", \"fileName\":\"%s\", \"size\":%d, \"hash\":\"%s\", \"peerIp\":\"%s\"}", fileName, size, hash, peerIp));
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