package com.thezeroer.nexalithic.core.messaging.task;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.infra.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.infra.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.infra.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.infra.timer.DedicatedTimeWheel;
import com.thezeroer.nexalithic.core.infra.timer.TimeWheel;
import com.thezeroer.nexalithic.core.infra.timer.TimerExecutor;
import org.jctools.queues.SpmcArrayQueue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务追踪表
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/21
 * @version 1.0.0
 */
public class TaskTracer implements TimerExecutor<NexalithicTask> {
    public static final Options OPTIONS = OptionsDefinition.initOptions(Options.class, TaskTracer.class);
    public static final class Options extends OptionsDefinition {
        public final TimeWheel.Options TimeWheel = new  TimeWheel.Options(holder) {};
        public Options(Class<?> holder) {
            super(holder);
        }
    }

    private final Map<Long, NexalithicTask> taskMap = new ConcurrentHashMap<>();
    private final DedicatedTimeWheel<NexalithicTask> timeWheel;

    public TaskTracer(NexalithicBuilderContext context) {
        timeWheel = new DedicatedTimeWheel<>(
                context.getOption(OPTIONS.TimeWheel.Tick),
                context.getOption(OPTIONS.TimeWheel.Slot),
                context.getOption(OPTIONS.TimeWheel.TickQuotaShift),
                context.getOption(OPTIONS.TimeWheel.WaitQueue_ChunkSize),
                new SelfStaticWrapperPool<>(
                        PoolStorage.of(SpmcArrayQueue::new, context.getOption(OPTIONS.TimeWheel.WrapperPool_Capacity)),
                        PoolStrategy.alwaysCreate(),
                        DedicatedTimeWheel.DedicatedScheduleWrapper<NexalithicTask>::new
                ),
                this,
                TaskTracer.class.getSimpleName()
        );
        timeWheel.start();
    }

    public boolean track(NexalithicTask task) {
        return taskMap.putIfAbsent(task.getTaskId(), task) == null;
    }

    public void activate(long taskId) {
        NexalithicTask task = taskMap.get(taskId);
        if (task != null) {
            timeWheel.schedule(task);
        }
    }

    public NexalithicTask pick(long taskId) {
        return taskMap.remove(taskId);
    }

    public void cancel(long taskId) {
        NexalithicTask task = taskMap.remove(taskId);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean hasTrackingTasks() {
        return !taskMap.isEmpty();
    }

    @Override
    public void trigger(NexalithicTask target) {
        NexalithicTask task = pick(target.getTaskId());
        if (task != null) {
            task.timeout();
        }
    }
}
