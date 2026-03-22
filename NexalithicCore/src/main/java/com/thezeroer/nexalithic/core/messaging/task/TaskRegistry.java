package com.thezeroer.nexalithic.core.messaging.task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务注册表
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/21
 * @version 1.0.0
 */
public class TaskRegistry {
    private final Map<Long, NexalithicTask> tasks = new ConcurrentHashMap<>();

    public boolean register(NexalithicTask task) {
        return tasks.putIfAbsent(task.getTaskId(), task) == null;
    }

    public void activate(long taskId) {

    }

    public NexalithicTask trigger(long taskId) {
        return tasks.remove(taskId);
    }
}
