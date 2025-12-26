package com.p2pstream.service;

import com.p2pstream.model.VideoMetadata;
import com.p2pstream.model.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class FileService {

    private final Path rootFolder;
    // Hash -> Metadata eşleşmesi (Hızlı erişim için)
    private final Map<String, VideoMetadata> localFiles = new ConcurrentHashMap<>();

    public FileService(String rootFolderPath) {
        this.rootFolder = Paths.get(rootFolderPath);
        initialize();
    }

    private void initialize() {
        try {
            // 1. Klasör yoksa oluştur
            if (!Files.exists(rootFolder)) {
                Files.createDirectories(rootFolder);
                System.out.println("Root klasörü oluşturuldu: " + rootFolder.toAbsolutePath());
            } else {
                System.out.println("Root klasörü mevcut: " + rootFolder.toAbsolutePath());
                // Mevcut dosyaları tara
                scanFiles();
            }
        } catch (IOException e) {
            System.err.println("Dosya sistemi hatası: " + e.getMessage());
        }
    }

    // Klasördeki tüm dosyaları bul ve hash'le
    public void scanFiles() {
        try (Stream<Path> paths = Files.walk(rootFolder)) {
            paths.filter(Files::isRegularFile)
                    .forEach(this::processFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processFile(Path path) {
        try {
            long size = Files.size(path);
            String fileName = path.getFileName().toString();

            // Chunk sayısını hesapla (Boyut / 256KB)
            int chunks = (int) Math.ceil((double) size / (256 * 1024));

            // Hash hesapla (Zaman alabilir, büyük dosyalar için asenkron yapılabilir)
            String hash = calculateSha256(path);

            VideoMetadata meta = new VideoMetadata(hash, fileName, size, chunks);
            localFiles.put(hash, meta); // Hash'i anahtar olarak kullanıyoruz

            System.out.println("Dosya İndekslendi: " + meta);

        } catch (Exception e) {
            System.err.println("Dosya işlenemedi: " + path + " -> " + e.getMessage());
        }
    }

    // PDF Gereksinimi: Hashing Mechanism (SHA-256)
    private String calculateSha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192]; // 8KB buffer ile oku
            int n = 0;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        // Byte array'i Hex String'e çevir
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest.digest()) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Arama yaparken bu listeyi kullanacağız
    public List<VideoMetadata> searchFiles(String queryName) {
        List<VideoMetadata> results = new ArrayList<>();
        for (VideoMetadata meta : localFiles.values()) {
            if (meta.getFileName().toLowerCase().contains(queryName.toLowerCase())) {
                results.add(meta);
            }
        }
        return results;
    }

    // Hash ile dosya bulma (İndirme isteği geldiğinde lazım olacak)
    public VideoMetadata getFileByHash(String hash) {
        return localFiles.get(hash);
    }
}