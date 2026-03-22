package com.thezeroer.nexalithic.core.messaging.task;

import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;

import java.util.concurrent.atomic.AtomicLong;

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
    public enum State {

    }
    private static final AtomicLong COUNTER = new AtomicLong(0);
    private final TaskFunction delegate;
    private final long taskId;

    public NexalithicTask(TaskFunction delegate) {
        this.delegate = delegate;
        this.taskId = COUNTER.getAndIncrement();
    }

    public final BusinessPacket request() {
        return delegate.request();
    }
    public final void response(BusinessPacket packet) {
        delegate.response(packet);
    }
    public final void timeout() {
        delegate.timeout();
    }
    public final void cancel() {
        delegate.cancel();
    }
    public final void finish() {
        delegate.finish();
    }
    public final void exception(Exception e) {
        delegate.exception(e);
    }

    public final long getTaskId() {
        return taskId;
    }
}