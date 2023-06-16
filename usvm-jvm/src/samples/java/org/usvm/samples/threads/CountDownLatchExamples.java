package org.usvm.samples.threads;

import static org.usvm.api.mock.UMockKt.assume;

import java.util.concurrent.CountDownLatch;

public class CountDownLatchExamples {
    long getAndDown(CountDownLatch countDownLatch) {
        assume(countDownLatch != null);

        final long count = countDownLatch.getCount();

        if (count < 0) {
            // Unreachable
            return -1;
        }

        countDownLatch.countDown();
        final long nextCount = countDownLatch.getCount();

        if (nextCount < 0) {
            // Unreachable
            return -2;
        }

        if (count == 0) {
            // Could not differs from 0 too
            return nextCount;
        }

        if (count - nextCount != 1) {
            // Unreachable
            return -3;
        }

        return nextCount;
    }
}
