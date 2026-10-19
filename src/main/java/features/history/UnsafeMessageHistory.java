package features.history;

import java.io.*;
import java.util.ArrayList;

// main bug with this unsafe version is that ArrayList is losing entries when there are multiple messages being sent by users
// and so the threads will override eachother trying to add to the history arraylist
public class UnsafeMessageHistory implements MessageHistory {

    private File file;
    private ArrayList<String> history; // Otherwise getting the last 50 messages would require a scan through the entire file each time

    public UnsafeMessageHistory() throws IOException {
        this.file = new File("data/history.txt"); // reference to the path so we can check if it exists
        if(!file.exists()){
            file.createNewFile(); // if the file does not exist make the file
        }

        this.history = new ArrayList<>(); // history array to load the content from the file
        loadHistory();
    }

    public void saveHistory(String msg){
        try(BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file, true))) {
            bufferedWriter.write(msg);
            history.add(msg);
            bufferedWriter.newLine();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        } ;
    }

    public void loadHistory(){
        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
            String msg = bufferedReader.readLine();
            while(msg != null){
                history.add(msg);
                msg = bufferedReader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ArrayList<String> getRecent(){ // get <=50 most recent messages
        if(history.size() < 50){
            return new ArrayList<>(history);
        }
        return new ArrayList<> (history.subList(history.size() - 50, history.size()));
    }

    public ArrayList<String> getAll(){ // get all messages
        return new ArrayList<>(history);
    }

}
