package com.thezeroer.nexalithic.core.timer;

import com.thezeroer.nexalithic.core.recyclable.WrapperPool;

/**
 * 通用型时间轮
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/25
 * @version 1.0.0
 */
public class GenericTimeWheel extends TimeWheel<GenericTimeWheel.GenericScheduleWrapper<? extends Expirable>> {

    public GenericTimeWheel(long tick, int slot, int tickQuotaShift, int waitQueueChunkSize, WrapperPool<GenericTimeWheel.GenericScheduleWrapper<? extends Expirable>> wrapperPool, String name) {
        super(tick, slot, tickQuotaShift, waitQueueChunkSize, wrapperPool, name);
    }

    @SuppressWarnings("unchecked")
    public <T extends Expirable> void schedule(T expirable, TimerExecutor<T> executor) {
        waitQueue.offer(((GenericScheduleWrapper<T>) wrapperPool.acquire()).wrap(expirable, executor));
    }

    @Override
    protected boolean onTrigger(GenericScheduleWrapper<?> wrapper) {
        return privateOnTrigger(wrapper);
    }
    private <T extends Expirable> boolean privateOnTrigger(GenericScheduleWrapper<T> wrapper) {
        T expirable = wrapper.getExpirable();
        if (expirable.onExpiryTriggered()) {
            wrapper.getExecutor().trigger(expirable);
            return true;
        } else {
            waitQueue.offer(wrapper);
            return false;
        }
    }

    public static class GenericScheduleWrapper<E extends Expirable> extends ScheduleWrapper<GenericTimeWheel.GenericScheduleWrapper<? extends Expirable>> {
        private volatile E expirable;
        private volatile TimerExecutor<E> executor;

        public GenericScheduleWrapper<E> wrap(E expirable, TimerExecutor<E> executor) {
            this.expirable = expirable;
            this.executor = executor;
            return this;
        }

        public E getExpirable() {
            return expirable;
        }
        public TimerExecutor<E> getExecutor() {
            return executor;
        }

        @Override
        public long getExpiryTime() {
            return expirable.getExpiryTime();
        }

        @Override
        public boolean isCancelled() {
            return expirable.isCancelled();
        }

        @Override
        public void onRecycle() {
            super.onRecycle();
            expirable = null;
            executor = null;
        }
    }
}
