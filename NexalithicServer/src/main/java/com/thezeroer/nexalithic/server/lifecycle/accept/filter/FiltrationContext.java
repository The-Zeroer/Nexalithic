package com.thezeroer.nexalithic.server.lifecycle.accept.filter;

import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.pool.GeneralRecyclableWrapper;
import com.thezeroer.nexalithic.core.pool.WrapperPool;
import com.thezeroer.nexalithic.server.lifecycle.accept.FiltrationStrategy;
import com.thezeroer.nexalithic.server.lifecycle.handshake.HandshakeLoop;
import com.thezeroer.nexalithic.server.lifecycle.handshake.PendingChannel;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * 连接过滤逻辑的上下文载体。
 *
 * <p><b>核心职责：</b><br>
 * 该类作为 {@code Acceptor} 阶段的数据总线，承载了从原始 {@link SocketChannel}
 * 到 {@link PendingChannel} 转换过程中的所有中间状态。它负责协调连接的生命周期决策（准入或拒绝）。</p>
 *
 * <p><b>池化与生命周期：</b><br>
 * 本类设计为<b>高性能池化对象</b>，存储于 {@code FiltrationContextPool} 中。
 * <ul>
 * <li><b>获取：</b>通过 {@code acquire().init(...)} 从池中借用并初始化。</li>
 * <li><b>流转：</b>在 {@link FiltrationStrategy} 链中传递，用于执行业务过滤。</li>
 * <li><b>终点：</b>必须由 {@link #approve()} 或 {@link #reject()} 触发回收。</li>
 * </ul>
 * 警告：严禁在调用 {@code approve()} 或 {@code reject()} 后继续持有或操作此对象引用。</p>
 *
 * @author tbrtz647@outlook.com
 * @since 2026/02/07
 * @version 1.0.0
 * @see FiltrationStrategy
 */
public class FiltrationContext {
    private AbstractPacket.TYPE type;
    private SocketChannel socketChannel;
    private LoadBalancer<Void, HandshakeLoop> handshakeLoopBalancer;
    private PendingChannel.Recyclable cachedPendingChannel;
    private Recyclable recyclable;

    /**
     * 准入操作：将当前连接判定为合法，并移交给后续的握手处理器。
     * * <p><b>执行流程：</b>
     * <ol>
     * <li>从 {@code pendingChannelPool} 获取载体对象并绑定当前通道。</li>
     * <li>将载体投递至 {@code handshakeSelector} 的待处理队列。</li>
     * <li><b>关键：</b>一旦投递成功，SocketChannel 的管理权即移交给 Handshake 阶段。</li>
     * <li>调用 {@code close()} 归还当前 Context 资源。</li>
     * </ol>
     * </p>
     */
    public void approve() {
        if (this.socketChannel == null) return;
        try {
            handshakeLoopBalancer.select(null).dispatch(cachedPendingChannel.initTarget(type, socketChannel).unwrap());
            cachedPendingChannel = null;
        } catch (Exception e) {
            reject();
        } finally {
            recyclable.recycle();
        }
    }

    /**
     * 拒绝操作：判定连接非法或由于系统压力主动丢弃连接。
     * * <p>此方法负责执行物理清理工作：
     * <ol>
     * <li>强制关闭 {@link SocketChannel}，断开 TCP 连接。</li>
     * <li>触发 {@code close()} 逻辑，将当前 {@code FiltrationContext} 实例归还至 JCTools 循环池。</li>
     * </ol>
     * </p>
     * * <p><b>幂等性：</b>多次调用此方法是安全的，仅第一次调用会执行实际的关闭逻辑。</p>
     */
    public void reject() {
        if (this.socketChannel == null) return;
        try {
            socketChannel.close();
        } catch (IOException ignored) {
        } finally {
            recyclable.recycle();
        }
    }

    public static class Recyclable extends GeneralRecyclableWrapper<FiltrationContext, Recyclable> {

        public Recyclable(FiltrationContext target, WrapperPool<? super Recyclable> pool, LoadBalancer<Void, HandshakeLoop> balancer) {
            super(target, pool);
            target.recyclable = this;
            target.handshakeLoopBalancer = balancer;
        }

        public Recyclable initTarget(AbstractPacket.TYPE type, SocketChannel socketChannel,
                                     WrapperPool<PendingChannel.Recyclable> pendingChannelPool) {
            target.type = type;
            target.socketChannel = socketChannel;
            if (target.cachedPendingChannel == null) {
                target.cachedPendingChannel = pendingChannelPool.acquire();
            }
            return this;
        }

        @Override
        protected void onRecycle(FiltrationContext target) {
            target.type = null;
            target.socketChannel = null;
        }

        @Override
        protected void onOverflow(FiltrationContext target) {
            target.recyclable = null;
            target.handshakeLoopBalancer = null;
            target.cachedPendingChannel.recycle();
        }
    }
}
