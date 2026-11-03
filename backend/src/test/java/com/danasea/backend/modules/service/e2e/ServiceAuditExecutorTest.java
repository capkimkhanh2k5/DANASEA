package com.danasea.backend.modules.service.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ServiceAuditExecutorTest {
    @Test
    void completionWaitsForARealBlockedTaskSubmittedThroughAsyncTaskExecutor() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch waiting = new CountDownLatch(1);
        try (var executor = new BaseServiceE2ETest.TrackingAuditExecutor();
                var observer = Executors.newSingleThreadExecutor()) {
            try {
                var task = executor.submit(() -> {
                    started.countDown();
                    await(release);
                    return Thread.currentThread().threadId();
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var completion = observer.submit(() -> {
                    waiting.countDown();
                    executor.awaitAll(Duration.ofSeconds(5));
                    return true;
                });
                assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
                assertThrows(TimeoutException.class, () -> completion.get(100, TimeUnit.MILLISECONDS));
                release.countDown();
                assertThat(completion.get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(task.get(5, TimeUnit.SECONDS)).isNotEqualTo(Thread.currentThread().threadId());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void completionIncludesANestedTaskEvenAfterItsParentReturns() throws Exception {
        CountDownLatch parentStarted = new CountDownLatch(1);
        CountDownLatch releaseParent = new CountDownLatch(1);
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        CountDownLatch childFinished = new CountDownLatch(1);
        CountDownLatch waiting = new CountDownLatch(1);
        try (var executor = new BaseServiceE2ETest.TrackingAuditExecutor();
                var observer = Executors.newSingleThreadExecutor()) {
            try {
                var parent = executor.submit(() -> {
                    parentStarted.countDown();
                    await(releaseParent);
                    executor.submit(() -> {
                        childStarted.countDown();
                        await(releaseChild);
                        childFinished.countDown();
                    });
                });
                assertThat(parentStarted.await(5, TimeUnit.SECONDS)).isTrue();
                var completion = observer.submit(() -> {
                    waiting.countDown();
                    executor.awaitAll(Duration.ofSeconds(5));
                    return true;
                });
                assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
                releaseParent.countDown();
                assertThat(childStarted.await(5, TimeUnit.SECONDS)).isTrue();
                parent.get(5, TimeUnit.SECONDS);
                assertThrows(TimeoutException.class, () -> completion.get(100, TimeUnit.MILLISECONDS));
                releaseChild.countDown();
                assertThat(completion.get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(childFinished.getCount()).isZero();
            } finally {
                releaseParent.countDown();
                releaseChild.countDown();
            }
        }
    }

    @Test
    void rejectedSubmissionDoesNotLeaveAnOutstandingTrackedTask() throws Exception {
        var executor = new BaseServiceE2ETest.TrackingAuditExecutor();
        executor.close();
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
        executor.awaitAll(Duration.ofSeconds(1));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test task was not released");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test task interrupted", exception);
        }
    }
}
