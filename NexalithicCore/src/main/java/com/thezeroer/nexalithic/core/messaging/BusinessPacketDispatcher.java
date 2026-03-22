package com.thezeroer.nexalithic.core.messaging;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskRegistry;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import com.thezeroer.nexalithic.core.session.NexalithicSession;

import java.util.concurrent.ExecutorService;

/**
 * 业务包分发器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public class BusinessPacketDispatcher<
        S extends NexalithicSession<?, ?, ?, ?, ?>,
        HC extends HandlerContext<S>,
        HR extends HandlerContext.Recyclable<S, HC, HR>
    > {
    protected final TaskRegistry taskRegistry;
    protected final HandlerRegistry<HC> handlerRegistry;
    protected final WrapperPool<HR> handlerContextPool;
    protected final ExecutorService threadPool;

    public BusinessPacketDispatcher(
            TaskRegistry taskRegistry,
            HandlerRegistry<HC> handlerRegistry,
            WrapperPool<HR> handlerContextPool,
            ExecutorService threadPool
    ) {
        this.taskRegistry = taskRegistry;
        this.handlerRegistry = handlerRegistry;
        this.handlerContextPool = handlerContextPool;
        this.threadPool = threadPool;
    }

    public final void dispatch(BusinessPacket packet, S session) {
        NexalithicTask task = taskRegistry.trigger(packet.getTaskId());
        if (task != null) {
            try {
                task.response(packet);
            } catch (Exception e) {
                task.exception(e);
            } finally {
                task.finish();
            }
            return;
        }
        NexalithicHandler<HC> handler = handlerRegistry.match(packet.getPath());
        if (handler == null) {

            return;
        }
        if (handler.requireAuth() && session.getSessionName() == null) {

            return;
        }
        threadPool.execute(() -> {
            HR recyclable = handlerContextPool.acquire().initTarget(packet, session);
            handler.handle(recyclable.unwrap());
            recyclable.recycle();
        });
    }
}
