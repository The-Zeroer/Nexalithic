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

    public GenericTimeWheel(long tick, int slot, WrapperPool<GenericTimeWheel.GenericScheduleWrapper<? extends Expirable>> wrapperPool) {
        super(tick, slot, wrapperPool);
    }

    @SuppressWarnings("unchecked")
    public <T extends Expirable> void schedule(T expirable, TimerExecutor<T> executor) {
        mountWrapper(((GenericScheduleWrapper<T>) wrapperPool.acquire()).wrap(expirable, executor));
    }

    @Override
    protected void onTrigger(GenericScheduleWrapper<?> wrapper) {
        privateOnTrigger(wrapper);
    }
    private <T extends Expirable> void privateOnTrigger(GenericScheduleWrapper<T> wrapper) {
        T expirable = wrapper.getExpirable();
        if (expirable.onExpiryTriggered()) {
            wrapper.getExecutor().trigger(expirable);
        } else {
            mountWrapper(wrapper);
        }
    }

    public static class GenericScheduleWrapper<E extends Expirable> extends ScheduleWrapper {
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
        public long getDeadline() {
            return expirable.getExpiryTime();
        }

        @Override
        public void onRecycle() {
            super.onRecycle();
            expirable = null;
            executor = null;
        }
    }
}
