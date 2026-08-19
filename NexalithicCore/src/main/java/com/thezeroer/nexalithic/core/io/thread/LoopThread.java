package com.thezeroer.nexalithic.core.io.thread;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.infra.buffer.LoopBuffer;
import com.thezeroer.nexalithic.core.infra.recyclable.*;
import com.thezeroer.nexalithic.core.io.loop.AbstractLoop;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;

import java.nio.ByteBuffer;

/**
 * Loop的执行线程
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/03
 * @version 1.0.0
 */
public class LoopThread extends Thread {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, LoopThread.class);
    public static final class Options extends OptionsDefinition {
        public final NexalithicOption<Integer> GlobalLoopBufferPool_Capacity = NexalithicOption.create(
                1024, OptionValidator.positive()
        );
        public final NexalithicOption<Integer> GlobalLoopBufferPool_Limit = NexalithicOption.create(
                GlobalLoopBufferPool_Capacity.defaultValue() * 2, OptionValidator.positive()
        );
        public final NexalithicOption<Double> GlobalLoopBufferPool_PrefillRatio = NexalithicOption.create(
                0.1, OptionValidator.unitInterval()
        );
        public final NexalithicOption<Integer> LocalLoopBufferPool_Capacity = NexalithicOption.create(
                1024, OptionValidator.positive()
        );
        public final NexalithicOption<Double> LocalLoopBufferPool_PrefillRatio = NexalithicOption.create(
                0.5, OptionValidator.unitInterval()
        );
        public final NexalithicOption<Integer> LoopBuffer_Capacity = NexalithicOption.create(
                1024 * 32, OptionValidator.powerOfTwo()
        );

        public Options(Class<?> holder) {
            super(holder);
        }
    }
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<WrapperPool<LoopBuffer>> GlobalLoopBufferPool = NexalithicModule.create("LoopThread_GlobalLoopBufferPool", WrapperPool.class);
    }
    private final WrapperPool<LoopBuffer> globalLoopBufferPool;
    private final WrapperPool<LoopBuffer> localLoopBufferPool;

    public LoopThread(NexalithicBuilderContext context, AbstractLoop loop) {
        super(loop);
        int bufferCapacity = context.getOption(OPTIONS.LoopBuffer_Capacity);
        globalLoopBufferPool = context.getModule(Modules.GlobalLoopBufferPool, () -> new GenericWrapperPool<LoopBuffer, LoopBuffer>(
                PoolStorageFactory.bounded(MpmcArrayQueue::new, context.getOption(OPTIONS.GlobalLoopBufferPool_Capacity)),
                PoolStrategyFactory.failFast(context.getOption(OPTIONS.GlobalLoopBufferPool_Limit)),
                owner -> new LoopBuffer(owner, ByteBuffer.allocateDirect(bufferCapacity))
        ).warmUp(context.getOption(OPTIONS.GlobalLoopBufferPool_PrefillRatio)));
        localLoopBufferPool = new GenericWrapperPool<LoopBuffer, LoopBuffer>(
                PoolStorageFactory.bounded(SpscArrayQueue::new, context.getOption(OPTIONS.LocalLoopBufferPool_Capacity)),
                PoolStrategyFactory.skip(),
                owner -> new LoopBuffer(owner, ByteBuffer.allocateDirect(bufferCapacity))
        ).warmUp(context.getOption(OPTIONS.LocalLoopBufferPool_PrefillRatio));
    }

    public LoopBuffer aquireLoopBuffer() {
        LoopBuffer loopBuffer = localLoopBufferPool.acquire();
        if (loopBuffer == null) {
            loopBuffer = globalLoopBufferPool.acquire();
        }
        return loopBuffer;
    }
}
