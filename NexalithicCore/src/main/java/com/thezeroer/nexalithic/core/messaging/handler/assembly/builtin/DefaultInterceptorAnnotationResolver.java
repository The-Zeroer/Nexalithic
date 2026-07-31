package com.thezeroer.nexalithic.core.messaging.handler.assembly.builtin;

import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorAnnotationResolver;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorConfigurationBinding;
import com.thezeroer.nexalithic.core.messaging.handler.interceptor.HandlerInterceptor;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * 默认拦截器注释解析器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/29
 */
public class DefaultInterceptorAnnotationResolver implements InterceptorAnnotationResolver {
    private final List<InterceptorAnnotationParser> parsers;

    public DefaultInterceptorAnnotationResolver(List<InterceptorAnnotationParser> parsers) {
        List<InterceptorAnnotationParser> sorted = new ArrayList<>(parsers);
        sorted.sort(Comparator.comparingInt(InterceptorAnnotationParser::priority).reversed());
        this.parsers = List.copyOf(sorted);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public InterceptorConfigurationBinding resolve(Annotation annotation, Class<? extends HandlerInterceptor<?>> interceptorType) {
        for (InterceptorAnnotationParser parser : parsers) {
            if (parser.supports(annotation.annotationType(), interceptorType)) {
                return new InterceptorConfigurationBinding(parser.parse(annotation, interceptorType), interceptorType);
            }
        }
        throw new IllegalStateException("No InterceptorAnnotationParser supports binding '%s -> %s'"
                .formatted(annotation.annotationType().getName(), interceptorType.getName())
        );
    }

    public static class Builder {
        private final List<InterceptorAnnotationParser> parsers = new ArrayList<>();

        public Builder parser(InterceptorAnnotationParser parser) {
            parsers.add(parser);
            return this;
        }
        public Builder parsers(List<InterceptorAnnotationParser> parsers) {
            this.parsers.addAll(parsers);
            return this;
        }

        public DefaultInterceptorAnnotationResolver build() {
            return new DefaultInterceptorAnnotationResolver(parsers);
        }
    }
}
