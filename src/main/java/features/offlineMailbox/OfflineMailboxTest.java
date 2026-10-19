package features.offlineMailbox;

import java.util.concurrent.CountDownLatch;
import java.util.Queue;

public class OfflineMailboxTest {

    public static void main(String[] args) throws InterruptedException {
        // UnsafeOfflineMailbox mailbox = new UnsafeOfflineMailbox();
        SafeOfflineMailbox mailbox = new SafeOfflineMailbox();
        int threads = 200;
        int messagesPerThread = 200;
        CountDownLatch latch = new CountDownLatch(threads);

        String user = "mia";

        for (int i = 0; i < threads; i++) {
            int id = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        mailbox.addMessage(user, "sender" + id, "msg-" + j);
                    }
                } catch (Exception e) {
                    System.out.println("Exception occurred: " + e);
                }
                latch.countDown();
            }).start();
        }

        latch.await();

        Queue<String[]> messages = mailbox.clearMessages(user);

        int expected = threads * messagesPerThread;
        int actual = messages.size();

        System.out.println("Expected messages: " + expected);
        System.out.println("Actual messages:   " + actual);

    }
}

