package com.thezeroer.nexalithic.core.infra.timer;

import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.infra.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.infra.recyclable.WrapperPool;
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
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, TimeWheel.class);
    public static class Options extends OptionsDefinition {
        public final NexalithicOption<Long> Tick = Tick();
        public final NexalithicOption<Integer> Slot = Slot();
        /**
         * 任务处理配额位移量。
         * 结果为 1/(2^shift)。
         * 例如：2 代表 25% 的 tick 时间，3 代表 12.5%。
         */
        public final NexalithicOption<Integer> TickQuotaShift = TickQuotaShift();
        public final NexalithicOption<Integer> WaitQueue_ChunkSize = WaitQueue_ChunkSize();
        public final NexalithicOption<Integer> WrapperPool_Capacity = WrapperPool_Capacity();
        protected Options(Class<?> holder) {
            super(holder);
        }
        protected NexalithicOption<Long> Tick() {
            return NexalithicOption.create(1024L, OptionValidator.positive());
        }
        protected NexalithicOption<Integer> Slot() {
            return NexalithicOption.create(64, OptionValidator.positive());
        }
        protected NexalithicOption<Integer> TickQuotaShift() {
            return NexalithicOption.create(2, OptionValidator.min(1));
        }
        protected NexalithicOption<Integer> WaitQueue_ChunkSize() {
            return NexalithicOption.create(1024, OptionValidator.positive());
        }
        protected NexalithicOption<Integer> WrapperPool_Capacity() {
            return NexalithicOption.create(256, OptionValidator.positive());
        }
    }
    private static final Logger logger = LoggerFactory.getLogger(TimeWheel.class);
    protected final WrapperPool<W> wrapperPool;
    protected final MpscUnboundedArrayQueue<W> waitQueue;
    private final long tick;
    private final int tickShift;
    private final int slotMask;
    private final int slotShift;
    private final W[] buckets;
    private final Worker worker;
    private final AtomicLong currentTick = new AtomicLong(0);
    private final AtomicLong targetTick = new AtomicLong(0);
    private final int tickQuotaShift;

    /**
     * 时间轮构造函数
     *
     * @param tick        每格的时间跨度（毫秒）。内部会通过 normalize 强制转换为 2 的幂。
     * @param slot        时间轮的槽位数。内部会通过 normalize 强制转换为 2 的幂。
     * @param wrapperPool 包装器对象池，用于实现 ScheduleWrapper 的复用，降低 GC 频率。
     * @param name        Worker 线程的名称，便于在 JVisualVM 或日志中识别。
     */
    @SuppressWarnings("unchecked")
    public TimeWheel(long tick, int slot, int tickQuotaShift, int waitQueueChunkSize, WrapperPool<W> wrapperPool, String name) {
        long normalizedTick = normalize(tick);
        int normalizedSlot = normalize(slot);
        this.tick = normalizedTick;
        this.tickShift = Long.numberOfTrailingZeros(normalizedTick);
        this.slotMask = normalizedSlot - 1;
        this.slotShift = Long.numberOfTrailingZeros(normalizedSlot);
        this.wrapperPool = wrapperPool;
        this.waitQueue = new MpscUnboundedArrayQueue<>(waitQueueChunkSize);
        this.buckets = (W[]) new ScheduleWrapper[normalizedSlot];
        this.tickQuotaShift = tickQuotaShift;
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
        long quotaNanos = (tick * 1_000_000L) >> tickQuotaShift;
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
        waitQueue.drain((wrapper -> {
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
}
