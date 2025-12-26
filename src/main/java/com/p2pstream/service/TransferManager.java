package com.p2pstream.service;

import com.p2pstream.common.AppLogger;
import com.p2pstream.common.Constants;
import com.p2pstream.domain.VideoMetadata;
import com.p2pstream.network.TcpFileDownloader;

import java.net.InetAddress;
import java.util.concurrent.*;

public final class TransferManager {

    public record Progress(VideoMetadata meta, int haveChunks, int totalChunks, double percent) {}

    private final EventBus bus;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final TcpFileDownloader downloader = new TcpFileDownloader();

    public TransferManager(EventBus bus) {
        this.bus = bus;
    }

    public void downloadAllChunks(VideoMetadata meta, InetAddress sourceIp) {
        int total = meta.getTotalChunks();
        bus.emit(EventBus.EventType.LOG, "Starting download: " + meta.getFileName() + " chunks=" + total);

        CompletionService<Integer> cs = new ExecutorCompletionService<>(pool);

        for (int i = 0; i < total; i++) {
            final int idx = i;
            cs.submit(() -> {
                downloader.downloadChunk(sourceIp, Constants.TCP_PORT, meta.getFileHashSha256(), idx);
                return idx;
            });
        }

        pool.execute(() -> {
            int done = 0;
            while (done < total) {
                try {
                    Future<Integer> f = cs.take();
                    f.get();
                    done++;
                    double pct = (done * 100.0) / total;
                    bus.emit(EventBus.EventType.TRANSFER_PROGRESS, new Progress(meta, done, total, pct));
                } catch (Exception e) {
                    AppLogger.error("Download task failed", e);
                    bus.emit(EventBus.EventType.LOG, "Download error: " + e.getMessage());
                    // basit davranış: devam et
                    done++;
                }
            }
            bus.emit(EventBus.EventType.LOG, "Download finished: " + meta.getFileName());
        });
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
