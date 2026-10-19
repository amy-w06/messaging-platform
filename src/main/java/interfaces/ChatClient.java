package interfaces;

import java.net.Socket;

/**
 * Both the web client and TCP client (terminal) implement this interface
 * so that they can be put in the same list together
 */

public interface ChatClient {
    void sendMessage(String msg);
    String getUsername();

    Socket getSocket();
}
