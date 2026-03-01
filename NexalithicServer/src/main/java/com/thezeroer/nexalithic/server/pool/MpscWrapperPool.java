package com.thezeroer.nexalithic.server.pool;

import com.thezeroer.nexalithic.core.pool.GeneralWrapperPool;
import com.thezeroer.nexalithic.core.pool.RecyclableWrapper;
import com.thezeroer.nexalithic.core.pool.WrapperPool;
import org.jctools.queues.MpscArrayQueue;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 基于 MPSC 队列的高性能包装池实现
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/11
 * @version 1.0.0
 */
public class MpscWrapperPool<T, W extends RecyclableWrapper<T, W>> extends GeneralWrapperPool<T, W> {
    private final int capacity;

    public MpscWrapperPool(int capacity, Supplier<T> targetFactory, BiFunction<T, WrapperPool<W>, W> wrapperFactory) {
        super(new MpscArrayQueue<>(capacity), targetFactory, wrapperFactory);
        this.capacity = capacity;
    }

    /**
     * 执行池预热
     * @param prefillRatio 预热比例 (0.0 ~ 1.0)。
     * 例如 0.5 表示启动时填充 50% 的容量。
     * @throws IllegalArgumentException 如果比例不在有效范围内
     */
    public MpscWrapperPool<T, W> warmUp(double prefillRatio) {
        if (prefillRatio < 0.0 || prefillRatio > 1.0) {
            throw new IllegalArgumentException("Prefill ratio must be between 0.0 and 1.0");
        }
        for (int i = 0; i < (int) (capacity * prefillRatio); i++) {
            if (!pool.offer(wrapperFactory.apply(targetFactory.get(), this))) {
                break;
            }
        }
        return this;
    }
}
