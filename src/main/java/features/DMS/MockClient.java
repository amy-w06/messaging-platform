package features.DMS;

import interfaces.ChatClient;

import java.net.Socket;

//
public class MockClient implements ChatClient {
    private final String username;
    private volatile boolean connected = true;
    private Socket mockSocket = null;

    public MockClient(String username) {
        this.username = username;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Socket getSocket() {
        return null;
    }

    public void disconnect() {
        connected = false;
    }

    @Override
    public void sendMessage(String message) {
        if (!connected) {
            // This shows TOCTOU bug in unsafe version
            System.out.println("[ERROR] sendMessage to DISCONNECTED " + username + " -> " + message);
            return;
        }
        System.out.println("[" + username + "] " + message);
    }
}
