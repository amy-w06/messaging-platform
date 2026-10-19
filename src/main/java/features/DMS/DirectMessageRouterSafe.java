package features.DMS;

import interfaces.ChatClient;
import java.util.List;

public class DirectMessageRouterSafe {

    // Thread safe direct message router
    // Synchronises the shared clients list to prevent concurrent modifications
    // No race condition
    public boolean send(ChatClient sender, String username, String msg, List<ChatClient> clients) {

        ChatClient target = null;

        // using synchronised clients
        // mutual exclusion so there is no race condition, makes it so only one thread can modify the list at a time.
        synchronized (clients) {
            for (ChatClient ch : clients) {
                if (ch.getUsername().equals(username)) {
                    target = ch;
                    break;
                }
            }

            // Target not found, Dm can't be delivered
            if (target == null) {
                return false;
            }

            // Target cannot be removed while inside the synchronised block
            String dmText = "DM from " + sender.getUsername() + ": " + msg;
            target.sendMessage(dmText);
            sender.sendMessage("DM to " + username + ": " + msg);
            return true;
        }
    }
}
