package com.p2pstream.gui;

import com.p2pstream.model.Constants;
import com.p2pstream.model.MessageType;
import com.p2pstream.net.udp.Packet;
import com.p2pstream.net.udp.UdpSender;
import com.p2pstream.service.FileService;
import com.p2pstream.service.PacketCodec;

import javax.swing.*;
import java.awt.*;

/**
 * Basit Swing GUI'si. Aramayı manuel tetiklemek veya keşif flood'u başlatmak için kullanılır.
 */
public class GuiPeerApp {
    private final String peerId;
    private final String myIp;
    private final int myPort;
    private final UdpSender sender;
    private final FileService fileService;

    private final JTextArea logArea = new JTextArea();
    private final JTextField searchField = new JTextField();
    private final JSpinner ttlSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));

    public GuiPeerApp(String peerId, String myIp, int myPort, UdpSender sender,
                      FileService fileService, String defaultSearchQuery) {
        this.peerId = peerId;
        this.myIp = myIp;
        this.myPort = myPort;
        this.sender = sender;
        this.fileService = fileService;
        this.searchField.setText(defaultSearchQuery);
    }

    public void startUi() {
        SwingUtilities.invokeLater(this::buildUi);
    }

    private void buildUi() {
        JFrame frame = new JFrame("P2P GUI Peer (" + peerId.substring(0, 8) + ")");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(720, 480);

        JPanel topPanel = new JPanel(new GridLayout(3, 1));
        topPanel.add(new JLabel("Kimlik: " + peerId));
        topPanel.add(new JLabel("Dinlenen IP/Port: " + myIp + ":" + myPort));
        topPanel.add(new JLabel("Paylaşım klasörü: " + fileService.toString()));

        JPanel controls = new JPanel(new BorderLayout(8, 8));
        JPanel searchPanel = new JPanel(new BorderLayout(8, 8));
        searchPanel.add(new JLabel("Arama ifadesi:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JButton searchButton = new JButton("SEARCH flood");
        searchButton.addActionListener(e -> triggerSearch());
        searchPanel.add(searchButton, BorderLayout.EAST);

        JPanel discoverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        discoverPanel.add(new JLabel("TTL:"));
        discoverPanel.add(ttlSpinner);
        JButton discoverButton = new JButton("DISCOVER flood");
        discoverButton.addActionListener(e -> triggerDiscover());
        discoverPanel.add(discoverButton);

        controls.add(searchPanel, BorderLayout.NORTH);
        controls.add(discoverPanel, BorderLayout.SOUTH);

        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(controls, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.SOUTH);

        frame.setVisible(true);
        appendLog("GUI peer hazır. Discovery veya Search flood'u tetikle.");
    }

    private void triggerDiscover() {
        try {
            int ttl = (Integer) ttlSpinner.getValue();
            Packet discover = Packet.simpleText(MessageType.DISCOVER, myIp, myPort, ttl, peerId);
            sender.sendToAllLocalSubnets(PacketCodec.encode(discover), Constants.UDP_PORT);
            appendLog("DISCOVER yayını gönderildi. TTL=" + ttl);
        } catch (Exception ex) {
            appendLog("DISCOVER gönderilemedi: " + ex.getMessage());
        }
    }

    private void triggerSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            appendLog("Lütfen arama için bir ifade girin.");
            return;
        }
        try {
            int ttl = (Integer) ttlSpinner.getValue();
            Packet searchPacket = Packet.simpleText(MessageType.SEARCH, myIp, myPort, ttl, query);
            sender.sendToAllLocalSubnets(PacketCodec.encode(searchPacket), Constants.UDP_PORT);
            appendLog("SEARCH yayını gönderildi: '" + query + "' (TTL=" + ttl + ")");
        } catch (Exception ex) {
            appendLog("SEARCH gönderilemedi: " + ex.getMessage());
        }
    }

    public java.util.function.Consumer<String> uiLogger() {
        return this::appendLog;
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
