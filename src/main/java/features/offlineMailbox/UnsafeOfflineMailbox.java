package features.offlineMailbox;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class UnsafeOfflineMailbox {
    // this is based off of the Offline messaging 🟩 HashMap<String, Queue<String[]>>. Key = username.
    // in the google doc, if we want to do something different lmk
    // map stores each users mailbox, key is the users username
    // and the value is queue of messages (sender, message)
    private final HashMap<String, Queue<String[]>> mailbox;

    // initialise mailbox as hashmap
    public UnsafeOfflineMailbox() {
        this.mailbox = new HashMap<>();
    }
        // Adds message to queue for the reciever
    public void addMessage(String receiver, String sender, String message) {
        // get current queue of messages for receiver
        Queue<String[]> queue = mailbox.get(receiver);

        // if queue doesnt exist, create new linkedlist to store messages
        if (queue == null) {
            queue = new LinkedList<>();
            mailbox.put(receiver, queue);

        }
        // add the new message to receivers queue
        queue.add(new String[]{sender, message});
    }
    // Clears all messages for the user and returns a copy of the queue before clearing it
    // If no messages exist, an empty queue is returned
    public Queue<String[]> clearMessages(String username) {
        Queue<String[]> queue = mailbox.get(username);

        if (queue == null) {
            return new LinkedList<>();
        }
        Queue<String[]> copy = new LinkedList<>(queue);
        queue.clear();
        return copy;
    }
}







