package client;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Opens a received image file in a Swing window automatically after it finishes downloading.
 * This is called by the FileReceiver in Client.java when it detects an image extension so the
 * user does not have to go and find the file manually — it just appears on screen. The image
 * is scaled down to fit 80% of the screen if it is larger than that, but never scaled up beyond
 * its natural size so small images stay sharp. The show method is static so it can be called
 * directly without needing to create an instance.
 */
public class ImageViewer {

    public static void show(File file) {
        SwingUtilities.invokeLater(() -> {
            try {
                BufferedImage img = ImageIO.read(file);
                if (img == null) {
                    System.out.println("[Viewer] Can't display — unrecognised image format: " + file.getName());
                    return;
                }

                // scale down to fit the screen, but don't scale up beyond the natural size
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                int maxW = (int) (screen.width  * 0.8);
                int maxH = (int) (screen.height * 0.8);
                double scale = Math.min(
                        (double) maxW / img.getWidth(),
                        (double) maxH / img.getHeight()
                );
                scale = Math.min(scale, 1.0);

                int displayW = (int) (img.getWidth()  * scale);
                int displayH = (int) (img.getHeight() * scale);

                JLabel imageLabel = new JLabel(
                        new ImageIcon(img.getScaledInstance(displayW, displayH, Image.SCALE_SMOOTH))
                );
                imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

                JFrame frame = new JFrame(file.getName());
                frame.add(new JScrollPane(imageLabel));
                frame.setSize(displayW + 40, displayH + 60);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);

                System.out.println("[Viewer] Displaying: " + file.getName()
                        + " (" + img.getWidth() + "x" + img.getHeight() + ")");

            } catch (Exception e) {
                System.out.println("[Viewer] Could not open image: " + e.getMessage());
            }
        });
    }
}