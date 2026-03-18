package com.thezeroer.nexalithic.client;

import com.thezeroer.nexalithic.client.messaging.ClientBusinessPacketDispatcher;
import com.thezeroer.nexalithic.client.messaging.ClientHandlerContext;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerRegistry;
import com.thezeroer.nexalithic.core.messaging.handler.HandlerScanner;
import com.thezeroer.nexalithic.core.messaging.handler.NexalithicHandler;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.BusinessPacket;
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

    private NexalithicClient(GeneralLoop generalLoop) {
        this.generalLoop = generalLoop;
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
    public boolean push(BusinessPacket packet) {
        return generalLoop.pushBusinessPacket(packet);
    }

    public static class Builder {
        private ClientSecurityPolicy securityPolicy;
        private HandlerRegistry<ClientHandlerContext> registry;
        private ExecutorService businessPacketDispatcherThreadPool;

        public Builder() {
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

        public Builder handlerRegistryTrieNodeChildrenStorageFactory(Supplier<HandlerRegistry.TrieNodeChildrenStorage<ClientHandlerContext>> factory) {
            if (registry == null) {
                registry = new HandlerRegistry<>(factory);
            } else {
                throw new IllegalStateException("handler registry has already been set");
            }
            return this;
        }
        public Builder scanControllers(String packageName, BeanFactory factory) throws Throwable {
            if (registry == null) {
                registry = new HandlerRegistry<>(HandlerRegistry.MapTrieNodeChildrenStorage::new);
            }
            HandlerScanner.scanAndRegister(packageName, factory, registry);
            return this;
        }
        public Builder registerHandler(HandlerRegistry.PathMatcher matcher, NexalithicHandler<ClientHandlerContext> handler) {
            if (registry == null) {
                registry = new HandlerRegistry<>(HandlerRegistry.MapTrieNodeChildrenStorage::new);
            }
            registry.register(matcher, handler);
            return this;
        }

        public Builder businessPacketDispatcherThreadPool(ExecutorService threadPool) {
            this.businessPacketDispatcherThreadPool = threadPool;
            return this;
        }

        public NexalithicClient build() throws Exception {
            verifyOptions();

            if (registry == null) {
                registry = new HandlerRegistry<>(HandlerRegistry.MapTrieNodeChildrenStorage::new);
            }
            ClientBusinessPacketDispatcher dispatcher = new ClientBusinessPacketDispatcher(registry, businessPacketDispatcherThreadPool);
            GeneralLoop generalLoop = new GeneralLoop(securityPolicy, dispatcher);

            return new NexalithicClient(generalLoop);
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
