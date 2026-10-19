package features.fileTransfer;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

@SuppressWarnings("unused")
public class FileSender extends Thread {
    private File file;
    private int port;

    public FileSender(File file, int port) {
        this.file = file;
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port);
             Socket peer = ss.accept();
             FileInputStream fis = new FileInputStream(file);
             DataOutputStream dos = new DataOutputStream(peer.getOutputStream())) {

            // send metadata first
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            byte[] buffer = new byte[4096];
            int count;
            while ((count = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, count);
            }
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
