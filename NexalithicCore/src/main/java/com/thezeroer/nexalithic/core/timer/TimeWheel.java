package com.thezeroer.nexalithic.core.timer;

import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * 时间轮
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/25
 * @version 1.0.0
 */
public abstract class TimeWheel<W extends TimeWheel.ScheduleWrapper<W>> {
    private static final Logger logger = LoggerFactory.getLogger(TimeWheel.class);
    protected final long tick;
    protected final int mask;
    protected final AtomicReference<W>[] buckets;
    protected final WrapperPool<W> wrapperPool;
    private final Worker worker;
    private final AtomicLong currentTick = new AtomicLong(0);
    private final AtomicLong targetTick = new AtomicLong(0);

    public TimeWheel(long tick, int slot, WrapperPool<W> wrapperPool) {
        this(tick, slot, wrapperPool, null);
    }
    @SuppressWarnings("unchecked")
    public TimeWheel(long tick, int slot, WrapperPool<W> wrapperPool, String name) {
        this.tick = tick;
        this.mask = normalize(slot) - 1;
        this.wrapperPool = wrapperPool;
        this.buckets = new AtomicReference[mask + 1];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new AtomicReference<>();
        }
        if (name != null) {
            this.worker = new Worker(name);
        } else {
            this.worker = new Worker();
        }
    }

    protected void mountWrapper(W wrapper) {
        long ticks = Math.max(0, wrapper.getDeadline() - System.currentTimeMillis()) / tick;
        wrapper.setRemainingRounds((int) (ticks / buckets.length));
        mergeBack((int) ((targetTick.get() + ticks) & mask), wrapper, wrapper);
    }

    protected abstract void onTrigger(W wrapper);

    private int normalize(int slot) {
        int n = slot - 1;
        n |= n >>> 1; n |= n >>> 2; n |= n >>> 4; n |= n >>> 8; n |= n >>> 16;
        return n < 0 ? 1 : n + 1;
    }

    @SuppressWarnings("unchecked")
    private void tick(int slot) {
        AtomicReference<W> bucket = buckets[slot];
        W current = bucket.getAndSet(null);
        W unexpiredHead = null, unexpiredTail = null;
        while (current != null) {
            W next = (W) current.getNext();
            if (current.remainingRounds() <= 0) {
                try {
                    onTrigger(current);
                } catch (Exception e) {
                    handleTriggerError(current, e);
                } finally {
                    current.recycle();
                }
            } else {
                current.setNext(unexpiredHead);
                if (unexpiredHead != null) {
                    unexpiredHead.setPrev(current);
                } else {
                    unexpiredTail = current;
                }
                current.setPrev(null);
                unexpiredHead = current;
            }
            current = next;
        }
        if (unexpiredHead != null) {
            mergeBack(slot, unexpiredHead, unexpiredTail);
        }
    }

    private void handleTriggerError(W current, Exception e) {
        logger.error("TimeWheel task execution failed. Task: {}", current, e);
    }

    private void mergeBack(int slot, W unexpiredHead, W unexpiredTail) {
        W currentHead;
        do {
            currentHead = buckets[slot].get();
            unexpiredTail.setNext(currentHead);
            if (currentHead != null) {
                currentHead.setPrev(unexpiredTail);
            }
        } while (!buckets[slot].compareAndSet(currentHead, unexpiredHead));
    }

    public static abstract class ScheduleWrapper<W extends ScheduleWrapper<W>> extends SelfStaticWrapperPool.InteriorRecyclableWrapper<W> {
        private volatile ScheduleWrapper<W> prev, next;
        private volatile int remainingRounds;

        void setRemainingRounds(int remainingRounds) {
            this.remainingRounds = remainingRounds;
        }
        int remainingRounds() {
            return remainingRounds--;
        }

        public abstract long getDeadline();

        public void setPrev(ScheduleWrapper<W> prev) {
            this.prev = prev;
        }
        public void setNext(ScheduleWrapper<W> next) {
            this.next = next;
        }
        public ScheduleWrapper<W> getPrev() {
            return prev;
        }
        public ScheduleWrapper<W> getNext() {
            return next;
        }

        @Override
        public void onRecycle() {
            prev = null;
            next = null;
            remainingRounds = 0;
        }
    }

    private class Worker extends Thread {
        private final long tickNanos = tick * 1_000_000L;
        private final long startTime;

        public Worker() {
            setDaemon(true);
            setName("TimeWheel-Worker");
            startTime = System.nanoTime();
            start();
        }
        public Worker(String name) {
            setDaemon(true);
            setName("TimeWheel-Worker: " + name);
            startTime = System.nanoTime();
            start();
        }

        @Override
        public void run() {
            logger.debug("[{}] started", getName());
            while (!isInterrupted()) {
                long now = waitNextTick();
                if (now < 0) {
                    break;
                }
                long target = now / tickNanos;
                targetTick.set(target);
                long current = currentTick.get();
                while (current <= target) {
                    tick((int) (current & mask));
                    current++;
                }
                currentTick.set(current);
            }
            logger.debug("[{}] stopped", getName());
        }

        private long waitNextTick() {
            long deadline = tickNanos * (currentTick.get() + 1);
            while (true) {
                long now = System.nanoTime() - startTime;
                long sleep = deadline - now;
                if (sleep <= 0) {
                    return now;
                }
                if (sleep > 1_000_000L) {
                    LockSupport.parkNanos(sleep);
                } else {
                    Thread.onSpinWait();
                }
                if (Thread.interrupted()) {
                    return -1;
                }
            }
        }
    }
}
