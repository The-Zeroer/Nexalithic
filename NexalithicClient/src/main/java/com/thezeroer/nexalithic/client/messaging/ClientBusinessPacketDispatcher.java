package com.thezeroer.nexalithic.client.messaging;

import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.core.io.codec.wrapper.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.task.TaskRegistry;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.recyclable.*;
import org.jctools.queues.MpmcArrayQueue;

import java.util.concurrent.ExecutorService;

/**
 * 客户端业务分发器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/18
 * @version 1.0.0
 */
public class ClientBusinessPacketDispatcher extends BusinessPacketDispatcher<
        ClientSession,
        ClientHandlerContext,
        ClientHandlerContext.Recyclable
    > {
    public static final NexalithicOption<Integer> HandlerContextPool_Capacity = NexalithicOption.create("ClientBusinessPacketDispatcher_HandlerContextPool_Capacity", 8);
    public static final NexalithicOption<Double> HandlerContextPool_PrefillRatio = NexalithicOption.create("ClientBusinessPacketDispatcher_HandlerContextPool_PrefillRatio", 0.25);
    public static final NexalithicOption<Integer> WrapperPool_Capacity = NexalithicOption.create("ClientBusinessPacketDispatcher_BusinessPacketWrapperPool_Capacity", 128);
    private final WrapperPool<BusinessPacketFragmentWrapper> WRAPPER_POOL;

    public ClientBusinessPacketDispatcher(TaskRegistry taskRegistry, HandlerRegistry<ClientHandlerContext> handlerRegistry, ExecutorService threadPool) {
        this(taskRegistry, handlerRegistry, threadPool, new ClientBusinessPacketDispatcher[1]);
    }
    private ClientBusinessPacketDispatcher(TaskRegistry taskRegistry, HandlerRegistry<ClientHandlerContext> handlerRegistry, ExecutorService threadPool, ClientBusinessPacketDispatcher[] holder) {
        super(
                taskRegistry,
                handlerRegistry,
                new TargetStaticWrapperPool<>(
                        PoolStorage.of(new MpmcArrayQueue<>(HandlerContextPool_Capacity.value()), HandlerContextPool_Capacity.value()),
                        PoolStrategy.alwaysCreate(),
                        () -> new ClientHandlerContext(holder[0]),
                        ClientHandlerContext.Recyclable::new
                ),
                threadPool
        );
        holder[0] = this;
        this.WRAPPER_POOL = new TargetDynamicWrapperPool<>(
                PoolStorage.of(new MpmcArrayQueue<>(WrapperPool_Capacity.value()), WrapperPool_Capacity.value()),
                PoolStrategy.alwaysCreate(),
                () -> new BusinessPacketFragmentWrapper(taskRegistry)
        );
        this.handlerContextPool.warmUp(HandlerContextPool_PrefillRatio.value());
    }

    @Override
    public boolean pushBusinessPacket(ClientSession session, BusinessPacket packet) {
        BusinessPacketFragmentWrapper wrapper = WRAPPER_POOL.acquire();
        wrapper.wrap(packet);
        return session.pushBusinessPacketWrapper(wrapper);
    }
}
