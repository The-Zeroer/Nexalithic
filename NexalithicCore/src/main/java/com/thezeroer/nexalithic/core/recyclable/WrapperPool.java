package com.thezeroer.nexalithic.core.recyclable;

/**
 * 包装器池 (Wrapper Pool)
 * <p>负责管理 {@link RecyclableWrapper} 实例的生命周期。通过复用包装器对象，
 * 消除高并发场景下频繁创建临时包装对象带来的 GC 压力。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 * <li><b>高吞吐：</b> 建议实现类使用无锁队列（如 MpscQueue）以减少竞争。</li>
 * <li><b>内存安全：</b> 配合包装器状态位，防止资源归还后的数据踩踏。</li>
 * </ul>
 *
 * @param <W> 池所管理的具体包装器类型，必须是自引用泛型。
 * @author tbrtz647@outlook.com
 * @since 2026/02/11
 */
public interface WrapperPool<W extends RecyclableWrapper<?>> {
    /**
     * 从池中获取一个可用的包装器。
     * <p>如果池为空，实现类应根据策略创建新实例或阻塞等待。</p>
     * @return 准备就绪的包装器实例
     */
    W acquire();

    /**
     * 执行池预热
     * @param prefillRatio 预热比例 (0.0 ~ 1.0)。
     * 例如 0.5 表示启动时填充 50% 的容量。
     * @throws IllegalArgumentException 如果比例不在有效范围内
     */
    WrapperPool<W> warmUp(double prefillRatio);
}
