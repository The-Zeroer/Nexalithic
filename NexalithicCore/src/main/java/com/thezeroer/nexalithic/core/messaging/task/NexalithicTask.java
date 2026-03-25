package com.thezeroer.nexalithic.core.messaging.task;

import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * <h1>Nexalithic 异步任务 (Task)</h1>
 * <p>该类代表一次主动发起的通信请求交互，通常遵循 “请求-响应” 模式。</p>
 * <p><b>核心特性：</b></p>
 * <ul>
 * <li><b>关联性：</b> 通过 {@code taskId} 追踪并匹配对端返回的回执包。</li>
 * <li><b>状态化：</b> 支持超时控制、重试机制以及异步结果生成（Future/Promise）。</li>
 * <li><b>双工支持：</b> 客户端和服务端均可发起任务以驱动对端执行特定逻辑。</li>
 * </ul>
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/15
 * @see NexalithicHandler
 */
public class NexalithicTask {
    public enum Pattern {
        /** 只有请求，无回执。发送完即销毁。 */
        ONE_WAY,

        /** 标准请求-响应。收到一个回执或超时后销毁。 */
        REQUEST_RESPONSE,

        /** 流模式。持续接收回执，直到手动取消或收到结束帧。 */
        STREAM
    }
    public enum Strategy {
        /** 默认：异步提交，立即返回。 */
        ASYNC,

        /** 阻塞：提交后线程等待回执。 */
        SYNC_WAIT,

        /** 队列：严格按序提交，前一个完成才发下一个。 */
        SEQUENTIAL_QUEUE
    }
    public enum State {
        /** 任务已创建，初始状态 */
        NEW,

        /** 策略相关：正在排队等待发送（Strategy.SEQUENTIAL_QUEUE 特有） */
        ENQUEUED,

        REQUESTING,

        /** 正在发送或已提交至网络缓冲区 */
        SENDING,

        /** 模式相关：已发出，正在等待对端回执（ONE_WAY 模式通常跳过此状态） */
        WAITING,

        /** 模式相关：流处理中，已接收过数据但尚未结束（Pattern.STREAM 特有） */
        STREAMING,

        /** 已收到响应包，正在执行 ResponseAction 业务逻辑 */
        RESPONDING,

        COMPLETED,

        TIMEOUT,

        FAILED,

        CANCELLED,

        FINISHED,
    }
    private static final AtomicLong COUNTER = new AtomicLong(0);
    private final long taskId;
    private TaskFunction.RequestAction requestAction;
    private TaskFunction.ResponseAction responseAction;
    private TaskFunction.FinishAction finishAction;
    private TaskFunction.TimeoutAction timeoutAction;
    private TaskFunction.CancelAction cancelAction;
    private TaskFunction.ExceptionAction exceptionAction;
    private AtomicReference<State> state = new  AtomicReference<>(State.NEW);
    private Pattern pattern;
    private Strategy strategy;
    private long waitTime = 3000;

    private NexalithicTask() {
        this.taskId = COUNTER.getAndIncrement();
    }

    public static Builder builder() {
        return new Builder();
    }

    public final BusinessPacket request() {
        if (state.get() == State.NEW || state.get() == State.ENQUEUED) {
            state.set(State.REQUESTING);
        } else {
            throw new IllegalStateException("State " + state.get() + " is not in NEW or ENQUEUED state");
        }
        BusinessPacket request = requestAction.execute();
        if (request == null) {
            finish();
        }
        return request;
    }
    public final void response(BusinessPacket packet) {
        if (pattern.equals(Pattern.REQUEST_RESPONSE)) {
            if (state.compareAndSet(State.WAITING, State.RESPONDING)) {
                responseAction.execute(packet);
                state.compareAndSet(State.RESPONDING, State.CANCELLED);
            }
        } else {
            if (state.get() == State.WAITING) {
                state.set(State.STREAMING);
                responseAction.execute(packet);
            } else if (state.get() == State.STREAMING) {
                responseAction.execute(packet);
            }
        }
    }
    public final void finish() {
        if (state.get() != State.FINISHED) {
            state.set(State.FINISHED);
        }
    }
    public final void timeout() {
        if (state.compareAndSet(State.WAITING, State.TIMEOUT)) {
            timeoutAction.execute();
        }
    }
    public final void cancel() {
        cancelAction.execute();
    }
    public final void exception(Exception e) {
        exceptionAction.execute(e);
    }

    public boolean transitTo(State newState) {
        return false;
    }

    public final long getTaskId() {
        return taskId;
    }
    public final Pattern getPattern() {
        return pattern;
    }
    public final Strategy getStrategy() {
        return strategy;
    }
    public final State getState() {
        return state.get();
    }
    public final long getWaitTime() {
        return waitTime;
    }

    public static class Builder {
        private final NexalithicTask task;

        public Builder() {
            task = new NexalithicTask();
        }

        public Builder onRequest(TaskFunction.RequestAction requestAction) {
            task.requestAction = requestAction;
            return this;
        }
        public Builder onResponse(TaskFunction.ResponseAction responseAction) {
            task.responseAction = responseAction;
            return this;
        }
        public Builder onFinish(TaskFunction.FinishAction finishAction) {
            task.finishAction = finishAction;
            return this;
        }
        public Builder onTimeout(TaskFunction.TimeoutAction timeoutAction) {
            task.timeoutAction = timeoutAction;
            return this;
        }
        public Builder onCancel(TaskFunction.CancelAction cancelAction) {
            task.cancelAction = cancelAction;
            return this;
        }
        public Builder onException(TaskFunction.ExceptionAction exceptionAction) {
            task.exceptionAction = exceptionAction;
            return this;
        }

        public Builder setPattern(Pattern pattern) {
            task.pattern = pattern;
            return this;
        }
        public Builder setStrategy(Strategy strategy) {
            task.strategy = strategy;
            return this;
        }
        public Builder setWaitTime(int seconds) {
            task.waitTime = seconds * 1000L;
            return this;
        }

        public NexalithicTask build() {
            if (task.requestAction == null) {
                throw new IllegalArgumentException("requestAction is required");
            }
            if (task.responseAction == null) {
                task.responseAction = packet -> {};
            }
            if (task.finishAction == null) {
                task.finishAction = () -> {};
            }
            if (task.timeoutAction == null) {
                task.timeoutAction = () -> {};
            }
            if (task.cancelAction == null) {
                task.cancelAction = () -> {};
            }
            if (task.exceptionAction == null) {
                task.exceptionAction = e -> {};
            }
            return task;
        }
    }
}