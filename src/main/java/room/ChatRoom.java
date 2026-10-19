package room;

import features.DMS.DirectMessageRouter;
import features.DMS.DirectMessageRouterSafe;
import features.friends.Friends;
import features.GroupChatRouter;
import features.fileTransfer.FileSender;
import features.friends.SafeFriends;
import features.history.MessageHistory;
import features.history.SafeMessageHistory;
import features.status.SafeStatusManager;
import features.status.StatusManager;
import features.offlineMailbox.SafeOfflineMailbox;
import interfaces.ChatClient;
import server.ClientHandler;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The central chat room that handles all incoming messages and routes them based on what command they start with.
 * When a client sends a message it comes here first, and depending on the prefix it gets passed to the right handler
 * for group chats, direct messages, friend requests, file transfer or the stage 4 P2P features.
 *
 * For the P2P features this class acts as a signalling server. It never touches the actual video or file data,
 * it just reads the sender's IP address from their TCP socket and forwards it to the target user so the two
 * clients can open a direct connection to each other. Once both sides have each other's details the server is
 * completely out of the picture and the data flows directly between the two machines.
 */
public class ChatRoom {

    private List<ChatClient> clients;
    private CopyOnWriteArrayList<ChatClient> safeClients;

    private MessageHistory messageHistory;
    private Friends friends;
    private GroupChatRouter groupChatRouter;
    private StatusManager statusManager;
    private DirectMessageRouter dmRouter;
    private DirectMessageRouterSafe dmSafeRouter;
    private Map<String, FileOffer> pendingOffers = new HashMap<>();
    private SafeOfflineMailbox offlineMailbox;


    // swap this to true to use the thread safe DM router instead
    private boolean useSafeDm = false;

    public ChatRoom() {
        clients     = new ArrayList<>();
        safeClients = new CopyOnWriteArrayList<>();
        try {
            messageHistory  = new SafeMessageHistory();
            friends         = new SafeFriends();
            groupChatRouter = GroupChatRouter.getInstance();
            dmRouter        = new DirectMessageRouter();
            dmSafeRouter    = new DirectMessageRouterSafe();
            statusManager   = new SafeStatusManager();
            offlineMailbox = new SafeOfflineMailbox();
            statusManager.startIdleMonitor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Using " + (useSafeDm ? "SAFE" : "UNSAFE") + " DM router");
    }

    public void handleMessage(ChatClient sender, String msg) {
        statusManager.updateActivity(sender);

        if      (msg.startsWith("/createGC "))    handleCreateGroup(sender, msg);
        else if (msg.startsWith("/gc "))           handleGroupMessage(sender, msg);
        else if (msg.startsWith("/joinGC "))       groupChatRouter.joinGroup(sender, msg.replaceFirst("/joinGC", "").trim());
        else if (msg.startsWith("/dm "))           handleDirectMessage(sender, msg);
        else if (msg.startsWith("/addFriend "))    friends.sendRequest(msg.replaceFirst("/addFriend", "").trim(), sender.getUsername());
        else if (msg.startsWith("/acceptFriend ")) friends.acceptRequest(sender.getUsername(), msg.replaceFirst("/acceptFriend", "").trim());
        else if (msg.startsWith("/denyFriend "))   friends.denyRequest(sender.getUsername(), msg.replaceFirst("/denyFriend", "").trim());
        else if (msg.startsWith("/sendfile "))     handleSendFile(sender, msg);
        else if (msg.startsWith("/acceptfile "))   handleAcceptFile(sender, msg);
        else if (msg.startsWith("/rejectfile "))   handleRejectFile(sender, msg);
        else if (msg.startsWith("/stream "))       handleStreamRequest(sender, msg);
        else if (msg.startsWith("/call "))         handleCallRequest(sender, msg);
        else if (msg.startsWith("/acceptcall "))   handleCallAccept(sender, msg);
        else {
            String formatted = sender.getUsername() + ": " + msg;
            messageHistory.saveHistory(formatted);
            for (ChatClient ch : clients) {
                if (ch != sender) ch.sendMessage(formatted);
            }
        }
    }

    public void sendAllClients(String msg) {
        messageHistory.saveHistory(msg);
        for (ChatClient ch : clients) {
            ch.sendMessage(msg);
        }
    }

    // the sender has already started a VideoStreamSender on their end, we just need to
    // tell the target who wants to stream to them and what port to connect to
    private void handleStreamRequest(ChatClient sender, String command) {
        String[] parts = command.substring(8).trim().split(" ", 3);
        if (parts.length < 3) {
            sender.sendMessage("Usage: /stream <username> <filepath>");
            return;
        }
        String targetUser = parts[0];
        String filename   = parts[1];
        int    streamPort = Integer.parseInt(parts[2]);

        ChatClient target = findClient(targetUser);
        if (target == null) {
            sender.sendMessage("User '" + targetUser + "' is not online.");
            return;
        }

        String senderIp = getClientIp(sender);
        target.sendMessage("[STREAM] " + sender.getUsername() + " " + senderIp + " " + streamPort + " " + filename);
        sender.sendMessage("[Stream] Streaming to " + targetUser + ". Waiting for them to connect...");
    }

    // the caller has opened a ServerSocket ready to receive the other person's video,
    // we forward their IP and port to the target so they know where to send their feed
    private void handleCallRequest(ChatClient sender, String command) {
        String[] parts = command.substring(6).trim().split(" ", 2);
        if (parts.length < 2) {
            sender.sendMessage("Usage: /call <username>");
            return;
        }
        String targetUser       = parts[0];
        int    senderListenPort = Integer.parseInt(parts[1]);

        ChatClient target = findClient(targetUser);
        if (target == null) {
            sender.sendMessage("User '" + targetUser + "' is not online.");
            return;
        }

        String senderIp = getClientIp(sender);
        target.sendMessage("[CALL] " + sender.getUsername() + " " + senderIp + " " + senderListenPort);
        sender.sendMessage("[Call] Calling " + targetUser + "...");
    }

    // the person accepting has opened their own ServerSocket for the caller to connect to,
    // we pass their details back to the caller and both sides can now connect directly
    private void handleCallAccept(ChatClient receiver, String command) {
        String[] parts = command.substring(12).trim().split(" ", 2);
        if (parts.length < 2) return;
        String callerUser         = parts[0];
        int    receiverListenPort = Integer.parseInt(parts[1]);

        ChatClient caller = findClient(callerUser);
        if (caller == null) {
            receiver.sendMessage("User '" + callerUser + "' is no longer online.");
            return;
        }

        String receiverIp = getClientIp(receiver);
        caller.sendMessage("[CALLACCEPT] " + receiver.getUsername() + " " + receiverIp + " " + receiverListenPort);
        receiver.sendMessage("[Call] Connected to " + callerUser + "!");
    }

    private void handleCreateGroup(ChatClient sender, String command) {
        try {
            String[] parts = command.substring(9).trim().split(" ");
            if (parts.length < 1) {
                sender.sendMessage("Usage: /createGC [groupname] [user1] [user2]...");
                return;
            }
            String groupName = parts[0];
            List<String> usernames = Arrays.asList(parts).subList(1, parts.length);
            boolean created = groupChatRouter.createGroup(sender, groupName, usernames, clients);
            sender.sendMessage(created
                    ? "Group '" + groupName + "' created! You and " + usernames.size() + " others joined."
                    : "Failed to create group '" + groupName + "'");
        } catch (Exception e) {
            sender.sendMessage("Error: " + e.getMessage());
        }
    }

    private void handleGroupMessage(ChatClient sender, String command) {
        try {
            String[] parts = command.substring(4).trim().split(" ", 2);
            if (parts.length < 2) {
                sender.sendMessage("Usage: /gc [groupname] <message>");
                return;
            }
            groupChatRouter.sendToGroup(sender, parts[0], parts[1]);
        } catch (Exception e) {
            sender.sendMessage("Error sending group message: " + e.getMessage());
        }
    }

    private void handleDirectMessage(ChatClient sender, String command) {
        String[] parts = command.substring(4).split(" ", 2);
        if (parts.length == 2) {
            boolean sent = useSafeDm
                    ? dmSafeRouter.send(sender, parts[0], parts[1], safeClients)
                    : dmRouter.send(sender, parts[0], parts[1], clients);
            if (!sent) {
                offlineMailbox.addMessage(parts[0], sender.getUsername(), parts[1]);
                sender.sendMessage("User '" + parts[0] + "' is not online. Message sent to their inbox.");
            }
        } else {
            sender.sendMessage("Usage: /dm <username> <message>");
        }
    }

    private void handleSendFile(ChatClient sender, String command) {
        String[] parts = command.substring(9).trim().split(" ", 4);
        if (parts.length < 4) {
            sender.sendMessage("Usage: /sendfile <username> <filename> <filesize> <port>");
            return;
        }
        String targetUser = parts[0];
        String filename   = parts[1];
        long   filesize   = Long.parseLong(parts[2]);
        int    filePort   = Integer.parseInt(parts[3]);

        ChatClient target = findClient(targetUser);
        if (target == null) {
            sender.sendMessage("User '" + targetUser + "' not online.");
            return;
        }

        String senderIp  = getClientIp(sender);
        File   senderFile = new File("uploads", filename);
        new FileSender(senderFile, filePort).start();

        target.sendMessage("File offer from " + sender.getUsername() + ": '" + filename
                + "' (" + filesize + "B). Reply: /acceptfile "
                + sender.getUsername() + " " + senderIp + " " + filePort);
        sender.sendMessage("Offer sent. Awaiting accept on port " + filePort);
    }

    private void handleAcceptFile(ChatClient receiver, String command) {
        String[] parts = command.substring(11).trim().split(" ", 3);
        if (parts.length < 3) return;
        ChatClient sender = findClient(parts[0]);
        if (sender != null) {
            sender.sendMessage(receiver.getUsername() + " accepted your file.");
        }
    }

    private void handleRejectFile(ChatClient receiver, String command) {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) return;
        ChatClient sender = findClient(parts[1]);
        if (sender != null) sender.sendMessage(receiver.getUsername() + " rejected your file.");
        receiver.sendMessage("You rejected file from " + parts[1] + ".");
    }

    // add the client to both lists, set their initial status, and send them the recent chat history
    public void addClient(ChatClient client) {
        clients.add(client);
        safeClients.add(client);
        statusManager.initClient(client);

        Queue<String[]> pending = offlineMailbox.clearMessages(client.getUsername());
        if (!pending.isEmpty()) {
            client.sendMessage(pending.size() + " offline message(s)");
            for (String[] msg : pending) {
                client.sendMessage(msg[0] + " (unread): " + msg[1]);
            }
        }

        for (String msg : messageHistory.getRecent()) {
            client.sendMessage(msg);
        }
    }

    // remove from list, clean up their status and group memberships, notify everyone else
    public void removeClient(ChatClient client) {
        clients.remove(client);
        safeClients.remove(client);
        statusManager.removeClient(client);
        groupChatRouter.removeClient(client);
        for (ChatClient ch : clients) {
            ch.sendMessage(client.getUsername() + " has left the chat");
        }
    }

    private ChatClient findClient(String username) {
        for (ChatClient c : clients) {
            if (username.equals(c.getUsername())) return c;
        }
        return null;
    }

    // gets the IP from a TCP client's socket, falls back to localhost for WebSocket clients
    // since getSocket() returns null for those
    private String getClientIp(ChatClient client) {
        if (client instanceof ClientHandler) {
            return ((ClientHandler) client).getSocket().getInetAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    static class FileOffer {
        ChatClient sender;
        String filename;
        String ip;
        int port;

        FileOffer(ChatClient sender, String filename, String ip, int port) {
            this.sender   = sender;
            this.filename = filename;
            this.ip       = ip;
            this.port     = port;
        }
    }
}