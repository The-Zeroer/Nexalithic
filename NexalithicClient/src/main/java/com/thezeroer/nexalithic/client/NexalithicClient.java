package com.thezeroer.nexalithic.client;

import com.thezeroer.nexalithic.client.event.LinkStatusListener;
import com.thezeroer.nexalithic.client.event.Registration;
import com.thezeroer.nexalithic.client.lifecycle.LifecycleManager;
import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.client.manager.LinkStatusManager;
import com.thezeroer.nexalithic.client.messaging.ClientBusinessPacketDispatcher;
import com.thezeroer.nexalithic.client.messaging.ClientHandlerContext;
import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.io.codec.assembler.BusinessPacketsAssembler;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerScanner;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.messaging.handler.TrieNodeChildrenStorage;
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
import com.thezeroer.nexalithic.core.util.BeanFactory;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
    }
    private static final Logger logger = LoggerFactory.getLogger(NexalithicClient.class);
    private final LifecycleManager lifecycleManager;
    private final LinkStatusManager linkStatusManager;
    private final GeneralLoop generalLoop;
    private final ClientBusinessPacketDispatcher businessPacketDispatcher;

    private NexalithicClient(NexalithicBuilderContext context) {
        this.lifecycleManager = context.getModule(Modules.LifecycleManager);
        this.linkStatusManager = context.getModule(Modules.LinkStatusManager);
        this.businessPacketDispatcher = context.getModule(Modules.BusinessPacketDispatcher);
        this.generalLoop = context.getModule(LifecycleManager.Modules.GeneralLoop);
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
        SocketChannel socketChannel = SocketChannel.open(remote);
        logger.info("Linking to [{}]", socketChannel.getRemoteAddress());
        if (linkStatusManager.getCurrentStatus() != LinkStatusListener.Status.UNLINKED) {
            throw new IllegalStateException("Cannot link while in State " + linkStatusManager.getCurrentStatus() + ", must be " + LinkStatusListener.Status.UNLINKED);
        }
        linkStatusManager.trigger(LinkStatusListener.Status.LINKING);
        generalLoop.getNetworkRouter().setServerAddress(remote);
        return generalLoop.dispatch(AbstractPacket.PacketType.SIGNALING, socketChannel, null);
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

    /**
     * @see LinkStatusManager#onStatusTransition(LinkStatusListener.EventKey event, LinkStatusListener listener)
     */
    public Registration onStatusTransition(LinkStatusListener.EventKey event, LinkStatusListener listener) {
        return linkStatusManager.onStatusTransition(event, listener);
    }
    /**
     * @see LinkStatusManager#onStatusTransitionOnce(LinkStatusListener.EventKey event, LinkStatusListener listener)
     */
    public Registration onStatusTransitionOnce(LinkStatusListener.EventKey event, LinkStatusListener listener) {
        return linkStatusManager.onStatusTransitionOnce(event, listener);
    }
    /**
     * @see LinkStatusManager#onStatusTransition(LinkStatusListener.Status from, LinkStatusListener.Status to, LinkStatusListener listener)
     */
    public Registration onStatusTransition(LinkStatusListener.Status from, LinkStatusListener.Status to, LinkStatusListener listener) {
        return linkStatusManager.onStatusTransition(from, to, listener);
    }
    /**
     * @see LinkStatusManager#onEnterStatus(LinkStatusListener.Status to, LinkStatusListener listener)
     */
    public Registration onEnterStatus(LinkStatusListener.Status to, LinkStatusListener listener) {
        return linkStatusManager.onEnterStatus(to, listener);
    }
    /**
     * @see LinkStatusManager#onLeaveStatus(LinkStatusListener.Status from, LinkStatusListener listener)
     */
    public Registration onLeaveStatus(LinkStatusListener.Status from, LinkStatusListener listener) {
        return linkStatusManager.onLeaveStatus(from, listener);
    }
    /**
     * @see LinkStatusManager#getCurrentStatus()
     */
    public LinkStatusListener.Status getLinkStatus() {
        return linkStatusManager.getCurrentStatus();
    }

    public LifecycleManager.State getState() {
        return lifecycleManager.getState();
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
        private final HandlerRegistry.Builder<ClientHandlerContext> handlerRegistryBuilder;
        private final PayloadRegistry.Builder payloadRegistryBuilder;

        public Builder() {
            handlerRegistryBuilder = HandlerRegistry.builder();
            payloadRegistryBuilder = PayloadRegistry.builder();
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
        public Builder registerHandler(HandlerRegistry.PathMatcher matcher, NexalithicHandler<ClientHandlerContext> handler) {
            handlerRegistryBuilder.register(matcher, handler);
            return this;
        }
        public Builder scanControllers(String packageName, BeanFactory factory) throws Throwable {
            HandlerScanner.scanAndRegister(packageName, factory, handlerRegistryBuilder, ClientHandlerContext.class);
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

        public NexalithicClient build() throws IOException {
            if (logger.isTraceEnabled()) {
                logger.trace("NexalithicClient-Options\n{}", OptionsDefinition.toString("com.thezeroer.nexalithic", context));
            }

            context.setModule(BusinessPacketDispatcher.Modules.TaskTracer, new TaskTracer(context));
            context.setModule(BusinessPacketDispatcher.Modules.HandlerRegistry, handlerRegistryBuilder.build());
            context.setModule(BusinessPacketDispatcher.Modules.TransferTracer, new TransferTracer(context));
            context.setModule(BusinessPacketsAssembler.Modules.PayloadRegistry, payloadRegistryBuilder.build());
            context.setModule(Modules.BusinessPacketDispatcher, new ClientBusinessPacketDispatcher(context));
            context.setModule(Modules.LinkStatusManager, new LinkStatusManager());
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
                
                         :: Nexalithic Client ::              (v0.1.0)\s
                """;
    }
}
