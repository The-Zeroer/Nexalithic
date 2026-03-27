package com.thezeroer.nexalithic.core.messaging.task;

import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.recyclable.PoolStorage;
import com.thezeroer.nexalithic.core.recyclable.PoolStrategy;
import com.thezeroer.nexalithic.core.recyclable.SelfStaticWrapperPool;
import com.thezeroer.nexalithic.core.timer.DedicatedTimeWheel;
import com.thezeroer.nexalithic.core.timer.TimerExecutor;
import org.jctools.queues.SpmcArrayQueue;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 任务注册表
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/21
 * @version 1.0.0
 */
public class TaskRegistry implements TimerExecutor<NexalithicTask> {
    public static final NexalithicOption<Long> TimeWheel_Tick = NexalithicOption.create("TaskRegistry_TimeWheel_Tick", 1000L);
    public static final NexalithicOption<Integer> TimeWheel_Slot = NexalithicOption.create("TaskRegistry_TimeWheel_Slot", 30);
    public static final NexalithicOption<Integer> TimeWheel_WrapperPool_Capacity = NexalithicOption.create("TaskRegistry_TimeWheel_WrapperPool_Capacity", 128);
    private final Map<Long, NexalithicTask> taskMap = new ConcurrentHashMap<>();
    private final DedicatedTimeWheel<NexalithicTask> timeWheel;

    public TaskRegistry() {
        this.timeWheel = new DedicatedTimeWheel<>(
                TimeWheel_Tick.value(),
                TimeWheel_Slot.value(),
                new SelfStaticWrapperPool<>(
                        PoolStorage.of(new SpmcArrayQueue<>(TimeWheel_WrapperPool_Capacity.value()), TimeWheel_WrapperPool_Capacity.value()),
                        PoolStrategy.alwaysCreate(),
                        DedicatedTimeWheel.DedicatedScheduleWrapper<NexalithicTask>::new
                ),
                this,
                "TaskRegistry"
        );
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
