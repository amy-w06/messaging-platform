package features.offlineMailbox;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SafeOfflineMailbox {

    private final ConcurrentHashMap<String, Queue<String[]>> mailbox;

    public SafeOfflineMailbox() {
        this.mailbox = new ConcurrentHashMap<>();
    }

    public void addMessage(String receiver, String sender, String message) {
        Queue<String[]> queue =
                mailbox.computeIfAbsent(receiver, k -> new ConcurrentLinkedQueue<>());

        queue.add(new String[]{sender, message});
    }

    public Queue<String[]> clearMessages(String username) {
        Queue<String[]> queue = mailbox.get(username);

        if (queue == null) {
            return new ConcurrentLinkedQueue<>();
        }
        Queue<String[]> copy = new ConcurrentLinkedQueue<>(queue);
        queue.clear();
        return copy;
    }
}


