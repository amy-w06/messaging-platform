package features;

import interfaces.ChatClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class GroupChatRouter {

    // thread-safe storage (no external locks needed)
    // all update operations create a cloned copy of the underlying list so if multiple people ar doing something with it at the same time no concurrency issues occur.
    private final Map<String, CopyOnWriteArraySet<ChatClient>> groupChats = new ConcurrentHashMap<>();
    private final Map<ChatClient, CopyOnWriteArraySet<String>> userGroups = new ConcurrentHashMap<>();

    public boolean createGroup(ChatClient creator, String groupName, List<String> usernames, List<ChatClient> allClients) {
        CopyOnWriteArraySet<ChatClient> members = new CopyOnWriteArraySet<>();

        // add creator + specified users
        members.add(creator);
        addToGroupTracking(creator, groupName);

        for (String username : usernames) {
            ChatClient user = findClient(username, allClients);
            if (user != null) {
                members.add(user);
                addToGroupTracking(user, groupName);
            }
        }

        groupChats.put(groupName, members);
        return true;
    }

    public void sendToGroup(ChatClient sender, String groupName, String msg) {
        CopyOnWriteArraySet<ChatClient> members = groupChats.get(groupName);
        if (members == null || !members.contains(sender)) {
            return;
        }

        String groupMsg = "[Group: " + groupName + "] " + sender.getUsername() + ": " + msg;

        // SAFE: Iterates snapshot copy - handles concurrent add/remove automatically
        for (ChatClient member : members) {
            if (member != sender) {
                member.sendMessage(groupMsg);
            }
        }
    }

    public void joinGroup(ChatClient user, String groupName) {
        CopyOnWriteArraySet<ChatClient> members = groupChats.get(groupName);
        if (members != null) {
            members.add(user);
            addToGroupTracking(user, groupName);
        }
    }

    // cleanup disconnect
    public void removeClient(ChatClient client) {
        CopyOnWriteArraySet<String> groups = userGroups.remove(client);
        if (groups != null) {
            for (String groupName : groups) {
                CopyOnWriteArraySet<ChatClient> members = groupChats.get(groupName);
                if (members != null) {
                    members.remove(client);
                }
            }
        }
    }

    private ChatClient findClient(String username, List<ChatClient> allClients) {
        for (ChatClient client : allClients) {
            if (client.getUsername().equals(username)) {
                return client;
            }
        }
        return null;
    }

    private void addToGroupTracking(ChatClient user, String groupName) {
        userGroups.computeIfAbsent(user, k -> new CopyOnWriteArraySet<>()).add(groupName);
    }

    public static GroupChatRouter getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private static class SingletonHelper {
        private static final GroupChatRouter INSTANCE = new GroupChatRouter();
    }
}
