package com.thezeroer.nexalithic.core.pool;

import java.util.Queue;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 通用包装器池 (General Wrapper Pool)
 *
 * <p>该类是资源池化设计的核心实现，采用<b>双工厂模式</b>将底层资源的创建逻辑与包装器的组装逻辑解耦。
 * 配合 {@link RecyclableWrapper} 使用，可实现高性能、低 GC 开销的对象复用机制。</p>
 *
 * <p>设计模式说明：</p>
 * <ul>
 * <li><b>闭环工厂模式：</b> 通过 {@code BiFunction} 在创建包装器时注入池引用 {@code this}，实现自归还。</li>
 * <li><b>策略模式：</b> 允许外部通过 {@code Supplier} 和 {@code BiFunction} 自定义资源的初始化行为。</li>
 * </ul>
 *
 * @param <T> 底层物理资源类型（如 {@code TLoopBuffer}）
 * @param <W> 具备回收能力的包装器类型
 * @author tbrtz647@outlook.com
 * @since 2026/02/11
 */
public class GeneralWrapperPool<T, W extends RecyclableWrapper<T, W>> implements WrapperPool<W> {

    /** 内部存储容器。建议在并发场景下使用 JCTools 的 MpscQueue 或 ConcurrentLinkedQueue */
    protected final Queue<W> pool;

    /** 原始资源工厂，负责生产 {@code T} 实例（如申请堆外内存） */
    protected final Supplier<T> targetFactory;

    /**包装器组装工厂。
     * <p>输入参数 1: 底层资源 {@code T}</p>
     * <p>输入参数 2: 当前池引用 {@code WrapperPool<W>}</p>
     * <p>输出：完成闭环绑定的包装器实例 {@code W}</p>
     */
    protected final BiFunction<T, WrapperPool<W>, W> wrapperFactory;

    /**
     * 构造一个通用包装器池。
     *
     * @param pool           存储容器实现（决定了池的并发特性与容量限制）
     * @param targetFactory  原始资源工厂
     * @param wrapperFactory 包装器组装工厂
     */
    public GeneralWrapperPool(Queue<W> pool, Supplier<T> targetFactory, BiFunction<T, WrapperPool<W>, W> wrapperFactory) {
        this.pool = pool;
        this.targetFactory = targetFactory;
        this.wrapperFactory = wrapperFactory;
    }

    /**
     * 获取一个包装器实例。
     * <p>逻辑流程：</p>
     * <ol>
     * <li>尝试从队列中弹出（poll）现有包装器。</li>
     * <li>若队列为空，则触发 {@code targetFactory} 生产原始资源，
     * 并利用 {@code wrapperFactory} 完成“资源+池引用”的组装。</li>
     * </ol>
     *
     * @return 绑定的包装器实例
     */
    @Override
    public W acquire() {
        W w = pool.poll();
        if (w == null) {
            // 核心闭环：将 this 传递给 wrapperFactory，使 Wrapper 具备回池路径
            w = wrapperFactory.apply(targetFactory.get(), this);
        }
        return w;
    }

    /**
     * 释放包装器并回池。
     *
     * <p><b>注意：</b>
     * 1. 此方法通常由 {@link RecyclableWrapper#recycle()} 内部调用。
     * 2. 若 {@code pool} 为有界队列且已满，该操作可能会失败或触发丢弃策略（取决于 Queue 实现）。
     * </p>
     *
     * @param w 需要归还的包装器实例
     * @return 是否成功归还到 Queue
     */
    @Override
    public boolean release(W w) {
        return pool.offer(w);
    }
}