package com.thezeroer.nexalithic.core.messaging.handler;

import com.thezeroer.nexalithic.core.messaging.handler.interceptor.HandlerInterceptor;
import com.thezeroer.nexalithic.core.messaging.handler.interceptor.InterceptorPipeline;
import com.thezeroer.nexalithic.core.messaging.handler.interceptor.InterceptorPipelineFactory;
import com.thezeroer.nexalithic.core.messaging.handler.mapping.HandlerPathMatcher;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private static final Logger logger = LoggerFactory.getLogger(NexalithicHandler.class);
    private final HandlerInvoker<HC> invoker;
    private final InterceptorPipeline<HC> pipeline;
    private final HandlerMetadata metadata;

    private NexalithicHandler(HandlerInvoker<HC> invoker, InterceptorPipeline<HC> pipeline, HandlerMetadata metadata) {
        this.invoker = invoker;
        this.pipeline = pipeline;
        this.metadata = metadata;
    }

    public static <HC extends HandlerContext<?>> Builder<HC> builder() {
        return new Builder<>();
    }
    public static <HC extends HandlerContext<?>> Builder<HC> builder(HandlerPathMatcher pathMatcher, HandlerInvoker<HC> invoker) {
        return new Builder<>(pathMatcher, invoker);
    }

    public void handle(HC context) throws Exception {
        try {
            if (!pipeline.applyBefore(context)) {
                return;
            }
        } catch (Exception e) {
            logger.warn("HandlerInterceptor.onBefore failed, {}", metadata, e);
            throw e;
        }
        try {
            invoker.invoke(context);
        } catch (Exception e) {
            pipeline.complete(context, e);
            logger.warn("HandlerInvoker.handle failed, {}", metadata, e);
            throw e;
        }
        try {
            pipeline.applyAfter(context);
            pipeline.complete(context, null);
        } catch (Exception e) {
            logger.warn("HandlerInterceptor.onAfter failed, {}", metadata, e);
            pipeline.complete(context, e);
        }
    }

    public HandlerMetadata getMetadata() {
        return metadata;
    }

    public static class Builder<HC extends HandlerContext<?>> {
        private HandlerPathMatcher pathMatcher;
        private HandlerInvoker<HC> invoker;
        private List<HandlerInterceptor<HC>> interceptors = new ArrayList<>();
        private String name, description;

        public Builder() {}
        public Builder(HandlerPathMatcher pathMatcher, HandlerInvoker<HC> invoker) {
            this.pathMatcher = pathMatcher;
            this.invoker = invoker;
        }

        public Builder<HC> pathMatcher(HandlerPathMatcher pathMatcher) {
            this.pathMatcher = pathMatcher;
            return this;
        }

        public Builder<HC> invoker(HandlerInvoker<HC> invoker) {
            this.invoker = invoker;
            return this;
        }

        public Builder<HC> interceptor(HandlerInterceptor<HC> interceptor) {
            this.interceptors.add(interceptor);
            return this;
        }

        @SafeVarargs
        public final Builder<HC> interceptors(HandlerInterceptor<HC>... interceptors) {
            Collections.addAll(this.interceptors, interceptors);
            return this;
        }

        public Builder<HC> interceptors(List<? extends HandlerInterceptor<HC>> interceptors) {
            this.interceptors.addAll(interceptors);
            return this;
        }

        public Builder<HC> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<HC> description(String description) {
            this.description = description;
            return this;
        }

        public NexalithicHandler<HC> build() {
            if (pathMatcher == null) {
                throw new IllegalArgumentException("pathMatcher is required");
            }
            if (invoker == null) {
                throw new IllegalArgumentException("invoker is required");
            }
            if (name == null || name.isEmpty()) {
                name = "Unnamed" + pathMatcher.formatPath();
            }
            if (description == null) {
                description = "";
            }
            HandlerMetadata metadata = new HandlerMetadata(name, description, pathMatcher);
            return new NexalithicHandler<>(invoker, InterceptorPipelineFactory.create(metadata, interceptors), metadata);
        }
    }
}
