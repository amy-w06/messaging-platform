package features.friends;
import java.util.*;
import java.io.*;

public interface Friends {
    void loadFriends();
    void saveFriends();
    void sendRequest(String to, String from);
    void acceptRequest(String to, String from);
    void denyRequest(String to, String from);
}
