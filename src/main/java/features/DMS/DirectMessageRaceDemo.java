package features.DMS;

import room.ChatRoom;
import interfaces.ChatClient;

import java.net.Socket;

// Stand-in for a real ChatClient used in race condition demo
// Simulates disconnect without using real sockets
public class DirectMessageRaceDemo {
    static class MockClient implements ChatClient {
        private final String username;
        private volatile boolean connected = true;

        MockClient(String username) { this.username = username; }

        @Override public String getUsername() { return username; }

        @Override
        public Socket getSocket() {
            return null;
        }

        // Simulates the client being removed from the server
        public void disconnect() { connected = false; }

        @Override
        public void sendMessage(String message) {

            // If the unsafe router sends a message to a disconnected client,
            // this prints the error showing the race condition
            if (!connected) {
                System.out.println("[ERROR] DEAD CLIENT " + username + " -> " + message);
                return;
            }
            System.out.println("[" + username + "] OK");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ChatRoom room = new ChatRoom();

        System.out.println("testing for bob being offline while receiving a dm"); // make sure in chat room addclient bits are commented and dmRouter is being used

        MockClient alice = new MockClient("Alice");
        final MockClient[] bob = {new MockClient("Bob")};

        room.addClient(alice);
        room.addClient(bob[0]);

        // spam dms from alice
        Thread sender = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                room.handleMessage(alice, "/dm Bob RACE" + i);
            }
        });

        // remove and add bob
        Thread remover = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                MockClient oldBob = bob[0];
                bob[0] = new MockClient("Bob");
                room.removeClient(oldBob);  // remove OLD bob
                oldBob.disconnect();
                room.addClient(bob[0]);  // add NEW bob
            }
        });

        sender.start();
        remover.start();
        sender.join();
//        remover.join();



        System.out.println("Test done");
    }
}
