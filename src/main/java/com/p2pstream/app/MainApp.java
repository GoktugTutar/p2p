package com.p2pstream.app;

import com.p2pstream.common.AppLogger;
import com.p2pstream.common.ConfigLoader;
import com.p2pstream.network.UdpTransceiver;
import com.p2pstream.service.*;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // shared kernel
        EventBus bus = new EventBus();
        UdpTransceiver udp = new UdpTransceiver((pkt, ip, port) -> {
            // dispatcher fan-out will be wired by controller after load (simple route)
        });

        // Load UI
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main_window.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 720);

        com.p2pstream.ui.MainWindowController controller = loader.getController();

        // Real dispatcher wiring: create services and set dispatcher chain
        PeerDiscoveryService discovery = new PeerDiscoveryService(bus, udp);
        SearchService search = new SearchService(bus, udp);
        TransferManager transfer = new TransferManager(bus);

        // single dispatcher that forwards to services
        UdpTransceiver udp2 = new UdpTransceiver((packet, fromIp, fromPort) -> {
            discovery.onPacket(packet, fromIp, fromPort);
            search.onPacket(packet, fromIp, fromPort);
        });
        // close old udp, use new
        udp.shutdown();

        controller.bootstrap(bus, udp2, discovery, search, transfer);

        stage.setTitle("Serverless P2P Stream & Share");
        stage.setScene(scene);
        stage.show();

        udp2.start();
        bus.emit(EventBus.EventType.LOG, "GUI started. mode=" + ConfigLoader.getInstance().getMode());
    }

    @Override
    public void stop() {
        AppLogger.info("JavaFX stopping...");
    }
}
