package com.thezeroer.nexalithic.core.messaging;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskFuture;
import com.thezeroer.nexalithic.core.messaging.task.TaskTracer;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.recyclable.*;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import org.jctools.queues.MpmcArrayQueue;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * 业务包分发器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public abstract class BusinessPacketDispatcher<
        S extends NexalithicSession<?, ?, ?, ?, BusinessPacketFragmentWrapper>,
        HC extends HandlerContext<S>,
        HR extends HandlerContext.Recyclable<S, HC, HR>
    > {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, BusinessPacketDispatcher.class);
    public static class Options extends OptionsDefinition {
        public final NexalithicOption<Integer> HandlerContextPool_Capacity = NexalithicOption.create(
                HandlerContextPool_Capacity_DefaultValue(), OptionValidator.positive()
        );
        public final NexalithicOption<Double> HandlerContextPool_PrefillRatio = NexalithicOption.create(
                HandlerContextPool_PrefillRatio_DefaultValue(), OptionValidator.unitInterval()
        );
        public final NexalithicOption<Integer> PacketWrapperPool_Capacity = NexalithicOption.create(
                PacketWrapperPool_Capacity_DefaultValue(), OptionValidator.positive()
        );
        protected Options(Class<?> holder) {
            super(holder);
        }
        protected Integer HandlerContextPool_Capacity_DefaultValue() {
            return 1024;
        }
        protected Double HandlerContextPool_PrefillRatio_DefaultValue() {
            return 0.5;
        }
        protected Integer PacketWrapperPool_Capacity_DefaultValue() {
            return 4096;
        }
        protected Integer ThreadPool_CorePoolSize_DefaultValue() {
            return Runtime.getRuntime().availableProcessors();
        }
        protected Integer ThreadPool_MaximumPoolSize_DefaultValue() {
            return ThreadPool_CorePoolSize_DefaultValue() * 2;
        }
        protected Long ThreadPool_KeepAliveTime_DefaultValue() {
            return 60L;
        }
        protected Integer ThreadPool_WorkQueue_Capacity_DefaultValue() {
            return 1024;
        }
    }
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<TaskTracer> TaskTracer = NexalithicModule.create("BusinessPacketDispatcher_TaskTracer", TaskTracer.class);
        public static final NexalithicModule<HandlerRegistry<? extends HandlerContext<?>>> HandlerRegistry = NexalithicModule.create("BusinessPacketDispatcher_HandlerRegistry", HandlerRegistry.class);
        public static final NexalithicModule<ExecutorService> ExecutorService = NexalithicModule.create("BusinessPacketDispatcher_ExecutorService", ExecutorService.class);
    }
    private final TaskTracer taskTracer;
    private final HandlerRegistry<HC> handlerRegistry;
    private final WrapperPool<HR> handlerContextPool;
    private final WrapperPool<BusinessPacketFragmentWrapper> packetWrapperPool;
    private final ExecutorService threadPool;
    private final Queue<Runnable> waitQueue;

    public BusinessPacketDispatcher(NexalithicBuilderContext context, Options options) {
        this.taskTracer = context.getModule(Modules.TaskTracer);
        this.handlerRegistry = context.getModule(Modules.HandlerRegistry);
        this.threadPool = context.getModule(Modules.ExecutorService, () -> {
            int cores = Runtime.getRuntime().availableProcessors();
            return new ThreadPoolExecutor(options.ThreadPool_CorePoolSize_DefaultValue(), options.ThreadPool_MaximumPoolSize_DefaultValue(),
                    options.ThreadPool_KeepAliveTime_DefaultValue(), TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(options.ThreadPool_WorkQueue_Capacity_DefaultValue()), new ThreadPoolExecutor.CallerRunsPolicy());
        });
        this.waitQueue = new ConcurrentLinkedQueue<>();
        this.handlerContextPool = new TargetStaticWrapperPool<>(
                PoolStorage.of(MpmcArrayQueue::new, context.getOption(options.HandlerContextPool_Capacity)),
                PoolStrategy.alwaysCreate(),
                this::createHandlerContext,
                this::createRecyclableWrapper
        );
        this.packetWrapperPool = new TargetDynamicWrapperPool<>(
                PoolStorage.of(MpmcArrayQueue::new, context.getOption(options.PacketWrapperPool_Capacity)),
                PoolStrategy.alwaysCreate(),
                () -> new BusinessPacketFragmentWrapper(this.taskTracer)
        );
        this.handlerContextPool.warmUp(context.getOption(options.HandlerContextPool_PrefillRatio));
    }

    protected abstract HC createHandlerContext();
    protected abstract HR createRecyclableWrapper(HC hc);

    /**
     * 摄入业务包（收后工作）
     */
    public final void ingest(BusinessPacket packet, S session) {
        NexalithicTask task = taskTracer.pick(packet.getTaskId());
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

    public final TaskFuture submitNexalithicTask(S session, NexalithicTask task) {
        if (session == null) {
            return null;
        }
        switch (task.getStrategy()) {
            case ASYNC -> threadPool.submit(() -> onTaskRequest(session, task));
            case SYNC_WAIT -> {
                onTaskRequest(session, task);
                task.getFuture().waitFinish();
            }
            case SEQUENTIAL_QUEUE -> {
                if (taskTracer.hasTrackingTasks()) {
                    waitQueue.offer(() -> onTaskRequest(session, task));
                } else {
                    threadPool.submit(() -> onTaskRequest(session, task));
                }
            }
        }
        return task.getFuture();
    }

    /**
     * 流出业务包（发前工作）
     */
    public final boolean egress(S session, BusinessPacket packet) {
        if (session == null) {
            return false;
        }
        BusinessPacketFragmentWrapper wrapper = packetWrapperPool.acquire();
        wrapper.wrap(packet.seal());
        return session.pushBusinessPacketWrapper(wrapper);
    }

    private boolean onTaskRequest(S session, NexalithicTask task) {
        BusinessPacket packet = null;
        boolean pushed = false;
        try {
            packet = task.request();
            if (packet == null) {
                return false;
            }
            if (task.getPattern() != NexalithicTask.Pattern.ONE_WAY) {
                if (!taskTracer.track(task)) {
                    throw new RuntimeException("Task " + task.getTaskId() + " already registered");
                }
                task.transitTo(NexalithicTask.State.WAITING);
            }
            return pushed = egress(session, packet.setTaskId(task.getTaskId()));
        } catch (Exception e) {
            task.exception(e);
            return false;
        } finally {
            if (packet == null) {
                task.finish();
            } else if (!pushed) {
                taskTracer.pick(task.getTaskId());
                task.finish();
            }
        }
    }
    private void onTaskResponse(NexalithicTask task, BusinessPacket packet) {
        task.response(packet);
    }
}
