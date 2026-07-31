package com.thezeroer.nexalithic.core.messaging.handler.assembly.builtin;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorConfigurationBinding;
import com.thezeroer.nexalithic.core.messaging.handler.interceptor.HandlerInterceptor;

/**
 * 拦截器创建器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/11
 */
public interface InterceptorCreator<HC extends HandlerContext<?>> {

    /**
     * 根据配置绑定创建拦截器。
     *
     * @param configurationBinding 配置绑定
     * @return 创建的拦截器
     */
    HandlerInterceptor<HC> create(InterceptorConfigurationBinding configurationBinding);

    /**
     * 返回当前创建器负责创建的拦截器类型。
     *
     * @return 拦截器实现类型
     */
    Class<? extends HandlerInterceptor<HC>> type();

    default InterceptorScope scope() {
        return InterceptorScope.PER_BINDING;
    }
}