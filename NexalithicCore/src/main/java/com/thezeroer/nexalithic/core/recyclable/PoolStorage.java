package com.thezeroer.nexalithic.core.recyclable;

import java.util.Queue;

/**
 * <h2>池化存储抽象接口 (Pool Storage)</h2>
 *
 * <p>该接口定义了 Nexalithic 回收池底层的物理存储契约。通过引入此抽象层，框架实现了
 * 与具体集合框架（如 JDK Queue, JCTools MPSC, 或自定义 RingBuffer）的解耦。</p>
 *
 * <li><b>组合优于继承：</b> 用户无需让自定义容器硬性继承 {@link Queue}，只需通过适配器实现 {@code offer/poll}。</li>
 *
 * @param <T> 存储的资源类型
 * @author tbrtz647@outlook.com
 * @since 2026/03/11
 * @version 1.0.0
 */
public interface PoolStorage<T> {
    /**
     * 将资源尝试归还至存储。
     * @param t 待回收的资源实例
     * @return {@code true} 如果归还成功；{@code false} 如果存储已满（触发溢出逻辑）
     */
    boolean offer(T t);

    /**
     * 从存储中获取一个可用资源。
     * @return 资源实例，若存储为空则返回 {@code null}
     */
    T poll();

    /**
     * 获取该存储单元的逻辑容量上限。
     * <p>常用于监控指标统计或作为溢出控制的阈值基础。</p>
     * @return 存储允许的最大容量
     */
    int capacity();

    /**
     * 静态工厂方法：将标准的 {@link Queue} 包装为 {@link PoolStorage}。
     *
     * @param queue    底层的队列实现（建议使用无锁队列以获得最佳吞吐）
     * @param capacity 队列的逻辑上限
     * @param <E>      元素类型
     * @return 基于 Record 优化后的存储适配器
     */
    static <E> PoolStorage<E> of(Queue<E> queue, int capacity) {
        return new QueueStorageAdapter<>(queue, capacity);
    }

    /**
     * 队列存储适配器
     *
     * @author tbrtz647@outlook.com
     * @since 2026/03/11
     * @version 1.0.0
     */
    record QueueStorageAdapter<W>(Queue<W> queue, int capacity) implements PoolStorage<W> {
        @Override public boolean offer(W w) { return queue.offer(w); }
        @Override public W poll() { return queue.poll(); }
    }
}
