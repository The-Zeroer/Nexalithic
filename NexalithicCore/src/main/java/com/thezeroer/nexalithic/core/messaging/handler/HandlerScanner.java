package com.thezeroer.nexalithic.core.messaging.handler;

import com.thezeroer.nexalithic.core.util.BeanFactory;
import com.thezeroer.nexalithic.core.util.ClassScanner;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 处理器扫描器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/03/16
 * @version 1.0.0
 */
public class HandlerScanner {

    public static <HC extends HandlerContext> void scanAndRegister(String packageName, BeanFactory factory, HandlerRegistry<HC> registry) throws Throwable {
        List<Class<?>> classes = ClassScanner.scan(packageName);
        for (Class<?> clazz : classes) {
            HandlerMapping classAnnotation = clazz.getAnnotation(HandlerMapping.class);
            if (classAnnotation == null) {
                continue;
            }
            HandlerRegistry.PathMatcher classMatcher = HandlerRegistry.parse(classAnnotation);
            Object bean = factory.getBean(clazz);
            for (Method method : clazz.getDeclaredMethods()) {
                HandlerMapping methodAnnotation = method.getAnnotation(HandlerMapping.class);
                if (methodAnnotation == null) {
                    continue;
                }
                HandlerRegistry.PathMatcher methodMatcher = HandlerRegistry.parse(methodAnnotation);
                HandlerRegistry.PathMatcher fullMatcher = new HandlerRegistry.PathMatcher();
                fullMatcher.combine(classMatcher).combine(methodMatcher);
                registry.register(fullMatcher, createHandler(bean, method));
            }
        }
    }
    private static <HC extends HandlerContext> NexalithicHandler<HC> createHandler(Object bean, Method method) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle methodHandle = lookup.unreflect(method);
        MethodType methodType = MethodType.methodType(void.class, HandlerContext.class);
        MethodType factoryType = MethodType.methodType(HandlerFunction.class, bean.getClass());
        MethodType instantiatedMethodType = MethodType.methodType(void.class, HandlerContext.class);
        CallSite site = LambdaMetafactory.metafactory(
                lookup,
                "handle", // 接口中的方法名
                factoryType,                // 产生的工厂方法签名，包含被捕获的 bean
                methodType,                 // 抽象方法签名
                methodHandle,               // 实际执行的方法句柄
                instantiatedMethodType      // 实际执行时的方法签名
        );
        @SuppressWarnings("unchecked")
        HandlerFunction<HC> func = (HandlerFunction<HC>) site.getTarget().invoke(bean);
        AccessControl methodAc = method.getAnnotation(AccessControl.class);
        AccessControl classAc = bean.getClass().getAnnotation(AccessControl.class);
        boolean auth = (methodAc != null) ? methodAc.requireAuth() : (classAc == null || classAc.requireAuth());
        return new NexalithicHandler<>(func, auth, bean.getClass().getSimpleName() + "#" + method.getName());
    }
}
