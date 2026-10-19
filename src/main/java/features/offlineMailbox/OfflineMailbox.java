package features.offlineMailbox;

import java.util.ArrayList;
import java.util.Queue;

public interface OfflineMailbox {
    void addMessage(String receiver, String sender, String message);
    Queue<String[]> clearMessages(String username);
}
