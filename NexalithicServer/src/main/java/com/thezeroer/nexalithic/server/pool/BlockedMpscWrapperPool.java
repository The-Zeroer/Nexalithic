package com.thezeroer.nexalithic.server.pool;

import com.thezeroer.nexalithic.core.pool.BlockedGeneralWrapperPool;
import com.thezeroer.nexalithic.core.pool.RecyclableWrapper;
import com.thezeroer.nexalithic.core.pool.WrapperPool;
import org.jctools.queues.MpscArrayQueue;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 阻塞MPSC包装池
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/23
 */
public class BlockedMpscWrapperPool<T, W extends RecyclableWrapper<T, W>> extends BlockedGeneralWrapperPool<T, W> {
    private final int capacity;

    public BlockedMpscWrapperPool(int capacity, int limit, Supplier<T> targetFactory, BiFunction<T, WrapperPool<W>, W> wrapperFactory) {
        super(limit, new MpscArrayQueue<>(capacity), targetFactory, wrapperFactory);
        this.capacity = capacity;
    }

    /**
     * 执行池预热
     * @param prefillRatio 预热比例 (0.0 ~ 1.0)。
     * 例如 0.5 表示启动时填充 50% 的容量。
     * @throws IllegalArgumentException 如果比例不在有效范围内
     */
    public BlockedMpscWrapperPool<T, W> warmUp(double prefillRatio) {
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
