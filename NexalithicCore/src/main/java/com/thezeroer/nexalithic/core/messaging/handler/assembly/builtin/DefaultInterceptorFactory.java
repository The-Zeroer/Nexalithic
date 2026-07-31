package com.thezeroer.nexalithic.core.messaging.handler.assembly.builtin;

import com.thezeroer.nexalithic.core.exception.NexalithicDuplicateKeyException;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorConfigurationBinding;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorFactory;
import com.thezeroer.nexalithic.core.messaging.handler.interceptor.HandlerInterceptor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认拦截机工厂
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/29
 */
public class DefaultInterceptorFactory<HC extends HandlerContext<?>> implements InterceptorFactory<HC> {
    private final Map<Class<? extends HandlerInterceptor<?>>, InterceptorCreator<HC>> creators;
    private final Map<Class<? extends HandlerInterceptor<?>>, HandlerInterceptor<HC>> caches;

    public DefaultInterceptorFactory(Map<Class<? extends HandlerInterceptor<?>>, InterceptorCreator<HC>> creators) {
        this.creators = Map.copyOf(creators);
        this.caches = new LinkedHashMap<>();
    }

    public static <HC extends HandlerContext<?>> Builder<HC> builder() {
        return new Builder<>();
    }

    @Override
    public HandlerInterceptor<HC> get(InterceptorConfigurationBinding binding) {
        InterceptorCreator<HC> creator = creators.get(binding.interceptorType());
        if (creator == null) {
            throw new IllegalStateException("No InterceptorCreator for " + binding.interceptorType().getName());
        }
        if (creator.scope() == InterceptorScope.SINGLETON) {
            return caches.computeIfAbsent(binding.interceptorType(), ignored -> creator.create(binding));
        }
        return creator.create(binding);
    }

    public static class Builder<HC extends HandlerContext<?>> {
        private final Map<Class<? extends HandlerInterceptor<?>>, InterceptorCreator<HC>> creators = new LinkedHashMap<>();
        private Builder() {}

        public Builder<HC> creator(InterceptorCreator<HC> creator) {
            if (creators.putIfAbsent(creator.type(),  creator) != null) {
                throw new NexalithicDuplicateKeyException("creators", creator);
            }
            return this;
        }

        public Builder<HC> creator(Collection<InterceptorCreator<HC>> creators) {
            for (InterceptorCreator<HC> creator : creators) {
                creator(creator);
            }
            return this;
        }

        public DefaultInterceptorFactory<HC> build() {
            return new DefaultInterceptorFactory<>(creators);
        }
    }
}
