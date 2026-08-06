package com.thezeroer.nexalithic.client;

import com.thezeroer.nexalithic.client.lifecycle.LifecycleManager;
import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.client.manager.LinkStatusManager;
import com.thezeroer.nexalithic.client.messaging.ClientBusinessPacketDispatcher;
import com.thezeroer.nexalithic.client.messaging.ClientHandlerContext;
import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.event.NexalithicEventBus;
import com.thezeroer.nexalithic.core.io.codec.assembler.BusinessPacketsAssembler;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.ControllerHandlerAssembler;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.ControllerHandlerAssemblerConfigurer;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.ControllerHandlerAssemblerHelper;
import com.thezeroer.nexalithic.core.messaging.handler.mapping.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.messaging.handler.mapping.TrieNodeChildrenStorage;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadConstructorStorage;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskFuture;
import com.thezeroer.nexalithic.core.messaging.task.TaskTracer;
import com.thezeroer.nexalithic.core.messaging.visual.TransferListenerGroup;
import com.thezeroer.nexalithic.core.messaging.visual.TransferTracer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.business.payload.AbstractPayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.FilePayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.SerializablePayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.TextPayload;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.client.lifecycle.GeneralLoop;
import com.thezeroer.nexalithic.client.security.ClientSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Nexalithic客户端
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
@SuppressWarnings("UnusedReturnValue")
public class NexalithicClient {
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<LifecycleManager> LifecycleManager = NexalithicModule.create("NexalithicClient_LifecycleManager", LifecycleManager.class);
        public static final NexalithicModule<LinkStatusManager> LinkStatusManager = NexalithicModule.create("NexalithicClient_LinkStatusManager", LinkStatusManager.class);
        public static final NexalithicModule<ClientBusinessPacketDispatcher> BusinessPacketDispatcher = NexalithicModule.create("NexalithicClient_BusinessPacketDispatcher", ClientBusinessPacketDispatcher.class);
        public static final NexalithicModule<ClientSecurityPolicy> SecurityPolicy = NexalithicModule.create("NexalithicClient_SecurityPolicy", ClientSecurityPolicy.class);
        public static final NexalithicModule<NexalithicEventBus> EventBus = NexalithicModule.create("NexalithicClient_EventBus", NexalithicEventBus.class);
    }
    private static final Logger logger = LoggerFactory.getLogger(NexalithicClient.class);
    private final LifecycleManager lifecycleManager;
    private final LinkStatusManager linkStatusManager;
    private final GeneralLoop generalLoop;
    private final ClientBusinessPacketDispatcher businessPacketDispatcher;
    private final NexalithicEventBus eventBus;

    private NexalithicClient(NexalithicBuilderContext context) {
        this.lifecycleManager = context.getModule(Modules.LifecycleManager);
        this.linkStatusManager = context.getModule(Modules.LinkStatusManager);
        this.businessPacketDispatcher = context.getModule(Modules.BusinessPacketDispatcher);
        this.generalLoop = context.getModule(LifecycleManager.Modules.GeneralLoop);
        this.eventBus = context.getModule(Modules.EventBus);
        System.gc();
    }
    public static NexalithicClient unsafeCreate(NexalithicBuilderContext context) {
        return new NexalithicClient(context);
    }

    public static Builder builder() {
        logger.info(Banner.BANNER);
        return new Builder();
    }

    public void start() {
        lifecycleManager.start();
    }
    public void stop() {
        lifecycleManager.stop();
    }
    public void shutdown() {
        lifecycleManager.shutdown();
    }

    public boolean link(InetSocketAddress remote) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, ShortBufferException {
        if (linkStatusManager.getStatus() != LinkStatusManager.Status.UNLINKED) {
            throw new IllegalStateException("Cannot link while in State " + linkStatusManager.getStatus() + ", must be " + LinkStatusManager.Status.UNLINKED);
        }
        SocketChannel socketChannel = SocketChannel.open(remote);
        logger.info("Linking to [{}]", remote);
        linkStatusManager.trigger(LinkStatusManager.Status.LINKING, remote);
        generalLoop.getNetworkRouter().setServerAddress(remote);
        try {
            if (generalLoop.link(AbstractPacket.PacketType.SIGNALING, socketChannel, null)) {
                return true;
            } else {
                linkStatusManager.trigger(LinkStatusManager.Status.UNLINKED, LinkStatusManager.Reason.REMOTE_ACTIVE);
            }
        } catch (Exception e) {
            linkStatusManager.trigger(LinkStatusManager.Status.UNLINKED, e instanceof IOException ? LinkStatusManager.Reason.NETWORK_ERROR : LinkStatusManager.Reason.PROTOCOL_ERROR, e);
            throw e;
        }
        return false;
    }
    public void unlink() {
        generalLoop.unlink();
    }

    public TaskFuture submit(NexalithicTask.Builder taskBuilder) {
        return businessPacketDispatcher.submitNexalithicTask(getSession(), taskBuilder, null);
    }
    public TaskFuture submit(NexalithicTask.Builder taskBuilder, TransferListenerGroup.Builder transferVisualizerBuilder) {
        return businessPacketDispatcher.submitNexalithicTask(getSession(), taskBuilder, transferVisualizerBuilder);
    }
    public boolean push(BusinessPacket packet) {
        return businessPacketDispatcher.egress(getSession(), packet);
    }

    public NexalithicEventBus getEventBus() {
        return eventBus;
    }

    public LifecycleManager.State getState() {
        return lifecycleManager.getState();
    }

    public LinkStatusManager.Status getLinkStatus() {
        return linkStatusManager.getStatus();
    }

    private ClientSession getSession() {
        ClientSession session = generalLoop.getSession();
        if (session == null) {
            for (int i = 0; i < 100; i++) {
                if (session != null) {
                    return session;
                } else {
                    if (i < 50) {
                        Thread.onSpinWait();
                    } else {
                        LockSupport.parkNanos(i * 1_000_000L);
                    }
                }
                session = generalLoop.getSession();
            }
        }
        return session;
    }

    public static class Builder {
        private final NexalithicBuilderContext context = new NexalithicBuilderContext();
        private final PayloadRegistry.Builder payloadRegistryBuilder;
        private final HandlerRegistry.Builder<ClientHandlerContext> handlerRegistryBuilder;
        private final ControllerHandlerAssembler.Builder<ClientHandlerContext> controllerHandlerAssemblyBuilder;

        public Builder() {
            handlerRegistryBuilder = HandlerRegistry.builder();
            payloadRegistryBuilder = PayloadRegistry.builder();
            controllerHandlerAssemblyBuilder = ControllerHandlerAssembler.builder(ClientHandlerContext.class);
            payloadRegistryBuilder.register(TextPayload::new);
            payloadRegistryBuilder.register(FilePayload::new);
            payloadRegistryBuilder.register(SerializablePayload::new);
        }

        public <T> Builder apply(NexalithicOption<T> option, T value) {
            context.setOption(option, value);
            return this;
        }

        public Builder securityPolicy(ClientSecurityPolicy securityPolicy) {
            context.setModule(Modules.SecurityPolicy, securityPolicy);
            return this;
        }

        public Builder handlerRegistryTrieNodeChildrenStorageFactory(Function<Integer, TrieNodeChildrenStorage<ClientHandlerContext>> factory) {
            handlerRegistryBuilder.factory(factory);
            return this;
        }
        public Builder registerHandler(NexalithicHandler.Builder<ClientHandlerContext> handlerBuilder) {
            NexalithicHandler<ClientHandlerContext> handler = handlerBuilder.build();
            handlerRegistryBuilder.register(handler.getMetadata().pathMatcher(), handler);
            return this;
        }

        /**
         * 配置客户端注解式 Controller Handler 装配器。
         *
         * <p>这是客户端注册 {@code @NexalithicHandlerController} Controller 的入口。
         * 回调中的 {@code builder} 用于注册 Controller、参数转换器、返回值转换器和拦截器组件；
         * {@code helper} 用于创建与 {@link ClientHandlerContext} 匹配的默认组件。</p>
         *
         * <pre>{@code
         * NexalithicClient.builder()
         *         .controllerHandlerAssemblerConfigurer((builder, helper) -> {
         *             helper.defaultHandlerMethodConverterSelector(builder)
         *                     .controller(new ClientEventController());
         *         });
         * }</pre>
         *
         * @param configurer Controller Handler 装配器配置器
         * @return 当前客户端构建器
         */
        public Builder controllerHandlerAssemblerConfigurer(ControllerHandlerAssemblerConfigurer<ClientHandlerContext> configurer) {
            configurer.configure(controllerHandlerAssemblyBuilder, new ControllerHandlerAssemblerHelper<>(ClientHandlerContext.class));
            return this;
        }

        public Builder payloadRegistryPayloadConstructorStorageFactory(Supplier<PayloadConstructorStorage> factory) {
            payloadRegistryBuilder.withStorage(factory.get());
            return this;
        }
        public Builder registerPayload(Supplier<? extends AbstractPayload<?>> constructor) {
            payloadRegistryBuilder.register(constructor);
            return this;
        }

        public NexalithicClient build() throws Throwable {
            return build(false);
        }
        public NexalithicClient build(boolean showOptions) throws Throwable {
            if (showOptions) {
                logger.trace("NexalithicClient-Options\n{}", OptionsDefinition.toString("com.thezeroer.nexalithic", context));
            }

            controllerHandlerAssemblyBuilder.build().assembleInto(handlerRegistryBuilder);
            context.setModule(Modules.EventBus, new NexalithicEventBus());
            context.setModule(BusinessPacketDispatcher.Modules.TaskTracer, new TaskTracer(context));
            context.setModule(BusinessPacketDispatcher.Modules.HandlerRegistry, handlerRegistryBuilder.build());
            context.setModule(BusinessPacketDispatcher.Modules.TransferTracer, new TransferTracer(context));
            context.setModule(BusinessPacketsAssembler.Modules.PayloadRegistry, payloadRegistryBuilder.build());
            context.setModule(Modules.BusinessPacketDispatcher, new ClientBusinessPacketDispatcher(context));
            context.setModule(Modules.LinkStatusManager, new LinkStatusManager(context));
            context.setModule(LifecycleManager.Modules.GeneralLoop, new GeneralLoop(context));
            context.setModule(Modules.LifecycleManager, new LifecycleManager(context));

            return new NexalithicClient(context);
        }
    }

    public static class Banner {
        public static final String BANNER =
                """
                          \s
                          _   _                _ _ _   _     _     \s
                          | \\ | | _____  ____ _| (_) |_| |__ (_) ___\s
                          |  \\| |/ _ \\ \\/ / _` | | | __| '_ \\| |/ __|\s
                          | |\\  |  __/>  < (_| | | | |_| | | | | (__\s
                          |_| \\_|\\___/_/\\_\\__,_|_|_|\\__|_| |_|_|\\___|\s
                
                         :: Nexalithic Client ::              (v0.2.0)\s
                """;
    }
}
