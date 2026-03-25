package com.thezeroer.nexalithic.core.messaging;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskRegistry;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
import com.thezeroer.nexalithic.core.session.NexalithicSession;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

/**
 * 业务包分发器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public abstract class BusinessPacketDispatcher<
        S extends NexalithicSession<?, ?, ?, ?, ?>,
        HC extends HandlerContext<S>,
        HR extends HandlerContext.Recyclable<S, HC, HR>
    > {
    protected final TaskRegistry taskRegistry;
    protected final HandlerRegistry<HC> handlerRegistry;
    protected final WrapperPool<HR> handlerContextPool;
    protected final ExecutorService threadPool;
    protected final Queue<Runnable> waitQueue;

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
        this.waitQueue = new ConcurrentLinkedQueue<>();
    }

    public final void dispatch(BusinessPacket packet, S session) {
        NexalithicTask task = taskRegistry.pick(packet.getTaskId());
        if (task != null) {
            threadPool.submit(() -> onTaskResponse(task, packet));
            Runnable runnable = waitQueue.poll();
            if (runnable != null) {
                threadPool.submit(runnable);
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

    public boolean submitNexalithicTask(S session, NexalithicTask task) {
        switch (task.getStrategy()) {
            case ASYNC -> threadPool.submit(() -> onTaskRequest(task, session));
            case SYNC_WAIT -> {
                onTaskRequest(task, session);
                synchronized (task) {
                    try {
                        task.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
            case SEQUENTIAL_QUEUE -> {
                if (taskRegistry.hasTrackingTasks()) {
                    waitQueue.offer(() -> onTaskRequest(task, session));
                } else {
                    threadPool.submit(() -> onTaskRequest(task, session));
                }
            }
        }
        return false;
    }

    private boolean onTaskRequest(NexalithicTask task, S session) {
        BusinessPacket packet = null;
        boolean pushed = false;
        try {
            packet = task.request();
            if (packet == null) {
                return false;
            }
            if (task.getPattern() != NexalithicTask.Pattern.ONE_WAY) {
                if (!taskRegistry.track(task)) {
                    throw new RuntimeException("Task " + task.getTaskId() + " already registered");
                }
            }
            return pushed = pushBusinessPacket(session, packet.setTaskId(task.getTaskId()));
        } catch (Exception e) {
            task.exception(e);
            return false;
        } finally {
            if (packet == null) {
                task.finish();
            } else if (!pushed) {
                taskRegistry.pick(task.getTaskId());
                task.finish();
            }
        }
    }
    private void onTaskResponse(NexalithicTask task, BusinessPacket packet) {

        try {
            task.response(packet);
        } catch (Exception e) {
            task.exception(e);
        } finally {
            task.finish();
        }
    }

    public abstract boolean pushBusinessPacket(S session, BusinessPacket packet);
}
