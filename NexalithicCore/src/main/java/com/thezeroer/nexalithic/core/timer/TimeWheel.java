package com.thezeroer.nexalithic.core.timer;

import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 时间轮
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/25
 * @version 1.0.0
 */
public abstract class TimeWheel<W extends TimeWheel.ScheduleWrapper<W>> {
    /**
     * 任务处理配额位移量。
     * 结果为 1/(2^shift)。
     * 例如：2 代表 25% 的 tick 时间，3 代表 12.5%。
     */
    public static final NexalithicOption<Integer> TickQuotaShift = NexalithicOption.create("TimeWheel_TickQuotaShift", 2);
    private static final Logger logger = LoggerFactory.getLogger(TimeWheel.class);
    protected final long tick;
    protected final int tickShift;
    protected final int slotMask;
    protected final int slotShift;
    protected final W[] buckets;
    protected final WrapperPool<W> wrapperPool;
    protected final MpscUnboundedArrayQueue<W> queue = new MpscUnboundedArrayQueue<>(1024);
    private final Worker worker;
    private final AtomicLong currentTick = new AtomicLong(0);
    private final AtomicLong targetTick = new AtomicLong(0);

    /**
     * 时间轮构造函数
     *
     * @param tick        每格的时间跨度（毫秒）。内部会通过 normalize 强制转换为 2 的幂。
     * @param slot        时间轮的槽位数。内部会通过 normalize 强制转换为 2 的幂。
     * @param wrapperPool 包装器对象池，用于实现 ScheduleWrapper 的复用，降低 GC 频率。
     * @param name        Worker 线程的名称，便于在 JVisualVM 或日志中识别。
     */
    @SuppressWarnings("unchecked")
    public TimeWheel(long tick, int slot, WrapperPool<W> wrapperPool, String name) {
        long normalizedTick = normalize(tick);
        int normalizedSlot = normalize(slot);
        this.tick = normalizedTick;
        this.tickShift = Long.numberOfTrailingZeros(normalizedTick);
        this.slotMask = normalizedSlot - 1;
        this.slotShift = Long.numberOfTrailingZeros(normalizedSlot);
        this.wrapperPool = wrapperPool;
        this.buckets = (W[]) new ScheduleWrapper[normalizedSlot];
        this.worker = new Worker(name);
    }

    public void start () {
        worker.start();
    }
    public void stop() {
        worker.interrupt();
    }

    protected abstract boolean onTrigger(W wrapper);

    private int normalize(int value) {
        if (value <= 1) {
            return 1;
        }
        int n = Integer.highestOneBit(value);
        return n == value ? n : n << 1;
    }
    private long normalize(long value) {
        if (value <= 1) {
            return 1;
        }
        long n = Long.highestOneBit(value);
        return n == value ? n : n << 1;
    }

    @SuppressWarnings("unchecked")
    private void tick(int slot) {
        long quotaNanos = (tick * 1_000_000L) >> Interior.TickQuotaShift;
        long startNanos = System.nanoTime();
        boolean hasTriggered;
        do {
            hasTriggered = false;
            transferQueueToBuckets();
            W current = buckets[slot];
            buckets[slot] = null;
            W unexpiredHead = null, unexpiredTail = null;
            while (current != null) {
                W next = (W) current.getNext();
                if (current.isCancelled()) {
                    current.recycle();
                } else {
                    if (current.remainingRounds() < 0) {
                        try {
                            if (onTrigger(current)) {
                                current.recycle();
                                hasTriggered = true; // 标记本次循环有任务触发
                            }
                        } catch (Exception e) {
                            handleTriggerError(current, e);
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
                }
                current = next;
            }
            if (unexpiredHead != null) {
                mergeBack(slot, unexpiredHead, unexpiredTail);
            }
        } while (hasTriggered && buckets[slot] != null && (System.nanoTime() - startNanos) < quotaNanos);
    }
    private void transferQueueToBuckets() {
        long target = targetTick.get();
        queue.drain((wrapper -> {
            long deadline = (wrapper.getExpiryTime() - worker.startTimeMillis) >> tickShift;
            if (deadline <= target) {
                if (wrapper.isCancelled()) {
                    wrapper.recycle();
                } else {
                    try {
                        if (onTrigger(wrapper)) {
                            wrapper.recycle();
                        }
                    } catch (Exception e) {
                        handleTriggerError(wrapper, e);
                        wrapper.recycle();
                    }
                }
            } else {
                wrapper.setRemainingRounds((int) ((deadline - target) >> slotShift));
                mergeBack((int) (deadline & slotMask), wrapper, wrapper);
            }
        }), 1024);
    }
    private void mergeBack(int slot, W head, W tail) {
        W current = buckets[slot];
        tail.setNext(current);
        if (current != null) {
            current.setPrev(tail);
        }
        buckets[slot] = head;
    }

    private void handleTriggerError(W current, Exception e) {
        logger.error("TimeWheel task execution failed. Task: {}", current.toString(), e);
    }

    public static abstract class ScheduleWrapper<W extends ScheduleWrapper<W>> extends SelfStaticWrapperPool.InteriorRecyclableWrapper<W> {
        private volatile ScheduleWrapper<W> prev, next;
        private volatile int remainingRounds;

        void setRemainingRounds(int remainingRounds) {
            this.remainingRounds = remainingRounds;
        }
        int remainingRounds() {
            return --remainingRounds;
        }

        public abstract long getExpiryTime();
        public abstract boolean isCancelled();

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
        private final long startTimeNanos = System.nanoTime();
        private final long startTimeMillis = System.currentTimeMillis();

        public Worker(String name) {
            setDaemon(true);
            if (name == null) {
                setName("TimeWheel-Worker");
            } else {
                setName("TimeWheel-Worker: " + name);
            }
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
                    tick((int) (current & slotMask));
                    current++;
                }
                currentTick.set(current);
            }
            logger.debug("[{}] stopped", getName());
        }

        private long waitNextTick() {
            long deadline = tickNanos * (currentTick.get() + 1);
            while (true) {
                long now = System.nanoTime() - startTimeNanos;
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

    private static class Interior {
        public static final int TickQuotaShift = TimeWheel.TickQuotaShift.value();
    }
}
