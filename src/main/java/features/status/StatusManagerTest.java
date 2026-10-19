package features.status;

import interfaces.ChatClient;

import java.net.Socket;

public class StatusManagerTest {

    // A dummy client  for testing purposes - means we don't need a TCP socket or WebSocket
    static class DummyClient implements ChatClient {
        private String username;
        public DummyClient(String username) { this.username = username; }
        public void sendMessage(String msg) {}
        public String getUsername() { return username; }

        @Override
        public Socket getSocket() {
            return null;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- Unsafe Version Test ---");
        testUnsafe();

        System.out.println("\n\n-- Safe Version Test ---");
        testSafe();
    }

    public static void testUnsafe() throws InterruptedException {
        UnsafeStatusManager unsafeManager = new UnsafeStatusManager(true);
        // For testing sake lower the intervals to 10ms and 50ms
        unsafeManager.checkInterval = 10;
        unsafeManager.idleThreshold = 50;

        unsafeManager.startIdleMonitor();

        int attempts = 50;
        int brokenClients = 0;
        for (int i = 0; i < attempts; i++) {
            ChatClient c = new DummyClient("User" + i);
            unsafeManager.initClient(c);

            // Wait until just after the idle threshold (50 + buffer)
            Thread.sleep(65);

            // Background monitor has seen user is idle, they are currently trapped in simulated 'waiting for CPU time' queue
            unsafeManager.updateActivity(c); // we send a message, and they are updated to online


            // Wait for the background thread to finish its sleep which will (hopefully for test-sake) blindly execute `statusMap.put(client, IDLE)` over our online update
            Thread.sleep(75);

            // Check the outcome
            if (unsafeManager.getStatus(c) == OnlineStatus.IDLE) {
                brokenClients++;
            }

            // Clean up the map so the background thread doesn't iterate over old clients
            unsafeManager.removeClient(c);
        }

        System.out.println(brokenClients + " out of " + attempts + " clients incorrectly marked as idle");
        if (brokenClients > 0) {
            System.out.println("Broken clients > 0 therefore concurrency issue present.");
        } else {
            System.out.println("Broken clients = 0 therefore no concurrency issue present.");
        }
    }

    public static void testSafe() throws InterruptedException {
        SafeStatusManager safeManager = new SafeStatusManager(true);
        safeManager.checkInterval = 10;
        safeManager.idleThreshold = 50;
        safeManager.testMode = true;

        safeManager.startIdleMonitor();

        int attempts = 50;
        int brokenClients = 0;

        for (int i = 0; i < attempts; i++) {
            ChatClient c = new DummyClient("User" + i);
            safeManager.initClient(c);

            Thread.sleep(65);

            safeManager.updateActivity(c);

            Thread.sleep(75);

            if (safeManager.getStatus(c) == OnlineStatus.IDLE) {
                brokenClients++;
            }

            safeManager.removeClient(c);
        }

        System.out.println(brokenClients + " out of " + attempts + " clients incorrectly marked as idle");
        if (brokenClients > 0) {
            System.out.println("Broken clients > 0 therefore concurrency issue present.");
        } else {
            System.out.println("Broken clients = 0 therefore no concurrency issue present.");
        }
    }
}