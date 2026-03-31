package com.thezeroer.nexalithic.client;

import com.thezeroer.nexalithic.client.lifecycle.session.ClientSession;
import com.thezeroer.nexalithic.client.messaging.ClientBusinessPacketDispatcher;
import com.thezeroer.nexalithic.client.messaging.ClientHandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerScanner;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.messaging.handler.TrieNodeChildrenStorage;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadConstructorStorage;
import com.thezeroer.nexalithic.core.messaging.payload.PayloadRegistry;
import com.thezeroer.nexalithic.core.messaging.task.NexalithicTask;
import com.thezeroer.nexalithic.core.messaging.task.TaskFuture;
import com.thezeroer.nexalithic.core.messaging.task.TaskTracer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
import com.thezeroer.nexalithic.core.model.packet.payload.AbstractPayload;
import com.thezeroer.nexalithic.core.model.packet.payload.FilePayload;
import com.thezeroer.nexalithic.core.model.packet.payload.SerializablePayload;
import com.thezeroer.nexalithic.core.model.packet.payload.TextPayload;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.client.lifecycle.GeneralLoop;
import com.thezeroer.nexalithic.client.security.ClientSecurityPolicy;
import com.thezeroer.nexalithic.core.util.BeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
    private static final Logger logger = LoggerFactory.getLogger(NexalithicClient.class);
    private final GeneralLoop generalLoop;
    private final ClientBusinessPacketDispatcher businessPacketDispatcher;

    private NexalithicClient(GeneralLoop generalLoop, ClientBusinessPacketDispatcher businessPacketDispatcher) {
        this.generalLoop = generalLoop;
        this.businessPacketDispatcher = businessPacketDispatcher;
    }

    public static Builder builder() {
        logger.info(Banner.BANNER);
        return new Builder();
    }

    public void start() throws Exception {
        generalLoop.start();
    }
    public void stop() throws Exception {
        generalLoop.stop();
    }
    public void shutdown() throws Exception {
        generalLoop.shutdown();
    }

    public boolean link(InetSocketAddress remote) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        SocketChannel socketChannel = SocketChannel.open(remote);
        logger.info("Linking to [{}]", socketChannel.getRemoteAddress());
        generalLoop.getNetworkRouter().setServerHost(remote.getAddress().getHostAddress());
        return generalLoop.dispatch(AbstractPacket.PacketType.SIGNALING, socketChannel);
    }

    public TaskFuture submit(NexalithicTask.Builder taskBuilder) {
        return businessPacketDispatcher.submitNexalithicTask(getSession(), taskBuilder.build());
    }
    public boolean push(BusinessPacket packet) {
        return businessPacketDispatcher.pushBusinessPacket(getSession(), packet);
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
        private ClientSecurityPolicy securityPolicy;
        private final HandlerRegistry.Builder<ClientHandlerContext> handlerRegistryBuilder;
        private final PayloadRegistry.Builder payloadRegistryBuilder;

        private ExecutorService businessPacketDispatcherThreadPool;

        public Builder() {
            handlerRegistryBuilder = HandlerRegistry.builder();
            payloadRegistryBuilder = PayloadRegistry.builder();
            payloadRegistryBuilder.register(TextPayload::new);
            payloadRegistryBuilder.register(FilePayload::new);
            payloadRegistryBuilder.register(SerializablePayload::new);
            businessPacketDispatcherThreadPool = new ThreadPoolExecutor(4, 8,
                    60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());
        }

        public <T> Builder apply(NexalithicOption<T> option, T value) {
            option.set(value);
            return this;
        }

        public Builder securityPolicy(ClientSecurityPolicy securityPolicy) {
            this.securityPolicy = securityPolicy;
            return this;
        }

        public Builder handlerRegistryTrieNodeChildrenStorageFactory(Supplier<TrieNodeChildrenStorage<ClientHandlerContext>> factory) {
            handlerRegistryBuilder.factory(factory);
            return this;
        }
        public Builder registerHandler(HandlerRegistry.PathMatcher matcher, NexalithicHandler<ClientHandlerContext> handler) {
            handlerRegistryBuilder.register(matcher, handler);
            return this;
        }
        public Builder scanControllers(String packageName, BeanFactory factory) throws Throwable {
            HandlerScanner.scanAndRegister(packageName, factory, handlerRegistryBuilder);
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

        public Builder businessPacketDispatcherThreadPool(ExecutorService threadPool) {
            this.businessPacketDispatcherThreadPool = threadPool;
            return this;
        }

        public NexalithicClient build() throws Exception {
            verifyOptions();
            TaskTracer taskTracer = new TaskTracer();
            HandlerRegistry<ClientHandlerContext> handlerRegistry = handlerRegistryBuilder.build();
            ClientBusinessPacketDispatcher dispatcher = new ClientBusinessPacketDispatcher(taskTracer, handlerRegistry, businessPacketDispatcherThreadPool);
            GeneralLoop generalLoop = new GeneralLoop(securityPolicy, dispatcher, payloadRegistryBuilder.build());

            return new NexalithicClient(generalLoop, dispatcher);
        }

        private void verifyOptions() {

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
