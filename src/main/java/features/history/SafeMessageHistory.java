package features.history;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SafeMessageHistory implements MessageHistory {
    private File file;
    private ArrayList<String> history;
    private ReadWriteLock readWriteLock;
    private Lock readLock;
    private Lock writeLock;

    public SafeMessageHistory() throws IOException {
        this.file = new File("data/history.txt");
        if(!file.exists()){
            file.createNewFile();
        }
        this.history = new ArrayList<>();
        this.readWriteLock = new ReentrantReadWriteLock();
        readLock = readWriteLock.readLock();
        writeLock = readWriteLock.writeLock();
        loadHistory();
    }

    @Override
    public void saveHistory(String msg) {
        writeLock.lock();
        try(BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file, true))){
            bufferedWriter.write(msg);
            history.add(msg);
            bufferedWriter.newLine();

        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            writeLock.unlock();
        }
    }

    @Override
    public void loadHistory() {
        writeLock.lock();
        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
            String msg = bufferedReader.readLine();
            while(msg != null){
                history.add(msg);
                msg = bufferedReader.readLine();
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            writeLock.unlock();
        }
    }

    @Override
    public ArrayList<String> getRecent() {
        readLock.lock();
        try {
            if (history.size() < 50) {
                return new ArrayList<>(history);
            }
            return new ArrayList<>(history.subList(history.size() - 50, history.size()));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public ArrayList<String> getAll(){ // get all messages
        readLock.lock();
        try{
            return new ArrayList<>(history);
        }
        finally {
            readLock.unlock();
        }




    }

}
