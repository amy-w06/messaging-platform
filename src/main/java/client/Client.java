package client;

import features.call.CameraCallHandler;
import features.streaming.VideoStreamReceiver;
import features.streaming.VideoStreamSender;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Terminal chat client. Connects to the server via tcp socket and allows the user to send and read messages.
 * It uses two threads. The main thread for reading messages and a separate thread for writing messages and
 * sending it to the server which is then handled by the ClientHandler.
 *
 * Extended for stage 4 to support P2P file transfer, live video streaming and camera calls. For all of these
 * the server only acts as a go-between to exchange connection details — the actual data goes directly between
 * the two clients without touching the server at all. The server sends back prefixed messages like [STREAM],
 * [CALL] and [CALLACCEPT] to tell us someone wants to connect, which we intercept in the read thread and
 * use to kick off the appropriate handler rather than printing them as chat messages.
 *
 * The pending call fields are marked volatile because the read thread writes to them when a [CALL] message
 * arrives and the write thread reads them when the user types /acceptcall. Without volatile the write thread
 * could see a stale cached value.
 */
public class Client {

    private Socket s;
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader userInputReader;

    // written by the read thread when a call comes in, read by write thread on /acceptcall
    private volatile String pendingCallFrom    = null;
    private volatile String pendingCallFromIp  = null;
    private volatile int    pendingCallFromPort = 0;
    private volatile int    myCallListenPort    = 0;

    public Client(String addr, int port) {
        try {
            s = new Socket(addr, port);
            System.out.println("Connected");

            in  = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            userInputReader = new BufferedReader(new InputStreamReader(System.in));

            // inner classes defined here so they can access the outer 'out' stream directly

            // waits for the receiver to connect on listenPort then sends the file
            class FileSender extends Thread {
                private final File file;
                private final int listenPort;

                FileSender(File file, int listenPort) {
                    this.file = file;
                    this.listenPort = listenPort;
                    setDaemon(true);
                }

                @Override
                public void run() {
                    try (ServerSocket ss = new ServerSocket(listenPort);
                         Socket peer = ss.accept();
                         FileInputStream fis = new FileInputStream(file);
                         DataOutputStream dos = new DataOutputStream(peer.getOutputStream())) {

                        dos.writeUTF(file.getName());
                        dos.writeLong(file.length());
                        byte[] buf = new byte[4096];
                        int count;
                        while ((count = fis.read(buf)) > 0) dos.write(buf, 0, count);
                        dos.flush();
                        System.out.println("[File] Sent: " + file.getName());

                    } catch (IOException e) {
                        System.out.println("[File] Send error: " + e.getMessage());
                    }
                }
            }

            // connects to the sender and saves the file, then opens it automatically
            // based on the extension — images pop up in ImageViewer, videos open in the OS player
            class FileReceiver extends Thread {
                private final String ip;
                private final int filePort;

                FileReceiver(String ip, int filePort) {
                    this.ip = ip;
                    this.filePort = filePort;
                    setDaemon(true);
                }

                @Override
                public void run() {
                    try (Socket sock = new Socket(ip, filePort);
                         DataInputStream dis = new DataInputStream(sock.getInputStream())) {

                        String filename = dis.readUTF();
                        long size       = dis.readLong();

                        // keep the original extension so we can check it for display
                        String ext = filename.contains(".")
                                ? filename.substring(filename.lastIndexOf(".")).toLowerCase()
                                : "";
                        File savedFile = new File("received_" + System.currentTimeMillis() + ext);

                        try (FileOutputStream fos = new FileOutputStream(savedFile)) {
                            byte[] buf = new byte[4096];
                            long remaining = size;
                            while (remaining > 0) {
                                int read = dis.read(buf, 0, (int) Math.min(buf.length, remaining));
                                if (read == -1) break;
                                fos.write(buf, 0, read);
                                remaining -= read;
                            }
                        }

                        System.out.println("[File] Saved '" + savedFile.getName() + "' (" + size + " bytes)");

                        Set<String> imageExts = new HashSet<>(Arrays.asList(
                                ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"));
                        Set<String> videoExts = new HashSet<>(Arrays.asList(
                                ".mp4", ".avi", ".mov", ".mkv", ".wmv", ".m4v"));

                        if (imageExts.contains(ext)) {
                            ImageViewer.show(savedFile);
                        } else if (videoExts.contains(ext)) {
                            new VideoPlayerWindow(savedFile).start();
                        }

                    } catch (IOException e) {
                        System.out.println("[File] Receive error: " + e.getMessage());
                    }
                }
            }

            // write thread reads user input and either handles P2P commands locally
            // or passes normal messages straight to the server
            new Thread(() -> {
                try {
                    String msg;
                    while ((msg = userInputReader.readLine()) != null) {

                        if (msg.equals("Close")) {
                            System.out.println("Disconnected from server");
                            s.close();
                            break;
                        }

                        if (msg.startsWith("/sendfile ")) {
                            String[] parts = msg.split(" ", 3);
                            if (parts.length == 3) {
                                File f = new File(parts[2]);
                                if (f.exists() && f.isFile()) {
                                    int filePort = 6000 + new Random().nextInt(1000);
                                    new FileSender(f, filePort).start();
                                    out.writeUTF("/sendfile " + parts[1] + " "
                                            + f.getName() + " " + f.length() + " " + filePort);
                                    System.out.println("[File] Offer sent to " + parts[1]);
                                } else {
                                    System.out.println("[File] Not found: " + parts[2]);
                                }
                            } else {
                                System.out.println("Usage: /sendfile <username> <filepath>");
                            }
                            continue;
                        }

                        if (msg.startsWith("/acceptfile ")) {
                            String[] parts = msg.split(" ", 4);
                            if (parts.length == 4) {
                                new FileReceiver(parts[2], Integer.parseInt(parts[3])).start();
                            }
                            out.writeUTF(msg);
                            continue;
                        }

                        // starts a VideoStreamSender on a random port and tells the server
                        // to notify the target so they can connect and start receiving
                        if (msg.startsWith("/stream ")) {
                            String[] parts = msg.split(" ", 3);
                            if (parts.length == 3) {
                                File f = new File(parts[2]);
                                if (f.exists() && f.isFile()) {
                                    int streamPort = 7000 + new Random().nextInt(1000);
                                    new VideoStreamSender(f.getAbsolutePath(), streamPort).start();
                                    out.writeUTF("/stream " + parts[1] + " " + f.getName() + " " + streamPort);
                                    System.out.println("[Stream] Streaming '" + f.getName() + "' to " + parts[1]);
                                } else {
                                    System.out.println("[Stream] File not found: " + parts[2]);
                                }
                            } else {
                                System.out.println("Usage: /stream <username> <filepath>");
                            }
                            continue;
                        }

                        // pick a port to receive the other person's video on and tell the server
                        if (msg.startsWith("/call ") && msg.split(" ").length == 2) {
                            String targetUser = msg.split(" ")[1];
                            myCallListenPort = 8000 + new Random().nextInt(1000);
                            out.writeUTF("/call " + targetUser + " " + myCallListenPort);
                            System.out.println("[Call] Calling " + targetUser + "...");
                            continue;
                        }

                        if (msg.startsWith("/acceptcall ") && msg.split(" ").length == 2) {
                            if (pendingCallFrom == null) {
                                System.out.println("[Call] No incoming call to accept.");
                            } else {
                                int myListenPort = 8000 + new Random().nextInt(1000);
                                String fromIp   = pendingCallFromIp;
                                int    fromPort = pendingCallFromPort;
                                String fromUser = pendingCallFrom;
                                pendingCallFrom = null; // clear so we don't accept the same call twice

                                CameraCallHandler handler = new CameraCallHandler(myListenPort);
                                handler.startCall(fromIp, fromPort);

                                out.writeUTF("/acceptcall " + fromUser + " " + myListenPort);
                                System.out.println("[Call] Accepted call from " + fromUser);
                            }
                            continue;
                        }

                        out.writeUTF(msg);
                    }
                } catch (IOException e) {
                    System.out.println("[Client] Write error: " + e.getMessage());
                }
            }).start();

            // main thread reads responses, most get printed straight to the console
            // but signalling messages are intercepted and used to kick off P2P connections
            try {
                while (true) {
                    String response = in.readUTF();

                    if (response.startsWith("[STREAM] ")) {
                        // format: [STREAM] senderUser senderIp senderPort filename
                        String[] parts = response.substring(9).split(" ", 4);
                        System.out.println("[Stream] Incoming stream from " + parts[0] + ": " + parts[3] + " — opening player...");
                        new VideoStreamReceiver(parts[1], Integer.parseInt(parts[2]),
                                "Stream from " + parts[0] + " — " + parts[3]).start();
                    }
                    else if (response.startsWith("[CALL] ")) {
                        // format: [CALL] callerUser callerIp callerListenPort
                        String[] parts  = response.substring(7).split(" ", 3);
                        pendingCallFrom     = parts[0];
                        pendingCallFromIp   = parts[1];
                        pendingCallFromPort = Integer.parseInt(parts[2]);
                        System.out.println("[Call] Incoming call from " + pendingCallFrom
                                + " — type /acceptcall " + pendingCallFrom + " to answer");
                    }
                    else if (response.startsWith("[CALLACCEPT] ")) {
                        // format: [CALLACCEPT] acceptUser acceptIp acceptListenPort
                        String[] parts = response.substring(13).split(" ", 3);
                        System.out.println("[Call] " + parts[0] + " accepted! Starting call...");
                        // myCallListenPort was set when we sent /call so the other side can connect
                        CameraCallHandler handler = new CameraCallHandler(myCallListenPort);
                        handler.startCall(parts[1], Integer.parseInt(parts[2]));
                    }
                    else {
                        System.out.println(response);
                    }
                }
            } catch (IOException e) {
                System.out.println("Disconnected from server.");
                try {
                    s.close(); in.close(); out.close(); userInputReader.close();
                } catch (IOException ignored) {}
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new Client("localhost", 5005);
    }
}