package com.thezeroer.nexalithic.server.lifecycle;

import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.session.SessionId;
import com.thezeroer.nexalithic.server.lifecycle.accept.AcceptorLoop;
import com.thezeroer.nexalithic.server.lifecycle.handshake.HandshakeLoop;
import com.thezeroer.nexalithic.server.lifecycle.service.ServiceUnit;
import com.thezeroer.nexalithic.server.lifecycle.service.WorkerLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 生命周期管理器
 *
 * @author tbrtz647@outlook.com
 * @version 1.0.0
 * @since 2026/02/19
 */
public class LifecycleManager {
    /**
     * Nexalithic服务器的生命周期状态枚举。
     * <p>定义了服务器从创建到终止的完整状态转换过程，用于控制服务器的生命周期管理。</p>
     * <p><b>状态转换流程：</b><br>
     * {@link #NEW} → {@link #STARTING} → {@link #RUNNING} → ({@link #STOPPING} 或 {@link #SHUTTING_DOWN}) → {@link #TERMINATED}<br>
     * 任何状态都可能直接转换为 {@link #ERROR}（发生异常时）</p>
     */
    public enum State {
        /**
         * 服务器的初始状态。
         * <p>服务器刚创建但尚未调用{@link #start()}方法时的状态。</p>
         */
        NEW,
        /**
         * 服务器正在启动中的状态。
         * <p>调用{@link #start()}方法后，服务器开始启动各个组件时的状态。</p>
         */
        STARTING,
        /**
         * 服务器正常运行的状态。
         * <p>所有核心组件都已成功启动，服务器能够正常处理请求时的状态。</p>
         */
        RUNNING,
        /**
         * 服务器正在停止中的状态。
         * <p>调用{@link #stop()}方法后，服务器开始停止各个组件时的状态。</p>
         */
        STOPPING,
        /**
         * 服务器正在优雅关闭中的状态。
         * <p>调用{@link #shutdown()}方法后，服务器开始优雅关闭各个组件时的状态。</p>
         */
        SHUTTING_DOWN,
        /**
         * 服务器已终止的状态。
         * <p>服务器成功调用{@link #stop()}或{@link #shutdown()}方法后，所有组件都已关闭时的状态。</p>
         */
        TERMINATED,
        /**
         * 服务器发生错误的状态。
         * <p>服务器在启动、运行或关闭过程中发生异常时的状态。</p>
         */
        ERROR,
    }
    private static final Logger logger = LoggerFactory.getLogger(LifecycleManager.class);
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private final AcceptorLoop acceptorLoop;
    private final LoadBalancer<Void, HandshakeLoop> handshakeLoopLoadBalancer;
    private final LoadBalancer<SessionId, ServiceUnit> serviceUnitLoadBalancer;

    public LifecycleManager(AcceptorLoop acceptorLoop, LoadBalancer<Void, HandshakeLoop> handshakeLoopLoadBalancer, LoadBalancer<SessionId, ServiceUnit> serviceUnitLoadBalancer) {
        this.acceptorLoop = acceptorLoop;
        this.handshakeLoopLoadBalancer = handshakeLoopLoadBalancer;
        this.serviceUnitLoadBalancer = serviceUnitLoadBalancer;
    }

    public void start() throws Exception {
        if (!state.compareAndSet(State.NEW, State.STARTING)) {
            throw new IllegalStateException("Cannot start NexalithicServer while in state " + state.get());
        }
        logger.info("NexalithicServer starting");
        try {
            for (ServiceUnit serviceUnit : serviceUnitLoadBalancer.all()) {
                serviceUnit.getStewardLoop().start();
                for (WorkerLoop workerLoop : serviceUnit.getWorkerLoops()) {
                    workerLoop.start();
                }
            }
            for (HandshakeLoop handshakeLoop : handshakeLoopLoadBalancer.all()) {
                handshakeLoop.start();
            }
            acceptorLoop.start();
            state.set(State.RUNNING);
            logger.info("NexalithicServer start succeed");
        } catch (Exception e) {
            logger.error("NexalithicServer start failed", e);
            state.set(State.ERROR);
            throw e;
        }
    }
    public void stop() throws Exception {
        if (!state.compareAndSet(State.RUNNING, State.STOPPING)) {
            throw new IllegalStateException("Cannot stop NexalithicServer while in state " + state.get());
        }
        logger.info("NexalithicServer stopping");
        try {
            acceptorLoop.stop();
            for (HandshakeLoop handshakeLoop : handshakeLoopLoadBalancer.all()) {
                handshakeLoop.stop();
            }
            for (ServiceUnit serviceUnit : serviceUnitLoadBalancer.all()) {
                serviceUnit.getStewardLoop().stop();
                for (WorkerLoop workerLoop : serviceUnit.getWorkerLoops()) {
                    workerLoop.stop();
                }
            }
            state.set(State.TERMINATED);
            logger.info("NexalithicServer stop succeed");
        } catch (Exception e) {
            logger.error("NexalithicServer stop failed", e);
            state.set(State.ERROR);
            throw e;
        }
    }
    public void shutdown() throws Exception {
        if (!state.compareAndSet(State.RUNNING, State.SHUTTING_DOWN)) {
            throw new IllegalStateException("Cannot shutdown NexalithicServer while in state " + state.get());
        }
        logger.info("NexalithicServer shutting down");
        try {
            acceptorLoop.shutdown();
            for (HandshakeLoop handshakeLoop : handshakeLoopLoadBalancer.all()) {
                handshakeLoop.shutdown();
            }
            for (ServiceUnit serviceUnit : serviceUnitLoadBalancer.all()) {
                serviceUnit.getStewardLoop().shutdown();
                for (WorkerLoop workerLoop : serviceUnit.getWorkerLoops()) {
                    workerLoop.shutdown();
                }
            }
            state.set(State.TERMINATED);
            logger.info("NexalithicServer shutdown succeed");
        } catch (Exception e) {
            logger.error("NexalithicServer shutdown failed", e);
            state.set(State.ERROR);
            throw e;
        }
    }
    public State getState() {
        return state.get();
    }

    public AcceptorLoop getAcceptorLoop() {
        return acceptorLoop;
    }
}
