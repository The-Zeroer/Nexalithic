package com.thezeroer.nexalithic.core.messaging;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.core.builder.option.OptionValidator;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.infra.executor.BlockingTaskQueue;
import com.thezeroer.nexalithic.core.infra.executor.FixedTaskExecutor;
import com.thezeroer.nexalithic.core.infra.executor.TypedThreadFactory;
import com.thezeroer.nexalithic.core.infra.recyclable.*;
import com.thezeroer.nexalithic.core.io.codec.fragmenter.BusinessPacketFragmentWrapper;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskFuture;
import com.thezeroer.nexalithic.core.messaging.task.TaskTracer;
import com.thezeroer.nexalithic.core.messaging.visual.TransferListenerGroup;
import com.thezeroer.nexalithic.core.messaging.visual.TransferTracer;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.SpmcArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
        public final FixedTaskExecutor.Options FixedTaskExecutor = new FixedTaskExecutor.Options(holder) {
        };
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
    }
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<TaskTracer> TaskTracer = NexalithicModule.create("BusinessPacketDispatcher_TaskTracer", TaskTracer.class);
        public static final NexalithicModule<TransferTracer> TransferTracer = NexalithicModule.create("BusinessPacketDispatcher_TransferTracer", TransferTracer.class);
        public static final NexalithicModule<HandlerRegistry<? extends HandlerContext<?>>> HandlerRegistry = NexalithicModule.create("BusinessPacketDispatcher_HandlerRegistry", HandlerRegistry.class);
    }

    protected static final Logger logger = LoggerFactory.getLogger(BusinessPacketDispatcher.class);
    protected final TaskTracer taskTracer;
    protected final TransferTracer transferTracer;
    protected final HandlerRegistry<HC> handlerRegistry;
    protected final WrapperPool<HR> handlerContextPool;
    protected final WrapperPool<BusinessPacketFragmentWrapper> packetWrapperPool;
    protected final FixedTaskExecutor<Dispatchable, ?> executor;
    protected final Queue<NexalithicTask> waitQueue;

    public BusinessPacketDispatcher(NexalithicBuilderContext context, Options options, boolean shared) {
        taskTracer = context.getModule(Modules.TaskTracer);
        transferTracer = context.getModule(Modules.TransferTracer);
        handlerRegistry = context.getModule(Modules.HandlerRegistry);
        waitQueue = new ConcurrentLinkedQueue<>();
        handlerContextPool = new TargetStaticWrapperPool<>(
                PoolStorage.of(shared ? MpmcArrayQueue::new : MpscArrayQueue::new, context.getOption(options.HandlerContextPool_Capacity)),
                PoolStrategy.alwaysCreate(),
                this::createHandlerContext,
                this::createRecyclableWrapper
        );
        handlerContextPool.warmUp(context.getOption(options.HandlerContextPool_PrefillRatio));
        packetWrapperPool = new TargetDynamicWrapperPool<>(
                PoolStorage.of(MpmcArrayQueue::new, context.getOption(options.PacketWrapperPool_Capacity)),
                PoolStrategy.alwaysCreate(),
                () -> new BusinessPacketFragmentWrapper(taskTracer, transferTracer)
        );
        executor = createFixedTaskExecutor(context, shared);
    }
    @SuppressWarnings("unchecked")
    private FixedTaskExecutor<Dispatchable, ?> createFixedTaskExecutor(NexalithicBuilderContext context, boolean shared) {
        return new FixedTaskExecutor<>(
                context.getOption(OPTIONS.FixedTaskExecutor.CoreWorkerSize),
                context.getOption(OPTIONS.FixedTaskExecutor.MaxWorkerSize),
                context.getOption(OPTIONS.FixedTaskExecutor.KeepAliveTimeNanos),
                BlockingTaskQueue.of(shared
                        ? new MpmcArrayQueue<>(context.getOption(OPTIONS.FixedTaskExecutor.TaskQueue_Capacity))
                        : new SpmcArrayQueue<>(context.getOption(OPTIONS.FixedTaskExecutor.TaskQueue_Capacity))
                ),
                new TypedThreadFactory<>() {
                    private final AtomicInteger counter = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, "BusinessPacketDispatcher-FixedTaskExecutor-" + counter.getAndIncrement());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                (dispatchable, executor) -> {
                    switch (dispatchable.type()) {
                        case Handler -> ((HR) dispatchable).unwrap().pushResponse(BusinessPacket.create(BusinessPacket.Way.RESPONSE_Busy));
                        case Task -> ((NexalithicTask) dispatchable).failed(null);
                    }
                },
                (dispatchable, thread) -> {
                    switch (dispatchable.type()) {
                        case Handler -> {
                            HR recyclable = (HR) dispatchable;
                            NexalithicHandler<HC> handler = recyclable.getHandler();
                            try {
                                handler.handle(recyclable.unwrap());
                            } catch (Exception e) {
                                logger.warn("BusinessPacketDispatcher: Uncaught exception in [{}]NexalithicHandler.handle(HandlerContext) ", handler.getName(), e);
                                recyclable.unwrap().pushResponse(BusinessPacket.create(BusinessPacket.Way.RESPONSE_Error));
                            } finally {
                                recyclable.recycle();
                            }
                        }
                        case Task -> {
                            NexalithicTask task = (NexalithicTask) dispatchable;
                            BusinessPacket responsePacket = task.getResponsePacket();
                            if (responsePacket == null) {
                                onTaskRequest((S) task.getTargetSession(), task);
                            } else {
                                onTaskResponse(task, responsePacket);
                            }
                        }
                    }
                }
        );
    }

    protected abstract HC createHandlerContext();
    protected abstract HR createRecyclableWrapper(HC hc);
    protected abstract boolean onIngest(BusinessPacket packet, S session, NexalithicHandler<HC> handler);

    /**
     * 摄入业务包（收后工作）
     */
    public final void ingest(BusinessPacket packet, S session) {
        NexalithicTask task = taskTracer.pick(packet.getTaskId());
        if (task != null) {
            task.setResponsePacket(packet);
            executor.submit(task);
            executor.submit(waitQueue.poll());
            return;
        }
        NexalithicHandler<HC> handler = handlerRegistry.match(packet.getPath());
        if (!onIngest(packet, session, handler)) {
            return;
        }
        HR recyclable = handlerContextPool.acquire();
        if (recyclable == null) {
            egress(session, BusinessPacket.create(BusinessPacket.Way.RESPONSE_Busy).setTaskId(packet.getTaskId()));
            return;
        }
        recyclable.initTarget(packet, session, handler);
        executor.submit(recyclable);
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

    public final TaskFuture submitNexalithicTask(S session, NexalithicTask.Builder taskBuilder, TransferListenerGroup.Builder visualizerBuilder) {
        if (session == null) {
            return null;
        }
        NexalithicTask task = taskBuilder.build(taskTracer);
        if (visualizerBuilder != null) {
            transferTracer.putVisualizer(visualizerBuilder.build(task.getTaskId()));
        }
        task.setTargetSession(session);
        switch (task.getStrategy()) {
            case ASYNC -> executor.submit(task);
            case SYNC_WAIT -> {
                if (onTaskRequest(session, task)) {
                    task.getFuture().waitFinish();
                }
            }
            case SEQUENTIAL_QUEUE -> {
                if (taskTracer.hasTrackingTasks()) {
                    waitQueue.offer(task);
                } else {
                    executor.submit(task);
                }
            }
        }
        return task.getFuture();
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
            logger.warn("BusinessPacketDispatcher: Uncaught exception in NexalithicTask.request() ", e);
            task.failed(e);
            return false;
        } finally {
            if (packet == null) {
                task.finish();
            } else if (!pushed) {
                taskTracer.pick(task.getTaskId());
                task.failed(null);
            }
        }
    }

    private void onTaskResponse(NexalithicTask task, BusinessPacket packet) {
        try {
            task.response(packet);
            if (task.getPattern() != NexalithicTask.Pattern.STREAM) {
                task.finish();
            }
        } catch (Exception e) {
            logger.warn("BusinessPacketDispatcher: Uncaught exception in NexalithicTask.response(BusinessPacket) ", e);
            task.failed(e);
        }
    }
}
