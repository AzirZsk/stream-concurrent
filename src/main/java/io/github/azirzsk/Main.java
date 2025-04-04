package io.github.azirzsk;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author azir
 * @since 2025/04/04
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class Main {

    private List<Foo> fooList = new ArrayList<>();

    private ExecutorService executorService;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Main.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    @Setup
    public void setup() {
        fooList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            fooList.add(new Foo());
        }
        // 10核心线程 200最大线程 60秒超时 任务满时，重复尝试添加
        executorService = new ThreadPoolExecutor(10, 200, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100), (r, executor) -> {
            // 队列满时，循环添加
            if (!executor.isShutdown()) {
                try {
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @TearDown
    public void tearDown() {
        fooList.clear();
        executorService.shutdown();
    }

    @Benchmark
    public void normal() {
        for (Foo foo : fooList) {
            foo.init();
        }
    }

    @Benchmark
    public void normalCompletableFuture() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Foo foo : fooList) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(foo::init, executorService);
            futures.add(future);
        }
        futures.forEach(CompletableFuture::join);
    }

    @Benchmark
    public void streamCompletableFuture() {
        fooList.stream()
                .map(foo -> CompletableFuture.runAsync(foo::init, executorService))
                .forEach(CompletableFuture::join);
    }

    public static class Foo {

        public void init() {
            // 模拟耗时
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}