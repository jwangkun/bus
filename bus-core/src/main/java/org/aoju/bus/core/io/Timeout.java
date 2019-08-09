/*
 * The MIT License
 *
 * Copyright (c) 2017, aoju.org All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
*/
package org.aoju.bus.core.io;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

/**
 * A policy on how much time to spend on a task before giving up. When a task
 * times out, it is left in an unspecified state and should be abandoned. For
 * example, if reading from a source times out, that source should be closed and
 * the read should be retried later. If writing to a sink times out, the same
 * rules apply: close the sink and retry later.
 *
 * <h3>Timeouts and Deadlines</h3>
 * This class offers two complementary controls to define a timeout policy.
 *
 * <p><strong>Timeouts</strong> specify the maximum time to wait for a single
 * operation to complete. Timeouts are typically used to detect problems like
 * network partitions. For example, if a remote peer doesn't return <i>any</i>
 * data for ten seconds, we may assume that the peer is unavailable.
 *
 * <p><strong>Deadlines</strong> specify the maximum time to spend on a job,
 * composed of first or more operations. Use deadlines to set an upper bound on
 * the time invested on a job. For example, a battery-conscious app may limit
 * how much time it spends pre-loading content.
 *
 * @author aoju.org
 * @version 3.0.1
 * @group 839128
 * @since JDK 1.8
 */
public class Timeout {
    /**
     * An empty timeout that neither tracks nor detects timeouts. Use this when
     * timeouts aren't necessary, such as in implementations whose operations
     * do not block.
     */
    public static final Timeout NONE = new Timeout() {
        @Override
        public Timeout timeout(long timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public Timeout deadlineNanoTime(long deadlineNanoTime) {
            return this;
        }

        @Override
        public void throwIfReached() throws IOException {
        }
    };

    /**
     * True if {@code deadlineNanoTime} is defined. There is no equivalent to null
     * or 0 for {@link System#nanoTime}.
     */
    private boolean hasDeadline;
    private long deadlineNanoTime;
    private long timeoutNanos;

    public Timeout() {
    }

    /**
     * Wait at most {@code timeout} time before aborting an operation. Using a
     * per-operation timeout means that as long as forward progress is being made,
     * no sequence of operations will fail.
     *
     * <p>If {@code timeout == 0}, operations will run indefinitely. (Operating
     * system timeouts may still apply.)
     */
    public Timeout timeout(long timeout, TimeUnit unit) {
        if (timeout < 0) throw new IllegalArgumentException("timeout < 0: " + timeout);
        if (unit == null) throw new IllegalArgumentException("unit == null");
        this.timeoutNanos = unit.toNanos(timeout);
        return this;
    }

    /**
     * Returns the timeout in nanoseconds, or {@code 0} for no timeout.
     */
    public long timeoutNanos() {
        return timeoutNanos;
    }

    /**
     * Returns true if a deadline is enabled.
     */
    public boolean hasDeadline() {
        return hasDeadline;
    }

    /**
     * Returns the {@linkplain System#nanoTime() nano time} when the deadline will
     * be reached.
     *
     * @throws IllegalStateException if no deadline is set.
     */
    public long deadlineNanoTime() {
        if (!hasDeadline) throw new IllegalStateException("No deadline");
        return deadlineNanoTime;
    }

    /**
     * Sets the {@linkplain System#nanoTime() nano time} when the deadline will be
     * reached. All operations must complete before this time. Use a deadline to
     * set a maximum bound on the time spent on a sequence of operations.
     */
    public Timeout deadlineNanoTime(long deadlineNanoTime) {
        this.hasDeadline = true;
        this.deadlineNanoTime = deadlineNanoTime;
        return this;
    }

    /**
     * Set a deadline of now plus {@code duration} time.
     */
    public final Timeout deadline(long duration, TimeUnit unit) {
        if (duration <= 0) throw new IllegalArgumentException("duration <= 0: " + duration);
        if (unit == null) throw new IllegalArgumentException("unit == null");
        return deadlineNanoTime(System.nanoTime() + unit.toNanos(duration));
    }

    /**
     * Clears the timeout. Operating system timeouts may still apply.
     */
    public Timeout clearTimeout() {
        this.timeoutNanos = 0;
        return this;
    }

    /**
     * Clears the deadline.
     */
    public Timeout clearDeadline() {
        this.hasDeadline = false;
        return this;
    }

    /**
     * Throws an {@link InterruptedIOException} if the deadline has been reached or if the current
     * thread has been interrupted. This method doesn't detect timeouts; that should be implemented to
     * asynchronously abort an in-progress operation.
     */
    public void throwIfReached() throws IOException {
        if (Thread.interrupted()) {
            Thread.currentThread().interrupt(); // Retain interrupted status.
            throw new InterruptedIOException("interrupted");
        }

        if (hasDeadline && deadlineNanoTime - System.nanoTime() <= 0) {
            throw new InterruptedIOException("deadline reached");
        }
    }

    /**
     * Waits on {@code monitor} until it is notified. Throws {@link InterruptedIOException} if either
     * the thread is interrupted or if this timeout elapses before {@code monitor} is notified. The
     * caller must be synchronized on {@code monitor}.
     */
    public final void waitUntilNotified(Object monitor) throws InterruptedIOException {
        try {
            boolean hasDeadline = hasDeadline();
            long timeoutNanos = timeoutNanos();

            if (!hasDeadline && timeoutNanos == 0L) {
                monitor.wait(); // There is no timeout: wait forever.
                return;
            }

            // Compute how long we'll wait.
            long waitNanos;
            long start = System.nanoTime();
            if (hasDeadline && timeoutNanos != 0) {
                long deadlineNanos = deadlineNanoTime() - start;
                waitNanos = Math.min(timeoutNanos, deadlineNanos);
            } else if (hasDeadline) {
                waitNanos = deadlineNanoTime() - start;
            } else {
                waitNanos = timeoutNanos;
            }

            // Attempt to wait that long. This will break out early if the monitor is notified.
            long elapsedNanos = 0L;
            if (waitNanos > 0L) {
                long waitMillis = waitNanos / 1000000L;
                monitor.wait(waitMillis, (int) (waitNanos - waitMillis * 1000000L));
                elapsedNanos = System.nanoTime() - start;
            }

            // Throw if the timeout elapsed before the monitor was notified.
            if (elapsedNanos >= waitNanos) {
                throw new InterruptedIOException("timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Retain interrupted status.
            throw new InterruptedIOException("interrupted");
        }
    }
    
}
