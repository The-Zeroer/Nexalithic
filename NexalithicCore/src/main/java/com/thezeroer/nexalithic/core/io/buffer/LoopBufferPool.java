package com.thezeroer.nexalithic.core.io.buffer;

import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import org.jctools.queues.MpscArrayQueue;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * 环路缓冲池
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/03
 * @version 1.0.0
 */
public class LoopBufferPool extends SelfStaticWrapperPool<LoopBuffer> {
    public static final LoopBufferPool INSTANCE = new LoopBufferPool(
            PoolStorage.of(new MpscArrayQueue<>(1024), 1024),
            PoolStrategy.alwaysCreate(),
            () -> new LoopBuffer(ByteBuffer.allocate(1024 * 16)));

    public LoopBufferPool(PoolStorage<LoopBuffer> storage, PoolStrategy<LoopBuffer> strategy, Supplier<LoopBuffer> factory) {
        super(storage, strategy, factory);
    }
}
