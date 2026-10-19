package features.friends;
import java.util.*;
import java.io.*;

public class UnsafeFriends implements Friends {
    private Map<String, Set<String>> userFriends;         // stores the user then a set of their friends
    private Map<String, Set<String>> incomingRequests;    // stores the user then any requests from other users
    private Map<String, Set<String>> outgoingRequests;    // stores the user then any requests to other users

    public UnsafeFriends() {
        this.userFriends = new HashMap<>();
        this.incomingRequests = new HashMap<>();
        this.outgoingRequests = new HashMap<>();
        loadFriends();
    }

    public Map<String, Set<String>> getAllFriends(){
        return userFriends;
    }

    public Map<String, Set<String>> getAllIncoming(){
        return incomingRequests;
    }

    public Set<String> getIncoming(String user){ return incomingRequests.get(user); }

    public Map<String, Set<String>> getAllOutgoing(){
        return outgoingRequests;
    }

    public Set<String> getOutgoing(String user){ return outgoingRequests.get(user); }

    public Set<String> getFriends(String user){ return userFriends.get(user); }

    // read all friends from the file
    public void loadFriends(){
        try(Scanner scanner = new Scanner(new File("friends.txt"))){
            // each line is of the format map,user,friend1,friend2, etc.
            while(scanner.hasNextLine()){
                String[] line = scanner.nextLine().split(",");
                String type = line[0];  // this is what map the line will be written into
                String user = line[1];
                Set<String> friends = new HashSet<>();
                friends.addAll(Arrays.asList(line).subList(2, line.length));

                switch (type){
                    case "friends":
                        userFriends.put(user, friends);  // sets user as key and friends as value
                        break;
                    case "incoming":
                        incomingRequests.put(user, friends);
                        break;
                    case "outgoing":
                        outgoingRequests.put(user, friends);
                        break;
                }
            }
            scanner.close();
        }
        catch(FileNotFoundException e){
            System.out.println(e);
        }
    }

    // write all the maps to the file
    public void saveFriends(){
        try(FileWriter writer = new FileWriter("friends.txt")) {
            // writing all friends
            for(String user : userFriends.keySet()){
                writer.write("friends,");
                Set<String> friends = userFriends.get(user);
                writer.write(user);
                for(String friend : friends){
                    writer.write(","+friend);
                }
                writer.write("\n");
            }
            // writing all incoming requests
            for(String user : incomingRequests.keySet()){
                writer.write("incoming,");
                Set<String> friends = incomingRequests.get(user);
                writer.write(user);
                for(String friend : friends){
                    writer.write(","+friend);
                }
                writer.write("\n");
            }
            // writing all outgoing requests
            for(String user : outgoingRequests.keySet()){
                writer.write("outgoing,");
                Set<String> friends = outgoingRequests.get(user);
                writer.write(user);
                for(String friend : friends){
                    writer.write(","+friend);
                }
                writer.write("\n");
            }
            writer.close();
        }
        catch(IOException e){
            System.out.println(e);
        }
    }

    // sends a request to another user (when called 'to' is just current user)
    public void sendRequest(String to, String from){
        // if users aren't on the file
        if(!userFriends.containsKey(to)){
            userFriends.put(to, new HashSet<String>());
        }
        if(!userFriends.containsKey(from)){
            userFriends.put(from, new HashSet<String>());
        }
        // check that they aren't already friends
        if(!userFriends.get(to).contains(from)) {  // aren't friends
            // check if "from" has already sent a request to "to"
            if(outgoingRequests.containsKey(from) && outgoingRequests.get(from).contains(to)){
                System.out.println(to+" is already in request");
                return;
            }
            // check if "to" has already sent a request to "from"
            if(outgoingRequests.containsKey(to) && outgoingRequests.get(to).contains(from)){
                System.out.println(to+" has already sent a request");
                return;
            }
            // if map doesn't evist make it
            if (!outgoingRequests.containsKey(from)) {
                outgoingRequests.put(from, new HashSet<>());
            }
            if (!incomingRequests.containsKey(to)) {
                incomingRequests.put(to, new HashSet<>());
            }

            // add the request
            outgoingRequests.get(from).add(to);
            incomingRequests.get(to).add(from);
//            System.out.println("incoming "+ incomingRequests);
//            System.out.println("outgoing "+ outgoingRequests);
            saveFriends();
        }
    }

    // accepts a request from another user (when called 'to' is just current user)
    public void acceptRequest(String to, String from){
        if(incomingRequests.containsKey(to)){
            if(incomingRequests.get(to).contains(from)){
                // remove the request from pending
                incomingRequests.get(to).remove(from);
                outgoingRequests.get(from).remove(to);
                // add the friendship
                if(userFriends.containsKey(to)) {
                    userFriends.get(to).add(from);
                    userFriends.get(from).add(to);
                }
                else {
                    userFriends.put(to, new HashSet<>());
                    userFriends.get(from).add(to);
                }
                saveFriends();
            }
        }
        System.out.println("friends "+ userFriends);
    }

    // denys a request from another user (when called 'to' is just current user)
    public void denyRequest(String to, String from){
        if(incomingRequests.containsKey(to)){
            if(incomingRequests.get(to).contains(from)){
                incomingRequests.get(to).remove(from);
                outgoingRequests.get(from).remove(to);
                saveFriends();
            }
            else {System.out.println("User has not sent request to you");}
        }
        System.out.println(incomingRequests);
        System.out.println(outgoingRequests);
    }
}
