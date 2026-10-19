package features.friends;

import java.util.concurrent.CountDownLatch;

public class FriendsTest {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1000);  // used to count the number of threads executed

        UnsafeFriends unsafe = new  UnsafeFriends();
        SafeFriends safe = new SafeFriends();
        // create a new thread and send a request to the same person
        for(int i=0; i<1000; i++){
            String user = "test"+i;
            new Thread(()-> {
                unsafe.sendRequest("receiver", user);
                latch.countDown();  // decrement the number of executed threads
            }).start();
        }

        latch.await();

        int amount = unsafe.getIncoming("receiver").size();  // amount of incoming requests
        long ran = 1000 - latch.getCount();  // amount of threads that were executed
        System.out.println("Expected: " + ran);
        System.out.println("Actual: "+ amount);
    }
}
