package com.danasea.backend.shared.infrastructure.http;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.web.client.ResourceAccessException;

/** A request-scoped deadline for interruptible JDK HTTP calls, including reading response bodies. */
public final class HttpRequestDeadline implements AutoCloseable {
    private static final ThreadLocal<HttpRequestDeadline> CURRENT = new ThreadLocal<>();
    private final HttpRequestDeadline previous;
    private final long deadline;

    private HttpRequestDeadline(Duration duration) {
        previous = CURRENT.get();
        long requested = System.nanoTime() + duration.toNanos();
        deadline = previous == null ? requested : Math.min(previous.deadline, requested);
        CURRENT.set(this);
    }

    public static HttpRequestDeadline open(Duration duration) { return new HttpRequestDeadline(duration); }

    public static <T> T execute(Duration maximum, Supplier<T> request) {
        var scope = CURRENT.get();
        long remaining = scope == null ? maximum.toNanos()
                : Math.min(maximum.toNanos(), scope.deadline - System.nanoTime());
        if (remaining <= 0) throw new ResourceAccessException("HTTP request budget exhausted");
        var task = new FutureTask<T>(request::get);
        Thread.startVirtualThread(task);
        try {
            return task.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            throw new ResourceAccessException("HTTP request budget exhausted");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResourceAccessException("HTTP request interrupted");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            if (exception.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException("HTTP request failed", exception.getCause());
        } finally {
            if (!task.isDone()) task.cancel(true);
        }
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
