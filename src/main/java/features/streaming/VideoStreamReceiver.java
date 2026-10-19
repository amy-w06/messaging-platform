package features.streaming;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;

/**
 * The receiver of the video stream. Connects directly to the sender's socket and reads
 * each incoming JPEG frame, reconstructing it as a BufferedImage and displaying it in a Swing
 * window to give the live playback effect. We use readFully rather than a regular read because
 * TCP can deliver data in chunks and a partial read would give us a corrupt JPEG that fails to decode.
 * When the sender sends -1 as the length the stream is over and the window updates to show that
 */
public class VideoStreamReceiver extends Thread {

    private final String senderIp;
    private final int senderPort;
    private final String windowTitle;
    private volatile boolean running = true;  // volatile for thread safe

    public VideoStreamReceiver(String senderIp, int senderPort, String windowTitle) {
        this.senderIp = senderIp;
        this.senderPort = senderPort;
        this.windowTitle = windowTitle;
        setDaemon(true);
    }

    @Override
    public void run() {
        // build the window on the EDT as swing requires
        JFrame frame = new JFrame(windowTitle);
        JLabel videoLabel = new JLabel("Connecting to stream...", SwingConstants.CENTER);
        videoLabel.setBackground(Color.BLACK);
        videoLabel.setForeground(new Color(180, 180, 180));
        videoLabel.setOpaque(true);

        frame.add(videoLabel);
        frame.setSize(854, 480);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { running = false; }
        });  // stop the call when close
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // connects to sender make new socket tcp usign ip
        try (Socket socket = new Socket(senderIp, senderPort);
             DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

            System.out.println("[Stream] Connected to sender — receiving frames...");

            // sent as [length][bytes] for each image
            int len;
            while (running && (len = dis.readInt()) != -1) {
                byte[] bytes = new byte[len];
                dis.readFully(bytes);  // readFully makes sure we get all the bytes before trying to decode

                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img == null) continue;  // skip bad frames

                // scales and resizes for window
                int w = Math.max(videoLabel.getWidth(), 854);
                int h = Math.max(videoLabel.getHeight(), 480);
                Image scaled = img.getScaledInstance(w, h, Image.SCALE_FAST);

                SwingUtilities.invokeLater(() -> {  // updates ui
                    videoLabel.setIcon(new ImageIcon(scaled));
                    videoLabel.setText("");
                    frame.repaint();
                });
            }

            // end call
            SwingUtilities.invokeLater(() -> {
                videoLabel.setIcon(null);
                videoLabel.setText("Stream ended.");
            });
            System.out.println("[Stream] Playback complete.");

        } catch (Exception e) {
            if (running) System.out.println("[Stream] Receiver error: " + e.getMessage());
        }
    }
}