package server;

import interfaces.ChatClient;
import org.java_websocket.WebSocket;

import java.net.Socket;

/**
 * This provides the wrapper for storing clients in the ChatWebSocketServer and
 * also implements ChatClient to let all our clients live together (web and terminal)
 *
 * Wraps around the raw websocket so it has meaning
 *
 */

public class WebSocketChatClient implements ChatClient {
    private WebSocket conn;
    private String username;

    public WebSocketChatClient(WebSocket conn, String username){
        this.conn = conn;
        this.username = username;
    }
    @Override
    public void sendMessage(String msg) {
        conn.send(msg);
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Socket getSocket() {
        return null;
    }
}
