package features.status;

import interfaces.ChatClient;

public interface StatusManager {


    // Called when a client first connects to set their initial status
    void initClient(ChatClient client);


     // Called whenever a client sends a message to update their last activity
    void updateActivity(ChatClient client);


    // Starts the background thread that checks for idle clients
    void startIdleMonitor();

    void removeClient(ChatClient client);

    OnlineStatus getStatus(ChatClient client);

}
