package features.DMS;

import interfaces.ChatClient;

import java.util.List;

public class DirectMessageRouter {
    public DirectMessageRouter() {

    }

    // Routes a direct message without thread safety
    // Iterates over a shared clients list that can be modified concurrently

    public boolean send(ChatClient sender, String username, String msg, List<ChatClient> clients) {

        // Linear search for target user
        // If another thread removes them during iteration, we may get a stale reference or ConcurrentModificationException
        ChatClient target = null;
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

        // Debug output showing this version is unsafe against tie of check to time of use bugs
        System.out.println("UNSAFE: Found " + username + " = " + target +
                " (still in list = " + clients.contains(target) + ")");


        String dmText = "DM from " + sender.getUsername() + ": " + msg;
        target.sendMessage(dmText);
        sender.sendMessage("DM to " + username + ": " + msg);
        return true;
    }
}

