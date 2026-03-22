package com.thezeroer.nexalithic.server.messaging;

import com.thezeroer.nexalithic.core.io.codec.wrapper.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskRegistry;
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
    public static final NexalithicOption<Integer> WrapperPool_Capacity = NexalithicOption.create("ServerBusinessPacketDispatcher_BusinessPacketWrapperPool_Capacity", 4096);
    private final TaskRegistry taskRegistry;
    private final WrapperPool<BusinessPacketFragmentWrapper> WRAPPER_POOL;

    public ServerBusinessPacketDispatcher(TaskRegistry taskRegistry, HandlerRegistry<ServerHandlerContext> handlerRegistry, ExecutorService threadPool) {
        this(taskRegistry, handlerRegistry, threadPool, new ServerBusinessPacketDispatcher[1]);
    }
    private ServerBusinessPacketDispatcher(TaskRegistry taskRegistry, HandlerRegistry<ServerHandlerContext> handlerRegistry, ExecutorService threadPool, ServerBusinessPacketDispatcher[] holder) {
        super(
                taskRegistry,
                handlerRegistry,
                new TargetStaticWrapperPool<>(
                        PoolStorage.of(new MpmcArrayQueue<>(HandlerContextPool_Capacity.value()), HandlerContextPool_Capacity.value()),
                        PoolStrategy.alwaysCreate(),
                        () -> new ServerHandlerContext(holder[0]),
                        ServerHandlerContext.Recyclable::new
                ),
                threadPool
        );
        holder[0] = this;
        this.taskRegistry = taskRegistry;
        this.WRAPPER_POOL = new TargetDynamicWrapperPool<>(
                PoolStorage.of(new MpmcArrayQueue<>(WrapperPool_Capacity.value()), WrapperPool_Capacity.value()),
                PoolStrategy.alwaysCreate(),
                () -> new BusinessPacketFragmentWrapper(taskRegistry)
        );
        this.handlerContextPool.warmUp(HandlerContextPool_PrefillRatio.value());
    }

    public boolean submitNexalithicTask(ServerSession session, NexalithicTask task) {
        if (!taskRegistry.register(task)) {
            throw new RuntimeException("Task " + task.getTaskId() + " already registered");
        }
        BusinessPacket packet = null;
        boolean pushed = false;
        try {
            packet = task.request();
            if (packet == null) {
                return false;
            }
            return pushed = pushBusinessPacket(session, packet.setTaskId(task.getTaskId()));
        } catch (Exception e) {
            task.exception(e);
            return false;
        } finally {
            if (packet == null) {
                task.finish();
            } else if (!pushed) {
                taskRegistry.trigger(task.getTaskId());
                task.finish();
            }
        }
    }

    public boolean pushBusinessPacket(ServerSession session, BusinessPacket packet) {
        BusinessPacketFragmentWrapper wrapper = WRAPPER_POOL.acquire();
        wrapper.wrap(packet);
        return session.pushBusinessPacketWrapper(wrapper);
    }
}
