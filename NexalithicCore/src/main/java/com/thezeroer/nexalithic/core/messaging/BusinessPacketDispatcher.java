package com.thezeroer.nexalithic.core.messaging;

import com.thezeroer.nexalithic.core.io.loop.ChannelLoop;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.TargetStaticWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import com.thezeroer.nexalithic.core.session.channel.SessionChannel;
import org.jctools.queues.MpmcArrayQueue;

import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 业务包分发器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public class BusinessPacketDispatcher<
        SC extends SessionChannel<BusinessPacket, ?, CL>,
        CL extends ChannelLoop<? super SC, ? super BusinessPacket>,
        HC extends HandlerContext<BusinessPacket, SC, CL>,
        HR extends HandlerContext.Recyclable<BusinessPacket, SC, CL, HC, HR>
    > {
    public static final NexalithicOption<Integer> HandlerContextPool_Capacity = NexalithicOption.create("BusinessPacketDispatcher_HandlerContextPool_Capacity", 1024);
    public static final NexalithicOption<Double> HandlerContextPool_PrefillRatio = NexalithicOption.create("BusinessPacketDispatcher_HandlerContextPool_PrefillRatio", 0.5);
    private final HandlerRegistry<HC> handlerRegistry;
    private final WrapperPool<HR> handlerContextPool;
    private final ExecutorService threadPool;

    public BusinessPacketDispatcher(
            HandlerRegistry<HC> registry,
            Supplier<? extends HC> contextFactory,
            Function<HC, HR> wrapperFactory,
            ExecutorService threadPool
    ) {
        this.handlerRegistry = registry;
        this.threadPool = threadPool;
        handlerContextPool = new TargetStaticWrapperPool<>(
                PoolStorage.of(new MpmcArrayQueue<>(HandlerContextPool_Capacity.value()), HandlerContextPool_Capacity.value()),
                PoolStrategy.alwaysCreate(),
                contextFactory::get,
                wrapperFactory
        ).warmUp(HandlerContextPool_PrefillRatio.value());
    }

    public final void dispatch(BusinessPacket packet, SC channel) {
        NexalithicSession<?, ?, ?> session = channel.session();
        NexalithicHandler<HC> handler = handlerRegistry.match(packet.getPath());
        if (handler == null) {

            return;
        }
        if (handler.requireAuth() && session.getSessionName() == null) {
            return;
        }
        threadPool.execute(() -> {
            handler.handle(handlerContextPool.acquire().initTarget(packet, channel).unwrap());
        });
    }
}
