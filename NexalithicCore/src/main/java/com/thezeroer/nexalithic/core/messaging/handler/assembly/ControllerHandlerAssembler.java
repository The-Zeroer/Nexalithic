package com.thezeroer.nexalithic.core.messaging.handler.assembly;

import com.thezeroer.nexalithic.core.exception.NexalithicDuplicateKeyException;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerInvoker;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.annotation.InterceptorBinding;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.annotation.NexalithicHandlerController;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.annotation.NexalithicHandlerMethod;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorAnnotationResolver;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorConfigurationBinding;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.interceptor.InterceptorFactory;
import com.thezeroer.nexalithic.core.messaging.handler.interceptor.HandlerInterceptor;
import com.thezeroer.nexalithic.core.messaging.handler.mapping.HandlerPathMatcher;
import com.thezeroer.nexalithic.core.messaging.handler.mapping.HandlerRegistry;
import com.thezeroer.nexalithic.core.util.BeanFactory;
import com.thezeroer.nexalithic.core.util.ClassScanner;

import java.lang.annotation.Annotation;
import java.lang.invoke.*;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 控制器处理程序汇编器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/07/11
 */
public class ControllerHandlerAssembler<HC extends HandlerContext<?>> implements ControllerHandlerAssembly<HC> {
    private final Set<Object> controllers = new LinkedHashSet<>();
    private InterceptorFactory<HC> interceptorFactory;
    private InterceptorAnnotationResolver annotationResolver;
    private final Class<HC> handlerContextType;

    public ControllerHandlerAssembler(Class<HC> handlerContextType) {
        this.handlerContextType = handlerContextType;
    }

    public void assembleInto(HandlerRegistry.Builder<HC> registryBuilder) throws Throwable {
        for (Object controller : controllers) {
            Class<?> clazz = controller.getClass();
            NexalithicHandlerController handlerController = clazz.getAnnotation(NexalithicHandlerController.class);
            List<InterceptorAnnotationBinding> controllerInterceptorBinding = findInterceptorBindings(clazz);
            for (Method method : clazz.getDeclaredMethods()) {
                NexalithicHandlerMethod handlerMethod = method.getAnnotation(NexalithicHandlerMethod.class);
                if (handlerMethod == null) {
                    continue;
                }
                HandlerPathMatcher pathMatcher = HandlerPathMatcherParser.parse(handlerController, handlerMethod);
                List<InterceptorAnnotationBinding> handlerInterceptorBinding = findInterceptorBindings(method);
                registryBuilder.register(pathMatcher, NexalithicHandler.<HC>builder()
                        .pathMatcher(pathMatcher)
                        .invoker(createInvoker(controller, method))
                        .interceptors(createInterceptors(controllerInterceptorBinding, handlerInterceptorBinding))
                        .build());
            }
        }
    }

    @Override
    public ControllerHandlerAssembly<HC> interceptorFactory(InterceptorFactory<HC> factory) {
        interceptorFactory = factory;
        return this;
    }

    @Override
    public ControllerHandlerAssembly<HC> annotationResolver(InterceptorAnnotationResolver resolver) {
        annotationResolver = resolver;
        return this;
    }

    @Override
    public ControllerHandlerAssembly<HC> controller(Object controller) {
        if (!controller.getClass().isAnnotationPresent(NexalithicHandlerController.class)) {
            throw new IllegalArgumentException(controller.getClass().getName() + " is not annotated with @NexalithicHandlerController");
        }
        if (!controllers.add(controller)) {
            throw new NexalithicDuplicateKeyException("controllerSet", controller);
        }
        return this;
    }

    @Override
    public ControllerHandlerAssembly<HC> scanControllers(String packageName, BeanFactory factory) {
        List<Class<?>> classes = ClassScanner.scan(packageName);
        for(Class<?> clazz : classes) {
            NexalithicHandlerController handlerController = clazz.getAnnotation(NexalithicHandlerController.class);
            if (handlerController == null) {
                continue;
            }
            controller(factory.getBean(clazz));
        }
        return this;
    }

    private HandlerInvoker<HC> createInvoker(Object bean, Method method) throws Throwable {
        Class<?> declaringType = method.getDeclaringClass();

        /*
         * Controller实例必须属于方法的声明类型。
         *
         * 允许bean是Controller的子类或代理子类，例如：
         *
         * declaringType = AuthController.class
         * bean.getClass() = AuthController$$Proxy.class
         */
        if (!declaringType.isInstance(bean)) {
            throw new IllegalArgumentException(
                    "Bean type '%s' is not compatible with handler method declaring type '%s': %s"
                            .formatted(
                                    bean.getClass().getName(),
                                    declaringType.getName(),
                                    method
                            )
            );
        }

        int classModifiers = declaringType.getModifiers();

        /*
         * MethodHandles.lookup()从当前Assembler类的访问上下文出发。
         *
         * 为了确保能够访问其他包中的Controller，
         * Controller声明类本身必须是public。
         */
        if (!Modifier.isPublic(classModifiers)) {
            throw new IllegalArgumentException(
                    "Handler controller class must be public: "
                            + declaringType.getName()
            );
        }

        int methodModifiers = method.getModifiers();

        /*
         * 当前Lambda工厂通过捕获bean实例绑定Controller，
         * 因此只允许实例方法。
         */
        if (Modifier.isStatic(methodModifiers)) {
            throw new IllegalArgumentException(
                    "Handler method must be an instance method: "
                            + method
            );
        }

        if (Modifier.isAbstract(methodModifiers)) {
            throw new IllegalArgumentException(
                    "Handler method must not be abstract: "
                            + method
            );
        }

        if (Modifier.isNative(methodModifiers)) {
            throw new IllegalArgumentException(
                    "Handler method must not be native: "
                            + method
            );
        }

        /*
         * 使用MethodHandles.lookup()访问其他类型的方法时，
         * Handler方法必须是public。
         */
        if (!Modifier.isPublic(methodModifiers)) {
            throw new IllegalArgumentException(
                    "Handler method must be public: "
                            + method
            );
        }

        if (method.isBridge()) {
            throw new IllegalArgumentException(
                    "Handler method must not be a bridge method: "
                            + method
            );
        }

        if (method.isSynthetic()) {
            throw new IllegalArgumentException(
                    "Handler method must not be synthetic: "
                            + method
            );
        }

        /*
         * 当前HandlerInvoker的调用模型为：
         *
         * void handle(HC context)
         *
         * 因此Controller方法也必须返回void。
         */
        if (method.getReturnType() != void.class) {
            throw new IllegalArgumentException(
                    "Handler method must return void: "
                            + method
            );
        }

        Class<?>[] parameterTypes = method.getParameterTypes();

        /*
         * 当前只支持一个HandlerContext参数。
         */
        if (parameterTypes.length != 1) {
            throw new IllegalArgumentException(
                    "Handler method must declare exactly one parameter: "
                            + method
            );
        }

        Class<?> methodContextType = parameterTypes[0];

        /*
         * 方法参数必须是HandlerContext或其子类。
         */
        if (!HandlerContext.class.isAssignableFrom(methodContextType)) {
            throw new IllegalArgumentException(
                    "Handler method parameter must extend HandlerContext, but found '%s': %s"
                            .formatted(
                                    methodContextType.getName(),
                                    method
                            )
            );
        }

        /*
         * 方法参数必须能够接收Assembler运行时提供的上下文类型。
         *
         * 例如：
         *
         * handlerContextType = ServerHandlerContext
         * methodContextType  = HandlerContext
         *
         * HandlerContext可以接收ServerHandlerContext，因此合法。
         *
         * 正确判断方向：
         *
         * 方法参数类型.isAssignableFrom(实际上下文类型)
         */
        if (!methodContextType.isAssignableFrom(handlerContextType)) {
            throw new IllegalArgumentException(
                    "Handler method parameter type '%s' cannot accept assembler context type '%s': %s"
                            .formatted(
                                    methodContextType.getName(),
                                    handlerContextType.getName(),
                                    method
                            )
            );
        }

        /*
         * Lookup的访问上下文是ControllerHandlerAssembler所在类。
         *
         * 它可以访问其他公共类型中的公共方法，同时具有
         * LambdaMetafactory创建Lambda所需的调用者权限。
         */
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        /*
         * 将反射Method转换为直接MethodHandle。
         *
         * 假设Controller方法为：
         *
         * public void login(ServerHandlerContext context)
         *
         * 并且声明在AuthController中，则实例方法句柄类型是：
         *
         * (AuthController, ServerHandlerContext)void
         *
         * 第一个参数是实例方法的接收者。
         */
        MethodHandle implementationMethod =
                lookup.unreflect(method);

        /*
         * Lambda工厂的调用签名。
         *
         * 例如：
         *
         * (AuthController)HandlerInvoker
         *
         * 调用这个工厂并传入Controller实例，
         * 会得到捕获了该Controller实例的HandlerInvoker。
         */
        MethodType invokedType =
                MethodType.methodType(
                        HandlerInvoker.class,
                        declaringType
                );

        /*
         * HandlerInvoker泛型擦除后的SAM方法签名。
         *
         * 原始接口：
         *
         * void handle(HC context)
         *
         * HC extends HandlerContext<?>
         *
         * 擦除后的JVM签名：
         *
         * void handle(HandlerContext context)
         */
        MethodType samMethodType =
                MethodType.methodType(
                        void.class,
                        HandlerContext.class
                );

        /*
         * 当前Lambda实例化后的具体SAM签名。
         *
         * 这里使用Controller方法实际声明的参数类型。
         *
         * 例如：
         *
         * void handle(ServerHandlerContext context)
         *
         * 或：
         *
         * void handle(HandlerContext<?> context)
         */
        MethodType instantiatedMethodType =
                MethodType.methodType(
                        void.class,
                        methodContextType
                );

        /*
         * 创建Lambda调用点。
         *
         * 逻辑上相当于生成：
         *
         * context -> bean.handler(context)
         */
        CallSite callSite =
                LambdaMetafactory.metafactory(
                        lookup,

                        /*
                         * HandlerInvoker函数式接口中的抽象方法名。
                         */
                        "invoke",

                        /*
                         * Lambda工厂签名：
                         *
                         * (ControllerType)HandlerInvoker
                         */
                        invokedType,

                        /*
                         * HandlerInvoker擦除后的SAM签名：
                         *
                         * (HandlerContext)void
                         */
                        samMethodType,

                        /*
                         * 实际Controller方法句柄：
                         *
                         * (ControllerType, MethodContextType)void
                         */
                        implementationMethod,

                        /*
                         * HandlerInvoker实例化后的具体SAM签名：
                         *
                         * (MethodContextType)void
                         */
                        instantiatedMethodType
                );

        /*
         * callSite.getTarget()返回Lambda工厂MethodHandle：
         *
         * (ControllerType)HandlerInvoker
         *
         * 这里传入bean只是创建并绑定Invoker，
         * 不会立即执行Controller方法。
         */
        Object result =
                callSite.getTarget().invoke(bean);

        @SuppressWarnings("unchecked")
        HandlerInvoker<HC> invoker =
                (HandlerInvoker<HC>) result;

        return invoker;
    }
    private List<HandlerInterceptor<HC>> createInterceptors(List<InterceptorAnnotationBinding> controllerInterceptorBinding, List<InterceptorAnnotationBinding> handlerInterceptorBinding) {
        List<HandlerInterceptor<HC>> interceptors = new ArrayList<>();
        appendInterceptors(interceptors, controllerInterceptorBinding);
        appendInterceptors(interceptors, handlerInterceptorBinding);
        return interceptors;
    }

    private void appendInterceptors(List<HandlerInterceptor<HC>> interceptors, List<InterceptorAnnotationBinding> bindings) {
        if (bindings.isEmpty()) {
            return;
        }
        if (annotationResolver == null) {
            throw new IllegalStateException("InterceptorAnnotationResolver is required when interceptor bindings are declared");
        }
        if (interceptorFactory == null) {
            throw new IllegalStateException("InterceptorFactory is required when interceptor bindings are declared");
        }
        for (InterceptorAnnotationBinding binding : bindings) {
            InterceptorConfigurationBinding configurationBinding = annotationResolver.resolve(binding.annotation(), binding.interceptorType());
            HandlerInterceptor<HC> interceptor = interceptorFactory.get(configurationBinding);
            if (interceptor == null) {
                throw new IllegalStateException("InterceptorFactory returned null for " + binding.interceptorType().getName());
            }
            interceptors.add(interceptor);
        }
    }

    private record InterceptorAnnotationBinding(Class<? extends HandlerInterceptor<?>> interceptorType, Annotation annotation, int order) {
    }

    private static List<InterceptorAnnotationBinding> findInterceptorBindings(AnnotatedElement element) {
        Annotation[] annotations = element.getDeclaredAnnotations();
        if (annotations.length == 0) {
            return List.of();
        }
        List<InterceptorAnnotationBinding> bindings = new ArrayList<>(annotations.length);
        for (Annotation annotation : annotations) {
            for (InterceptorBinding interceptorBinding : annotation.annotationType().getDeclaredAnnotationsByType(InterceptorBinding.class)) {
                bindings.add(new InterceptorAnnotationBinding(interceptorBinding.value(), annotation, interceptorBinding.order()));
            }
        }
        bindings.sort(Comparator.comparingInt(InterceptorAnnotationBinding::order));
        return bindings;
    }
}
