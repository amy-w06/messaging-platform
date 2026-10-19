package server;


import interfaces.ChatClient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;


/**
 * Handles a single client's TCP connection. This means we can have multiple clients because each
 * client will have its own thread to handle the write operation which broadcasts
 *  a client's message to all other clients.
 *
 * Implements ChatClient so it can sit in the same client list as
 * WebSocketChatClient, the server doesn't need to know whether
 * a client is a terminal or browser user.
 */


public class ClientHandler extends Thread implements ChatClient {
    private Socket s;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;


    public ClientHandler(Socket s) throws IOException {
        this.s = s;
        in = new DataInputStream(s.getInputStream());
        out = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {
        try {
            out.writeUTF("What is your username?");
            username = in.readUTF();
            out.writeUTF("Welcome, " + username + "!");
            Server.getChatRoom().addClient(this);
            String msg = "";
            while(!msg.equals("Close")) {
                msg = in.readUTF();
                Server.getChatRoom().handleMessage(this, msg);
            }
        } catch (IOException e) {
            Server.getChatRoom().removeClient(this);
            System.out.println("A client disconnected");
            try {
                in.close();
                out.close();
                s.close();
            } catch (IOException ex) {
                System.out.println(ex);
            }
        }
    }


    @Override
    public void sendMessage(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Socket getSocket() { return s; }
}
