package com.thezeroer.nexalithic.core.messaging.task;

import com.thezeroer.nexalithic.core.messaging.Dispatchable;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.infra.timer.Expirable;
import com.thezeroer.nexalithic.core.session.NexalithicSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
public class NexalithicTask implements Expirable, Dispatchable {
    public enum Pattern {
        /** 只有请求，无回执。发送完即销毁。 */
        ONE_WAY,

        /** 标准请求-响应。收到一个回执或超时后销毁。 */
        REQUEST_RESPONSE,

        /** 流模式。持续接收回执，直到手动取消。 */
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

        /** 正在生成请求包 */
        REQUESTING,

        /** 正在发送或已提交至网络缓冲区 */
        SENDING,

        /** 模式相关：已发出，正在等待对端回执（ONE_WAY 模式通常跳过此状态） */
        WAITING,

        /** 模式相关：流处理中，已接收过数据但尚未结束（Pattern.STREAM 特有） */
        STREAMING,

        /** 已收到响应包，正在执行 ResponseAction 业务逻辑 */
        RESPONDING,

        /** 执行完响应 */
        COMPLETED,

        /** 未收到响应，超时 */
        TIMEOUT,

        /** 其他过程中出现异常 */
        FAILED,

        /** 任务取消 */
        CANCELLED,

        /** 结束 */
        FINISHED,
    }
    private static final Logger logger = LoggerFactory.getLogger(NexalithicTask.class);
    private static final AtomicLong COUNTER = new AtomicLong(0);
    private final long taskId;
    private final AtomicReference<State> state = new  AtomicReference<>(State.NEW);
    private final TaskFunction.RequestAction requestAction;
    private final TaskFunction.ResponseAction responseAction;
    private final TaskFunction.FinishAction finishAction;
    private final TaskFunction.TimeoutAction timeoutAction;
    private final TaskFunction.CancelAction cancelAction;
    private final TaskFunction.FailedAction failedAction;
    private final Pattern pattern;
    private final Strategy strategy;
    private final long waitTime;

    private final TaskFuture future;
    private volatile NexalithicSession<?, ?, ?, ?, ?> targetSession;
    private volatile BusinessPacket responsePacket;
    private volatile long lastResponseTime = -1;

    private NexalithicTask(TaskFunction.RequestAction requestAction, TaskFunction.ResponseAction responseAction,
                           TaskFunction.FinishAction finishAction, TaskFunction.TimeoutAction timeoutAction,
                           TaskFunction.CancelAction cancelAction, TaskFunction.FailedAction failedAction,
                           Pattern pattern, Strategy strategy, long waitTime, TaskTracer tracer) {
        this.taskId = COUNTER.getAndIncrement();
        this.requestAction = requestAction;
        this.responseAction = responseAction;
        this.finishAction = finishAction;
        this.timeoutAction = timeoutAction;
        this.cancelAction = cancelAction;
        this.failedAction = failedAction;
        this.pattern = pattern;
        this.strategy = strategy;
        this.waitTime = waitTime;
        future = new TaskFuture(this, tracer);
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
        return requestAction.execute();
    }
    public final void response(BusinessPacket packet) {
        if (pattern == Pattern.REQUEST_RESPONSE) {
            if (state.compareAndSet(State.WAITING, State.RESPONDING)) {
                try {
                    responseAction.execute(packet, future);
                    state.compareAndSet(State.RESPONDING, State.COMPLETED);
                } catch (Exception e) {
                    failed(e);
                } finally {
                    finish();
                }
            }
        } else {
            lastResponseTime = System.currentTimeMillis();
            try {
                if (state.get() == State.WAITING) {
                    state.set(State.STREAMING);
                    responseAction.execute(packet, future);
                } else if (state.get() == State.STREAMING) {
                    responseAction.execute(packet, future);
                }
            } catch (Exception e) {
                failed(e);
            }
        }
    }
    public final void finish() {
        if (state.get() != State.FINISHED) {
            state.set(State.FINISHED);
            finishAction.execute();
        }
        future.internalComplete();
    }
    public final void timeout() {
        if (state.compareAndSet(State.WAITING, State.TIMEOUT)) {
            timeoutAction.execute();
        }
        finish();
    }
    public final void cancel() {
        if (state.get() != State.CANCELLED) {
            state.set(State.CANCELLED);
            cancelAction.execute();
        }
        finish();
    }
    public final void failed(Exception e) {
        if (state.get() != State.FAILED) {
            state.set(State.FAILED);
            failedAction.execute(e);
        }
        finish();
    }

    public final void setTargetSession(NexalithicSession<?, ?, ?, ?, ?> targetSession) {
        this.targetSession = targetSession;
    }
    public final NexalithicSession<?, ?, ?, ?, ?> getTargetSession() {
        return targetSession;
    }
    public final void setResponsePacket(BusinessPacket responsePacket) {
        this.responsePacket = responsePacket;
    }
    public final BusinessPacket getResponsePacket() {
        return responsePacket;
    }

    public final void transitTo(State newState) {
        state.set(newState);
    }
    public final TaskFuture getFuture() {
        return future;
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

    @Override
    public long getExpiryTime() {
        if (lastResponseTime == -1) {
            return System.currentTimeMillis() + waitTime;
        } else {
            return lastResponseTime + waitTime;
        }
    }

    @Override
    public boolean onExpiryTriggered() {
        return System.currentTimeMillis() > lastResponseTime + waitTime;
    }

    @Override
    public boolean isCancelled() {
        return future.isDone();
    }

    @Override
    public final Type type() {
        return Type.Task;
    }

    public static class Builder {
        private TaskFunction.RequestAction requestAction;
        private TaskFunction.ResponseAction responseAction;
        private TaskFunction.FinishAction finishAction;
        private TaskFunction.TimeoutAction timeoutAction;
        private TaskFunction.CancelAction cancelAction;
        private TaskFunction.FailedAction failedAction;
        private Pattern pattern;
        private Strategy strategy;
        private long waitTime = 3000;

        public Builder() {
            pattern = Pattern.REQUEST_RESPONSE;
            strategy = Strategy.ASYNC;
            responseAction = (packet, future) -> {};
            finishAction = () -> {};
            timeoutAction = () -> {};
            cancelAction = () -> {};
            failedAction = exception -> {
                if (exception != null) {
                    logger.error("Exception in NexalithicTask", exception);
                }
            };
        }

        public Builder onRequest(TaskFunction.RequestAction requestAction) {
            this.requestAction = requestAction;
            return this;
        }
        public Builder onResponse(TaskFunction.ResponseAction responseAction) {
            this.responseAction = responseAction;
            return this;
        }
        public Builder onResponse(TaskFunction.SimpleResponseAction responseAction) {
            this.responseAction = (packet, future) -> responseAction.execute(packet);
            return this;
        }
        public Builder onFinish(TaskFunction.FinishAction finishAction) {
            this.finishAction = finishAction;
            return this;
        }
        public Builder onTimeout(TaskFunction.TimeoutAction timeoutAction) {
            this.timeoutAction = timeoutAction;
            return this;
        }
        public Builder onCancel(TaskFunction.CancelAction cancelAction) {
            this.cancelAction = cancelAction;
            return this;
        }
        public Builder onFailed(TaskFunction.FailedAction failedAction) {
            this.failedAction = failedAction;
            return this;
        }

        public Builder setPattern(Pattern pattern) {
            this.pattern = pattern;
            return this;
        }
        public Builder setStrategy(Strategy strategy) {
            this.strategy = strategy;
            return this;
        }
        public Builder setWaitTime(int seconds) {
            this.waitTime = seconds * 1000L;
            return this;
        }

        public NexalithicTask build(TaskTracer tracer) {
            if (requestAction == null) {
                throw new IllegalArgumentException("requestAction is required");
            }
            return new NexalithicTask(requestAction, responseAction, finishAction, timeoutAction, cancelAction, failedAction,
                    pattern, strategy, waitTime, tracer);
        }
    }
}