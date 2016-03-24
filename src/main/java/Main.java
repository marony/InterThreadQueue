import com.lmax.disruptor.*;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.*;
import java.util.concurrent.*;

/**
 * Created by tada on 2016/03/24.
 */
public class Main {
    private final static int N = (int)Math.pow(2, 23);
    private final static long TIMEOUT = 1000L;

    // Disruptor用データ保持クラス
    static class Holder { public int n = -1; }
    // Disruptor用受信イベントハンドラ
    static class MyEventHandler implements EventHandler<Holder> {
        public volatile boolean finish = false;
        public int max = 0;

        @Override
        public void onEvent(Holder holder, long sequence, boolean endOfBatch) throws Exception {
            // 受信処理
            if (holder.n < 0)
                finish = true;
            if (holder.n > max)
                max = holder.n;
        }
    };

    public static void main(String[] args) {
        System.out.println(String.format("N = %d", N));

        // BlockingQueue(送信1:受信1)でテスト
        {
            final BlockingQueue<Integer> blockingQueue = new LinkedBlockingDeque<>(N);

            long nanos = System.nanoTime();
            // 受信処理
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                int max = 0;
                try {
                    Integer n = blockingQueue.poll(TIMEOUT, TimeUnit.MILLISECONDS);
                    while (n != null) {
                        if (n < 0)
                            break;
                        if (n > max)
                            max = n;
                        n = blockingQueue.poll(TIMEOUT, TimeUnit.MILLISECONDS);
                    }
                }
                catch (InterruptedException ex) {
                    System.out.println(String.format("error occurred: %s", ex.toString()));
                }
                return max;
            });
            try {
                // 送信処理
                for (int i = 0; i < N; ++i)
                    blockingQueue.put(i);
                blockingQueue.put(-1);

                // 受信スレッド終了待ち
                int n = future.get();
                System.out.println(String.format("BlockingQueue: %d millis",
                        TimeUnit.MILLISECONDS.convert(System.nanoTime() - nanos, TimeUnit.NANOSECONDS)));
            }
            catch (InterruptedException | ExecutionException ex) {
                System.out.println(String.format("error occurred: %s", ex.toString()));
            }
        }
        // Disruptor(送信1:受信1)でテスト
        {
            final ExecutorService threadPool = Executors.newCachedThreadPool();
            final Disruptor<Holder> rootDisruptor = new Disruptor<>(
                    () -> new Holder(),
                    N, threadPool,
                    ProducerType.MULTI, new TimeoutBlockingWaitStrategy(TIMEOUT, TimeUnit.MILLISECONDS));
            MyEventHandler eventHandler = new MyEventHandler();
            rootDisruptor.handleEventsWith(eventHandler);
            rootDisruptor.start();

            long nanos = System.nanoTime();
            try {
                // 送信処理
                for (int i = 0; i < N; ++i) {
                    final int j = i;
                    rootDisruptor.publishEvent((event, sequence) -> {
                        event.n = j;
                    });
                }
                rootDisruptor.publishEvent((event, sequence) -> {
                    event.n = -1;
                });

                // 受信スレッド終了待ち
                while (!eventHandler.finish)
                    Thread.yield();

                System.out.println(String.format("Disruptor: %d millis",
                        TimeUnit.MILLISECONDS.convert(System.nanoTime() - nanos, TimeUnit.NANOSECONDS)));
            }
            finally {
                try {
                    rootDisruptor.shutdown(TIMEOUT, TimeUnit.MILLISECONDS);
                    threadPool.shutdown();
                }
                catch (TimeoutException ex) {
                    System.out.println(String.format("error occurred: %s", ex.toString()));
                }
            }
        }
    }
}
