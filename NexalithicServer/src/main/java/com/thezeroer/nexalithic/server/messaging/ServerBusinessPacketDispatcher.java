package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.io.codec.wrapper.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.task.TaskTracer;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.recyclable.*;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import org.jctools.queues.MpmcArrayQueue;

import java.util.concurrent.ExecutorService;

/**
 * 服务器业务分组器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/17
 * @version 1.0.0
 */
public class ServerBusinessPacketDispatcher extends BusinessPacketDispatcher<
        ServerSession,
        ServerHandlerContext,
        ServerHandlerContext.Recyclable
    > {

    public static final NexalithicOption<Integer> HandlerContextPool_Capacity = NexalithicOption.create("ServerBusinessPacketDispatcher_HandlerContextPool_Capacity", 1024);
    public static final NexalithicOption<Double> HandlerContextPool_PrefillRatio = NexalithicOption.create("ServerBusinessPacketDispatcher_HandlerContextPool_PrefillRatio", 0.5);
    public static final NexalithicOption<Integer> PacketWrapperPool_Capacity = NexalithicOption.create("ServerBusinessPacketDispatcher_BusinessPacketWrapperPool_Capacity", 4096);

    public ServerBusinessPacketDispatcher(TaskTracer taskTracer, HandlerRegistry<ServerHandlerContext> handlerRegistry, ExecutorService threadPool) {
        this(taskTracer, handlerRegistry, threadPool, new ServerBusinessPacketDispatcher[1]);
    }
    private ServerBusinessPacketDispatcher(TaskTracer taskTracer, HandlerRegistry<ServerHandlerContext> handlerRegistry, ExecutorService threadPool, ServerBusinessPacketDispatcher[] holder) {
        super(
                taskTracer,
                handlerRegistry,
                new TargetStaticWrapperPool<>(
                        PoolStorage.of(new MpmcArrayQueue<>(HandlerContextPool_Capacity.value()), HandlerContextPool_Capacity.value()),
                        PoolStrategy.alwaysCreate(),
                        () -> new ServerHandlerContext(holder[0]),
                        ServerHandlerContext.Recyclable::new
                ),
                new TargetDynamicWrapperPool<>(
                        PoolStorage.of(new MpmcArrayQueue<>(PacketWrapperPool_Capacity.value()), PacketWrapperPool_Capacity.value()),
                        PoolStrategy.alwaysCreate(),
                        () -> new BusinessPacketFragmentWrapper(taskTracer)
                ),
                threadPool
        );
        holder[0] = this;
        this.handlerContextPool.warmUp(HandlerContextPool_PrefillRatio.value());
    }

    @Override
    public boolean pushBusinessPacket(ServerSession session, BusinessPacket packet) {
        if (session == null) {
            return false;
        }
        BusinessPacketFragmentWrapper wrapper = packetWrapperPool.acquire();
        wrapper.wrap(packet);
        return session.pushBusinessPacketWrapper(wrapper);
    }
}
