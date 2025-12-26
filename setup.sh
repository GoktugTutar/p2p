# 1. Maven yapılandırma dosyasını oluştur (eğer yoksa)
touch pom.xml

# 2. Klasör hiyerarşisini oluştur (Tek seferde tüm paketleri açar)
mkdir -p src/main/java/com/p2pstream/app
mkdir -p src/main/java/com/p2pstream/common
mkdir -p src/main/java/com/p2pstream/domain
mkdir -p src/main/java/com/p2pstream/network
mkdir -p src/main/java/com/p2pstream/service
mkdir -p src/main/java/com/p2pstream/ui
mkdir -p src/main/resources/ui
mkdir -p src/test/java

# 3. app paketindeki dosyalar (JavaFX başlatıcıları)
touch src/main/java/com/p2pstream/app/AppLauncher.java
touch src/main/java/com/p2pstream/app/MainApp.java

# 4. common paketindeki dosyalar (Yardımcı araçlar)
touch src/main/java/com/p2pstream/common/AppLogger.java
touch src/main/java/com/p2pstream/common/ConfigLoader.java
touch src/main/java/com/p2pstream/common/Constants.java

# 5. domain paketindeki dosyalar (Veri modelleri)
touch src/main/java/com/p2pstream/domain/ChunkMap.java
touch src/main/java/com/p2pstream/domain/Packet.java
touch src/main/java/com/p2pstream/domain/PeerInfo.java
touch src/main/java/com/p2pstream/domain/VideoMetadata.java

# 6. network paketindeki dosyalar (Ağ iletişimi)
touch src/main/java/com/p2pstream/network/PacketDispatcher.java
touch src/main/java/com/p2pstream/network/TcpFileDownloader.java
touch src/main/java/com/p2pstream/network/TcpFileServer.java
touch src/main/java/com/p2pstream/network/UdpTransceiver.java

# 7. service paketindeki dosyalar (İş mantığı)
touch src/main/java/com/p2pstream/service/EventBus.java
touch src/main/java/com/p2pstream/service/PeerDiscoveryService.java
touch src/main/java/com/p2pstream/service/SearchService.java
touch src/main/java/com/p2pstream/service/TransferManager.java

# 8. ui paketindeki dosyalar (JavaFX Kontrolcüleri)
touch src/main/java/com/p2pstream/ui/MainWindowController.java
touch src/main/java/com/p2pstream/ui/VideoPlayerPane.java

# 9. resources (FXML arayüz dosyası)
touch src/main/resources/ui/main_window.fxml

echo "P2P Stream proje yapısı başarıyla oluşturuldu!"
