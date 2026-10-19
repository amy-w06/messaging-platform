package features.status;

import interfaces.ChatClient;
import server.Server;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SafeStatusManager implements StatusManager {

    public long checkInterval = 1000; // Check every second default
    public long idleThreshold = 30000; // Idle threshold 30s default

    public boolean testMode;

    public SafeStatusManager() {
        testMode = false;
    }

    public SafeStatusManager(boolean testMode) {
        this.testMode = testMode;
    }

    // Solve the concurrency problem by encapsulating the state into a single object, we can lock everything together
    private static class ClientState {
        long lastActivity;
        OnlineStatus status;
        ClientState(long lastActivity, OnlineStatus status) {
            this.lastActivity = lastActivity;
            this.status = status;
        }
    }

    private Map<ChatClient, ClientState> stateMap = new ConcurrentHashMap<>();

    public void initClient(ChatClient client) {
        stateMap.put(client, new ClientState(System.currentTimeMillis(), OnlineStatus.ONLINE));
    }


    public void updateActivity(ChatClient client) {
        ClientState state = stateMap.get(client);
        if (state != null) {
            // Lock the clients state before modifying
            synchronized (state) {
                state.lastActivity = System.currentTimeMillis();
                if (state.status != OnlineStatus.ONLINE) {
                    state.status = OnlineStatus.ONLINE;
                    if(!testMode) {Server.getChatRoom().sendAllClients("[SERVER] " + client.getUsername() + " is back ONLINE");}
                }
            }
        }
    }

    public void startIdleMonitor() {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(checkInterval);
                    long now = System.currentTimeMillis();

                    for (Map.Entry<ChatClient, ClientState> entry : stateMap.entrySet()) {
                        ChatClient client = entry.getKey();
                        ClientState state = entry.getValue();

                        // Now safe
                        synchronized (state) {
                            if (state.status == OnlineStatus.ONLINE && (now - state.lastActivity) > idleThreshold) { // If idle for 30s and currently online

                                if(testMode) Thread.sleep(50); // Give a tiny nudge to help prove race condition - in real world is random depending on CPU scheduling


                                state.status = OnlineStatus.IDLE;
                                if(!testMode) {Server.getChatRoom().sendAllClients("[SERVER] " + client.getUsername() + " set to IDLE");}
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    System.out.println("Idle monitor interrupted: " + e);
                    break;
                }
            }
        });
        monitor.setDaemon(true); // Setting daemon to true means it will die when server dies
        monitor.start();
        System.out.println("SAFE Idle Monitor started...");
    }

    public void removeClient(ChatClient client) {
        stateMap.remove(client);
    }

    public OnlineStatus getStatus(ChatClient client) {
        ClientState state = stateMap.get(client);
        return (state != null ? state.status : null);
    }

}