package server;

import interfaces.ChatClient;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

/**
 * WebSocket server which allows clients to join on the web
 * This is needed because the web can't understand TCP and so it needs
 * the websocket wrapper
 *
 * Unlike TCP where each client gets its own ClientHandler thread, websocket library manages
 * threads internally and calls these callback methods
 * (onOpen, onMessage, onClose) when events happen.
 *
 * clientMap links each raw WebSocket connection to its WebSocketChatClient
 * wrapper, this is how we look up which ChatClient sent a message.
 * pendingUsername tracks connections that haven't provided a username yet,
 * since we can't block and wait for it like we can with TCP.
 */

public class ChatWebSocketServer extends WebSocketServer {

    private HashMap<WebSocket, WebSocketChatClient> clientMap;
    private HashSet<WebSocket> pendingUsername;

    public ChatWebSocketServer(int port){
        super (new InetSocketAddress(port));
        clientMap = new HashMap<>();
        pendingUsername = new HashSet<>();
    }
    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        System.out.println("A client is connecting");
        pendingUsername.add(webSocket);
        webSocket.send("What is your username?");
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        if(clientMap.get(webSocket) != null){
            Server.getChatRoom().removeClient(clientMap.get(webSocket));
            clientMap.remove(webSocket);
        }
        pendingUsername.remove(webSocket);

    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {

        if(pendingUsername.contains(webSocket)){
            pendingUsername.remove(webSocket);
            WebSocketChatClient client = new WebSocketChatClient(webSocket, s);
            clientMap.put(webSocket, client);
            Server.getChatRoom().addClient(client);
            webSocket.send("Welcome, " + s + "!");
        }
        else {
            WebSocketChatClient sender = clientMap.get(webSocket);
            Server.getChatRoom().handleMessage(sender, s);
        }

    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        System.out.println(e);
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started");
    }
}
