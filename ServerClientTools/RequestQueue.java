package ServerClientTools;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;

public class RequestQueue {

    // One queue per service type (e.g. "CSV_Stats")
    private static final Map<String, LinkedBlockingQueue<PendingRequest>> queues =
        new ConcurrentHashMap<>();

    // Represents a single client request waiting for a node
    public static class PendingRequest {
        public final String payload;
        public volatile String response;
        private final Object lock = new Object();

        public PendingRequest(String payload) {
            this.payload = payload;
        }

        // ClientHandler calls this to block until the node responds
        public String waitForResponse() throws InterruptedException {
            synchronized (lock) {
                while (response == null) {
                    lock.wait();
                }
                return response;
            }
        }

        // ServiceNodeHandler calls this when it has a result
        public void complete(String result) {
            synchronized (lock) {
                this.response = result;
                lock.notifyAll();
            }
        }
    }

    // Submit a request for a given service — returns the ticket to wait on
    public static PendingRequest submit(String serviceName, String payload)
            throws InterruptedException {
        PendingRequest request = new PendingRequest(payload);
        getQueue(serviceName).put(request);
        return request;
    }

    // Service node calls this to grab its next job (blocks if queue is empty)
    public static PendingRequest take(String serviceName) throws InterruptedException {
        return getQueue(serviceName).take();
    }

    private static LinkedBlockingQueue<PendingRequest> getQueue(String serviceName) {
        return queues.computeIfAbsent(serviceName, k -> new LinkedBlockingQueue<>());
    }
}