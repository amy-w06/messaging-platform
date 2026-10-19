package features.streaming;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Streams a video file to another client directly without going through the server.
 * It opens a ServerSocket and waits for the receiver to connect, then uses FFmpegFrameGrabber
 * to read the video file frame by frame. Each frame is converted to a BufferedImage, compressed
 * as a JPEG and sent down the socket with length so the receiver knows how long each frame is.
 * The sending speed is changed to match the actual frame rate of the source video so it does not play back too fast.
 * Sending -1 as the length signals to the receiver that the stream has ended. This extends Thread so it can run in the
 * background without blocking the client from continuing to use the chat.
 */
public class VideoStreamSender extends Thread {

    private final String videoPath;
    private final int port;

    public VideoStreamSender(String videoPath, int port) {
        this.videoPath = videoPath;
        this.port = port;
        setDaemon(true);
    }

    @Override
    public void run() {
        System.out.println("[Stream] Waiting for receiver to connect on port " + port + "...");

        // wait for receiver to connect
        try (ServerSocket ss = new ServerSocket(port);
             Socket peer = ss.accept()) {

            System.out.println("[Stream] Receiver connected — starting stream of: " + videoPath);

            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(peer.getOutputStream()));

            // FFmpegFrameGrabber handles decoding the video file, it supports basically
            // every format so we don't need to worry about mp4 vs mkv vs avi etc.
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath);
            grabber.start();

            // read the fps from the video file itself, fall back to 25 if we can't get it
            double fps = grabber.getFrameRate();
            if (fps <= 0 || fps > 120) fps = 25;
            long frameDelayMs = (long) (1000.0 / fps);

            // make frame
            Java2DFrameConverter converter = new Java2DFrameConverter();
            Frame frame;
            int frameCount = 0;
            long streamStart = System.currentTimeMillis();

            while ((frame = grabber.grab()) != null) {
                if (frame.image == null) continue;  // grab() also returns audio frames, skip those

                BufferedImage img = converter.convert(frame);
                if (img == null) continue;  // skip bad frames

                // compress to JPEG rather than sending raw pixels, raw frames would be huge
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", baos);
                byte[] bytes = baos.toByteArray();

                // form [length][data]
                dos.writeInt(bytes.length);
                dos.write(bytes);
                dos.flush();

                // sleep between frames to match the source fps otherwise everything plays too fast
                frameCount++;
                long expected = streamStart + (frameCount * frameDelayMs);
                long now = System.currentTimeMillis();
                if (expected > now) {
                    Thread.sleep(expected - now);
                }
            }

            // stops stream
            dos.writeInt(-1); // tells the receiver the stream is over
            dos.flush();
            grabber.stop();
            System.out.println("[Stream] Finished streaming.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("[Stream] Sender error: " + e.getMessage());
        }
    }
}