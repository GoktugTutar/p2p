package com.p2pstream.ui;

import com.p2pstream.common.AppLogger;
import com.p2pstream.common.ConfigLoader;
import com.p2pstream.domain.VideoMetadata;
import com.p2pstream.network.UdpTransceiver;
import com.p2pstream.service.*;

import javafx.application.Platform;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.HexFormat;

public final class MainWindowController {

    @FXML private TextField searchField;
    @FXML private ListView<String> availableList;

    @FXML private TableView<ActiveRow> activeTable;
    @FXML private TableColumn<ActiveRow, String> colVideo;
    @FXML private TableColumn<ActiveRow, String> colPeer;
    @FXML private TableColumn<ActiveRow, String> colProgress;
    @FXML private TableColumn<ActiveRow, String> colStatus;

    @FXML private TextArea logArea;
    @FXML private ProgressBar bufferBar;

    @FXML private StackPane playerContainer;

    private final ObservableList<String> availableItems = FXCollections.observableArrayList();
    private final ObservableList<ActiveRow> activeRows = FXCollections.observableArrayList();

    private EventBus bus;
    private UdpTransceiver udp;
    private PeerDiscoveryService discovery;
    private SearchService search;
    private TransferManager transfer;

    private VideoPlayerPane playerPane;

    public record ActiveRow(String video, String peer, String progress, String status) {}

    public void bootstrap(EventBus bus,
                          UdpTransceiver udp,
                          PeerDiscoveryService discovery,
                          SearchService search,
                          TransferManager transfer) {
        this.bus = bus;
        this.udp = udp;
        this.discovery = discovery;
        this.search = search;
        this.transfer = transfer;

        // table bindings
        colVideo.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().video()));
        colPeer.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().peer()));
        colProgress.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().progress()));
        colStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().status()));
        activeTable.setItems(activeRows);

        availableList.setItems(availableItems);

        // player
        playerPane = new VideoPlayerPane();
        playerContainer.getChildren().add(playerPane.getNode());

        // bus listeners
        bus.on(EventBus.EventType.LOG, e -> uiLog(String.valueOf(e.payload())));
        bus.on(EventBus.EventType.PEER_FOUND, e -> uiLog("Peer found: " + e.payload()));
        bus.on(EventBus.EventType.SEARCH_RESULT, e -> {
            SearchService.SearchResult r = (SearchService.SearchResult) e.payload();
            String hex = HexFormat.of().formatHex(r.meta().getFileHashSha256()).substring(0, 12);
            String item = r.meta().getFileName() + " | " + r.sourceIp().getHostAddress() + " | " + hex;
            Platform.runLater(() -> availableItems.add(item));
        });
        bus.on(EventBus.EventType.TRANSFER_PROGRESS, e -> {
            TransferManager.Progress p = (TransferManager.Progress) e.payload();
            Platform.runLater(() -> {
                bufferBar.setProgress(p.percent() / 100.0);
                upsertActiveRow(p.meta().getFileName(), p.percent(), "DOWNLOADING");
            });
        });

        uiLog("Ready.");
    }

    private void uiLog(String s) {
        Platform.runLater(() -> {
            logArea.appendText(s + "\n");
        });
    }

    private void upsertActiveRow(String video, double pct, String status) {
        String prog = String.format("%.1f", pct);
        // simplistic: 1 row per video
        for (int i = 0; i < activeRows.size(); i++) {
            if (activeRows.get(i).video().equals(video)) {
                activeRows.set(i, new ActiveRow(video, "-", prog, status));
                return;
            }
        }
        activeRows.add(new ActiveRow(video, "-", prog, status));
    }

    // Menu actions
    @FXML private void onConnect() {
        uiLog("Connect: broadcasting HELLO...");
        discovery.broadcastHello();
    }

    @FXML private void onDisconnect() {
        uiLog("Disconnect: (placeholder)");
    }

    @FXML private void onSetRootFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Set Root Video Folder");
        var dir = dc.showDialog(logArea.getScene().getWindow());
        if (dir != null) {
            Path p = dir.toPath();
            uiLog("Root folder set to: " + p);
            // runtime override (config precedence is load-time; for now set directly)
            // (later we persist to config file)
            try {
                var cfg = ConfigLoader.getInstance();
                // hack: reload not needed; store via reflection is ugly; so just log for now
                AppLogger.info("Root folder chosen: " + p);
            } catch (Exception ignored) {}
        }
    }

    @FXML private void onSetBufferFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Set Buffer Folder");
        var dir = dc.showDialog(logArea.getScene().getWindow());
        if (dir != null) {
            Path p = dir.toPath();
            uiLog("Buffer folder set to: " + p);
            AppLogger.info("Buffer folder chosen: " + p);
        }
    }

    @FXML private void onExit() {
        uiLog("Exit.");
        Platform.exit();
    }

    @FXML private void onAbout() {
        uiLog("Serverless P2P Stream & Share (Java 17 / UDP Flood + TCP Chunk)");
    }

    @FXML private void onSearch() {
        String q = searchField.getText() == null ? "" : searchField.getText();
        availableItems.clear();
        search.search(q);
    }

    // (list selection) start download + play (placeholder)
    @FXML private void initialize() {
        availableList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            uiLog("Selected: " + newV);
            // In this skeleton we don't parse metadata back from list item.
            // Next step: store SearchResult objects, map selection->metadata+peer, start transfer+player.
        });
    }
}
