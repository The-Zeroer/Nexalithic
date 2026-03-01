package com.thezeroer.nexalithic.server;

import com.thezeroer.nexalithic.core.loadbalance.ConsistentHashBalancer;
import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.loadbalance.P2CBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.option.NexalithicOption;
import com.thezeroer.nexalithic.core.option.OptionMap;
import com.thezeroer.nexalithic.server.lifecycle.LifecycleManager;
import com.thezeroer.nexalithic.server.lifecycle.accept.AcceptorLoop;
import com.thezeroer.nexalithic.server.lifecycle.accept.FiltrationStrategy;
import com.thezeroer.nexalithic.server.lifecycle.handshake.HandshakeLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.security.ServerSecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Nexalithic 服务器
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/02
 * @version 1.0.0
 */
@SuppressWarnings("UnusedReturnValue")
public class NexalithicServer {
    private static final Logger logger = LoggerFactory.getLogger(NexalithicServer.class);
    private final LifecycleManager lifecycleManager;

    private NexalithicServer(LifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    public static Builder builder() {
        logger.info(Banner.BANNER);
        return new Builder();
    }

    /**
     * 启动Nexalithic服务器的核心方法。
     * <p><b>核心流程：</b>
     * <ol>
     * <li>线程安全检查：确保同一时刻只有一个线程调用此方法。</li>
     * <li>状态校验：仅当服务器处于{@link LifecycleManager.STATE#NEW}状态时允许启动，否则抛出异常。</li>
     * <li>状态转换：将服务器状态更新为{@link LifecycleManager.STATE#STARTING}。</li>
     * <li>组件启动：按特定顺序启动各个核心组件</li>
     * <li>启动成功：将服务器状态更新为{@link LifecycleManager.STATE#RUNNING}。</li>
     * </ol>
     * </p>
     *
     * <p><b>异常处理：</b><br>
     * 若启动过程中任一组件抛出异常，将执行以下操作：
     * <ul>
     * <li>记录详细错误日志</li>
     * <li>将服务器状态更新为{@link LifecycleManager.STATE#ERROR}</li>
     * <li>将原始异常重新抛出给调用者</li>
     * </ul>
     * </p>
     *
     * @throws IllegalStateException 当服务器不处于{@link LifecycleManager.STATE#NEW}状态时抛出
     * @throws Exception 当任一核心组件启动失败时抛出
     */
    public void start() throws Exception {
        lifecycleManager.start();
    }
    /**
     * 停止Nexalithic服务器的核心方法。
     * <p><b>核心流程：</b>
     * <ol>
     * <li>线程安全检查：确保同一时刻只有一个线程调用此方法。</li>
     * <li>状态校验：仅当服务器处于{@link LifecycleManager.STATE#RUNNING}状态时允许停止，否则抛出异常。</li>
     * <li>状态转换：将服务器状态更新为{@link LifecycleManager.STATE#STOPPING}。</li>
     * <li>组件停止：按特定顺序停止各个核心组件（与启动顺序相反）</li>
     * <li>停止成功：将服务器状态更新为{@link LifecycleManager.STATE#TERMINATED}。</li>
     * </ol>
     * </p>
     *
     * <p><b>异常处理：</b><br>
     * 若停止过程中任一组件抛出异常，将执行以下操作：
     * <ul>
     * <li>记录详细错误日志</li>
     * <li>将服务器状态更新为{@link LifecycleManager.STATE#ERROR}</li>
     * <li>将原始异常重新抛出给调用者</li>
     * </ul>
     * </p>
     *
     * @throws IllegalStateException 当服务器不处于{@link LifecycleManager.STATE#RUNNING}状态时抛出
     * @throws Exception 当任一核心组件停止失败时抛出
     */
    public void stop() throws Exception {
        lifecycleManager.stop();
    }
    /**
     * 优雅关闭Nexalithic服务器的方法，与{@link #stop()}方法相比提供更安全的资源释放。
     * <p><b>核心流程：</b>
     * <ol>
     * <li>线程安全检查：确保同一时刻只有一个线程调用此方法。</li>
     * <li>状态校验：仅当服务器处于{@link LifecycleManager.STATE#RUNNING}状态时允许关闭，否则抛出异常。</li>
     * <li>状态转换：将服务器状态更新为{@link LifecycleManager.STATE#SHUTTING_DOWN}。</li>
     * <li>组件优雅关闭：按特定顺序关闭各个核心组件（与启动顺序相反）</li>
     * <li>关闭成功：将服务器状态更新为{@link LifecycleManager.STATE#TERMINATED}。</li>
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
     * <li>将服务器状态更新为{@link LifecycleManager.STATE#ERROR}</li>
     * <li>将原始异常重新抛出给调用者</li>
     * </ul>
     * </p>
     *
     * @throws IllegalStateException 当服务器不处于{@link LifecycleManager.STATE#RUNNING}状态时抛出
     * @throws Exception 当任一核心组件关闭失败时抛出
     */
    public void shutdown() throws Exception {
        lifecycleManager.shutdown();
    }
    /**
     * 获取Nexalithic服务器当前的运行状态。
     * <p>此方法提供了线程安全的状态查询机制，允许外部调用者监控服务器的生命周期状态。</p>
     *
     * @return 服务器当前的运行状态，可能为以下值之一：
     *         {@link LifecycleManager.STATE#NEW}、{@link LifecycleManager.STATE#STARTING}、{@link LifecycleManager.STATE#RUNNING}、
     *         {@link LifecycleManager.STATE#STOPPING}、{@link LifecycleManager.STATE#SHUTTING_DOWN}、{@link LifecycleManager.STATE#TERMINATED}、
     *         {@link LifecycleManager.STATE#ERROR}
     */
    public LifecycleManager.STATE getState() {
        return lifecycleManager.getState();
    }

    /**
     * 使用默认的旁路过滤策略绑定并监听指定地址。
     * <p>此方法等同于调用 {@link #open(AbstractPacket.TYPE, InetSocketAddress, FiltrationStrategy)}
     * 并传入 {@link FiltrationStrategy#BYPASS}。适用于无需在接入层进行任何安全性或业务校验的场景。</p>
     *
     * @param type    绑定的协议包类型，决定了该端口接收数据后的解包逻辑。
     * @param local 监听的套接字地址（包含主机名和端口）。
     * @return 实际绑定的本地端口号。
     * @throws IOException 如果打开或绑定 ServerSocketChannel 失败。
     */
    public int open(AbstractPacket.TYPE type, InetSocketAddress local) throws IOException {
        return open(type, local, FiltrationStrategy.BYPASS);
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
     * @param type     协议包类型枚举。不能为空。
     * @param local  监听地址。如果端口号为 0，系统将选择一个临时端口。
     * @param strategy 自定义的过滤策略。不能为空，如需跳过过滤请显式传入 {@link FiltrationStrategy#BYPASS}。
     * @return 实际监听的本地端口号。
     * @throws NullPointerException 如果 type 或 strategy 为 null。
     * @throws IOException         如果资源初始化失败或无法绑定到指定地址。
     */
    public int open(AbstractPacket.TYPE type, InetSocketAddress local, FiltrationStrategy strategy) throws IOException {
        try {
            if (type == null) {
                throw new IllegalStateException("type is null");
            }
            if (strategy == null) {
                throw new IllegalStateException("filtrationStrategy is null");
            }
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            int bindPort = serverSocketChannel.bind(local, 2048).socket().getLocalPort();
            logger.info("Successfully bound server to [{}:{}] with type [{}] and strategy [{}]",
                    local.getHostString(), bindPort, type, strategy.getClass().getSimpleName());
            lifecycleManager.getAcceptorLoop().dispatch(new AcceptorLoop.DispatchWrapper(type, serverSocketChannel, strategy));
            return bindPort;
        } catch (IOException e) {
            logger.error("Failed to bind to local [{}]. type [{}], Strategy [{}]",
                    local, type, strategy.getClass().getSimpleName(), e);
            throw e;
        }
    }

    public static class Builder {
        private final Map<NexalithicOption<?>, Object> options = new HashMap<>();

        private ServerSecurityPolicy securityPolicy;
        private ExecutorService handshakeLoopThreadPool;

        public Builder() {
            handshakeLoopThreadPool = new ThreadPoolExecutor(HandshakeLoop.Count.defaultValue(), HandshakeLoop.Count.defaultValue() * 2,
                    60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());
        }

        public <T> Builder apply(NexalithicOption<T> option, T value) {
            options.put(option, value);
            return this;
        }

        public Builder securityPolicy(ServerSecurityPolicy securityPolicy) {
            this.securityPolicy = securityPolicy;
            return this;
        }
        public Builder handshakeLoopThreadPool(ExecutorService threadPool) {
            this.handshakeLoopThreadPool = threadPool;
            return this;
        }

        public NexalithicServer build() throws Exception {
            OptionMap options = OptionMap.of(this.options);

            ServiceUnit[] serviceUnits = new ServiceUnit[options.value(ServiceUnit.Count)];
            for (int i = 0; i < serviceUnits.length; i++) {
                serviceUnits[i] = new ServiceUnit(options).addIdToLoopName(String.valueOf(i));
            }
            LoadBalancer<String, ServiceUnit> serviceUnitLoadBalancer = new ConsistentHashBalancer<>(serviceUnits, 160);

            HandshakeLoop[] handshakeLoops = new HandshakeLoop[options.value(HandshakeLoop.Count)];
            for (int i = 0; i < handshakeLoops.length; i++) {
                handshakeLoops[i] = (HandshakeLoop) new HandshakeLoop(options, serviceUnitLoadBalancer, securityPolicy, handshakeLoopThreadPool).addIdToName(String.valueOf(i));
            }
            LoadBalancer<Void, HandshakeLoop> handshakeLoopBalancer = new P2CBalancer<>(handshakeLoops);

            AcceptorLoop acceptorLoop = (AcceptorLoop) new AcceptorLoop(options, handshakeLoopBalancer).addIdToName("0");

            return new NexalithicServer(new LifecycleManager(acceptorLoop, handshakeLoopBalancer, serviceUnitLoadBalancer));
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
                
                         :: Nexalithic Server ::              (v0.1.0)\s
                """;
    }
}