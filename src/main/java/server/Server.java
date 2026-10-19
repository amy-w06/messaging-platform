package server;


import features.friends.SafeFriends;
import room.ChatRoom;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * Two server are created here. The TCP server (terminal) and WebSocket server (web)
 * The server stores the list of ALL clients (terminal & web).
 * The server socket listens and accepts sockets which allows for two-way communication.
 * The socket accepted is then handed to the ClientHandler which handles the TCP sockets
 */


public class Server {

    private ServerSocket ss;
    private static ChatRoom chatRoom;

    public Server(int port){
        try {
            ss = new ServerSocket(port);
            System.out.println("server.Server started");

            while(true){
                Socket s = ss.accept();
                System.out.println("A new client is connecting");
                ClientHandler ch = new ClientHandler(s);
                ch.start();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ChatRoom getChatRoom(){
        return chatRoom;
    }

    public static void main(String[] args) {
        chatRoom = new ChatRoom(); // chat room created before anything connects
        ChatWebSocketServer wsServer = new ChatWebSocketServer(5002);
        SafeFriends friends = new SafeFriends();
        wsServer.start();
        Server server = new Server(5005);
    }
}
