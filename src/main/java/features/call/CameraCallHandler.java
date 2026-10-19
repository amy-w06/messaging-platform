package features.call;

import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;

/**
 * Manages a two-way P2P camera video call between two clients. A video call is
 * two video streams going in opposite directions simultaneously, so this class creates two
 * threads, one to capture and send our own webcam feed out, and one to receive the other
 * person's feed and display it. Both feeds are shown side by side in a single Swing window.
 *
 * Uses the sarxos webcam-capture library instead of OpenCV for camera access. This avoids
 * the MSMF bugs that plague OpenCVFrameGrabber on Windows with USB cameras. The library
 * uses its own native driver (based on OpenIMAJ/BridJ) and returns BufferedImages directly
 * so there is no frame conversion overhead.
 *
 * Each side opens their own ServerSocket on a port they chose and connects outward to the
 * other person's port. The chat server's only involvement was passing each person the other's
 * IP address and port number, so they knew where to connect. After that the server plays no
 * further part and all video data flows directly between the two machines.
 *
 * The send thread retries the outward connection a number of times because both sides start
 * at almost the same moment and the other person's ServerSocket might not be open yet on the
 * first attempt. Closing the call window sets running to false which stops both threads cleanly.
 */
public class CameraCallHandler {

    private final int myListenPort;
    private volatile boolean running = false;

    private JFrame callWindow;
    private JLabel localLabel;
    private JLabel remoteLabel;

    public CameraCallHandler(int myListenPort) {
        this.myListenPort = myListenPort;
    }

    public void startCall(String remoteIp, int remoteListenPort) {
        running = true;
        buildCallWindow();
        new Thread(this::receiveVideo,                          "call-receive").start();
        new Thread(() -> sendVideo(remoteIp, remoteListenPort), "call-send").start();
    }

    private void buildCallWindow() {
        callWindow = new JFrame("Video Call");
        callWindow.setLayout(new GridLayout(1, 2, 4, 0));
        callWindow.setSize(1200, 620);
        callWindow.getContentPane().setBackground(Color.BLACK);
        callWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        callWindow.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                running = false;
                System.out.println("[Call] Call ended.");
            }
        });

        localLabel  = makeVideoPanel("You");
        remoteLabel = makeVideoPanel("Remote");

        callWindow.add(wrapPanel(localLabel,  "You"));
        callWindow.add(wrapPanel(remoteLabel, "Remote"));
        callWindow.setLocationRelativeTo(null);
        callWindow.setVisible(true);
    }

    private JLabel makeVideoPanel(String placeholder) {
        JLabel label = new JLabel(placeholder, SwingConstants.CENTER);
        label.setBackground(Color.BLACK);
        label.setForeground(new Color(160, 160, 160));
        label.setOpaque(true);
        return label;
    }

    private JPanel wrapPanel(JLabel videoLabel, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.BLACK);

        JLabel titleLabel = new JLabel("  " + title, SwingConstants.LEFT);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBackground(new Color(25, 25, 25));
        titleLabel.setOpaque(true);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(videoLabel, BorderLayout.CENTER);
        return panel;
    }

    private void receiveVideo() {
        try (ServerSocket ss = new ServerSocket(myListenPort)) {
            System.out.println("[Call] Waiting for remote video on port " + myListenPort + "...");
            Socket peer = ss.accept();
            DataInputStream dis = new DataInputStream(new BufferedInputStream(peer.getInputStream()));
            System.out.println("[Call] Remote video stream connected.");

            int len;
            while (running && (len = dis.readInt()) != -1) {
                byte[] bytes = new byte[len];
                dis.readFully(bytes);

                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img == null) continue;

                Image scaled = scaleToLabel(img, remoteLabel);
                SwingUtilities.invokeLater(() -> {
                    remoteLabel.setIcon(new ImageIcon(scaled));
                    remoteLabel.setText("");
                });
            }
        } catch (Exception e) {
            if (running) System.out.println("[Call] Receive error: " + e.getMessage());
        }
    }

    private void sendVideo(String remoteIp, int remotePort) {

        // STEP 1: open the webcam BEFORE connecting to the remote peer
        System.out.println("[Call] Opening webcam...");
        Webcam webcam = Webcam.getDefault();

        if (webcam == null) {
            System.out.println("[Call] No webcam found. Call cannot proceed.");
            SwingUtilities.invokeLater(() -> localLabel.setText("No camera found"));
            return;
        }

        System.out.println("[Call] Found webcam: " + webcam.getName());
        webcam.setViewSize(new Dimension(320, 240));
        webcam.open();

        // verify we can get a frame
        BufferedImage testImg = webcam.getImage();
        if (testImg == null) {
            System.out.println("[Call] Webcam opened but returned no image. Call cannot proceed.");
            webcam.close();
            SwingUtilities.invokeLater(() -> localLabel.setText("Camera error"));
            return;
        }
        System.out.println("[Call] Webcam verified — " + testImg.getWidth() + "x" + testImg.getHeight());

        // STEP 2: connect to the remote peer
        Socket peer = null;
        for (int attempt = 0; attempt < 15 && running; attempt++) {
            try {
                peer = new Socket(remoteIp, remotePort);
                break;
            } catch (IOException e) {
                System.out.println("[Call] Waiting for remote to be ready... (" + (attempt + 1) + "/15)");
                try { Thread.sleep(500); } catch (InterruptedException ex) { return; }
            }
        }

        if (peer == null) {
            System.out.println("[Call] Could not reach remote client. Call failed.");
            webcam.close();
            running = false;
            return;
        }

        // STEP 3: stream frames
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(peer.getOutputStream()))) {
            System.out.println("[Call] Webcam started — streaming to remote.");

            while (running) {
                BufferedImage img = webcam.getImage();
                if (img == null) continue;

                // show our own feed locally so we can see ourselves
                Image localScaled = scaleToLabel(img, localLabel);
                SwingUtilities.invokeLater(() -> {
                    localLabel.setIcon(new ImageIcon(localScaled));
                    localLabel.setText("");
                });

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", baos);
                byte[] bytes = baos.toByteArray();

                dos.writeInt(bytes.length);
                dos.write(bytes);
                dos.flush();

                Thread.sleep(50); // ~20fps, comfortable for a video call
            }

            dos.writeInt(-1);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running) System.out.println("[Call] Send error: " + e.getMessage());
        } finally {
            webcam.close();
        }
    }

    private Image scaleToLabel(BufferedImage img, JLabel label) {
        int w = label.getWidth()  > 0 ? label.getWidth()  : 580;
        int h = label.getHeight() > 0 ? label.getHeight() : 580;
        return img.getScaledInstance(w, h, Image.SCALE_FAST);
    }

    public void endCall() {
        running = false;
        if (callWindow != null) SwingUtilities.invokeLater(() -> callWindow.dispose());
    }
}