package client;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Opens a received video file and plays it back in a Swing window using FFmpegFrameGrabber.
 * This is called automatically by the FileReceiver in Client.java when it detects a video
 * extension after a download finishes. It reads the file frame by frame, converts each frame
 * to a BufferedImage and updates a JLabel to display it, the same approach used by
 * VideoStreamReceiver for live streams. The frame rate is read from the file itself so
 * playback runs at the correct speed. This extends Thread so the player runs in the background
 * without blocking the user from continuing to chat.
 */
public class VideoPlayerWindow extends Thread {

    private final File videoFile;
    private volatile boolean running = true;

    public VideoPlayerWindow(File videoFile) {
        this.videoFile = videoFile;
        setDaemon(true);
    }

    @Override
    public void run() {
        JFrame frame = new JFrame(videoFile.getName());
        JLabel videoLabel = new JLabel("Loading...", SwingConstants.CENTER);
        videoLabel.setBackground(Color.BLACK);
        videoLabel.setForeground(new Color(180, 180, 180));
        videoLabel.setOpaque(true);

        frame.add(videoLabel);
        frame.setSize(854, 480);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { running = false; }
        });
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        try {
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile.getAbsolutePath());
            grabber.start();

            // read the fps from the file so we play at the correct speed
            double fps = grabber.getFrameRate();
            if (fps <= 0 || fps > 120) fps = 25;
            long frameDelayMs = (long) (1000.0 / fps);

            Java2DFrameConverter converter = new Java2DFrameConverter();
            Frame f;
            int frameCount = 0;
            long playbackStart = System.currentTimeMillis();

            while (running && (f = grabber.grab()) != null) {
                if (f.image == null) continue; // skip audio frames

                BufferedImage img = converter.convert(f);
                if (img == null) continue;

                int w = Math.max(videoLabel.getWidth(), 854);
                int h = Math.max(videoLabel.getHeight(), 480);
                Image scaled = img.getScaledInstance(w, h, Image.SCALE_FAST);

                SwingUtilities.invokeLater(() -> {
                    videoLabel.setIcon(new ImageIcon(scaled));
                    videoLabel.setText("");
                    frame.repaint();
                });

                // throttle to the source frame rate, same as VideoStreamSender does
                frameCount++;
                long expected = playbackStart + (frameCount * frameDelayMs);
                long now = System.currentTimeMillis();
                if (expected > now) Thread.sleep(expected - now);
            }

            grabber.stop();
            SwingUtilities.invokeLater(() -> {
                videoLabel.setIcon(null);
                videoLabel.setText("Playback finished.");
            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("[Player] Error playing video: " + e.getMessage());
        }
    }
}