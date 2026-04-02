package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.io.codec.fragmenter.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.task.TaskTracer;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionValidator;
import com.thezeroer.nexalithic.core.option.OptionsDefinition;
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
    public static final class Options implements OptionsDefinition {
        public static final NexalithicOption<Integer> HandlerContextPool_Capacity = NexalithicOption.create(
                "ServerBusinessPacketDispatcher_HandlerContextPool_Capacity", 1024, OptionValidator.positive()
        );
        public static final NexalithicOption<Double> HandlerContextPool_PrefillRatio = NexalithicOption.create(
                "ServerBusinessPacketDispatcher_HandlerContextPool_PrefillRatio", 0.5, OptionValidator.unitInterval()
        );
        public static final NexalithicOption<Integer> PacketWrapperPool_Capacity = NexalithicOption.create(
                "ServerBusinessPacketDispatcher_BusinessPacketWrapperPool_Capacity", 4096, OptionValidator.positive()
        );
    }

    public ServerBusinessPacketDispatcher(TaskTracer taskTracer, HandlerRegistry<ServerHandlerContext> handlerRegistry, ExecutorService threadPool) {
        this(taskTracer, handlerRegistry, threadPool, new ServerBusinessPacketDispatcher[1]);
    }
    private ServerBusinessPacketDispatcher(TaskTracer taskTracer, HandlerRegistry<ServerHandlerContext> handlerRegistry, ExecutorService threadPool, ServerBusinessPacketDispatcher[] holder) {
        super(
                taskTracer,
                handlerRegistry,
                new TargetStaticWrapperPool<>(
                        PoolStorage.of(new MpmcArrayQueue<>(Interior.HandlerContextPool_Capacity), Interior.HandlerContextPool_Capacity),
                        PoolStrategy.alwaysCreate(),
                        () -> new ServerHandlerContext(holder[0]),
                        ServerHandlerContext.Recyclable::new
                ),
                new TargetDynamicWrapperPool<>(
                        PoolStorage.of(new MpmcArrayQueue<>(Interior.PacketWrapperPool_Capacity), Interior.PacketWrapperPool_Capacity),
                        PoolStrategy.alwaysCreate(),
                        () -> new BusinessPacketFragmentWrapper(taskTracer)
                ),
                threadPool
        );
        holder[0] = this;
        this.handlerContextPool.warmUp(Interior.HandlerContextPool_PrefillRatio);
    }

    @Override
    public boolean pushBusinessPacket(ServerSession session, BusinessPacket packet) {
        if (session == null) {
            return false;
        }
        BusinessPacketFragmentWrapper wrapper = packetWrapperPool.acquire();
        wrapper.wrap(packet.seal());
        return session.pushBusinessPacketWrapper(wrapper);
    }

    private static class Interior {
        public static final int HandlerContextPool_Capacity = Options.HandlerContextPool_Capacity.value();
        public static final double HandlerContextPool_PrefillRatio = Options.HandlerContextPool_PrefillRatio.value();
        public static final int PacketWrapperPool_Capacity = Options.PacketWrapperPool_Capacity.value();
    }
}
