package com.p2pstream.net.udp;

import java.net.*;
import java.util.Enumeration;
import java.util.List;

public final class UdpSender {

    /**
     * Belirtilen hedef IP ve Porta tek bir paket gönderir (Unicast).
     */
    public void send(byte[] payload, InetAddress targetIp, int targetPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true); // Gerekirse broadcast'e izin ver
            DatagramPacket packet = new DatagramPacket(payload, payload.length, targetIp, targetPort);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("Paket gönderilemedi: " + targetIp + " -> " + e.getMessage());
        }
    }

    /**
     * BULUNDUĞU AĞDAKİ SUBNET MASK'E BAKARAK DISCOVER YOLLAR.
     * Tüm ağ arayüzlerini (Wi-Fi, Ethernet) sırayla gezer ve her birinin
     * broadcast adresini hesaplayıp paketi oraya atar.
     */
    public void sendToAllLocalSubnets(byte[] payload, int targetPort) {
        try {
            // 1. Bilgisayardaki tüm ağ arayüzlerini (Interface) getir
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // Kapalı olanları (Down) veya Loopback (127.0.0.1) olanları atla
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                // 2. Bu arayüze bağlı IP adreslerini ve Subnet bilgilerini al
                List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();

                for (InterfaceAddress addr : interfaceAddresses) {
                    // Subnet Mask kullanılarak hesaplanmış Broadcast adresi
                    InetAddress broadcast = addr.getBroadcast();

                    // Eğer broadcast adresi null değilse (IPv6'da genelde null olur, IPv4'te doludur)
                    if (broadcast != null) {
                        System.out.println("Subnet Taraması: " + networkInterface.getDisplayName()
                                + " üzerinden " + broadcast + " adresine gönderiliyor...");

                        // Hesaplanmış broadcast adresine gönder
                        send(payload, broadcast, targetPort);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Subnet taraması sırasında hata: " + e.getMessage());
            e.printStackTrace();
        }
    }
}