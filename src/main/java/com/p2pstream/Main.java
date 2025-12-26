package com.p2pstream;

import com.p2pstream.model.Constants;
import com.p2pstream.model.MessageType;
import com.p2pstream.net.udp.*;
import com.p2pstream.service.FileService;
import com.p2pstream.service.PacketCodec;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.InetAddress;
import java.util.UUID;

public class Main {

    public static void main(String[] args) {
        try {
            System.out.println("=== P2P Peer Başlatılıyor ===");

            // 1. Dosya Servisi Hazırlığı
            // Docker volume ile bağlanan klasör: /app/shared_videos
            String rootPath = "shared_videos";
            FileService fileService = new FileService(rootPath);

            // Mevcut dosyaları tara ve hash'le (YTlogo.png burada bulunacak)
            fileService.scanFiles();

            // Kimlik Bilgileri
            String peerId = UUID.randomUUID().toString();
            String myIp = InetAddress.getLocalHost().getHostAddress();
            int myPort = Constants.UDP_PORT;

            System.out.println("ID: " + peerId);
            System.out.println("IP: " + myIp);

            // 2. Ağ Bileşenlerinin Kurulumu
            UdpSender sender = new UdpSender();
            // Handler'a fileService'i veriyoruz ki arama gelince cevap verebilsin
            MyUdpHandler handler = new MyUdpHandler(sender, fileService, peerId, myIp, myPort);
            UdpServer server = new UdpServer(Constants.UDP_PORT, handler);

            // Sunucuyu başlat
            server.start();

            // 3. Test Mantığı (INITIATOR ise Arama Yap)
            String isInitiator = System.getenv("INITIATOR");

            if ("true".equalsIgnoreCase(isInitiator)) {
                System.out.println("\n[MOD] INITIATOR (Aktif Arama)");
                System.out.println("Diğer peerlar dosyalarını hazırlasın diye 5sn bekleniyor...");
                Thread.sleep(5000);

                System.out.println(">>> ARAMA BAŞLATILIYOR (SEARCH FLOOD) <<<");

                // DEĞİŞİKLİK BURADA: Artık "video" değil, dosya adında geçen "YTlogo" kelimesini arıyoruz.
                String searchQuery = "YTlogo";
                System.out.println("Aranan kelime: '" + searchQuery + "'");

                // SEARCH paketi hazırla
                Packet searchPacket = Packet.simpleText(
                        MessageType.SEARCH,
                        myIp,
                        myPort,
                        5, // TTL
                        searchQuery // Payload: "YTlogo"
                );

                sender.sendToAllLocalSubnets(PacketCodec.encode(searchPacket), Constants.UDP_PORT);

            } else {
                System.out.println("\n[MOD] LISTENER (Pasif Dinleme)");
                System.out.println("Dosyalar paylaşıma açıldı. Search isteklerine cevap verilecek...");
            }

            // Programın kapanmasını engelle
            synchronized (server) {
                server.wait();
            }

        } catch (Exception e) {
            System.err.println("Uygulama hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }
}