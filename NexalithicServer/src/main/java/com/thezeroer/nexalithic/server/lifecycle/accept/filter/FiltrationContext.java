package com.thezeroer.nexalithic.server.lifecycle.accept.filter;

import com.thezeroer.nexalithic.core.loadbalance.LoadBalancer;
import com.thezeroer.nexalithic.core.model.packet.AbstractPacket;
import com.thezeroer.nexalithic.core.recyclable.SelfWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.TargetWrapperPool;
import com.thezeroer.nexalithic.core.recyclable.WrapperPool;
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
public class FiltrationContext extends SelfWrapperPool.SelfRecyclableWrapper<FiltrationContext> implements FiltrationContextView {
    private final LoadBalancer<Void, HandshakeLoop> handshakeLoopBalancer;
    private AbstractPacket.PacketType packetType;
    private SocketChannel socketChannel;
    private PendingChannel cachedPendingChannel;

    public FiltrationContext(LoadBalancer<Void, HandshakeLoop> balancer)  {
        this.handshakeLoopBalancer = balancer;
    }

    public FiltrationContext init(AbstractPacket.PacketType packetType, SocketChannel socketChannel, WrapperPool<PendingChannel> pendingChannelPool) {
        this.packetType = packetType;
        this.socketChannel = socketChannel;
        if (this.cachedPendingChannel == null) {
            this.cachedPendingChannel = pendingChannelPool.acquire();
        }
        return this;
    }

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
    @Override
    public void approve() {
        if (this.socketChannel == null) return;
        try {
            handshakeLoopBalancer.select(null).dispatch(cachedPendingChannel.init(packetType, socketChannel).unwrap());
            cachedPendingChannel = null;
        } catch (Exception e) {
            reject();
        } finally {
            recycle();
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
    @Override
    public void reject() {
        if (this.socketChannel == null) return;
        try {
            socketChannel.close();
        } catch (IOException ignored) {
        } finally {
            recycle();
        }
    }

    @Override
    protected void onRecycle() {
        packetType = null;
        socketChannel = null;
    }

    @Override
    protected void onOverflow() {
        cachedPendingChannel.recycle();
    }
}
