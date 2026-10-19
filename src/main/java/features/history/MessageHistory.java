package features.history;

import java.util.ArrayList;

public interface MessageHistory {

    void saveHistory(String msg);
    void loadHistory();
    ArrayList<String> getRecent();
    ArrayList<String> getAll();
}
