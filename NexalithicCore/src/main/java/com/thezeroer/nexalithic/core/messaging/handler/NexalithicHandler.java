package com.thezeroer.nexalithic.core.messaging.handler;

import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;

/**
 * <h1>Nexalithic 消息处理器 (Handler)</h1>
 * <p>该类是业务逻辑处理的核心抽象，负责接收、解析并响应对端发送的 {@link BusinessPacket}。</p>
 * <p><b>主要职责：</b></p>
 * <ul>
 * <li>定义特定 {@code WayCode} 或消息类型的处理行为。</li>
 * <li>解包 Payload 数据并转换为具体的业务领域模型。</li>
 * <li>通过会话上下文回传处理结果或执行状态。</li>
 * </ul>
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/03/15
 * @see NexalithicTask
 */
public class NexalithicHandler<HC extends HandlerContext<?>> {
    private final HandlerFunction<HC> delegate;
    private final boolean defaultRequireAuth;
    private String name;

    public NexalithicHandler(HandlerFunction<HC> delegate) {
        this.delegate = delegate;
        AccessControl ac = getClass().getAnnotation(AccessControl.class);
        this.defaultRequireAuth = (ac == null || ac.requireAuth());
    }
    public NexalithicHandler(HandlerFunction<HC> delegate, boolean requireAuth) {
        this.delegate = delegate;
        this.defaultRequireAuth = requireAuth;
    }
    public NexalithicHandler(HandlerFunction<HC> delegate, boolean requireAuth, String name) {
        this.delegate = delegate;
        this.defaultRequireAuth = requireAuth;
        this.name = name;
    }

    public void handle(HC context) {
        delegate.handle(context);
    }

    public boolean requireAuth() {
        return defaultRequireAuth;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
