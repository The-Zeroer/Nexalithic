package com.thezeroer.nexalithic.core.messaging.task;

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
public class TaskRegistry {
    private final Map<Long, NexalithicTask> taskMap = new ConcurrentHashMap<>();
    /** 追踪已发送的任务 */
    public boolean track(NexalithicTask task) {
        return taskMap.putIfAbsent(task.getTaskId(), task) == null;
    }

    public void activate(long taskId) {

    }
    /** 释放已完成的任务 */
    public NexalithicTask pick(long taskId) {
        return taskMap.remove(taskId);
    }

    public boolean hasTrackingTasks() {
        return !taskMap.isEmpty();
    }
}
