package com.thezeroer.nexalithic.core.messaging.task;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 任务追踪器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/27
 */
public class TaskFuture {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final NexalithicTask task;
    private final TaskTracer tracer;

    public TaskFuture(NexalithicTask task, TaskTracer tracer) {
        this.task = task;
        this.tracer = tracer;
    }

    public NexalithicTask.State getState() {
        return task.getState();
    }
    public void cancel() {
        if (!tracer.cancel(task.getTaskId())) {
            task.cancel();
        }
        latch.countDown();
    }

    /**
     * 无限制等待任务执行结束（成功、失败或取消）。
     */
    public void waitFinish() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 在指定时间内等待任务结束。
     * @param timeout 超时时间，单位毫秒。若 <= 0 则视为永不超时（同 waitFinish()）。
     * @return {@code true} 如果任务在时间内结束；{@code false} 如果超时。
     */
    public boolean waitFinish(long timeout) {
        try {
            if (timeout <= 0) {
                latch.await();
                return true;
            }
            return latch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放所有等待在 waitFinish 上的线程。
     */
    void internalComplete() {
        latch.countDown();
    }
    boolean isDone() {
        return latch.getCount() == 0;
    }
}
