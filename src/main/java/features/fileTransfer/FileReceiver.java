package features.fileTransfer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

@SuppressWarnings("unused")
public class FileReceiver extends Thread {
    private String ip;
    private int port;
    private File saveTo;

    public FileReceiver(String ip, int port, File saveTo) {
        this.ip = ip;
        this.port = port;
        this.saveTo = saveTo;
    }

    @Override
    public void run() {
        try (Socket socket = new Socket(ip, port);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             FileOutputStream fos = new FileOutputStream(saveTo)) {

            String filename = dis.readUTF();
            long size = dis.readLong();

            byte[] buffer = new byte[4096];
            long remaining = size;
            while (remaining > 0) {
                int read = dis.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (read == -1) break;
                fos.write(buffer, 0, read);
                remaining -= read;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
