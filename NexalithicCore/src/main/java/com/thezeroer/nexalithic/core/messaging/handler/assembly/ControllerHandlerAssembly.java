package com.thezeroer.nexalithic.core.messaging.handler.assembly;

import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorAnnotationResolver;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorFactory;
import com.thezeroer.nexalithic.core.util.BeanFactory;

import java.util.Collection;

/**
 * 控制器处理器组件
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/21
 */
public interface ControllerHandlerAssembly<HC extends HandlerContext<?>> {
    ControllerHandlerAssembly<HC> interceptorFactory(InterceptorFactory<HC> factory);
    ControllerHandlerAssembly<HC> annotationResolver(InterceptorAnnotationResolver resolver);

    ControllerHandlerAssembly<HC> controller(Object controller);
    ControllerHandlerAssembly<HC> scanControllers(String packageName, BeanFactory factory);
    default ControllerHandlerAssembly<HC> controllers(Collection<Object> controllers) {
        for (Object controller : controllers) {
            controller(controller);
        }
        return this;
    }
}
