package com.thezeroer.nexalithic.core.io.buffer;

import com.thezeroer.nexalithic.core.io.thread.IOThread;
import com.thezeroer.nexalithic.core.pool.WrapperPool;

import java.nio.ByteBuffer;

/**
 * 环路缓冲池
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/03
 * @version 1.0.0
 */
public class LoopBufferPool implements WrapperPool<LoopBuffer.Recyclable> {
    public static final LoopBufferPool INSTANCE = new LoopBufferPool();

    @Override
    public LoopBuffer.Recyclable acquire() {
        if (Thread.currentThread() instanceof IOThread thread) {

        }
        return new LoopBuffer.Recyclable(new LoopBuffer(ByteBuffer.allocate(1024 * 16)), this);
    }

    @Override
    public boolean release(LoopBuffer.Recyclable recyclable) {
        return false;
    }
}
