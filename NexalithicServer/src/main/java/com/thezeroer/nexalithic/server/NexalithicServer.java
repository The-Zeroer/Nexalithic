package com.thezeroer.nexalithic.server;

import com.thezeroer.nexalithic.core.builder.NexalithicBuilderContext;
import com.thezeroer.nexalithic.core.builder.module.ModulesDefinition;
import com.thezeroer.nexalithic.core.builder.module.NexalithicModule;
import com.thezeroer.nexalithic.core.builder.option.OptionsDefinition;
import com.thezeroer.nexalithic.core.event.NexalithicEventBus;
import com.thezeroer.nexalithic.core.io.codec.assembler.BusinessPacketsAssembler;
import com.thezeroer.nexalithic.core.infra.loadbalance.P2CBalancer;
import com.thezeroer.nexalithic.core.messaging.BusinessPacketDispatcher;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.ControllerHandlerAssembler;
import com.thezeroer.nexalithic.core.messaging.handler.assembly.ControllerHandlerAssemblyConfigurer;
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
import com.thezeroer.nexalithic.core.model.packet.business.payload.AbstractPayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.FilePayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.SerializablePayload;
import com.thezeroer.nexalithic.core.model.packet.business.payload.TextPayload;
import com.thezeroer.nexalithic.core.session.SessionAttachment;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.model.packet.business.BusinessPacket;
import com.thezeroer.nexalithic.core.builder.option.NexalithicOption;
import com.thezeroer.nexalithic.server.lifecycle.LifecycleManager;
import com.thezeroer.nexalithic.server.lifecycle.accept.AcceptorLoop;
import com.thezeroer.nexalithic.server.lifecycle.accept.FiltrationStrategy;
import com.thezeroer.nexalithic.server.lifecycle.handshake.HandshakeLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.session.ServerSession;
import com.thezeroer.nexalithic.server.manager.NetworkRouter;
import com.thezeroer.nexalithic.server.manager.SessionsManager;
import com.thezeroer.nexalithic.server.messaging.ServerBusinessPacketDispatcher;
import com.thezeroer.nexalithic.server.messaging.ServerHandlerContext;
import com.thezeroer.nexalithic.server.security.ServerSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Nexalithic 服务器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
@SuppressWarnings("UnusedReturnValue")
public class NexalithicServer {
    public static final class Modules implements ModulesDefinition {
        public static final NexalithicModule<LifecycleManager> LifecycleManager = NexalithicModule.create("NexalithicServer_LifecycleManager", LifecycleManager.class);
        public static final NexalithicModule<SessionsManager> SessionsManager = NexalithicModule.create("NexalithicServer_SessionsManager", SessionsManager.class);
        public static final NexalithicModule<NetworkRouter> NetworkRouter = NexalithicModule.create("NexalithicServer_NetworkRouter", NetworkRouter.class);
        public static final NexalithicModule<ServerBusinessPacketDispatcher> BusinessPacketDispatcher = NexalithicModule.create("NexalithicServer_BusinessPacketDispatcher", ServerBusinessPacketDispatcher.class);
        public static final NexalithicModule<ServerSecurityPolicy> SecurityPolicy = NexalithicModule.create("NexalithicServer_SecurityPolicy", ServerSecurityPolicy.class);
        public static final NexalithicModule<NexalithicEventBus> EventBus = NexalithicModule.create("NexalithicServer_EventBus", NexalithicEventBus.class);
    }
    private static final Logger logger = LoggerFactory.getLogger(NexalithicServer.class);
    private final LifecycleManager lifecycleManager;
    private final SessionsManager sessionsManager;
    private final NetworkRouter networkRouter;
    private final ServerBusinessPacketDispatcher businessPacketDispatcher;
    private final NexalithicEventBus eventBus;

    private NexalithicServer(NexalithicBuilderContext context) {
        this.lifecycleManager = context.getModule(Modules.LifecycleManager);
        this.sessionsManager = context.getModule(Modules.SessionsManager);
        this.networkRouter = context.getModule(Modules.NetworkRouter);
        this.businessPacketDispatcher = context.getModule(Modules.BusinessPacketDispatcher);
        this.eventBus = context.getModule(Modules.EventBus);
        System.gc();
    }

    public static Builder builder() {
        logger.info(Banner.BANNER);
        return new Builder();
    }
    public static NexalithicServer unsafeCreate(NexalithicBuilderContext context) {
        return new NexalithicServer(context);
    }

    /**
     * 启动Nexalithic服务器的核心方法。
     * <p><b>核心流程：</b>
     * <ol>
     * <li>线程安全检查：确保同一时刻只有一个线程调用此方法。</li>
     * <li>状态校验：仅当服务器处于{@link LifecycleManager.State#NEW}状态时允许启动，否则抛出异常。</li>
     * <li>状态转换：将服务器状态更新为{@link LifecycleManager.State#STARTING}。</li>
     * <li>组件启动：按特定顺序启动各个核心组件</li>
     * <li>启动成功：将服务器状态更新为{@link LifecycleManager.State#RUNNING}。</li>
     * </ol>
     * </p>
     *
     * <p><b>异常处理：</b><br>
     * 若启动过程中任一组件抛出异常，将执行以下操作：
     * <ul>
     * <li>记录详细错误日志</li>
     * <li>将服务器状态更新为{@link LifecycleManager.State#ERROR}</li>
     * <li>将原始异常重新抛出给调用者</li>
     * </ul>
     * </p>
     *
     */
    public void start() {
        lifecycleManager.start();
    }
    /**
     * 停止Nexalithic服务器的核心方法。
     * <p><b>核心流程：</b>
     * <ol>
     * <li>线程安全检查：确保同一时刻只有一个线程调用此方法。</li>
     * <li>状态校验：仅当服务器处于{@link LifecycleManager.State#RUNNING}状态时允许停止，否则抛出异常。</li>
     * <li>状态转换：将服务器状态更新为{@link LifecycleManager.State#STOPPING}。</li>
     * <li>组件停止：按特定顺序停止各个核心组件（与启动顺序相反）</li>
     * <li>停止成功：将服务器状态更新为{@link LifecycleManager.State#TERMINATED}。</li>
     * </ol>
     * </p>
     *
     * <p><b>异常处理：</b><br>
     * 若停止过程中任一组件抛出异常，将执行以下操作：
     * <ul>
     * <li>记录详细错误日志</li>
     * <li>将服务器状态更新为{@link LifecycleManager.State#ERROR}</li>
     * <li>将原始异常重新抛出给调用者</li>
     * </ul>
     * </p>
     *
     */
    public void stop() {
        lifecycleManager.stop();
    }
    /**
     * 优雅关闭Nexalithic服务器的方法，与{@link #stop()}方法相比提供更安全的资源释放。
     * <p><b>核心流程：</b>
     * <ol>
     * <li>线程安全检查：确保同一时刻只有一个线程调用此方法。</li>
     * <li>状态校验：仅当服务器处于{@link LifecycleManager.State#RUNNING}状态时允许关闭，否则抛出异常。</li>
     * <li>状态转换：将服务器状态更新为{@link LifecycleManager.State#SHUTTING_DOWN}。</li>
     * <li>组件优雅关闭：按特定顺序关闭各个核心组件（与启动顺序相反）</li>
     * <li>关闭成功：将服务器状态更新为{@link LifecycleManager.State#TERMINATED}。</li>
     * </ol>
     * </p>
     *
     * <p><b>与{@link #stop()}方法的区别：</b><br>
     * shutdown()方法通常会等待正在处理的请求完成后再关闭组件，而stop()方法可能会立即中断正在处理的请求。
     * 适用于需要确保数据完整性和优雅退出的场景。</p>
     *
     * <p><b>异常处理：</b><br>
     * 若关闭过程中任一组件抛出异常，将执行以下操作：
     * <ul>
     * <li>记录详细错误日志</li>
     * <li>将服务器状态更新为{@link LifecycleManager.State#ERROR}</li>
     * <li>将原始异常重新抛出给调用者</li>
     * </ul>
     * </p>
     *
     */
    public void shutdown() {
        lifecycleManager.shutdown();
    }

    /**
     * 使用默认的旁路过滤策略绑定并监听指定地址。
     * <p>此方法等同于调用 {@link #open(AbstractPacket.PacketType, InetSocketAddress, FiltrationStrategy)}
     * 并传入 {@link FiltrationStrategy.Bypass}。适用于无需在接入层进行任何安全性或业务校验的场景。</p>
     *
     * @param packetType    绑定的协议包类型，决定了该端口接收数据后的解包逻辑。
     * @param local 监听的套接字地址（包含主机名和端口）。
     * @return 实际绑定的本地端口号。
     * @throws IOException 如果打开或绑定 ServerSocketChannel 失败。
     */
    public int open(AbstractPacket.PacketType packetType, InetSocketAddress local) throws IOException {
        return open(packetType, local, new FiltrationStrategy.Bypass());
    }
    /**
     * 绑定协议类型与监听地址，并配置特定的接入过滤策略。
     * <p><b>核心流程：</b>
     * <ol>
     * <li>同步打开并绑定 {@link ServerSocketChannel} 到指定地址。</li>
     * <li>获取实际分配的端口（尤其是当传入端口为 0 时，系统将自动分配空闲端口）。</li>
     * <li>将 Channel 及其策略封装并异步分发至 {@code AcceptorLoop}。</li>
     * </ol>
     * </p>
     *
     * <p><b>所有权转移：</b><br>
     * 方法成功返回后，{@code serverSocketChannel} 的生命周期管理权正式移交给内部的 {@code AcceptorLoop}。
     * 除非发生严重异常，否则外部调用者不应尝试关闭该 Channel。</p>
     *
     * @param packetType     协议包类型枚举。不能为空。
     * @param local  监听地址。如果端口号为 0，系统将选择一个临时端口。
     * @param strategy 自定义的过滤策略。不能为空，如需跳过过滤请显式传入 {@link FiltrationStrategy.Bypass}。
     * @return 实际监听的本地端口号。
     * @throws NullPointerException 如果 packetType 或 strategy 为 null。
     * @throws IOException         如果资源初始化失败或无法绑定到指定地址。
     */
    public int open(AbstractPacket.PacketType packetType, InetSocketAddress local, FiltrationStrategy strategy) throws IOException {
        try {
            if (packetType == null) {
                throw new IllegalStateException("packetType is null");
            }
            if (strategy == null) {
                throw new IllegalStateException("filtrationStrategy is null");
            }
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            int bindPort = serverSocketChannel.bind(local, 2048).socket().getLocalPort();
            logger.info("Successfully bound server to [{}:{}] with packetType [{}] and strategy [{}]",
                    local.getHostString(), bindPort, packetType, strategy.getName());
            lifecycleManager.getAcceptorLoop().dispatch(packetType, serverSocketChannel, strategy);
            return bindPort;
        } catch (IOException e) {
            logger.error("Failed to bind to local [{}]. packetType [{}], Strategy [{}]",
                    local, packetType, strategy.getName(), e);
            throw e;
        }
    }

    public boolean kick(String sessionName) {
        ServerSession session = sessionsManager.removeSession(sessionName);
        if (session == null) {
            return false;
        }
        session.close();
        return true;
    }

    public TaskFuture submit(String sessionName, NexalithicTask.Builder taskBuilder) {
        ServerSession session = sessionsManager.getSession(sessionName);
        if (session == null) {
            return null;
        }
        return businessPacketDispatcher.submitNexalithicTask(session, taskBuilder, null);
    }
    public TaskFuture submit(String sessionName, NexalithicTask.Builder taskBuilder, TransferListenerGroup.Builder transferVisualizerBuilder) {
        ServerSession session = sessionsManager.getSession(sessionName);
        if (session == null) {
            return null;
        }
        return businessPacketDispatcher.submitNexalithicTask(session, taskBuilder, transferVisualizerBuilder);
    }

    /**
     * 向指定的用户推送数据包
     *
     * @param sessionName 目标会话名
     * @param packet 业务数据包
     */
    public boolean push(String sessionName, BusinessPacket packet) {
        ServerSession session = sessionsManager.getSession(sessionName);
        if (session == null) {
            return false;
        }
        return businessPacketDispatcher.egress(session, packet);
    }

    /**
     * 向所有已命名（已登录）的用户推送数据包
     *
     * @param packet 业务数据包
     */
    public void pushToAll(BusinessPacket packet) {
        packet.seal();
        sessionsManager.forEachNamedSession(session -> businessPacketDispatcher.egress(session, packet.duplicate()));
    }

    /**
     * 遍历所有会话的业务附件
     * @param action 业务处理逻辑
     */
    public void forEachSession(Consumer<SessionAttachment> action) {
        sessionsManager.forEachNamedSession(session -> action.accept(session.attachment()));
    }

    public Collection<String> getAllSessionsName() {
        return sessionsManager.allSessionName();
    }

    /**
     * 获取Nexalithic服务器当前的运行状态。
     * <p>此方法提供了线程安全的状态查询机制，允许外部调用者监控服务器的生命周期状态。</p>
     *
     * @return 服务器当前的运行状态，可能为以下值之一：
     *         {@link LifecycleManager.State#NEW}、{@link LifecycleManager.State#STARTING}、{@link LifecycleManager.State#RUNNING}、
     *         {@link LifecycleManager.State#STOPPING}、{@link LifecycleManager.State#SHUTTING_DOWN}、{@link LifecycleManager.State#TERMINATED}、
     *         {@link LifecycleManager.State#ERROR}
     */
    public LifecycleManager.State getState() {
        return lifecycleManager.getState();
    }

    /**
     * <p>获取当前服务器的路由管理器。</p>
     * <ul>
     * <li><b>前置性：</b> 开发者必须在调用 {@link #open(AbstractPacket.PacketType, InetSocketAddress, FiltrationStrategy)} 开启端口监听<b>之前</b>，
     * 通过此方法获取路由器并完成所有初始路由规则的添加（{@link NetworkRouter#addRoutes}）。</li>
     * <li><b>冷启动保护：</b> 若在 open 之后才添加路由，可能会导致服务器启动瞬间涌入的Channel
     * 因找不到匹配端口（Return -1）而触发静默丢弃或连接断开。</li>
     * <li><b>动态性：</b> 服务器运行期间仍支持动态增删路由，但基础骨干路由应在 open 前就位。</li>
     * </ul>
     *
     * @return 全局唯一的网络路由器实例 {@link NetworkRouter}
     */
    public NetworkRouter getNetworkRouter() {
        return networkRouter;
    }

    public NexalithicEventBus getEventBus() {
        return eventBus;
    }

    public static final class Builder {
        private final NexalithicBuilderContext context = new NexalithicBuilderContext();
        private final HandlerRegistry.Builder<ServerHandlerContext> handlerRegistryBuilder;
        private final PayloadRegistry.Builder payloadRegistryBuilder;
        private final ControllerHandlerAssembler<ServerHandlerContext> controllerHandlerAssembler;

        public Builder() {
            handlerRegistryBuilder = HandlerRegistry.builder();
            payloadRegistryBuilder = PayloadRegistry.builder();
            payloadRegistryBuilder.register(TextPayload::new);
            payloadRegistryBuilder.register(FilePayload::new);
            payloadRegistryBuilder.register(SerializablePayload::new);
            controllerHandlerAssembler = new ControllerHandlerAssembler<>(ServerHandlerContext.class);
        }

        public <T> Builder apply(NexalithicOption<T> option, T value) {
            context.setOption(option, value);
            return this;
        }

        public Builder addRoute(AbstractPacket.PacketType type, String cidr, int port) throws UnknownHostException {
            NetworkRouter router = context.getModule(Modules.NetworkRouter, NetworkRouter::new);
            router.addRoute(type, cidr, port);
            return this;
        }

        public Builder securityPolicy(ServerSecurityPolicy securityPolicy) {
            context.setModule(Modules.SecurityPolicy, securityPolicy);
            return this;
        }

        public Builder handlerRegistryTrieNodeChildrenStorageFactory(Function<Integer, TrieNodeChildrenStorage<ServerHandlerContext>> factory) {
            handlerRegistryBuilder.factory(factory);
            return this;
        }
        public Builder registerHandler(NexalithicHandler.Builder<ServerHandlerContext> handlerBuilder) {
            NexalithicHandler<ServerHandlerContext> handler = handlerBuilder.build();
            handlerRegistryBuilder.register(handler.getMetadata().pathMatcher(), handler);
            return this;
        }
        public Builder controllerHandlerAssembler(ControllerHandlerAssemblyConfigurer<ServerHandlerContext> configurer) {
            configurer.configure(controllerHandlerAssembler);
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

        public NexalithicServer build() throws Throwable {
            return build(false);
        }
        public NexalithicServer build(boolean showOptions) throws Throwable {
            if (showOptions) {
                logger.trace("NexalithicServer-Options\n{}", OptionsDefinition.toString("com.thezeroer.nexalithic", context));
            }

            controllerHandlerAssembler.assembleInto(handlerRegistryBuilder);
            context.setModule(Modules.EventBus, new NexalithicEventBus());
            context.setModule(Modules.SessionsManager, new SessionsManager(context));
            context.setModule(BusinessPacketsAssembler.Modules.PayloadRegistry, payloadRegistryBuilder.build());
            context.setModule(BusinessPacketDispatcher.Modules.TransferTracer, new TransferTracer(context));
            context.setModule(BusinessPacketDispatcher.Modules.TaskTracer, new TaskTracer(context));
            context.setModule(BusinessPacketDispatcher.Modules.HandlerRegistry, handlerRegistryBuilder.build());
            context.setModule(Modules.BusinessPacketDispatcher, new ServerBusinessPacketDispatcher(context));

            ServiceUnit[] serviceUnits = new ServiceUnit[context.getOption(LifecycleManager.OPTIONS.ServiceUnit_Count)];
            for (int i = 0; i < serviceUnits.length; i++) {
                serviceUnits[i] = new ServiceUnit(context).addIdToLoopName(String.valueOf(i));
            }
            context.setModule(LifecycleManager.Modules.ServiceUnitLoadBalancer, new P2CBalancer<>(serviceUnits));

            HandshakeLoop[] handshakeLoops = new HandshakeLoop[context.getOption(LifecycleManager.OPTIONS.HandshakeLoop_Count)];
            for (int i = 0; i < handshakeLoops.length; i++) {
                handshakeLoops[i] = (HandshakeLoop) new HandshakeLoop(context).addIdToName(String.valueOf(i));
            }
            context.setModule(LifecycleManager.Modules.HandshakeLoopLoadBalancer, new P2CBalancer<>(handshakeLoops));

            context.setModule(LifecycleManager.Modules.AcceptorLoop, (AcceptorLoop) new AcceptorLoop(context).addIdToName("0"));
            context.setModule(Modules.LifecycleManager, new LifecycleManager(context));
            return new NexalithicServer(context);
        }
    }

    public static final class Banner {
        public static final String BANNER =
                """
                          \s
                          _   _                _ _ _   _     _     \s
                          | \\ | | _____  ____ _| (_) |_| |__ (_) ___\s
                          |  \\| |/ _ \\ \\/ / _` | | | __| '_ \\| |/ __|\s
                          | |\\  |  __/>  < (_| | | | |_| | | | | (__\s
                          |_| \\_|\\___/_/\\_\\__,_|_|_|\\__|_| |_|_|\\___|\s
                
                         :: Nexalithic Server ::              (v0.2.0)\s
                """;
    }
}
