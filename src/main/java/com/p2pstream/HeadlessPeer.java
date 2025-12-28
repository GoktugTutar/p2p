package com.p2pstream;

import com.p2pstream.model.Constants;
import com.p2pstream.model.MessageType;
import com.p2pstream.net.udp.*;
import com.p2pstream.service.FileService;
import com.p2pstream.service.PacketCodec;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeadlessPeer {

    public static final Set<WsContext> webClients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        try {
            System.out.println(">>> HEADLESS P2P (PROD MODE) BAŞLATILIYOR...");

            // 1. Dosya ve Ağ Hazırlığı
            String rootPath = "shared_videos";
            FileService fileService = new FileService(rootPath);
            fileService.scanFiles(); // Klasördeki tüm dosyaları bul ve hash'le

            String peerId = UUID.randomUUID().toString();
            String myIp = InetAddress.getLocalHost().getHostAddress();
            int myPort = Constants.UDP_PORT;

            // 2. UDP Sunucusu (P2P Ağı)
            UdpSender udpSender = new UdpSender();
            MyUdpHandler udpHandler = new MyUdpHandler(udpSender, fileService, peerId, myIp, myPort);

            new Thread(() -> {
                try {
                    new UdpServer(Constants.UDP_PORT, udpHandler).start();
                } catch (Exception e) { e.printStackTrace(); }
            }).start();

            // 3. WEB GUI (Sadece Gateway Peer için arayüz sunar)
            try {
                String webDir = "/app/web";
                // Klasör kontrolü
                if (Files.exists(Paths.get(webDir))) {
                    Javalin app = Javalin.create(config -> {
                        config.staticFiles.add(webDir, Location.EXTERNAL);
                    }).start(8080);

                    System.out.println("✅ GUI ARAYÜZÜ AKTİF: http://localhost:8080");

                    // --- API: ARAMA YAP (UDP Flood Başlatır) ---
                    app.post("/api/search", ctx -> {
                        String query = ctx.queryParam("q");
                        System.out.println("🔍 Kullanıcı Araması Başlatıldı: " + query);

                        // Kullanıcı arayüzüne log düş
                        broadcastLog("Arama paketi ağa yayılıyor: '" + query + "'");

                        Packet searchPacket = Packet.simpleText(
                                MessageType.SEARCH, myIp, myPort, 5, query
                        );
                        // Tüm ağa yay (Flooding)
                        udpSender.sendToAllLocalSubnets(PacketCodec.encode(searchPacket), Constants.UDP_PORT);
                        ctx.result("OK");
                    });

                    // --- API: İNDİRME İSTEĞİ (TCP Hazırlık) ---
                    app.post("/api/download", ctx -> {
                        String targetIp = ctx.queryParam("ip");
                        String fileName = ctx.queryParam("file");

                        System.out.println("⬇️ İNDİRME EMRİ: " + fileName + " @ " + targetIp);
                        broadcastLog("TCP İsteği kuyruğa alındı: " + fileName);

                        ctx.result("Request Received");
                    });

                    // --- API: VİDEO STREAM ---
                    app.get("/api/watch/{filename}", ctx -> {
                        String fName = ctx.pathParam("filename");
                        java.io.File videoFile = new java.io.File("/app/shared_videos/" + fName);

                        if (videoFile.exists()) {
                            // Javalin 6.x Stream Formatı
                            ctx.writeSeekableStream(new java.io.FileInputStream(videoFile), "video/mp4");
                        } else {
                            ctx.status(404).result("Dosya bulunamadı");
                        }
                    });

                    // WebSocket (Canlı Bildirimler)
                    app.ws("/ws", ws -> {
                        ws.onConnect(ctx -> webClients.add(ctx));
                        ws.onClose(ctx -> webClients.remove(ctx));
                    });
                } else {
                    System.out.println("ℹ️ Bu bir Worker Node (Web arayüzü yok).");
                }
            } catch (Exception e) {
                System.out.println("⚠️ GUI başlatılamadı: " + e.getMessage());
            }

            synchronized (HeadlessPeer.class) { HeadlessPeer.class.wait(); }

        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void broadcastToWeb(String fileName, long size, String hash, String peerIp) {
        if (webClients.isEmpty()) return;
        String json = String.format(
                "{\"type\":\"RESULT\", \"fileName\":\"%s\", \"size\":%d, \"hash\":\"%s\", \"peerIp\":\"%s\"}",
                fileName, size, hash, peerIp
        );
        for (WsContext ctx : webClients) ctx.send(json);
    }

    public static void broadcastLog(String message) {
        if (webClients.isEmpty()) return;
        String json = String.format("{\"type\":\"LOG\", \"message\":\"%s\"}", message);
        for (WsContext ctx : webClients) ctx.send(json);
    }
}