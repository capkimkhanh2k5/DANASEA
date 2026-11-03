package com.danasea.backend.configs;

import org.springframework.core.task.AsyncTaskExecutor;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tracks real asynchronous tasks, including tasks submitted by another tracked task.
 * Shared test executor for audit and financial integration tests.
 */
public class TrackingAsyncTestExecutor implements AsyncTaskExecutor, AutoCloseable {
    private final ExecutorService delegate = Executors.newFixedThreadPool(4);
    private final Phaser tasks = new Phaser(1);
    private int lastArrivedPhase = -1;

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        tasks.register();
        try {
            delegate.execute(() -> {
                try {
                    task.run();
                } finally {
                    tasks.arriveAndDeregister();
                }
            });
        } catch (RuntimeException | Error exception) {
            tasks.arriveAndDeregister();
            throw exception;
        }
    }

    public synchronized void awaitAll(Duration timeout) throws InterruptedException, TimeoutException {
        int phase = tasks.getPhase();
        if (lastArrivedPhase != phase) {
            tasks.arrive();
            lastArrivedPhase = phase;
        }
        tasks.awaitAdvanceInterruptibly(phase, timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void close() {
        try {
            awaitAll(Duration.ofSeconds(10));
            delegate.shutdown();
            if (!delegate.awaitTermination(10, TimeUnit.SECONDS)) {
                delegate.shutdownNow();
                throw new IllegalStateException("Test executor did not terminate");
            }
        } catch (InterruptedException exception) {
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing test executor", exception);
        } catch (TimeoutException exception) {
            delegate.shutdownNow();
            throw new IllegalStateException("Timed out while closing test executor", exception);
        }
    }
}
