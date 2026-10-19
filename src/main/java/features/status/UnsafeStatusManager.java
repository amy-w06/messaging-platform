package features.status;

import interfaces.ChatClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import server.Server;

public class UnsafeStatusManager implements StatusManager {

    // Storing last-time-active and online status in two separate concurrent hashmaps
    // But using concurrent hashmaps doesn't solve all problems as shown
    private Map<ChatClient, Long> lastActivityMap = new ConcurrentHashMap<>();
    private Map<ChatClient, OnlineStatus> statusMap = new ConcurrentHashMap<>();
    public long checkInterval = 1000;
    public long idleThreshold = 30000;
    public boolean testMode;

    public UnsafeStatusManager(boolean testMode) {
        this.testMode = testMode;
    }

        public void initClient(ChatClient client) {
            lastActivityMap.put(client, System.currentTimeMillis());
            statusMap.put(client, OnlineStatus.ONLINE);
        }

        public void updateActivity(ChatClient client) {
            lastActivityMap.put(client, System.currentTimeMillis());
            statusMap.put(client, OnlineStatus.ONLINE);
        }

        public void startIdleMonitor() {
            Thread monitor = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(checkInterval); // Check every second
                        long now = System.currentTimeMillis();
                        for (ChatClient client : lastActivityMap.keySet()) {
                            long lastActivity = lastActivityMap.get(client);
                            OnlineStatus currentStatus = statusMap.get(client);

                            if (currentStatus == OnlineStatus.ONLINE && (now - lastActivity) > idleThreshold) { // If idle for 30s and currently online

                                if(testMode) Thread.sleep(50); // Give a tiny nudge to help prove race condition - in real world is random depending on CPU scheduling

                                // Unsafe - blindly overwrites status even if user sent a message between the check and the map.put
                                statusMap.put(client, OnlineStatus.IDLE);
                                if(!testMode) {Server.getChatRoom().sendAllClients("[SERVER] " + client.getUsername() + " status set to " + OnlineStatus.IDLE);}
                            }
                        }
                    } catch (InterruptedException e) {
                        System.out.println("Idle monitor interrupted");
                        break;
                    }
                }
            });
            monitor.setDaemon(true); // Setting daemon to true means it will die when server dies
            monitor.start();
            System.out.println("UNSAFE Idle Monitor started...");
        }

        public void removeClient(ChatClient client){
            statusMap.remove(client);
        }

    public OnlineStatus getStatus(ChatClient client) {
        return statusMap.get(client);
    }

    }

