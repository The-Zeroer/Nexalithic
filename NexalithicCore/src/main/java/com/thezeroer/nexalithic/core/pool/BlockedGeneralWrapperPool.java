package com.thezeroer.nexalithic.core.pool;

import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class BlockedGeneralWrapperPool<T, W extends RecyclableWrapper<T, W>> extends GeneralWrapperPool<T, W> {
    private final Semaphore semaphore;

    public BlockedGeneralWrapperPool(int limit, Queue<W> pool, Supplier<T> targetFactory, BiFunction<T, WrapperPool<W>, W> wrapperFactory) {
        super(pool, targetFactory, wrapperFactory);
        this.semaphore = new Semaphore(limit, false);
    }

    @Override
    public W acquire() {
        try {
            semaphore.acquire();
        } catch (InterruptedException ignored) {}
        try {
            return super.acquire();
        } catch (Exception e) {
            semaphore.release();
            throw e;
        }
    }

    @Override
    public boolean release(W w) {
        try {
            return pool.offer(w);
        } finally {
            semaphore.release();
        }
    }
}
