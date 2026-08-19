package com.thezeroer.nexalithic.core.infra.timer;

import com.thezeroer.nexalithic.core.infra.recyclable.GenericWrapperPool;
import com.thezeroer.nexalithic.core.infra.recyclable.WrapperPool;

/**
 * 通用型时间轮
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/25
 * @version 1.0.0
 */
public class GenericTimeWheel extends TimeWheel<GenericTimeWheel.GenericScheduleWrapper<? extends Expirable>> {

    public GenericTimeWheel(long tick, int slot, int tickQuotaShift, int waitQueueChunkSize, WrapperPool<GenericScheduleWrapper<? extends Expirable>> wrapperPool, String name) {
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

        public GenericScheduleWrapper(GenericWrapperPool<GenericScheduleWrapper<? extends Expirable>, GenericScheduleWrapper<? extends Expirable>> owner) {
            super(owner);
        }

        public final GenericScheduleWrapper<E> wrap(E expirable, TimerExecutor<E> executor) {
            this.expirable = expirable;
            this.executor = executor;
            return this;
        }

        public final E getExpirable() {
            return expirable;
        }
        public final TimerExecutor<E> getExecutor() {
            return executor;
        }

        @Override
        public final long getExpiryTime() {
            return expirable.getExpiryTime();
        }

        @Override
        public final boolean isCancelled() {
            return expirable.isCancelled();
        }

        @Override
        protected final void onReset() {
            super.onReset();
            expirable = null;
            executor = null;
        }
    }
}
