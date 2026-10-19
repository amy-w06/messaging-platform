package features.history;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class MessageHistoryTest {
    public static void main(String[] args) throws IOException, InterruptedException {
//        UnsafeMessageHistory msgHis = new UnsafeMessageHistory();
        SafeMessageHistory msgHis = new SafeMessageHistory();

        // Test to show writing has concurrency issues.
        // The arraylist size is different because threads overwriting each other
//        CountDownLatch latch = new CountDownLatch(200);
//        for(int i = 1; i <= 200; i++){
//            int finalI = i;
//            new Thread(() -> {
//                for(int j =  0; j < 50; j++){
//                    msgHis.saveHistory("Message saved from " + finalI + "This is message number " + j);
//                }
//                latch.countDown();
//            }).start();
//
//        }
//        latch.await();
//        System.out.println("list size " + msgHis.getAll().size());
//        System.out.println("expected 10000");

        // Test to show writing & reading has concurrency issues.
        // Thread calls getRecent (reading) will another thread is saving (writing)
        // The list changed during the subList operation and Java detected it and crashed.
        CountDownLatch latch = new CountDownLatch(400);

        for (int i = 0; i < 200; i++) {
            int finalI = i;
            new Thread(() -> {
                for(int j = 0; j < 50; j++){
                    msgHis.saveHistory("message from " + finalI + "message number " + j);
                }
                latch.countDown();
            }).start();

            new Thread(() -> {
                msgHis.getRecent();
                latch.countDown();
            }).start();
        }

        latch.await();
    }
}
