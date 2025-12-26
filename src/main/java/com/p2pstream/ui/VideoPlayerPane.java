package com.p2pstream.ui;

import javafx.embed.swing.SwingNode;
import javafx.scene.Node;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;

public final class VideoPlayerPane {

    private final SwingNode swingNode = new SwingNode();
    private EmbeddedMediaPlayerComponent mediaPlayerComponent;

    public VideoPlayerPane() {
        createSwingContent();
    }

    private void createSwingContent() {
        SwingUtilities.invokeLater(() -> {
            mediaPlayerComponent = new EmbeddedMediaPlayerComponent();

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(mediaPlayerComponent, BorderLayout.CENTER);

            swingNode.setContent(panel);
        });
    }

    public Node getNode() {
        return swingNode;
    }

    // later:
    // public void playFile(Path bufferFile) { mediaPlayerComponent.mediaPlayer().media().play(bufferFile.toString()); }
}
