package com.thezeroer.nexalithic.core.io.thread;

import com.thezeroer.nexalithic.core.io.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionValidator;
import com.thezeroer.nexalithic.core.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.recyclable.*;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;

/**
 * Loop的执行线程
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/03
 * @version 1.0.0
 */
public class LoopThread extends Thread {
    public static final class Options implements OptionsDefinition {
        public static final NexalithicOption<Integer> GlobalLoopBufferPool_Capacity = NexalithicOption.create(
                "LoopThread_GlobalLoopBufferPool_Capacity", 1024, OptionValidator.positive()
        );
        public static final NexalithicOption<Integer> GlobalLoopBufferPool_Limit = NexalithicOption.create(
                "LoopThread_GlobalLoopBufferPool_Limit", (int) (GlobalLoopBufferPool_Capacity.defaultValue() * 1.5), OptionValidator.positive()
        );
        public static final NexalithicOption<Double> GlobalLoopBufferPool_PrefillRatio = NexalithicOption.create(
                "LoopThread_GlobalLoopBufferPool_PrefillRatio", 0.1, OptionValidator.unitInterval()
        );
        public static final NexalithicOption<Integer> LocalLoopBufferPool_Capacity = NexalithicOption.create(
                "LoopThread_localLoopBufferPool_Capacity", 1024, OptionValidator.positive()
        );
        public static final NexalithicOption<Double> LocalLoopBufferPool_PrefillRatio = NexalithicOption.create(
                "LoopThread_LocalLoopBufferPool_PrefillRatio", 0.5, OptionValidator.unitInterval()
        );
    }
    private static volatile WrapperPool<LoopBuffer> globalLoopBufferPool;
    private final WrapperPool<LoopBuffer> localLoopBufferPool;
    private ProxyRecycler<?> proxyRecycler;

    public LoopThread(AbstractLoop loop) {
        super(loop);
        if (globalLoopBufferPool == null) {
            synchronized (LoopThread.class) {
                if (globalLoopBufferPool == null) {
                    globalLoopBufferPool = new SelfStaticWrapperPool<>(
                            PoolStorage.of(new MpmcArrayQueue<>(Interior.GlobalLoopBufferPool_Capacity), Interior.GlobalLoopBufferPool_Capacity),
                            PoolStrategy.failFast(Interior.GlobalLoopBufferPool_Limit),
                            LoopBuffer::create
                    ).warmUp(Interior.GlobalLoopBufferPool_PrefillRatio);
                }
            }
        }
        localLoopBufferPool = new SelfStaticWrapperPool<>(
                PoolStorage.of(new SpscArrayQueue<>(Interior.LocalLoopBufferPool_Capacity), Interior.LocalLoopBufferPool_Capacity),
                PoolStrategy.skip(),
                LoopBuffer::create
        ).warmUp(Interior.LocalLoopBufferPool_PrefillRatio);
    }

    public LoopBuffer aquireLoopBuffer() {
        LoopBuffer loopBuffer = localLoopBufferPool.acquire();
        if (loopBuffer == null) {
            loopBuffer = globalLoopBufferPool.acquire();
        }
        return loopBuffer;
    }

    public void productionProxyRecycler(ProxyRecycler<?> proxyRecycler) {
        this.proxyRecycler = proxyRecycler;
    }
    @SuppressWarnings("unchecked")
    public <R extends ProxyRecycler<?>> R consumeProxyRecycler() {
        R r = (R) proxyRecycler;
        proxyRecycler = null;
        return r;
    }
    private static class Interior {
        public static final int GlobalLoopBufferPool_Capacity = Options.GlobalLoopBufferPool_Capacity.value();
        public static final int GlobalLoopBufferPool_Limit = Options.GlobalLoopBufferPool_Limit.value();
        public static final double GlobalLoopBufferPool_PrefillRatio = Options.GlobalLoopBufferPool_PrefillRatio.value();
        public static final int LocalLoopBufferPool_Capacity = Options.LocalLoopBufferPool_Capacity.value();
        public static final double LocalLoopBufferPool_PrefillRatio = Options.LocalLoopBufferPool_PrefillRatio.value();
    }
}
