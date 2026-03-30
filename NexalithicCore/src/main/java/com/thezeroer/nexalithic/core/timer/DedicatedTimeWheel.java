package com.thezeroer.nexalithic.core.timer;

import com.thezeroer.nexalithic.core.recyclable.WrapperPool;

/**
 * 专用型时间轮
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/25
 * @version 1.0.0
 */
public class DedicatedTimeWheel<E extends Expirable> extends TimeWheel<DedicatedTimeWheel.DedicatedScheduleWrapper<E>> {
    private final TimerExecutor<E> executor;

    public DedicatedTimeWheel(long tick, int slot, WrapperPool<DedicatedTimeWheel.DedicatedScheduleWrapper<E>> wrapperPool, TimerExecutor<E> executor, String name) {
        super(tick, slot, wrapperPool, name);
        this.executor = executor;
    }

    public void schedule(E expirable) {
        queue.offer(wrapperPool.acquire().wrap(expirable));
    }

    @Override
    protected boolean onTrigger(DedicatedScheduleWrapper<E> wrapper) {
        E expirable = wrapper.getExpirable();
        if (expirable.onExpiryTriggered()) {
            executor.trigger(expirable);
            return true;
        } else {
            queue.offer(wrapper);
            return false;
        }
    }

    public static class DedicatedScheduleWrapper<E extends Expirable> extends ScheduleWrapper<DedicatedTimeWheel.DedicatedScheduleWrapper<E>> {
        private volatile E expirable;

        public DedicatedScheduleWrapper<E> wrap(E expirable) {
            this.expirable = expirable;
            return this;
        }

        public E getExpirable() {
            return expirable;
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
        }
    }
}
