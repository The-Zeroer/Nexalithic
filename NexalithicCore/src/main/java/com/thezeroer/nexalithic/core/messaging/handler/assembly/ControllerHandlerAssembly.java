package com.thezeroer.nexalithic.core.messaging.handler.assembly;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorAnnotationResolver;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorFactory;
import com.thezeroer.nexalithic.core.util.BeanFactory;

import java.util.Collection;

/**
 * Controller Handler 装配配置入口。
 *
 * <p>该接口抽象出 Controller 装配所需的可配置项，调用方可以注册 Controller 实例、
 * 扫描包路径，也可以为拦截器注解解析和实例创建提供扩展实现。</p>
 *
 * @param <HC> Handler 上下文类型
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/21
 */
public interface ControllerHandlerAssembly<HC extends HandlerContext<?>> {
    /**
     * 设置拦截器实例工厂。
     *
     * @param factory 拦截器实例工厂
     * @return 当前装配配置入口
     */
    ControllerHandlerAssembly<HC> interceptorFactory(InterceptorFactory<HC> factory);

    /**
     * 设置拦截器注解解析器。
     *
     * @param resolver 拦截器注解解析器
     * @return 当前装配配置入口
     */
    ControllerHandlerAssembly<HC> annotationResolver(InterceptorAnnotationResolver resolver);

    /**
     * 注册一个 Controller 实例。
     *
     * @param controller Controller 实例
     * @return 当前装配配置入口
     */
    ControllerHandlerAssembly<HC> controller(Object controller);

    /**
     * 扫描包路径并通过 Bean 工厂注册 Controller 实例。
     *
     * @param packageName 待扫描包名
     * @param factory Bean 工厂
     * @return 当前装配配置入口
     */
    ControllerHandlerAssembly<HC> scanControllers(String packageName, BeanFactory factory);

    /**
     * 批量注册 Controller 实例。
     *
     * @param controllers Controller 实例集合
     * @return 当前装配配置入口
     */
    default ControllerHandlerAssembly<HC> controllers(Collection<Object> controllers) {
        for (Object controller : controllers) {
            controller(controller);
        }
        return this;
    }
}
